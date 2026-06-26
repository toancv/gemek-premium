# C3 P2 smoke — oversize attachment upload: 11MB hangs, 35MB errors cleanly (DIAGNOSE-ONLY)

**Type:** DIAGNOSE-ONLY (read-only, cite `file:line`). No code/config change. For CTO ruling.
**Date:** 2026-06-26 **HEAD:** `1d43176` (C3 P2). Stack up (backend/nginx/minio/postgres/redis running).
**Symptom:** uploading a document over the 10MB/file cap behaves inconsistently — a **35MB** file returns an
error, an **11MB** file **hangs on "đang tải lên"** with no error.

---

## STEP 1 — Size-limit layers in the upload path (POST /api/announcements/{id}/attachments via admin :80)

| Layer | Knob | Value | Cite |
|---|---|---|---|
| nginx (global http) | `client_max_body_size` | **20m** | `nginx/nginx.conf:16` |
| nginx (:80 admin `/api/`) | `client_max_body_size` | none → **inherits 20m** | `nginx/nginx.conf:21-38` (no override in server/location) |
| nginx (:80 `/api/`) | `proxy_request_buffering` | unset → default **on** (buffers full body) | `nginx/nginx.conf:30-38` |
| nginx (:80 `/api/`) | `proxy_read_timeout` | 60s | `nginx/nginx.conf:36` |
| Spring multipart | `spring.servlet.multipart.max-file-size` | **10MB** (10485760 B) | `backend/src/main/resources/application.yml:27` |
| Spring multipart | `spring.servlet.multipart.max-request-size` | 55MB | `application.yml:28` |
| Tomcat connector | `server.tomcat.max-swallow-size` | **UNSET → default 2MB** | (no entry in `application.yml`; grep of `src/main/resources` finds none) |
| App (in-service) | per-file cap → `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE` (400) | 10MB | `AnnouncementServiceImpl.java` `uploadAttachment` (per-file guard); code `ErrorCode.java:118` |
| **Exception handling** | `@ExceptionHandler(MaxUploadSizeExceededException)` | **MISSING** | none in `common/exception/` (grep finds only the `ErrorCode` enum entry) |

(The :8090 `client_max_body_size 50m` at `nginx.conf:96` is the MinIO delivery vhost — NOT the upload path.)

**Ordering of caps on the upload path:** nginx **20m** (edge) → Spring multipart **10MB** (during
`DispatcherServlet.checkMultipart`, BEFORE the controller) → the in-service 10MB cap. **The Spring multipart
10MB trips first, so the app's own `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE` cap is NEVER reached for a multipart
file part** — it could only fire if the multipart limit were higher than the in-service limit.

---

## STEP 2 — Reproduction (running dev stack, ADMIN, fresh draft `89f93634-…`)

Raw evidence in scratchpad (`f11-resp.txt`, `f35-resp.txt`). curl with `--max-time 45`:

| File | HTTP | time | Layer that rejected | Response body |
|---|---|---|---|---|
| **11 MB** | **500 `INTERNAL_ERROR`** | 0.21s | Spring multipart (passed nginx 20m) | `{"error":"INTERNAL_ERROR","message":"An unexpected error occurred."}` |
| **35 MB** | **413** | 0.003s | **nginx** (edge, `client_max_body_size 20m`) | nginx HTML `413 Request Entity Too Large … nginx/1.28.3` |

**Backend log for the 11MB request (the smoking gun):**
```
ERROR ... GlobalExceptionHandler : Unhandled exception on /api/announcements/.../attachments
org.springframework.web.multipart.MaxUploadSizeExceededException: Maximum upload size exceeded
  at ...DispatcherServlet.checkMultipart(DispatcherServlet.java:1228)
Caused by: ...FileSizeLimitExceededException: The field file exceeds its maximum permitted size of 10485760 bytes.
```
→ `MaxUploadSizeExceededException` is **UNHANDLED** → falls to the generic catch-all → **HTTP 500
`INTERNAL_ERROR`**. It is thrown in `checkMultipart` (before the controller), confirming the in-service cap is
bypassed.

### Why curl gets a clean 500 but the BROWSER hangs
- **curl** buffers and sends the FULL 11MB body, THEN reads the response → it receives the 500 cleanly.
- A **browser XHR** STREAMS the 11MB. Tomcat trips the 10MB limit and produces the 500 response **while the
  browser is still uploading** (~1MB+ still unread on the wire). With `max-swallow-size` at its **default
  2MB**, Tomcat will not reliably drain the in-flight remainder and **resets the connection** mid-upload. The
  browser's still-in-"upload" XHR sees a connection reset → axios rejects with a **network error and NO
  `err.response`** (no status, no body). The spinner ("đang tải lên") never clears into a readable error =
  the observed **hang**. (The 35MB case is rejected by nginx at the edge BEFORE any streaming race, so the
  browser gets a clean 413.)

So the inconsistency is purely **which layer rejects + transport timing**: 35MB → nginx edge 413 (clean);
10–20MB → Spring multipart → unhandled 500 / browser connection-reset hang.

---

## STEP 3 — FE side: NO size pre-validation

- `AnnouncementAttachmentsManager.tsx` `onPick` calls `upload.mutateAsync({ id, file })` **directly — no
  `file.size` check** before upload. Same for `AnnouncementMediaManager.tsx` `onPick`.
- The 413 special-case (`AnnouncementAttachmentsManager.tsx` `errorText` / `AnnouncementMediaManager.tsx:67-70`)
  only fires when `err.response.status === 413`. That is TRUE for the nginx-edge 35MB case but **FALSE for the
  10–20MB case** (it's a 500, or a connection-reset network error with no `err.response` at all) → the
  special-case **cannot fire** → generic fallback at best, hang at worst. So the FE depends entirely on a
  clean server error that the over-cap-but-under-20m case does not produce.

---

## STEP 4 — Verdict + recommended fix (NOT applied)

**Root cause (two compounding server defects + one FE gap):**
1. **No `@ExceptionHandler(MaxUploadSizeExceededException)`** → an over-multipart-cap upload (10–20MB) returns
   an unhandled **500**, not a clean coded 413/400. (cited: "Unhandled exception" log + grep of `common/exception`.)
2. **`server.tomcat.max-swallow-size` left at default 2MB** → for a streaming browser upload, Tomcat resets the
   connection mid-upload instead of draining the remainder and delivering the error → **hang** (no `err.response`).
3. **FE sends oversize bytes with no `file.size` pre-check** → it relies on a clean server error that the
   10–20MB window doesn't produce.

The 35MB "works" only because it exceeds nginx's 20m and is killed at the edge with a real 413 before the
streaming race; the 11MB sits in the dead zone `(10MB cap, 20m nginx]` where the multipart layer rejects
mid-stream.

**Recommended fix (for CTO — pick a/b/both; (a)+(b) recommended):**
- **(a) FE pre-validate BEFORE upload (clean UX fix).** In `AnnouncementAttachmentsManager` `onPick`, reject
  instantly with the existing VN strings when `file.size > 10*1024*1024`
  (`ANNOUNCEMENT_ATTACHMENT_TOO_LARGE` message), when the running total + file.size > 50MB
  (`…LIMIT_EXCEEDED`), or when count ≥ 5 (already cap-disabled). No oversize request is ever sent → the hang
  and the 500 both disappear for honest clients. Apply the same to `AnnouncementMediaManager` (images, 10MB/5/50MB).
- **(b) Server defense-in-depth so oversize ALWAYS returns a clean error (covers non-browser / bypass clients):**
  1. Add `@ExceptionHandler(MaxUploadSizeExceededException)` (and `MultipartException`) in
     `GlobalExceptionHandler` → return **413** (or 400 `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE`) with the standard
     error body, so the FE 413/coded path catches it.
  2. Set **`server.tomcat.max-swallow-size: -1`** (unlimited swallow) — or ≥ `max-request-size` (55MB) — so
     Tomcat drains the in-flight body and the clean error reaches the browser instead of a connection reset.
     Without this, even a handler-produced 413 can be lost to the mid-upload reset.
  3. Optional consistency: align nginx `client_max_body_size` on `/api/` with the app caps so the edge-reject
     boundary (currently 20m) matches the 10MB intent (e.g. a small headroom over `max-request-size`).

**Scope: this is GENERAL, not attachments-specific.** Images use the SAME servlet multipart path
(`max-file-size 10MB`), the SAME missing handler, and the SAME 2MB swallow default — an >10MB IMAGE upload
hits the identical 500/hang. The image manager's 413 special-case only catches the nginx-edge (>20m) case.

**Prod implication:** prod nginx may set a different `client_max_body_size`, shifting the "edge vs multipart"
boundary; the handler gap (1) and `max-swallow-size` default (2) are app-level and affect EVERY environment.
The FE pre-validation (a) is environment-independent.

---

## OPEN RULING FOR CTO
Apply **(a)** FE pre-validation, **(b)** server defense-in-depth (multipart `@ExceptionHandler` + `max-swallow-size`),
or both (recommended)? And whether to fold the same fix into the IMAGE managers (same defect). No code/config
changed this turn.
