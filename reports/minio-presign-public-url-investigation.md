# MinIO presigned-URL public-host investigation (C2.1/C2.2 storage layer)

**Type:** INVESTIGATE-ONLY (read-only). No code/config change, no rebuild. For CTO ruling.
**Date:** 2026-06-25  **HEAD:** `62953ed`.
**Scope:** the presign/storage layer (C2.1/C2.2 `FileStorageService`), NOT the C2.3a renderer — the renderer
is correct (binds `<img src>` to the manifest url).

## Symptom

`GET /api/announcements/{id}` returns `media[].url` like
`http://minio:9000/gemek/announcements/{id}/{uuid}.png?X-Amz-…&X-Amz-SignedHeaders=host&X-Amz-Signature=…`.
`minio:9000` is the **internal docker service name** — it resolves only inside the compose network. The
browser on the host cannot resolve it, so every announcement image (cover banner + inline) is a broken
`<img>`. This is the **first real browser load of a presigned URL**, so it surfaces a pre-existing
C2.1/C2.2 gap (ticket photos F-05 would hit the same wall).

## Step 1 — How the presigned URL is built (SINGLE endpoint)

- One `MinioClient` bean, built from a **single** `MINIO_ENDPOINT`: `MinioConfig.java:51-53`
  (`.endpoint(endpoint)`), `endpoint` field `:28`. Value `http://minio:9000` (`.env:MINIO_ENDPOINT`,
  `docker-compose.yml:82`).
- That **same** client is used for BOTH SDK byte ops AND presign:
  `FileStorageService.upload()` → `minioClient.putObject(...)` (`FileStorageService.java:66-68`), and
  `FileStorageService.presign()` → `minioClient.getPresignedObjectUrl(...)` (`:89-96`). The presigned URL's
  host is therefore always the internal `minio:9000`.
- **No** public/external endpoint override exists anywhere — repo-wide grep for
  `MINIO_PUBLIC*/MINIO_EXTERNAL*/publicEndpoint/externalEndpoint` returns nothing. There is no
  half-built knob to flip; this is not config-only today.

## Step 2 — What the browser can reach (port 9000 NOT published)

- `docker-compose.yml:60-61` publishes **only** `9001:9001` (MinIO console). Port **9000 (the S3 API) is
  NOT published to the host** → `http://localhost:9000` is **not** browser-reachable in dev as-is.
- The internal endpoint `minio:9000` must STAY internal for `putObject` (the backend container resolves it
  over the compose network; the host/browser cannot). A browser-reachable host (`localhost:9000`) would in
  turn not resolve from inside the backend container. **A single endpoint cannot serve both** roles → any
  fix needs the presign URL generated against a *different* (public) host than the SDK byte ops use.

## Step 3 — Signature constraint (SignedHeaders=host)

The presigned URL carries `X-Amz-SignedHeaders=host` (AWS SigV4, MinIO default — visible in the symptom
URL). The signature covers the **host** the URL was generated for. Therefore:
- A client-side or nginx **host-rewrite of an already-signed URL → `403 SignatureDoesNotMatch`** (the host
  the browser sends no longer matches the signed host).
- The public host must be the one the URL is **generated against** (baked into the signature), and MinIO
  must be reachable at that exact host:port the browser uses. No post-hoc rewrite works.

## Step 4 — Production intent (NOT specified — do not invent)

- `nginx/nginx.conf` has **no** MinIO proxy/route (grep: none) — only admin :80 + resident :81 SPA vhosts
  with `/api` → backend.
- `DECISIONS.md:447`: "Self-hosted MinIO. DB stores object keys, not full URLs. Backend issues presigned GET
  URLs." `ARCHITECTURE.md:459`: presign is permission-gated. Neither describes a **public host / subdomain /
  nginx-fronting** for MinIO object delivery.
- **`[TODO: kiểm tra]`** — how MinIO is meant to be browser-exposed in production (public S3 subdomain? nginx
  route? CDN?) is **not specified anywhere** in ARCHITECTURE/DECISIONS/compose/nginx. Not invented here.

## Step 5 — Fix options (for CTO ruling — NONE applied)

All options keep the existing security posture: presign stays **scope-gated** (C2.1 access check before
minting) and **short-lived** (10-min expiry, `FileStorageService` PRESIGN_EXPIRY). Only the *host the URL is
signed for* changes.

### Option A — dedicated PUBLIC presign endpoint (recommended)
Add a second, presign-only `MinioClient` built against a configurable public endpoint; byte ops keep the
internal client.
- **Code (gated):** `MinioConfig` — add a `publicEndpoint` field + a second `MinioClient`/named bean built
  with `.endpoint(publicEndpoint)`; `FileStorageService.presign()` uses that presign client, while
  `upload()`/`delete()` keep the internal one. ~1 class + the presign line.
- **Config/compose:** new env `MINIO_PUBLIC_ENDPOINT`; dev = `http://localhost:9000` **and** publish
  `9000:9000` in `docker-compose.yml`; prod = the real public object URL/subdomain. Default it to the
  internal endpoint when unset (no behaviour change until configured).
- **Dev effort:** low–med. **Prod correctness:** correct with a real public host/subdomain. **Security:**
  unchanged (still gated + short-lived). **Type:** code change (gated) + config.
- Standard MinIO/S3 pattern (presign-only client against the public host); signature is generated for the
  public host so SignedHeaders=host matches the browser → no 403.

### Option B — front MinIO behind nginx on a browser host, presign against it
Add an nginx route/vhost that proxies to `minio:9000`, and generate presigns against that public host.
- **Code:** still needs the dual-endpoint split (presign host ≠ internal put host) — i.e. Option A’s code
  PLUS nginx. **Infra:** new nginx server/location for object delivery; prod subdomain + TLS.
- **Dev effort:** higher (nginx + still dual client). **Prod correctness:** good, and avoids exposing 9000
  directly (everything behind nginx). **Security:** unchanged. **Type:** code + nginx + config.
- Heavier; main upside is not publishing 9000 raw and unifying delivery behind nginx for prod.

### Option C — config-only (NOT available)
Not possible today: no existing public-endpoint knob (Step 1). Noted only to rule it out — any fix requires
the dual-endpoint code change.

## Recommendation

**Option A** — add `MINIO_PUBLIC_ENDPOINT` + a presign-only client. Minimal, standard, unblocks dev
immediately (publish 9000 + `MINIO_PUBLIC_ENDPOINT=http://localhost:9000`), and prod-correct once the real
public object host/subdomain is decided (resolve the Step-4 `[TODO]`). Consider Option B's nginx-fronting as
the prod delivery shape if exposing 9000 directly is undesirable — it builds on the same code change.

**This is a pre-existing C2.1/C2.2 gap** (internal host baked into every presign), surfaced now by the first
browser load. It blocks the C2.3a positive-render + out-of-scope-visual smoke; awaiting CTO ruling before any
code/config change.
