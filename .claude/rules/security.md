# Security Rules (Always Follow)

- NEVER hardcode passwords, API keys, tokens, or connection strings
- ALL secrets go in `.env` files, which are in `.gitignore`
- SQL queries MUST use parameterized inputs — no string concatenation with user data
- Auth middleware MUST be applied to all non-public endpoints
- Never log request bodies that may contain passwords or payment info
- HTTPS assumed in production — never downgrade security for dev convenience
- Dependency versions must be pinned (no `latest` in package files)
