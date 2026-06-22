# TEST.1a — Testcontainers Isolation + Credential Alignment

Date: 2026-06-22 | Branch: deploy/local | Base HEAD: 145e12c
Status: **IMPLEMENTED but MEASUREMENT BLOCKED** — environmental (Testcontainers cannot reach Docker Desktop 29.5.2). CTO decision required before the suite can be run/measured.

---

## What was implemented (test-infra only — no production code touched)

1. **Singleton-container base class** — `backend/src/test/java/vn/vtit/gemek/support/AbstractIntegrationTest.java`
   - Static `PostgreSQLContainer` (`postgres:15.18-alpine`) + `RedisContainer` (`redis:7.2.4-alpine`) — tags match the dev/prod compose stack for migration parity.
   - Started once per JVM (singleton, not `@Container` per-class) for speed; Ryuk tears down at exit.
   - `@DynamicPropertySource` rebinds `spring.datasource.url/username/password` and `spring.data.redis.host/port/password` to the containers. These take precedence over `application-test.yml`, overriding the hardcoded `localhost:5433/6380` dev targets. Redis runs no-auth; password bound to `""` (Lettuce sends no AUTH for empty).
2. **All 33 `@SpringBootTest` classes** now `extends AbstractIntegrationTest` (+ import). Mechanical; no other change. Compilation verified (tests compile and execute — they error only at container startup).
3. **Credential alignment (single source of truth = the test constants `GemekAdmin2026`)** — `application-test.yml` `app.admin.password` changed `Admin@123456 → GemekAdmin2026`. On a fresh container DB, `AdminSeeder` (phone default `0900000000`) would seed `0900000000 / GemekAdmin2026`, so test logins return 200.
4. **Redis**: required at startup (`RedisConfig`, `JwtAuthenticationFilter`, `AuthServiceImpl` token-revocation/rate-limit) → containerized (not faked).
5. Deps already present in `pom.xml`: `testcontainers:junit-jupiter`, `testcontainers:postgresql` (1.20.5), `com.redis:testcontainers-redis` (2.2.2), `docker-java-transport-httpclient5` (3.4.1).

This is the exact approach the task prescribed and is correct for any environment where Testcontainers can reach Docker (e.g. CI/Linux).

---

## BLOCKER — Testcontainers cannot reach Docker Desktop 29.5.2 (API 1.54)

Running the suite (`mvnw test`) fails at container startup for **every** `@SpringBootTest`:

```
org.testcontainers...DockerClientProviderStrategy -- Could not find a valid Docker environment.
  TcpDockerClientProviderStrategy: failed with BadRequestException (Status 400: {…/info stub…
     Labels:["com.docker.desktop.address=npipe://\\.\pipe\docker_cli"]})
  NpipeSocketClientProviderStrategy: failed with BadRequestException (Status 400: {…same stub…})
java.lang.IllegalStateException: Could not find a valid Docker environment
  at vn.vtit.gemek.support.AbstractIntegrationTest.<clinit>(AbstractIntegrationTest.java:45)
```

### Root cause
`docker-java 3.4.1` (the newest cached / the version Testcontainers 1.20.5 ships) is **incompatible with the installed Docker Desktop 29.5.2 / 4.76 (API 1.54)**. Docker returns HTTP **400** with a near-empty `/info` stub to docker-java's request, on every transport. A plain `curl` to the same daemon works:

| Probe | Result |
|-------|--------|
| `curl http://localhost:2375/_ping`, `/info`, `/v1.40,1.43,1.54/info` | **200** (full info) |
| docker-java via tcp `localhost:2375` (repo's forced strategy) | **400** stub |
| docker-java via npipe `docker_engine` (default) | **400** stub |
| docker-java via npipe `dockerDesktopLinuxEngine` (correct CLI pipe) + env strategy | **400** stub |
| docker-java with `DOCKER_API_VERSION=1.44` pinned | **400** stub |

So the daemon is reachable and healthy; only **docker-java's specific request** is rejected. Docker 29.5.2 / API 1.54 (June 2026) postdates every released docker-java, so a version bump is **speculative** and needs network access (no newer docker-java is cached: only 3.3.6 / 3.4.1 present).

### What this means for measurement
The 1a goal is to MEASURE how many of the 142 failures isolation+credential resolves and what second-wave (count-pollution) remains. **That measurement cannot be produced in this environment** — no `@SpringBootTest` can start a container, so the suite is currently *more* red locally (all integration tests error at `<clinit>`), not a meaningful 401-vs-count classification.

Pure-unit tests (no Spring context) still pass (e.g. `PhoneUtilsTest` 35, `AdminSeederTest` 4, `GlobalExceptionHandlerTest` 2).

---

## Decision required (one reply unblocks)

| Option | Action | Trade-off |
|--------|--------|-----------|
| **A (recommended)** — fallback to a dedicated migrated test DB | Keep the TC code for CI; for local runs, point `application-test.yml` at a separate **empty** DB on the running postgres (e.g. `gemek_test`), Flyway-migrated fresh each run. Delivers the 1a measurement NOW. | Deviates from "Testcontainers" locally; the DB persists between runs so committing tests re-pollute it (exactly the 1b concern) — acceptable for a one-shot measurement if dropped+recreated before the run. |
| **B** — upgrade docker-java/Testcontainers | Bump to the newest docker-java (needs network); re-probe against Docker 29. | Speculative — Docker 29.5.2 likely postdates any docker-java release; may still 400. |
| **C** — fix the Docker environment | Run tests against a docker-java-compatible engine (older Docker Desktop, or a Linux/WSL docker the client supports). | Env change outside the repo; not a code fix. |

Recommended: **A** — it is the fastest path to the gate's actual need (the second-wave measurement) while preserving the correct TC wiring for CI. I did NOT execute it unilaterally because it deviates from the prescribed Testcontainers approach (a CTO call).

---

## Files changed (uncommitted decision aside — see commit note)
- NEW `backend/src/test/java/vn/vtit/gemek/support/AbstractIntegrationTest.java`
- 33 `@SpringBootTest` classes: `+extends AbstractIntegrationTest` + import
- `backend/src/test/resources/application-test.yml`: `app.admin.password` → `GemekAdmin2026`
- Evidence: `reports/test1a-isolation.raw` (full failing run)

No production code touched. No auditing (AUD.2/AUD.3) code touched.
