# Diagnosis: email-dup returns CONFLICT instead of EMAIL_ALREADY_EXISTS

## Root cause
`ResidentServiceImpl.java:135` used `ErrorCode.CONFLICT` on its own email uniqueness guard.
`UserServiceImpl.java:111` (direct user-create path) already used `ErrorCode.EMAIL_ALREADY_EXISTS` correctly.
One path was wrong; the other was right — asymmetry introduced when the service-layer email guard was added to ResidentServiceImpl independently.

## Fix (one line)
`ResidentServiceImpl.java` — changed `ErrorCode.CONFLICT` → `ErrorCode.EMAIL_ALREADY_EXISTS`.
Message also stripped of raw email address (was "Email already registered: user@mail.com"; now "Email address is already registered.").

## Email message leakage
Before: both paths leaked the email address in `message`. After fix: ResidentServiceImpl no longer leaks it ("Email address is already registered."). UserServiceImpl still says "Email address is already registered: <email>" — low risk since FE now maps by code and will never display the raw message.

## Scope
Fix is exactly one line in one file. GlobalExceptionHandler's DataIntegrityViolationException fallback (which catches DB-constraint violations that slip past service-layer guards) still maps to bare CONFLICT — that is intentional defense-in-depth and NOT changed here.
