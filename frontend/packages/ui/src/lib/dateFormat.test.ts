import { describe, it, expect } from 'vitest';
import { formatVNDate, formatVNDateTime } from './dateFormat';

describe('formatVNDate', () => {
  it('formats ISO date as padded dd/mm/yyyy', () => {
    expect(formatVNDate('2026-06-11')).toBe('11/06/2026');
  });

  it('pads single-digit day and month', () => {
    expect(formatVNDate('2026-01-05')).toBe('05/01/2026');
  });

  it('accepts full ISO datetimes', () => {
    // Local-time render — date part is stable for a midday local timestamp
    expect(formatVNDate('2026-12-25T12:00:00')).toBe('25/12/2026');
  });

  it('returns empty string for null/undefined/empty/invalid', () => {
    expect(formatVNDate(null)).toBe('');
    expect(formatVNDate(undefined)).toBe('');
    expect(formatVNDate('')).toBe('');
    expect(formatVNDate('not-a-date')).toBe('');
  });
});

describe('formatVNDateTime', () => {
  it('formats as dd/mm/yyyy HH:mm in 24-hour clock', () => {
    // No timezone suffix → parsed as local time, so HH:mm is deterministic
    expect(formatVNDateTime('2026-06-11T14:05:00')).toBe('11/06/2026 14:05');
  });

  it('pads hour and minute', () => {
    expect(formatVNDateTime('2026-06-01T08:07:00')).toBe('01/06/2026 08:07');
  });

  it('returns empty string for null/undefined/empty/invalid', () => {
    expect(formatVNDateTime(null)).toBe('');
    expect(formatVNDateTime(undefined)).toBe('');
    expect(formatVNDateTime('')).toBe('');
    expect(formatVNDateTime('garbage')).toBe('');
  });
});
