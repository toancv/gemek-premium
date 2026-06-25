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
#   BASE_URL        default http://localhost
#   ADMIN_PHONE     required (admin login identifier; phone-based)
#   ADMIN_PASSWORD  required (read from env; never committed)
#
# Usage:
#   ADMIN_PHONE=09xxxxxxxx ADMIN_PASSWORD='***' ./scripts/smoke-c2-3a.sh
#
set -euo pipefail

# --- config -----------------------------------------------------------------
BASE_URL="${BASE_URL:-http://localhost}"
ADMIN_PHONE="${ADMIN_PHONE:?set ADMIN_PHONE (admin login phone)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?set ADMIN_PASSWORD (do not hardcode)}"

for bin in curl jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "FATAL: '$bin' not found on PATH" >&2; exit 1; }
done

API="${BASE_URL%/}/api"

# Minimal valid 1x1 PNG (real PNG magic bytes — Tika detects image/png from these).
PNG_B64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
PNG_FILE="$TMP_DIR/img.png"
printf '%s' "$PNG_B64" | base64 -d > "$PNG_FILE"

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
  -F "file=@$PNG_FILE" -F "kind=cover")" || fail "4 upload cover"
COVER_ID="$(jq -r '.id // .data.id // empty' <<<"$COVER_JSON")"
[ -n "$COVER_ID" ] || fail "4 upload cover (no id: $COVER_JSON)"

INLINE_JSON="$(curl -fsS "${AUTH[@]}" -X POST "$API/announcements/$ANN_ID/media" \
  -F "file=@$PNG_FILE" -F "kind=inline")" || fail "4 upload inline"
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

# --- 7) best-effort: pick one in-scope + one out-of-scope resident ----------
say "[7/7] Deriving in-scope / out-of-scope residents (best-effort) ..."
IN_PHONE=""; OUT_PHONE=""; OUT_BLOCK=""
if RES_JSON="$(curl -fsS "${AUTH[@]}" "$API/residents?isActive=true&size=200" 2>/dev/null)"; then
  IN_PHONE="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name == $b) | .user.phone) // empty' <<<"$RES_JSON")"
  OUT_PHONE="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name != $b) | .user.phone) // empty' <<<"$RES_JSON")"
  OUT_BLOCK="$(jq -r --arg b "$TARGET_BLOCK_NAME" \
    'first(.data[] | select(.apartment.block.name != $b) | .apartment.block.name) // empty' <<<"$RES_JSON")"
fi

# --- summary ----------------------------------------------------------------
cat <<EOF

================= C2.3a SMOKE-SEED SUMMARY =================
Announcement id : $ANN_ID
Target block    : $TARGET_BLOCK_NAME  ($TARGET_BLOCK_ID)
Cover media id  : $COVER_ID   (rendered as BANNER, not in body)
Inline media id : $INLINE_ID  (rendered inline in body)
Resident detail : ${BASE_URL%/}/announcements/$ANN_ID   (resident app path)

Residents for the manual smoke (demo residents password: Demo@1234):
EOF
if [ -n "$IN_PHONE" ]; then
  echo "  IN-SCOPE     (sees images) : $IN_PHONE   [block '$TARGET_BLOCK_NAME']"
else
  echo "  IN-SCOPE     : could not derive — log in any resident whose apartment is in block '$TARGET_BLOCK_NAME'."
fi
if [ -n "$OUT_PHONE" ]; then
  echo "  OUT-OF-SCOPE (empty media) : $OUT_PHONE   [block '$OUT_BLOCK']"
else
  echo "  OUT-OF-SCOPE : could not derive — log in any resident whose apartment is NOT in block '$TARGET_BLOCK_NAME'."
fi
cat <<EOF

Run the CTO smoke checklist in reports/c2-3a-announcement-image-render.md against
the resident detail above. Re-run this script any time for a fresh fixture.
===========================================================
EOF
