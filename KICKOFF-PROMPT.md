# KICKOFF PROMPT
# Paste this into Claude Code to start the entire SDLC.
# Edit the BUSINESS REQUIREMENTS section only.

---

Read CLAUDE.md for all working instructions and approval gates.
Read PROGRESS.md to check if this is a fresh start or a resume.

## Business Requirements

**System:** Apartment Management System
**Scale:** ~500 apartments, small management team

**User Roles:**
- Admin (building manager): full control
- Accountant: fee management, payment tracking
- Resident: read-only personal portal, submit requests

**Core Features:**
1. Resident & apartment management (CRUD)
2. Monthly fee management (generate, track, mark paid)
3. Utility billing (electricity, water per apartment)
4. Maintenance request tracking (submit, assign, resolve)
5. Building announcements / notice board
6. Basic reporting (fee collection summary, outstanding balances)

**Constraints:**
- Must run via docker-compose (single command startup)
- Vietnamese language UI
- No external payment gateway needed (manual payment recording only)
- Mobile-friendly but desktop-first

---

Start now. Begin with the architect agent to design the system, then create the G1 report.
Do not ask me questions. Make decisions and proceed.
