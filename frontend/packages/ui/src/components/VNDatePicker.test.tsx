import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { VNDatePicker } from './VNDatePicker';
import { parseISODateLocal, toISODateLocal } from '../lib/dateFormat';

describe('VNDatePicker', () => {
  it('shows ISO value as dd/mm/yyyy', () => {
    render(<VNDatePicker value="2026-01-05" onChange={() => {}} />);
    expect(screen.getByDisplayValue('05/01/2026')).toBeTruthy();
  });

  it('shows year-boundary date without off-by-one', () => {
    render(<VNDatePicker value="2025-12-31" onChange={() => {}} />);
    expect(screen.getByDisplayValue('31/12/2025')).toBeTruthy();
  });

  it('shows placeholder when value is empty', () => {
    render(<VNDatePicker value="" onChange={() => {}} />);
    expect(screen.getByPlaceholderText('dd/mm/yyyy')).toBeTruthy();
  });

  it('emits ISO yyyy-mm-dd when a day is picked (no off-by-one at month end)', () => {
    const onChange = vi.fn();
    render(<VNDatePicker value="2026-03-01" onChange={onChange} />);
    fireEvent.click(screen.getByDisplayValue('01/03/2026'));
    // March 2026 grid — pick the last day of the month
    fireEvent.click(screen.getByText('31'));
    expect(onChange).toHaveBeenCalledWith('2026-03-31');
  });

  it('emits empty string when the selected day is toggled off', () => {
    const onChange = vi.fn();
    render(<VNDatePicker value="2026-03-15" onChange={onChange} />);
    fireEvent.click(screen.getByDisplayValue('15/03/2026'));
    // Clicking the already-selected day deselects in single mode
    fireEvent.click(screen.getByText('15'));
    expect(onChange).toHaveBeenCalledWith('');
  });
});

describe('local-safe ISO conversion', () => {
  it('round-trips across month and year boundaries without UTC shift', () => {
    for (const iso of ['2025-12-31', '2026-01-01', '2026-02-28', '2024-02-29']) {
      const d = parseISODateLocal(iso)!;
      expect(toISODateLocal(d)).toBe(iso);
    }
  });

  it('parseISODateLocal returns undefined for empty/invalid', () => {
    expect(parseISODateLocal('')).toBeUndefined();
    expect(parseISODateLocal(null)).toBeUndefined();
    expect(parseISODateLocal('31/12/2025')).toBeUndefined();
  });
});
