#!/usr/bin/env bash
#
# smoke-c2-3a.sh — re-runnable XSS smoke-seed helper for C2.3a (announcement image render).
#
# Seeds ONE fresh announcement against the RUNNING dev stack so the CTO can run the
# manual browser XSS smoke (checklist in reports/c2-3a-announcement-image-render.md).
# It only exercises real, in-contract endpoints (login, create, media upload, update,
# publish, detail). It does NOT touch gemek_test, Flyway, the DB, or MinIO directly.
#
# Requirements: bash, curl, jq.
# Config (env, with defaults — NO secrets hardcoded):
#   BASE_URL          default http://localhost   (admin/nginx :80 — used for /api calls)
#   RESIDENT_BASE_URL default http://localhost:81 (resident portal — used only for the printed detail URL)
#   ADMIN_PHONE     required (admin login identifier; phone-based)
#   ADMIN_PASSWORD  required (read from env; never committed)
#
# Usage:
#   ADMIN_PHONE=09xxxxxxxx ADMIN_PASSWORD='***' ./scripts/smoke-c2-3a.sh
#
set -euo pipefail

# --- config -----------------------------------------------------------------
BASE_URL="${BASE_URL:-http://localhost}"
# Resident portal is served on a SEPARATE nginx vhost/port (:81) from the admin portal (:80).
# The seeded announcement is only deep-linkable on the RESIDENT app (route announcements/:id);
# opening it on the admin port hits the admin SPA fallback (a dashboard), not the detail page.
RESIDENT_BASE_URL="${RESIDENT_BASE_URL:-http://localhost:81}"
ADMIN_PHONE="${ADMIN_PHONE:?set ADMIN_PHONE (admin login phone)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?set ADMIN_PASSWORD (do not hardcode)}"

# Known password this script FORCES onto the residents it picks (via the admin reset endpoint),
# so the printed logins actually work. Meets the BE complexity rule (>=8, upper+lower+digit+special).
SMOKE_PASSWORD="Smoke@1234"

for bin in curl jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "FATAL: '$bin' not found on PATH" >&2; exit 1; }
done

# SAFETY GUARD: this script MUTATES user passwords (admin reset on the picked residents). Refuse to
# run against anything but a local dev stack — never a shared/staging/prod host.
case "$BASE_URL" in
  http://localhost|http://localhost:*|http://127.0.0.1|http://127.0.0.1:*) : ;;
  *) echo "FATAL: refusing to run — BASE_URL='$BASE_URL' is not localhost/127.0.0.1. This script resets resident passwords and must only target a local dev stack." >&2; exit 1 ;;
esac

API="${BASE_URL%/}/api"

# Two DISTINCT, eyeball-able 64x64 solid-colour PNGs (real PNG magic bytes — Tika accepts image/png):
# COVER = blue (#2176FF), INLINE = orange (#FF6D21), so a smoker can tell the cover banner from the
# inline image at a glance. Larger than the old 1x1 (which rendered as an invisible dot).
COVER_B64="iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAATklEQVR42u3PQQkAAAgEsKtiSetrBN/CYAWW6nktAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgKXBTAeYXjHRmbVAAAAAElFTkSuQmCC"
INLINE_B64="iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAT0lEQVR42u3PQQkAAAgEsKtiQPs/NYJvYbACy3S9FgEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQGBywImJdFpAMTM7wAAAABJRU5ErkJggg=="
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
COVER_FILE="$TMP_DIR/cover.png"
INLINE_FILE="$TMP_DIR/inline.png"
printf '%s' "$COVER_B64"  | base64 -d > "$COVER_FILE"
printf '%s' "$INLINE_B64" | base64 -d > "$INLINE_FILE"

say() { printf '%s\n' "$*" >&2; }
fail() { printf 'FAILED at step: %s\n' "$*" >&2; exit 1; }

# Auth header array — empty until login succeeds (login itself sends no auth header).
AUTH=()

# Send a JSON request body via a temp file + curl --data-binary, NOT -d "$VAR".
# On Windows Git-Bash/MSYS, non-ASCII bytes in a curl command-line ARGUMENT are
# transcoded to the ANSI codepage (em-dash U+2014 -> single byte 0x97), corrupting the
# body to invalid UTF-8 (server then 500s on parse). Reading the bytes from a file
# avoids the argument layer entirely. Echoes the response body to stdout.
send_json() {  # $1=method  $2=url  $3=json-body
  local bf
  bf="$(mktemp "$TMP_DIR/body.XXXXXX.json")"
  printf '%s' "$3" > "$bf"
  curl -fsS "${AUTH[@]}" -X "$1" "$2" \
    -H 'Content-Type: application/json' --data-binary @"$bf"
}

# --- 1) admin login (phone-based) -------------------------------------------
say "[1/7] Logging in as ADMIN ($ADMIN_PHONE) ..."
LOGIN_JSON="$(send_json POST "$API/auth/login" \
  "$(jq -n --arg p "$ADMIN_PHONE" --arg pw "$ADMIN_PASSWORD" '{phone:$p, password:$pw}')")" \
  || fail "1 admin login (HTTP error — check BASE_URL/stack up/credentials)"
TOKEN="$(jq -r '.accessToken // .data.accessToken // empty' <<<"$LOGIN_JSON")"
[ -n "$TOKEN" ] || fail "1 admin login (no accessToken in response: $LOGIN_JSON)"
AUTH=(-H "Authorization: Bearer $TOKEN")

# --- 2) pick a single in-scope target block ---------------------------------
say "[2/7] Resolving a target block (single-block, in-scope) ..."
BLOCKS_JSON="$(curl -fsS "${AUTH[@]}" "$API/blocks?size=200")" || fail "2 list blocks"
TARGET_BLOCK_ID="$(jq -r '.data[0].id // empty' <<<"$BLOCKS_JSON")"
TARGET_BLOCK_NAME="$(jq -r '.data[0].name // empty' <<<"$BLOCKS_JSON")"
[ -n "$TARGET_BLOCK_ID" ] || fail "2 list blocks (no blocks found — seed demo data first)"

# --- 3) create a fresh DRAFT announcement (re-runnable, no id collision) -----
say "[3/7] Creating a fresh DRAFT announcement ..."
CREATE_BODY="$(jq -n --arg blk "$TARGET_BLOCK_ID" '{
  title: "[C2.3a SMOKE] XSS image render fixture",
  content: "seeding",
  type: "GENERAL",
  targetScope: "BLOCK",
  targetBlockId: $blk,
  targetFloor: null,
  sendPush: false,
  sendEmail: false,
  sendSms: false,
  publishNow: false
}')"
CREATE_JSON="$(send_json POST "$API/announcements" "$CREATE_BODY")" || fail "3 create draft"
ANN_ID="$(jq -r '.id // .data.id // empty' <<<"$CREATE_JSON")"
[ -n "$ANN_ID" ] || fail "3 create draft (no id in response: $CREATE_JSON)"

# --- 4) upload one COVER + one INLINE via the real C2.2 endpoint -------------
say "[4/7] Uploading COVER + INLINE images (real multipart endpoint) ..."
COVER_JSON="$(curl -fsS "${AUTH[@]}" -X POST "$API/announcements/$ANN_ID/media" \
  -F "file=@$COVER_FILE" -F "kind=cover")" || fail "4 upload cover"
COVER_ID="$(jq -r '.id // .data.id // empty' <<<"$COVER_JSON")"
[ -n "$COVER_ID" ] || fail "4 upload cover (no id: $COVER_JSON)"

INLINE_JSON="$(curl -fsS "${AUTH[@]}" -X POST "$API/announcements/$ANN_ID/media" \
  -F "file=@$INLINE_FILE" -F "kind=inline")" || fail "4 upload inline"
INLINE_ID="$(jq -r '.id // .data.id // empty' <<<"$INLINE_JSON")"
[ -n "$INLINE_ID" ] || fail "4 upload inline (no id: $INLINE_JSON)"

# --- 5) update draft body to carry every checklist case ---------------------
say "[5/7] Updating draft body with all XSS + legit cases ..."
# One announcement exercises every CTO-checklist case. COVER stays a banner (NOT in body).
# ASCII-only body (no em-dash) carrying the 6 API-storable checklist cases. The raw-HTML
# case (<img onerror>) is intentionally OMITTED: it is write-blocked by the server
# (400 ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED) so it cannot reach the renderer via the API;
# its render inertness is covered by the UI unit tests, not this fixture.
BODY_MD="$(cat <<EOF
# C2.3a XSS image-render smoke

legit inline (MUST render):
![alt](announcement-media:${INLINE_ID})

external url attack (MUST be inert, no external fetch):
![x](https://evil.example/track.png)

javascript attack (MUST be inert):
![x](javascript:alert(1))

data-uri attack (MUST be inert):
![x](data:text/html;base64,PHNjcmlwdD4=)

unknown-id attack (MUST render nothing):
![x](announcement-media:00000000-0000-0000-0000-000000000000)

link-form not image (MUST be inert anchor):
[click](announcement-media:${INLINE_ID})
EOF
)"
UPDATE_BODY="$(jq -n --arg blk "$TARGET_BLOCK_ID" --arg content "$BODY_MD" '{
  title: "[C2.3a SMOKE] XSS image render fixture",
  content: $content,
  type: "GENERAL",
  targetScope: "BLOCK",
  targetBlockId: $blk,
  targetFloor: null,
  sendPush: false,
  sendEmail: false,
  sendSms: false
}')"
send_json PUT "$API/announcements/$ANN_ID" "$UPDATE_BODY" >/dev/null || fail "5 update body"

# --- 6) publish to the single in-scope block --------------------------------
say "[6/7] Publishing to block '$TARGET_BLOCK_NAME' ..."
curl -fsS "${AUTH[@]}" -X POST "$API/announcements/$ANN_ID/publish" >/dev/null || fail "6 publish"

# --- 7) pick one in-scope + one out-of-scope resident, reset each to a known pw -
say "[7/7] Picking in-scope / out-of-scope residents and resetting their passwords ..."
IN_PHONE=""; IN_ID=""; OUT_PHONE=""; OUT_ID=""; OUT_BLOCK=""
if RES_JSON="$(curl -fsS "${AUTH[@]}" "$API/residents?isActive=true&size=200" 2>/dev/null)"; then
  IN_PHONE="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name == $b) | .user.phone) // empty' <<<"$RES_JSON")"
  IN_ID="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name == $b) | .user.id) // empty' <<<"$RES_JSON")"
  OUT_PHONE="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name != $b) | .user.phone) // empty' <<<"$RES_JSON")"
  OUT_ID="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name != $b) | .user.id) // empty' <<<"$RES_JSON")"
  OUT_BLOCK="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name != $b) | .apartment.block.name) // empty' <<<"$RES_JSON")"
fi

# Force a known password on each picked resident via the admin reset endpoint (PUT
# /api/users/{id}/reset-password, body {newPassword}), so the printed creds actually log in.
reset_pw() {  # $1=userId  $2=label
  [ -n "$1" ] || return 0
  send_json PUT "$API/users/$1/reset-password" \
    "$(jq -n --arg pw "$SMOKE_PASSWORD" '{newPassword:$pw}')" >/dev/null \
    || fail "7 reset password for $2 resident (id=$1)"
}
reset_pw "$IN_ID" "in-scope"
reset_pw "$OUT_ID" "out-of-scope"

# --- summary ----------------------------------------------------------------
cat <<EOF

================= C2.3a SMOKE-SEED SUMMARY =================
Announcement id : $ANN_ID
Target block    : $TARGET_BLOCK_NAME  ($TARGET_BLOCK_ID)
Cover media id  : $COVER_ID   (rendered as BANNER, not in body)
Inline media id : $INLINE_ID  (rendered inline in body)
Resident detail : ${RESIDENT_BASE_URL%/}/announcements/$ANN_ID   (resident portal :81; log in as the in-scope resident first)

Residents for the manual smoke (password reset by this script to: $SMOKE_PASSWORD):
EOF
if [ -n "$IN_PHONE" ]; then
  echo "  IN-SCOPE     (sees images) : $IN_PHONE / $SMOKE_PASSWORD   [block '$TARGET_BLOCK_NAME']"
else
  echo "  IN-SCOPE     : could not derive — no active resident found in block '$TARGET_BLOCK_NAME'."
fi
if [ -n "$OUT_PHONE" ]; then
  echo "  OUT-OF-SCOPE (empty media) : $OUT_PHONE / $SMOKE_PASSWORD   [block '$OUT_BLOCK']"
else
  echo "  OUT-OF-SCOPE : could not derive — no active resident found outside block '$TARGET_BLOCK_NAME'."
fi
cat <<EOF

Run the CTO smoke checklist in reports/c2-3a-announcement-image-render.md against
the resident detail above. Re-run this script any time for a fresh fixture.
===========================================================
EOF
