# Phone-as-Login E2E Verification — 2026-06-08

## Verdict: PASS

All verification steps completed against a clean DB (volumes wiped via `docker compose down -v`).

---

## Step 1 — Stack Boot
- Backend healthy: **15 s** after `docker compose up -d --build`
- Flyway V12 (`phone as login`): `success = t`

## Step 2 — DB Admin Row
| Column | Value | Expected |
|--------|-------|----------|
| phone | `0900000000` | canonical `0xxxxxxxxx` ✅ |
| email | `admin@gemek.vn` | nullable column, value set ✅ |
| role | `ADMIN` | ✅ |
| is_active | `t` | ✅ |

Schema: `phone character varying(20) NOT NULL`, `email character varying(255)` (nullable) ✅

## Step 3 — Login Canonical
```
POST /api/auth/login  {"phone":"0900000000","password":"GemekAdmin2026"}
→ 200  user.phone=0900000000  accessToken present
```

## Step 4 — Login Non-Canonical (normalization)
```
POST /api/auth/login  {"phone":"+84900000000","password":"GemekAdmin2026"}
→ 200  user.phone=0900000000
```
Input `+84900000000` normalized to `0900000000` before DB lookup ✅

## Step 5 — Negative Cases
| Case | Status | Expected |
|------|--------|----------|
| Wrong password | 401 | 401 ✅ |
| Malformed phone `not-a-phone` | 400 VALIDATION_ERROR | 400 ✅ (not 500) |

Malformed response body:
```json
{"error":"VALIDATION_ERROR","message":"Invalid Vietnamese mobile phone number: not-a-phone",...}
```

## Step 6 — Unique Constraint
```
"uq_users_phone" UNIQUE CONSTRAINT, btree (phone)   ✅
"uq_users_email" UNIQUE CONSTRAINT, btree (email)   ✅ (preserved)
```

---

## Environment
- Stack: `docker compose down -v` → `docker compose up -d --build`
- Admin phone (env default): `0900000000`
- Stack left **UP** after verification (ready for FE step 6)
