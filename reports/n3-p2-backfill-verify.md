# N3 P2 — notification_subscriptions Backfill Verification (dev DB)

Date: 2026-06-11 · DB: `gemek` @ `gemek-postgres` (postgres:15.18-alpine, localhost:5433) · V14 installed 2026-06-11 10:49:38 UTC (17:49:38 +07).

## Verdict: BACKFILL COMPLETE — 0 missing rows on the pre-migration ticket set.

## Counts

| Metric | Value |
|---|---|
| Total subscription rows | 305 |
| CREATOR rows | 228 |
| ASSIGNEE rows | 77 |
| Tickets existing at backfill time | 228 → **228/228 have a CREATOR row (0 missing)** |
| Assigned tickets at backfill time | 77 → **77/77 have an assignee row (0 missing)** |
| Self-assigned tickets (creator = assignee) | 0 (CREATOR-wins rule untestable on this data — covered by the ON CONFLICT clause itself) |

## Expected post-backfill drift (NOT a failure)

Current `tickets` count is 249 (21 more than at backfill; 7 of them assigned). These were committed AFTER V14 ran, by non-`@Transactional` test classes in the same suite boots (ticket controller tests; tickets created 10:50–10:51 UTC vs backfill 10:49:38). A backfill by definition covers only pre-existing rows; live creation gets subscription rows when P3 wires `SubscriptionService.subscribe` into `createTicket`/`assignTicket`. First diagnostic pass mis-flagged this as 21 missing creator rows — root cause was a timezone artifact (`flyway_schema_history.installed_on` is a local-time `timestamp` (+07), DB session is UTC; naive `::timestamptz` cast shifted the cutoff by 7h). Re-verified with the explicit UTC instant.

## Queries used

```sql
-- Counts
SELECT count(*) FROM notification_subscriptions;
SELECT joined_via, count(*) FROM notification_subscriptions GROUP BY joined_via;
SELECT count(*) FROM tickets;
SELECT count(*) FROM tickets WHERE assigned_to_user_id IS NOT NULL;
SELECT count(*) FROM tickets WHERE assigned_to_user_id = submitted_by_user_id;

-- Completeness vs the pre-backfill set (cutoff = V14 installed_on as explicit UTC instant)
SELECT count(*) FROM tickets WHERE created_at <= '2026-06-11 10:49:38.232443+00';                -- 228
SELECT count(*) FROM tickets WHERE assigned_to_user_id IS NOT NULL
  AND created_at <= '2026-06-11 10:49:38.232443+00';                                            -- 77
SELECT count(*) FROM tickets t
 WHERE t.created_at <= '2026-06-11 10:49:38.232443+00'
   AND NOT EXISTS (SELECT 1 FROM notification_subscriptions s
                    WHERE s.entity_id = t.id AND s.entity_type = 'Ticket'
                      AND s.joined_via = 'CREATOR');                                             -- 0
SELECT count(*) FROM tickets t
 WHERE t.assigned_to_user_id IS NOT NULL
   AND t.created_at <= '2026-06-11 10:49:38.232443+00'
   AND NOT EXISTS (SELECT 1 FROM notification_subscriptions s
                    WHERE s.entity_id = t.id AND s.user_id = t.assigned_to_user_id
                      AND s.entity_type = 'Ticket');                                             -- 0
```

Note: dev DB and test-profile DB are the same database (`gemek` @ :5433) — the suite run applied V14 and the (rolled-back) `@Transactional` subscription tests left no rows behind; table content above = backfill + nothing else.
