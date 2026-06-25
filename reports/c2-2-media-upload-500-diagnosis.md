# C2.2 / C2.3a — Announcement media upload HTTP 500 — Root-cause diagnosis

**Type:** DIAGNOSE-ONLY (read-only). No fix applied, no rebuild, no migration, no MinIO mutation.
**Date:** 2026-06-25
**HEAD:** `c9c88ab` (after `de37123` helper, `6e6eb42` handoff freeze). Working tree: only pre-existing
untracked `reports/*` + this diagnosis; no tracked code changed.

## Symptom

`POST /api/announcements/{id}/media` (C2.2 media upload) returns **`500 INTERNAL_ERROR — "File upload
failed."`** — first hit during the C2.3a smoke (`scripts/smoke-c2-3a.sh` step 4, upload cover), reproduced
deterministically. PROGRESS labels C2.2 "DONE (smoked)", but this is the **first time the upload endpoint
is actually exercised against this dev DB/MinIO stack** — the label did not reflect a real upload run.

## Step 1 — Stacktrace (authoritative root cause)

Reproduced upload (fresh draft `aa9ce7eb-7b18-4e8b-9dfd-e66ea9852aaa`). Backend log
(`scratchpad/upload-500-logslice.txt`):

```
DEBUG AnnouncementServiceImpl : uploadMedia — announcementId=aa9ce7eb-…, kind=cover
ERROR FileStorageService      : MinIO upload failed for key
      announcements/aa9ce7eb-…/a9b24286-….png: The specified bucket does not exist
io.minio.errors.ErrorResponseException: The specified bucket does not exist
        at io.minio.S3Base$1.onResponse(S3Base.java:747)  [minio-8.5.9.jar]
        at okhttp3.internal.connection.RealCall$AsyncCall.run(RealCall.kt:519)
WARN  GlobalExceptionHandler  : AppException … INTERNAL_ERROR — File upload failed.
```

Root cause = the MinIO `PutObject` is rejected by the server with **"The specified bucket does not exist"**;
`FileStorageService` wraps it as the generic `INTERNAL_ERROR "File upload failed."` 500. The request reaches
the service layer fine (auth, multipart, draft lookup all pass) — it dies only at object storage.

## Step 2 — DB state (hypothesis A: V21 not migrated) → RULED OUT

DEV db `gemek` (user `gemek`, NOT `gemek_test`):
- `SELECT to_regclass('public.announcement_media')` → **`announcement_media`** (table exists).
- `flyway_schema_history` top rows: **V21 "create announcement media" `success = t`** (V14–V21 all `t`).

V21 is present and successful on dev. The table is not the problem.

## Step 3 — MinIO state (hypothesis B) → CONFIRMED CAUSE

- Bucket name read from config (not guessed): `application.yml:71` → `bucket: ${MINIO_BUCKET:gemek}`;
  `.env:23` → `MINIO_BUCKET=gemek`; endpoint `.env:24` / `docker-compose.yml:82` → `http://minio:9000`.
- `gemek-minio` container is **Up (healthy)**.
- **MinIO is reachable**: the error string above is an HTTP error *response from* MinIO (S3 `NoSuchBucket`),
  not a connection-refused / timeout — so the network path backend→`minio:9000` is fine.
- **The `gemek` bucket does not exist** in this MinIO instance — stated authoritatively by MinIO's own
  response via the app's client. `docker-compose.yml:9` documents the 9001 console as "first-run bucket
  setup only"; that one-time provisioning step was never performed on this dev instance, and the app does
  **not** auto-create the bucket on startup.

## Step 4 — Verdict

**Primary root cause: the MinIO bucket `gemek` was never created on this dev stack (hypothesis B).**
Cited: stacktrace `ErrorResponseException: The specified bucket does not exist` (FileStorageService /
S3Base.java:747) + config `application.yml:71` / `.env:23`.

Other hypotheses, explicitly ruled out with evidence:
- **A — V21 not migrated / stale backend image:** RULED OUT. `announcement_media` exists; V21 `success=t`
  in `flyway_schema_history`. The schema is current.
- **Tika / content validation:** RULED OUT. Failure occurs at the MinIO `PutObject`, *after* the service
  accepts the request; the uploaded bytes are a valid 1×1 PNG; there is no Tika/validation exception in the
  log, and a type rejection would be the documented `400 ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED`, not a 500.

This blocks **all** C2.2/C2.3 media uploads on this dev stack, not just the smoke.

**Leftover artifacts (NOT cleaned up, per instruction):** draft announcement
`aa9ce7eb-7b18-4e8b-9dfd-e66ea9852aaa` (created by this repro). No partial media row leaked —
`SELECT count(*) FROM announcement_media WHERE announcement_id='aa9ce7eb-…'` = **0** (the failed
`PutObject` rolled back / preceded the row insert; storage is clean). An earlier smoke run also left
similar `[C2.3a SMOKE]` / `[DIAG]` drafts.

## Recommended fix (for CTO approval — NOT applied)

This is a **dev-environment provisioning gap, not a code defect** — so it is **NOT** a rebuild and **NOT**
a code change. The fix is to **create the `gemek` bucket once** on the dev MinIO (e.g. via the 9001 console
or `mc mb local/gemek`), then re-run `scripts/smoke-c2-3a.sh`; `POST …/media` should then return `201` and
`GET /api/announcements/{id}` should return `media` with presigned entries.

Optional hardening (separate decision, a real code change — do NOT bundle with the dev fix): have the app
**ensure the bucket on startup** (`makeBucket` if absent) so fresh environments don't trip this. That
touches production code and should be its own gated change, not part of unblocking the smoke.

**C2.3a smoke remains BLOCKED until the bucket exists.**
