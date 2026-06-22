# Docker Image Version Recommendations
Generated: 2026-06-03 | Branch: deploy/local

---

## 1. Actual Tags in Repo vs Provided Summary

| Image | Summary tag | Actual tag (repo) | Match? |
|-------|-------------|-------------------|--------|
| postgres | `postgres:15-alpine` | `postgres:15-alpine` (docker-compose.yml:15) | ✓ |
| redis | `redis:7-alpine` | `redis:7-alpine` (docker-compose.yml:34) | ✓ |
| minio/minio | `minio/minio:RELEASE.2024-11-07T00-52-20Z` | `minio/minio:RELEASE.2024-11-07T00-52-20Z` (docker-compose.yml:49) | ✓ |
| nginx | `nginx:1.25-alpine` | `nginx:1.25-alpine` (nginx/Dockerfile:35) | ✓ |
| node (build) | `node:20-alpine` | `node:20-alpine` (nginx/Dockerfile:10) | ✓ |

No mismatches. All 5 tags are currently floating or under-pinned (no patch-level for postgres, redis, nginx; no minor for node).

---

## 2. Recommendations Table

| Service | Current tag | Recommended tag | Reason | Risk of bumping now |
|---------|-------------|-----------------|--------|---------------------|
| postgres | `postgres:15-alpine` | `postgres:15.18-alpine` | Pin to verified minor; floating tag can pull unvetted patch silently | Low — minor upgrades within PG 15 are data-compatible; existing volume safe |
| redis | `redis:7-alpine` | `redis:7.2.14-alpine` | **Critical:** floating `7-alpine` can resolve to 7.4+ which is RSAL/SSPL licensed — must pin to last BSD-3 line (7.2.x) | Low functionally, but HIGH urgency to pin before first `docker compose pull` overwrites cached 7-alpine with 7.4+ |
| minio/minio | `minio/minio:RELEASE.2024-11-07T00-52-20Z` | `minio/minio:RELEASE.2025-10-15T17-29-55Z` | Already pinned; bump gets ~11 months of security patches | Medium — MinIO has had breaking config changes between releases; test bucket/credential flow after upgrade |
| nginx | `nginx:1.25-alpine` | `nginx:1.28.3-alpine` | 1.25 is end-of-life; 1.28 is current stable (even minor) | Low for static SPA serving; review nginx.conf for any deprecated directives |
| node (build) | `node:20-alpine` | `node:24-alpine` | Node 20 EOL April 2026; Node 22 is **Maintenance LTS** (not Active LTS) — Active LTS is Node 24 (Jod) | Low for build stage only; test that pnpm workspace build succeeds on Node 24 before committing |

---

## 3. Redis License Note

`redis:7-alpine` is a **floating major tag**. As of Redis 7.4.0 (released 2024-07-31), Redis switched from BSD-3-Clause to RSALv2/SSPLv1. If Docker resolves `7-alpine` to 7.4 or later, the running container is under a non-OSS license.

**7.2.x is the last BSD-3-Clause release line.** Latest: `redis:7.2.14-alpine` (2026-05-09).

Future option: **Valkey** (`valkey/valkey`) is the Linux Foundation fork of Redis 7.2 under BSD-3, actively maintained. Switching to Valkey would be an architectural change requiring a DECISIONS.md entry and CTO approval.

---

## 4. Node.js LTS Clarification

The task assumed Node 22 is Active LTS. **It is not.** Current status as of 2026-06-03:

| Version | Status | EOL |
|---------|--------|-----|
| Node 20 | Maintenance LTS | 2026-04-30 (already EOL) |
| Node 22 | Maintenance LTS | 2027-04-30 |
| Node 24 | **Active LTS** (Jod) | 2028-04-30 |

Recommendation bumped to `node:24-alpine` accordingly.

---

## 5. Verification Method

| Image | Source | Command / URL |
|-------|--------|---------------|
| postgres:15.18-alpine | Docker Hub API | `GET https://hub.docker.com/v2/repositories/library/postgres/tags/?page_size=100&name=15` — highest `15.x-alpine` tag returned; last_updated 2026-05-16 |
| redis:7.2.14-alpine | Docker Hub API | `GET https://hub.docker.com/v2/repositories/library/redis/tags/?page_size=100&name=7.2` — highest `7.2.x-alpine` tag returned; last_updated 2026-05-09 |
| minio RELEASE.2025-10-15T17-29-55Z | GitHub Releases | `https://github.com/minio/minio/releases` — latest release tag as of query date [VERIFY: cross-check against `https://hub.docker.com/r/minio/minio/tags` before deploying] |
| nginx:1.28.3-alpine | Docker Hub API | `GET https://hub.docker.com/v2/repositories/library/nginx/tags/?page_size=100&name=1.28` — `1.28.3-alpine` found |
| node:24-alpine | nodejs.org release schedule | `https://nodejs.org/en/about/previous-releases` — Node 24 confirmed Active LTS; Node 22 confirmed Maintenance LTS |

---

## 6. Deploy-time Caution

### Postgres minor bump with existing volume
Bumping `postgres:15-alpine` → `postgres:15.18-alpine` against an existing `postgres_data` volume is **safe**. PostgreSQL minor releases within the same major version are data-format compatible and require no `pg_upgrade`. The container will start normally and apply any catalog updates automatically. Recommended: take a `pg_dump` backup before first `docker compose up` with the new image regardless.

### Redis 7-alpine → 7.2.14-alpine with existing volume
Redis RDB/AOF format is stable across 7.x patch versions. Switching from the floating `7-alpine` (which may already be cached as 7.0–7.2) to explicit `7.2.14-alpine` is safe **as long as the cached image resolves to 7.2.x**. Run `docker inspect gemek-redis | grep Image` on a running instance to confirm the current resolved version. If already on 7.2.x: no risk. If already on 7.4+: downgrade risk (7.4→7.2 RDB may have format differences — flush and repopulate from app, or take an RDB snapshot first).

### Switching redis → Valkey
This is an **architecture change** requiring:
1. A new DECISIONS.md entry
2. CTO approval
3. Updated docker-compose.yml image reference
4. Verification that Spring Data Redis / Lettuce client is compatible (it is — Valkey is a drop-in replacement at the protocol level)

Do not make this switch autonomously.

---

## 7. Summary — Recommended Tags

```
postgres:15.18-alpine
redis:7.2.14-alpine
minio/minio:RELEASE.2025-10-15T17-29-55Z   [VERIFY against Docker Hub before deploy]
nginx:1.28.3-alpine
node:24-alpine
```
