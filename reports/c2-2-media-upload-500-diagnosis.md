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

---

## Step-5 body-update 500 (after bucket created)

**Date:** 2026-06-25. With the `gemek` bucket created, the smoke now passes step 4 (upload succeeds —
log: `MinIO upload succeeded … kind=COVER/INLINE`) but fails at **step 5 "Updating draft body"** with
`curl: (22) … returned error: 500` on `PUT /api/announcements/{id}`. Step 5 is the first request whose
body carries the hostile payloads (`javascript:`, `data:`, raw `<img onerror>`, `announcement-media:`).

### Step 1 — Stacktrace (class: malformed request, NOT a deep app exception)

Backend log (`scratchpad/step5-500-logslice.txt`), first exception on the failed PUT:

```
ERROR GlobalExceptionHandler : Unhandled exception on /api/announcements/eb1f8964-…
org.springframework.http.converter.HttpMessageNotReadableException:
        JSON parse error: Invalid UTF-8 start byte 0x97
Caused by: com.fasterxml.jackson.databind.JsonMappingException: Invalid UTF-8 start byte 0x97
  at [Source: REDACTED …; line: 3, column: 181]
        (through reference chain: …dto.UpdateAnnouncementRequest["content"])
Caused by: com.fasterxml.jackson.core.JsonParseException: Invalid UTF-8 start byte 0x97
```

This is a **request-parse** failure (`HttpMessageNotReadableException`) — the body bytes are not valid
UTF-8 — NOT a validation / DataIntegrity / markdown / NPE exception. Byte `0x97` is the Windows-1252
(CP1252) encoding of an em-dash `—` (U+2014); valid UTF-8 for `—` is `0xE2 0x80 0x94`.

### Step 2 — What the server actually received (request-shape)

The script builds the body **correctly with jq** (`smoke-c2-3a.sh:118` `jq -n --arg content "$BODY_MD"`),
so the JSON is structurally well-formed — this is **NOT** a string-concatenation / quote-escaping bug. The
defect is a **character-encoding** one, proven by isolation on this Windows Git-Bash host:

| Transport of the *same* em-dash body | On-wire bytes | Result |
|---|---|---|
| `curl --data-binary @tmpfile` | `e2 80 94` (UTF-8) | **200**, content stored as `inert — dash test` |
| `curl -d "$VAR"` (script line 130) | `97` (CP1252) | **500** `Invalid UTF-8 start byte 0x97` |

Root mechanism: **Git-Bash/MSYS transcodes non-ASCII bytes in a curl command-line argument to the active
ANSI codepage (CP1252)**. The seed body contains a literal em-dash at `smoke-c2-3a.sh:99`
("MUST be inert — no external fetch", bytes `e2 80 94` in the file — the *file* is valid UTF-8), but
passing it through `-d "$UPDATE_BODY"` (line 130) mangles `—` to `0x97`, so the server receives invalid
UTF-8. jq itself preserves UTF-8 (verified: `jq --arg` output keeps `e2 80 94`).

### Step 3 — Endpoint contract (read-only)

- `PUT /api/announcements/{id}` (ADMIN), `docs/API-SPEC.md:2056-2064`. Same body as POST minus
  `publishNow`; `409 CONFLICT` if already published. Write-time content guards (API-SPEC:2017/2062):
  `400 ANNOUNCEMENT_CONTENT_TOO_LONG` (>20000) and `400 ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED` (raw HTML).
- DTO `UpdateAnnouncementRequest.java:30` — `content` is a plain `String` with **no** `@NotBlank`/`@Size`;
  the length + HTML guards are service-level in `AnnouncementServiceImpl.java:879` (too-long) and `:883`
  (raw-HTML reject). The script supplies all fields the controller needs — **no required field is omitted**.
- `GlobalExceptionHandler.java` has **no** `@ExceptionHandler(HttpMessageNotReadableException.class)`; an
  unparseable body therefore falls through to the catch-all `@ExceptionHandler(Exception.class)`
  (lines 188-192) → logged "Unhandled exception" → **500 INTERNAL_ERROR** (a 400 would be more correct).

### Step 4 — Verdict

**Primary root cause: hypothesis (2) — a fixture/script defect, but NOT the jq-escaping form hypothesised.**
A literal **em-dash (non-ASCII) in the seed body** (`smoke-c2-3a.sh:99`), sent via `curl -d "$VAR"`
(`:130`) on Windows Git-Bash, is transcoded to invalid UTF-8 (`0x97`); the server cannot parse it. Cited:
stacktrace `HttpMessageNotReadableException: Invalid UTF-8 start byte 0x97` + the transport isolation table
+ file bytes at `:99`. **Hypothesis (1) — app bug on a valid request — is RULED OUT:** a clean-UTF-8 body
containing the same `javascript:` / `data:` / `announcement-media:` payloads stores fine (**200**, verified).

Two secondary findings (neither blocks, both for CTO awareness):
1. **Mild app gap (a touch of hypothesis 1):** `HttpMessageNotReadableException` is unmapped, so a malformed
   body returns **500 instead of 400** (`GlobalExceptionHandler.java:188-192` catch-all). Benign — only
   reachable with a malformed request a correct client never sends; **no security relevance**.
2. **Fixture design vs write-guard:** even after the encoding fix, the raw `<img src=x onerror=alert(1)>`
   line (`smoke-c2-3a.sh:109`) is **correctly** rejected by the write-time HTML guard
   (`AnnouncementServiceImpl.java:883` → `400 ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`, verified on a
   clean-UTF-8 repro). Raw HTML **cannot** be stored via the API by design — so that XSS vector is closed
   at *write* time (stronger than render-time inertness). The renderer's raw-HTML inertness is covered by
   the UI unit tests, not seedable through the update endpoint.

**Leftover (NOT cleaned, per instruction):** draft `eb1f8964-cfbb-470d-aeb1-4f61208f18c6` from the real
script run (has 2 media rows from the successful step 4); plus `[DIAG step5]` repro drafts.

### Recommended fix (script/fixture — for CTO approval, NOT applied)

1. **Make the seed body ASCII-only** (replace `—` with `-` at `smoke-c2-3a.sh:99`) **and/or** send the body
   via `curl --data-binary @tmpfile` instead of `-d "$VAR"` (`:130`) so MSYS cannot transcode the argument —
   the latter is the robust, portable fix.
2. **Drop the raw `<img>` line** (`:109`) from the seeded body, since the write guard legitimately rejects
   it (400); add a note that raw-HTML-in-stored-content is impossible by design and its render inertness is
   covered by UI tests.
3. Optional, separate CTO decision (real code change, do NOT bundle): map
   `HttpMessageNotReadableException → 400` in `GlobalExceptionHandler`. Cosmetic.

**C2.3a smoke remains BLOCKED at step 5 pending the fixture fix (items 1-2 above).**

### Resolution (2026-06-25)

Fixture fix applied to `scripts/smoke-c2-3a.sh` (script only, no app change): all JSON request bodies now go
through a `send_json` helper that writes the jq-built body to a temp file and sends it with
`curl --data-binary @file` instead of `-d "$VAR"` — so MSYS never transcodes argument bytes — and the
em-dash in the seed body was replaced with ASCII; the write-blocked raw `<img>` line was dropped. The smoke
now seeds **end-to-end** (steps 1-7, exit 0). Verified as ADMIN on the new announcement
`f526ca49-b740-4748-9286-2a7e6c949df9`: `GET /api/announcements/{id}` returns `media` with **2 presigned
entries (COVER + INLINE)**, and the stored `content` holds all **6 storable lines intact, ASCII-clean (no
UTF-8 parse error)**. The secondary app gap (`HttpMessageNotReadableException` → 500 not 400) is left as a
benign, separately-gated cosmetic item — not fixed here.
