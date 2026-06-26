# Git Branch Topology — investigation report (READ-ONLY)

**Date:** 2026-06-26 · **Author:** PM agent (read-only; no merge/push/branch/code change) · **Status:** awaiting CTO decision

---

## 0. Ground truth
- Current branch: **`deploy/local`**, working tree **clean** (no tracked changes).
- Phone-search committed & closed on `deploy/local`:
  - `763cc29` feat(be): add phone to search filter (residents/users/contractors)
  - `f7294f8` test(be): phone-substring search + null-phone safe
  - `c95307f` docs(context): API-SPEC + DECISIONS + PROGRESS
  - `f54c26f` fix(admin): search placeholders include phone (FE)
  - `ff0e165` docs(context): FE placeholder note
  - Full BE suite 457/457 green at the time of commit.

## 1. Remote + branch map (actual output)
- **Remote:** `origin` → `https://github.com/toancv/gemek-premium.git` (fetch + push).
- **Local branches:** `deploy/local` (current), `improvement/security`, `master`, `phase/backend`, `phase/frontend`.
- **Remote-tracking:** `origin/deploy/local`, `origin/improvement/security`, `origin/master`.
- **`master` exists** both locally and on the remote. Local `master` is **2 commits ahead** of `origin/master`
  (`git rev-list --left-right --count master...origin/master` → `2  0`) — minor; the 2 are not yet pushed.
- No fetch/modify performed; refs reported as-is.

## 2. `deploy/local` vs `master` — the divergence (the crux)
- **Ahead/behind** (`git rev-list --left-right --count master...deploy/local` → `0  524`):
  `deploy/local` is **524 commits AHEAD**, **0 behind** master. Against `origin/master`: **0  526** (526 ahead).
- **`master` is a linear ANCESTOR of `deploy/local`** (`git merge-base --is-ancestor master deploy/local` → true).
  The history is linear / fast-forwardable — master has **no** commits that deploy/local lacks.
- **`master` is a 3-commit SKELETON, not an integration line:**
  ```
  7846903 chore(arch): redesign system for generalized ticket management (v2)
  bf4e522 chore(arch): design system architecture and produce G1 techstack report
  e5c7442 chore: agentic sdlc setup
  ```
  master contains only the agentic-SDLC config + architecture design + `CLAUDE.md`. It has **NO application
  code**: `backend/pom.xml` **absent**, `backend/.../UserServiceImpl.java` **absent**,
  `frontend/apps/admin/src/pages/UsersPage.tsx` **absent**. The **entire application** (all 524 commits:
  core platform, residency lifecycle, announcements C1/C2.1/C2.2/C2.3a/C2.3b, C3 attachments, phone-search,
  every module + test) lives only on `deploy/local`.
- **Files differing** `master...deploy/local`: **574 files** (effectively the whole repo — master has almost none of it).

### Local-only / deploy config (by name) — NONE currently on master
All of these exist on `deploy/local` and are **absent from master** (master predates them):

| File | On master? | Carries local markers (`localhost` / `:8090` / seed)? |
|---|---|---|
| `docker-compose.yml` | absent | **yes** |
| `docker-compose.dev.yml` | absent | (override) |
| `backend/src/main/resources/application-dev.yml` | absent | **yes** |
| `nginx/nginx.conf` | absent | **yes** |
| `scripts/seed-demo-local.sql` | absent | **yes** (local demo seed) |
| `.env.example` | absent | (template — safe; no secrets) |
| `backend/.../AdminSeeder.java`, `V2__seed_admin.sql`, `reports/seed-demo-*` | absent | seed/demo |

> Note: because master is empty of app code, EVERY file is "new to master" — the usual "feature code vs
> local-only config already on master" split does not apply yet. The local-markers files above are the ones
> to **review/parameterize before they reach master** (env-driven host/port, no committed demo seed), but
> they are not separable from a still-skeleton master via cherry-pick.

## 3. Separability of phone-search
- The 5 phone-search commits are **cleanly scoped** — each touches only feature/test/docs files, **zero**
  config files (verified per-commit `--stat`):
  - `763cc29` → 3 `*ServiceImpl.java` · `f7294f8` → 3 `*ControllerTest.java` · `f54c26f` → admin `i18n/vi.ts` + `UsersPage.tsx` · docs commits → `PROGRESS/DECISIONS/API-SPEC` only.
- **BUT** the files phone-search modifies (the ServiceImpls, ControllerTests, admin pages) **do not exist on
  master**. A standalone "cherry-pick phone-search onto master" is therefore **moot** — the base files aren't
  there. Phone-search is internally clean, but it **cannot reach master in isolation**; it only lands as part
  of bringing the application to master.
- The docs commits (`c95307f`, `ff0e165`) edit `PROGRESS.md`/`DECISIONS.md`, which on `deploy/local` carry
  524 commits of bookkeeping history absent from master — these would conflict on any partial cherry-pick.

## 4. Recommendation (for CTO — do NOT act)
1. **`master` is NOT yet a usable integration base.** It is a 3-commit skeleton; `deploy/local` is the de-facto
   trunk holding the entire product. Per-feature PRs onto the current master are not viable (no base code).
2. **One-time consolidation is required, not cherry-picks.** Bring the application to master via a single
   curated effort. Because history is linear (master is an ancestor), options for the CTO:
   - **(a)** Open ONE consolidation PR `deploy/local → master` (squash or curated), **excluding/parameterizing
     the local-only config** (docker-compose host/port, `application-dev.yml`, `nginx.conf` localhost,
     `scripts/seed-demo-local.sql`, demo seed) so production master is env-driven and secret-free; OR
   - **(b)** Fast-forward master to a vetted commit, then immediately follow with a config-sanitization PR.
   - Either way, **phone-search reaches master inside this consolidation**, not as a separate PR.
3. **Local-only config must not reach master unparameterized:** `docker-compose.yml`/`.dev.yml`,
   `application-dev.yml`, `nginx/nginx.conf` (localhost/:8090), `scripts/seed-demo-local.sql` + demo seed.
   `.env.example` is fine (template, no secrets). Real `.env` must stay gitignored (confirm before any PR).
4. **Broader backlog flag (do NOT solve now):** the deploy/local→master gap is the **whole app** (524 commits),
   not just phone-search. This needs a dedicated consolidation/sanitization effort with its own review — flag
   it for CTO scheduling; it is out of scope for any single feature turn.

## 5. Go-forward branch workflow (recorded in DECISIONS.md)
- New feature / large backlog item → its **own branch** off `<base — CTO to confirm after this report>`
  (likely `deploy/local` until master is consolidated) BEFORE coding; work + smoke + `/code-review` + close on
  the branch.
- On close → **push the FEATURE BRANCH** to `origin` and STOP. **The CTO opens the PR to master.** The agent
  **NEVER** merges to or pushes `master` (master is PR-protected). Even `deploy/local` pushes are the CTO's call.
- **Phone-search is the last exception** — done directly on `deploy/local` with no branch; it reaches master via
  a CTO-opened PR per this report (inside the consolidation).
