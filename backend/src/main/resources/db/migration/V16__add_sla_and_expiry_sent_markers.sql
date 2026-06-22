-- V16 — N3 P6: sent-markers for scheduler idempotency.
-- SLA scheduler (§D): a marker set in the same TX as the notification insert
-- guarantees a re-scan can never re-notify; a crash before commit rolls back both.
-- G6: same pattern retrofitted to ContractExpiryScheduler (was re-notifying daily for 30 days).
ALTER TABLE tickets ADD COLUMN sla_warning_notified_at TIMESTAMPTZ NULL;
ALTER TABLE tickets ADD COLUMN sla_overdue_notified_at TIMESTAMPTZ NULL;
ALTER TABLE contracts ADD COLUMN expiry_notified_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN tickets.sla_warning_notified_at IS
    'Sent-marker: when the TICKET_SLA_WARNING notification was dispatched. NULL = not yet sent. Also set (without a warning row) when a ticket is first seen already overdue — the warning is then pointless.';
COMMENT ON COLUMN tickets.sla_overdue_notified_at IS
    'Sent-marker: when the TICKET_SLA_BREACHED notification was dispatched. NULL = not yet sent.';
COMMENT ON COLUMN contracts.expiry_notified_at IS
    'Sent-marker: when the CONTRACT_EXPIRING notification was dispatched (G6 once-only fix). NULL = not yet sent.';
