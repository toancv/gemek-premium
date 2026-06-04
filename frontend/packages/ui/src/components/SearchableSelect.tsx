import React, { useState, useRef, useEffect } from 'react';

export interface SearchableOption {
  value: string;
  label: string;
}

interface SearchableSelectProps {
  options: SearchableOption[];
  value: string;
  onChange: (value: string) => void;
  loading?: boolean;
  placeholder?: string;
  error?: string;
  disabled?: boolean;
}

export function SearchableSelect({
  options,
  value,
  onChange,
  loading,
  placeholder = 'Chọn...',
  error,
  disabled,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlighted, setHighlighted] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const selected = options.find((o) => o.value === value);
  const filtered = query
    ? options.filter((o) => o.label.toLowerCase().includes(query.toLowerCase()))
    : options;

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setQuery('');
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const openDropdown = () => {
    if (disabled || loading) return;
    setOpen(true);
    setQuery('');
    setHighlighted(0);
    setTimeout(() => inputRef.current?.focus(), 0);
  };

  const selectOption = (opt: SearchableOption) => {
    onChange(opt.value);
    setOpen(false);
    setQuery('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlighted((h: number) => Math.min(h + 1, filtered.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlighted((h: number) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (filtered[highlighted]) selectOption(filtered[highlighted]);
    } else if (e.key === 'Escape') {
      setOpen(false);
      setQuery('');
    }
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={openDropdown}
        disabled={disabled || loading}
        className={`block w-full text-left rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white disabled:bg-gray-50 disabled:text-gray-500 ${error ? 'border-red-300' : 'border-gray-300'}`}
      >
        {loading ? (
          <span className="text-gray-400">Đang tải...</span>
        ) : selected ? (
          selected.label
        ) : (
          <span className="text-gray-400">{placeholder}</span>
        )}
      </button>

      {open && (
        <div className="absolute z-50 mt-1 w-full bg-white rounded-md border border-gray-200 shadow-lg">
          <div className="p-2 border-b border-gray-100">
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={(e) => { setQuery(e.target.value); setHighlighted(0); }}
              onKeyDown={handleKeyDown}
              placeholder="Tìm kiếm..."
              className="block w-full rounded border border-gray-200 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <ul className="max-h-48 overflow-auto py-1">
            {filtered.length === 0 && (
              <li className="px-3 py-2 text-sm text-gray-400">Không tìm thấy kết quả</li>
            )}
            {filtered.map((opt, idx) => (
              <li
                key={opt.value}
                onMouseDown={() => selectOption(opt)}
                onMouseEnter={() => setHighlighted(idx)}
                className={`px-3 py-2 text-sm cursor-pointer ${idx === highlighted ? 'bg-blue-50 text-blue-700' : 'text-gray-900 hover:bg-gray-50'}`}
              >
                {opt.label}
              </li>
            ))}
          </ul>
        </div>
      )}

      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}
