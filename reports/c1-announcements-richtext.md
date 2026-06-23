# C1 — Announcements ("Tin tức") rich TEXT (Markdown) + XSS-safe render

Scope: add Markdown rich text to announcement bodies. **NO** MinIO/media, **NO** schema change (`content`
stays `TEXT`, raw Markdown), **NO** feed-scope/publish-tx/immutability change. Defense-in-depth XSS:
safe React-element render on FE (primary) + lightweight write-check on BE (secondary).

Authoritative current-state: `reports/announcements-rich-content-investigation.md`.
Note: investigation cited FE paths as `apps/...`; real root is `frontend/apps/...` (verified).

## Verified anchors (re-checked this session)
- Resident detail body: `frontend/apps/resident/src/pages/AnnouncementDetailPage.tsx:41`
  — `<p className="text-sm text-gray-700 whitespace-pre-line">{announcement.content}</p>`.
- Resident feed list: `frontend/apps/resident/src/pages/AnnouncementsPage.tsx` — TITLE only (untouched).
- Admin authoring textarea: `frontend/apps/admin/src/pages/AnnouncementsPage.tsx:142` (`<textarea name="content">`),
  create handler reads `fd.get('content')` (`:35`).
- `packages/ui` (`@gemek/ui`): source-consumed (`main: ./src/index.ts`), vitest+jsdom present, react peerDep 18.3.1.
- BE create/update: `AnnouncementServiceImpl.java:216 / :255`; scope check `validateScopeConstraints:484`.
- DTOs: `CreateAnnouncementRequest` (`@NotBlank content`), `UpdateAnnouncementRequest` (content nullable).
- Error contract: `GlobalExceptionHandler:60` → body `error = ErrorCode.name()`; FE maps code→VN in
  `packages/ui/src/lib/errorMessages.ts`. No raw BE message shown to user.

## SHARED RENDERER — single source of truth (`packages/ui`)
New `packages/ui/src/components/MarkdownContent.tsx`, exported from `index.ts`. Consumed by BOTH the
resident detail page and the admin live preview → author preview == resident view; safe config cannot drift.

Deps added to `packages/ui/package.json` (pinned): `react-markdown@9.0.1`, `remark-breaks@4.0.0`.
react-markdown renders **React elements** (no innerHTML) and does **NOT** pass raw HTML through unless
`rehype-raw` is added — which it is NOT.

Locked-down config (the core of this task):
- `remarkPlugins=[remarkBreaks]` — single `\n` → `<br>`, so legacy plain-text announcements keep their
  line breaks (preserves old `whitespace-pre-line` behavior).
- **NO `rehype-raw`**, **NO `rehypePlugins`** → embedded raw HTML in markdown is NOT rendered as HTML.
- **NO `dangerouslySetInnerHTML`** anywhere (react-markdown does not use it; grep the diff to confirm 0).
- `allowedElements` = constrained subset: `p, br, strong, em, h1, h2, h3, h4, h5, h6, ul, ol, li, a,
  blockquote, code, pre`. Everything else removed. `img` is NOT allowed → image markdown renders as
  nothing (images arrive in C2). `unwrapDisallowed` left default (drops disallowed nodes).
- `urlTransform`: custom — permit only `http:`, `https:`, `mailto:` and relative/anchor (`/…`, `#…`,
  no scheme). Any other scheme (`javascript:`, `data:`, `vbscript:`, …) → returns `''` (neutralized).
- custom `a` component: validated href + `rel="noopener noreferrer"`; external (http/https) also
  `target="_blank"`.
- Typography: per-element Tailwind classes via the `components` map (no `@tailwindcss/typography` dep),
  matching the mobile-first resident type scale (`text-sm text-gray-700` base).

## RESIDENT
Replace `AnnouncementDetailPage.tsx:41` plain `<p>` with `<MarkdownContent content={announcement.content} />`.
Feed list unchanged (title-only, plain text).

## ADMIN editor + preview
`frontend/apps/admin/src/pages/AnnouncementsPage.tsx` create modal:
- Convert content to a controlled `useState` + `textareaRef` (FormData read still works via `name`/value).
- Format toolbar inserting Markdown at cursor: **Đậm** (`**`), **Nghiêng** (`*`), **Tiêu đề** (`## `),
  **Danh sách** (`- `), **Liên kết** (`[text](url)`), under a **Định dạng** hint.
- **Xem trước** live preview pane using the SAME `<MarkdownContent>` (identical safe config).
- Editing remains drafts-only (publish-immutable rule untouched).

## BACKEND write-check (secondary layer)
- New ErrorCodes (`ErrorCode.java`, both `BAD_REQUEST`): `ANNOUNCEMENT_CONTENT_TOO_LONG`,
  `ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`. Mapped to VN in `errorMessages.ts`.
- `validateContent(String)` in `AnnouncementServiceImpl`, called from create + update (when content set):
  - **Max length = 20000 chars** (sane bound for a single announcement body; far above realistic prose,
    well under a TEXT-abuse payload). Over → `ANNOUNCEMENT_CONTENT_TOO_LONG`.
  - **Raw-HTML reject** regex `</?[a-zA-Z][a-zA-Z0-9]*[\s/>]` → catches `<script>`, `<iframe ...>`,
    `<div>`, `</p>`, `<b>` etc. Deliberately does NOT match Markdown autolinks `<https://…>` (tag name
    followed by `:`) nor email autolinks `<a@b.com>` (followed by `@`) nor a lone `<` in prose ("a < b").
    Match → `ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`.
- No heavy BE markdown/sanitizer dep added (Tika irrelevant). FE renderer remains primary defense.
- Feed scope query, publish/dispatch tx, immutability rule UNCHANGED.
  `AnnouncementRecipientConsistencyTest` not edited.

## LEGACY CONTENT (recorded in DECISIONS)
Existing published bodies are plain text rendered as Markdown going forward. They look near-identical;
stray Markdown metacharacters (`*`, `_`, `#`, leading `>`) may be reinterpreted as formatting. CTO accepts
this — NO backfill/escape migration. `remark-breaks` keeps their newlines intact.

## TESTS
- FE (`packages/ui/src/components/MarkdownContent.test.tsx`): (1) `<script>alert(1)</script>` emits no
  `<script>` element; (2) `[x](javascript:alert(1))` produces an `<a>` with no `javascript:` href;
  (3) bold/italic/heading/list/link render correct elements; (4) single `\n` → `<br>`.
- BE (`AnnouncementControllerTest`): create with raw HTML → 400 `ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`;
  over-length → 400 `ANNOUNCEMENT_CONTENT_TOO_LONG`; normal `**bold**` markdown → 201.

## COMMIT GROUPS (separate)
1. `feat(ui)` — MarkdownContent shared safe renderer + deps + test.
2. `feat(resident)` — detail page consumes renderer.
3. `feat(admin)` — editor toolbar + live preview.
4. `feat(be)` — ErrorCodes + validateContent + BE tests; `feat(ui)`/`fix` errorMessages VN map.
5. `docs` — API-SPEC content contract + DECISIONS (ruling + legacy note).
6. `docs(context)` — PROGRESS + this report.
