# Trunk Rename Runbook — `deploy/local` → `main` (READ-ONLY pre-flight + CTO runbook)

**Date:** 2026-06-26 · **Author:** PM agent (read-only — NO rename/push/branch/delete, NO GitHub settings)
**Status:** awaiting CTO execution. Agent performed no remote/branch ops.

---

## 0. Ground truth
- Current branch **`deploy/local`**, working tree **clean**.
- Remote: `origin` → `https://github.com/toancv/gemek-premium.git`.
- Branches: local `deploy/local` (current), `improvement/security`, `master`, `phase/backend`, `phase/frontend`;
  remote-tracking `origin/deploy/local`, `origin/improvement/security`, `origin/master`.

---

## 1. Secret / local-config audit (the gate before main becomes trunk)

### Real `.env`?
- **No real `.env` is tracked.** Only **`.env.example`** (template, placeholders) is committed.
- `.gitignore:6` = `.env` → real env files stay untracked. ✅ **Safe.**

### Secret-pattern scan of files that become canonical (values NOT printed)
| File | Finding | Verdict |
|---|---|---|
| `.env.example` (l.9,15,22,23) | placeholder/template keys, no real values | **KEEP as-is** |
| `backend/.../application.yml` (l.23,61,70,79,80,88) | all secrets are `${ENV_REF}` (JWT_SECRET, MINIO keys, REDIS/ADMIN pw via env) | **KEEP** (env-driven) |
| `application.yml` l.69/71/72 | false positives — `jwt:` key NAME + token-expiry numbers, not secrets | **KEEP** |
| `backend/.../application-dev.yml` l.12 | `password: ""` (empty redis pw) + localhost/debug only | **KEEP** (no secret) |
| `docker-compose.yml` (l.4,24,56,57) | comment + `${ENV_REF}` values | **KEEP** (env-driven; localhost ports OK for trunk now) |
| `docker-compose.override.yml` | local override (compose auto-loads) | **review → consider gitignore** (local overrides shouldn't be canonical) |
| **`docker-compose.dev.yml` l.10** | **HARDCODED dev-DB password** for an *ephemeral* dev postgres (port 5434, throwaway volume) | **PARAMETERIZE** (`${POSTGRES_PASSWORD}`) or accept as documented dev-only default — low risk, NOT a prod secret |
| `nginx/nginx.conf` | localhost / `:8090` / port config | **KEEP** (host/port config, acceptable for trunk at this stage) |
| **`scripts/seed-demo-local.sql` l.28+** | committed **bcrypt HASHES** for `@demo.local` demo accounts (known demo password) | **KEEP but DEMO-ONLY** — must NEVER run against prod; label clearly; prod admin seeded via `${ADMIN_PASSWORD}` env, not this script |

### Audit conclusion
**Safe to make `main` the trunk.** No real `.env`, no prod secret, no plaintext prod password committed; all
prod secrets are env-driven (`${...}`). Two non-blocking items to address (CTO call, can follow the rename):
1. `docker-compose.dev.yml:10` hardcoded **dev** DB password → parameterize or accept as dev-only.
2. `scripts/seed-demo-local.sql` demo bcrypt hashes → keep demo-only, never seed prod.
Optional: `docker-compose.override.yml` — consider gitignoring local overrides.

---

## 2. Branch inventory (what else is open)
All other branches are **linear ancestors of `deploy/local` → fully merged, 0 unique commits** (nothing lost):

| Branch | ahead of deploy/local | merged? | Recommendation |
|---|---|---|---|
| `improvement/security` | 0 (491 behind) | yes (ancestor) | **deletable** — fully contained in trunk |
| `phase/backend` | 0 (512 behind) | yes | **deletable** |
| `phase/frontend` | 0 (499 behind) | yes | **deletable** |
| local `master` | 0 (525 behind) | yes (3-commit skeleton) | **keep for history OR delete** (CTO) — superseded as trunk |
| `origin/master` | 0 (527 behind) | yes | superseded; default moves to `main` |

→ Making `main` (the renamed deploy/local) the trunk loses **nothing**; every other branch is already inside it.

---

## 3. Runbook — CTO executes (agent does NOT)

### [LOCAL GIT — CTO runs]
```bash
# 1. Rename the local branch deploy/local → main (you are on deploy/local, tree clean)
git branch -m deploy/local main

# 2. Push main and set upstream (creates origin/main; does NOT delete origin/deploy/local yet)
git push -u origin main

# 3. (verify) confirm origin now has main
git ls-remote --heads origin main
```

### [GITHUB UI — CTO does]
1. **Settings → Branches → Default branch** → switch default from `master` to **`main`**.
2. **Branch protection:** move/re-create the protection rule from `master` (and/or `deploy/local`) onto **`main`**
   (require PR, reviews, status checks). Ensure `main` is PR-protected before any further pushes.
3. **CI / Actions / PR base:** update any workflow `on: push`/`pull_request` branch filters and any saved PR base
   that reference `deploy/local` or `master` → point to `main`.

### [LOCAL CLEANUP — only AFTER default switched + protection on main + CI green]
```bash
# 4. Delete the old remote branch once nothing depends on it
git push origin --delete deploy/local

# 5. Stale merged branches (all fully contained in main) — optional cleanup:
git branch -d improvement/security phase/backend phase/frontend     # local
git push origin --delete improvement/security                       # remote (if pushed)
# master skeleton: KEEP for history OR delete per CTO:
#   git push origin --delete master   # only if retiring the skeleton

# 6. Future feature branches now base off main:
#   git checkout main && git pull && git checkout -b feature/<x>
```

### Verify-after checklist
- [ ] `git remote show origin` → `HEAD branch: main`.
- [ ] GitHub default branch = `main`; branch protection **active** on `main`.
- [ ] CI green on `main`; PR base defaults to `main`.
- [ ] `origin/deploy/local` deleted (only after the above).
- [ ] Local working branch is `main`; future feature branches base off `main`.

---

## 4. Notes
- Supersedes the earlier "consolidate onto the 3-commit `master`" framing (`reports/git-branch-topology.md`):
  rather than importing 524 commits into the stale skeleton, **`deploy/local` itself becomes the trunk** (renamed
  `main`) — it already IS the full linear history descending from master's 3 commits, so no consolidation/merge
  is needed; the skeleton `master` is retired.
- Phone-search + all C2.3b/C3 work are already on `deploy/local` → become canonical on `main` automatically.
- Go-forward: feature-per-branch off `main`; agent pushes the feature branch + STOPs; CTO opens the PR (`main`
  PR-protected); agent never pushes/merges `main`.
