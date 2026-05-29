# Git Workflow Rules (Always Follow)

## Commit Format
`type(scope): description`

Types: feat, fix, test, refactor, docs, chore

Examples:
- `feat(auth): implement JWT login endpoint`
- `fix(residents): handle null apartment assignment`
- `test(fees): add unit tests for monthly fee calculation`

## Rules
- Commit after EACH completed module — never accumulate uncommitted work
- Never commit broken/failing code
- Never commit `.env` files
- Use branches: `phase/backend`, `phase/frontend`, merge to `main` at gates
