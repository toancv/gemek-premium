import React, { useEffect, useRef, useState } from 'react';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { parseISODateLocal, toISODateLocal } from '../lib/dateFormat';

export interface VNDatePickerProps {
  /** ISO 'yyyy-mm-dd' or '' — the WIRE value, never dd/mm. */
  value: string;
  /** Always called with ISO 'yyyy-mm-dd' (or '' when cleared). */
  onChange: (iso: string) => void;
  /** ISO 'yyyy-mm-dd' lower bound (mirrors native input min). */
  min?: string;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
}

const pad2 = (n: number): string => String(n).padStart(2, '0');

/**
 * Date input showing dd/mm/yyyy, backed by react-day-picker.
 * Display-only swap for native <input type="date">: state stays ISO
 * 'yyyy-mm-dd' (KIND C wire format) — conversion uses LOCAL Y-M-D parts,
 * never a UTC round-trip, so no off-by-one across timezones.
 */
export function VNDatePicker({ value, onChange, min, disabled, placeholder = 'dd/mm/yyyy', className }: VNDatePickerProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  // Close on outside click — same UX as native picker dismiss
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const selected = parseISODateLocal(value);
  const minDate = parseISODateLocal(min);
  const display = selected
    ? `${pad2(selected.getDate())}/${pad2(selected.getMonth() + 1)}/${selected.getFullYear()}`
    : '';

  return (
    <div ref={rootRef} className={`relative ${className ?? ''}`}>
      <input
        type="text"
        readOnly
        disabled={disabled}
        value={display}
        placeholder={placeholder}
        onClick={() => !disabled && setOpen((o) => !o)}
        className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
      />
      {open && (
        <div className="absolute z-50 mt-1 bg-white border border-gray-200 rounded-md shadow-lg p-2">
          <DayPicker
            mode="single"
            selected={selected}
            defaultMonth={selected ?? minDate}
            disabled={minDate ? { before: minDate } : undefined}
            onSelect={(d) => {
              onChange(d ? toISODateLocal(d) : '');
              setOpen(false);
            }}
          />
        </div>
      )}
    </div>
  );
}
