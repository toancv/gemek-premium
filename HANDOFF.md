# HANDOFF — fresh-assistant resume guide

This repo is the **ground truth**. A new assistant (no chat history) can resume from the in-repo files alone.
This note points to those files — it does not duplicate them. Keep it short; update the resume pointer only.

## Read these first (in order)
1. **`PROGRESS.md`** → the `▶ CURRENT STATE SNAPSHOT` block at the top: resume pointer, phase table with commit
   hashes, prioritized open-items/backlog. Everything else is dated history below it.
2. **`DECISIONS.md`** → every autonomous ruling + tradeoff, newest at top. Don't re-decide what's logged here.
3. **`reports/<phase>.md`** → the deep dive for whatever you're resuming. For the active phase:
   `reports/c2-3a-announcement-image-render.md` (incl. the **CTO smoke checklist**).
4. **`docs/API-SPEC.md`** → endpoint contracts (must stay in sync with any API change, same phase).
5. Role/workflow rules: **`CLAUDE.md`** (root) + **`.claude/rules/*.md`**.

## Current resume pointer (2026-06-28)
**CONTRACTOR DOCUMENTS feature** in progress on branch `feature/contractor-contract-upload` (off `main`).
- **P1 (BE)** — `contractor_document` table V23 + 3 endpoints + forced-download, committed (`093265b` feat /
  `6d8c611` test / `62e5258` docs). **Live HTTP/DB/MinIO smoke still DEFERRED** → folded into the end-of-P3 browser smoke (upload/list/download/403/413 against the running stack + real MinIO). See `reports/contractor-documents-p1.md`.
- **P2 (FE)** — dedicated contractor create/edit PAGES replacing the list-page modal, committed (`5d2df1a` feat).
  Build green (tsc + vite); `/code-review` high triaged (4 fixed, rest out-of-scope/pre-existing). See
  `reports/contractor-documents-p2.md`. **Awaiting CTO :80 manual smoke** (create→redirect-to-edit, edit-save, list nav).
- **Next action:** CTO :80 smoke of the P2 pages → then start **P3** = `ContractorDocumentsManager` (clone of
  `AnnouncementAttachmentsManager`) + lazy-save on `/contractors/new`, appended to the edit page.
- Trunk = `main` (rename runbook ready, pending CTO). Agent pushes feature branch + STOPs; CTO opens the PR.

## Stable project facts
- **Stack:** Spring Boot backend (`backend/`, Maven via `./mvnw`, Java) · React monorepo frontend
  (`frontend/`, pnpm workspace: `apps/resident`, `apps/admin`, shared `packages/ui` = `@gemek/ui`) · PostgreSQL
  · MinIO (object storage, presigned URLs) · Redis · nginx.
- **Run:** `docker compose up -d --build` (see `docker-compose.yml`). nginx serves FE + proxies `/api` on **:80**;
  backend **8080**; dev DB `gemek` (postgres 5432); **test DB `gemek_test` on :5433** (isolated, per-JVM Flyway
  clean+migrate — never touches dev). MinIO 9000 (console 9001).
- **Test:** backend `cd backend && ./mvnw -o test` (full suite currently **416/416 green**; keep sequential —
  suite is not parallel-safe by `@Transactional` nesting, a framework limit, not pollution). UI: per-package
  `vitest`. FE typecheck: per-app `./node_modules/.bin/tsc`.
- **Tooling:** ECC agents (architect / backend-dev / frontend-dev / tester / code-reviewer / security-reviewer)
  + `/code-review`. Secrets in `.env` only (never committed).

## Workflow rules (non-negotiable)
- **Ground-truth-first:** start every task with `git status` + `git log`; trust repo files, not summaries.
- **Investigate-first:** read the relevant report/code before changing anything.
- **One task per turn**, gate-controlled (G1–G4 hard stops; CTO approves). Mid-phase architecture forks → BLOCKER report.
- **File-to-disk output discipline:** analysis/plans go to `reports/*.md` (`.tmp`→rename on finish), not chat.
- **Commit grouping:** one logical change per commit (`type(scope): …`); never mix feat/fix/test with `docs(context)`.
- **Per-step context sync:** update `PROGRESS.md` (+ `DECISIONS.md` if a ruling was made) and commit as its own
  `docs(context): …` commit. Keep API-SPEC in sync in the same phase as any endpoint change.
