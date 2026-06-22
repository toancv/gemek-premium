/**
 * VN date display helpers — dd/mm/yyyy, zero-padded, 24-hour time.
 *
 * DISPLAY ONLY: wire strings stay ISO (yyyy-mm-dd / ISO-8601). Never feed
 * these outputs back into payloads, query params, or <input type="date">
 * value/min/max — those require ISO by API contract and HTML spec.
 *
 * TIMEZONE (deliberate decision, see DECISIONS.md 2026-06-11): BE sends ISO
 * (UTC for datetimes); we render with LOCAL-time getters, so users see their
 * browser's local time (UTC+7 for VN users). Built manually from date parts —
 * NOT toLocaleDateString('vi-VN'), which is unpadded and engine-variant.
 */

const pad2 = (n: number): string => String(n).padStart(2, '0');

/** ISO date/datetime string → 'dd/mm/yyyy'. null/undefined/invalid → ''. */
export function formatVNDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`;
}

/**
 * ISO 'yyyy-mm-dd' → Date at LOCAL midnight. undefined for empty/invalid.
 * Never use new Date(isoString) for date-only values — that parses as UTC
 * midnight and shifts a day in negative-offset timezones.
 */
export function parseISODateLocal(iso: string | null | undefined): Date | undefined {
  if (!iso) return undefined;
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return undefined;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return isNaN(d.getTime()) ? undefined : d;
}

/** Date → ISO 'yyyy-mm-dd' from LOCAL components (no UTC round-trip). */
export function toISODateLocal(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** ISO datetime string → 'dd/mm/yyyy HH:mm' (24h, local time). Invalid → ''. */
export function formatVNDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}
