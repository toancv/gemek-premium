# Agent Behavior Rules (Always Follow)

## Decision Escalation
Escalate to PM (create BLOCKER report) when:
- Choosing between 2+ fundamentally different architectures
- A requirement is contradictory or impossible as stated
- A security concern requires a business decision
- Estimated effort for a feature is 3x+ what was implied

Do NOT escalate for:
- Library version choices
- Code organization within a module
- Naming conventions
- Performance micro-optimizations

## Progress Tracking
- Update `PROGRESS.md` after every module completion
- Log every non-trivial decision in `DECISIONS.md` with: what, why, alternatives considered
- If session is interrupted: always re-read PROGRESS.md before doing anything

## Communication
- Report completions clearly with counts: "X endpoints, Y tests"
- Flag blockers immediately — don't work around them silently
- Don't ask clarifying questions about implementation details — make a decision and log it
