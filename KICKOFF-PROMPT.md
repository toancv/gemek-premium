# KICKOFF PROMPT
# Paste this into Claude Code to start the entire SDLC.
# Edit the BUSINESS REQUIREMENTS section only.
---
Read CLAUDE.md for all working instructions and approval gates.
Read PROGRESS.md to check if this is a fresh start or a resume.

## Business Requirements

**System:** Apartment Management System

**Scale:** ~1000 apartments, small management team

**User Roles:**
- Admin (building manager): full control over all modules
- Technician / Maintenance staff: receive and resolve assigned tickets (maintenance and other operational categories)
- Resident: personal portal, submit requests, book amenities, view announcements
- (Optional) Board member: read-only access to reports and dashboards

**Core Features:**
1. Resident & apartment management (CRUD) — apartment profiles (block, floor, area), owner/tenant info, vehicles, resident change history; distinguish owner vs tenant
2. Resident request / ticket management — residents submit any type of request or report with photos; each ticket has a category (maintenance/repair, complaint e.g. noise or neighbor dispute, administrative request, suggestion/feedback, other); classify, route/assign to the relevant internal staff OR contractor depending on category, track status (new → in progress → done), post-resolution rating, SLA tracking. Maintenance-category tickets can be assigned to contractors (see feature 5)
3. Building announcements / notice board — send notices by block / floor / whole complex; push / email / SMS; read confirmation
4. Amenity booking — book gym, pool, BBQ area, meeting room; view availability, approval, usage limits
5. Contractor & contract management — contractor/vendor profiles (cleaning, security, elevator, fire safety, landscaping, pest control); contracts (scope, value, term, clauses, attachments); expiry/renewal alerts; recurring maintenance schedule per contract; contractor payment tracking (record & remind only — NOT a disbursement-approval workflow); contractor quality rating and work history. Link to the ticket module: maintenance-category tickets can be assigned to contractors and feed contractor ratings
6. Parking management — assign slots, register license plates, parking cards, guest vehicles
7. Basic reporting / dashboard — ticket resolution rate (by category), amenity usage stats, contracts nearing expiry; role-based access
8. Access control / RBAC — role-based permissions, audit log, account management

**Out of Scope (handled outside this system — do NOT build):**
- Resident fee management, monthly fee generation, outstanding-balance tracking
- Utility billing (electricity/water per apartment)
- Any payment gateway or fee reconciliation for residents
- The management team already handles billing via emailed invoices + bank transfer + manual reconciliation; the system must NOT interfere with this flow

**Constraints:**
- Must run via docker-compose (single command startup)
- Vietnamese language UI
- No external payment gateway needed
- Mobile-friendly but desktop-first
- Resident portal (mobile-first, simple) and admin portal (full operational features) should be clearly separated
- Resident/apartment data is the system's primary source of truth

---
Start now. Begin with the architect agent to design the system, then create the G1 report.
Do not ask me questions. Make decisions and proceed.