import React, { useState, useEffect } from 'react';

type ToastItem = { id: number; type: 'success' | 'error'; message: string };
type ToastListener = (item: ToastItem) => void;

const listeners: ToastListener[] = [];
let seq = 0;

const emit = (type: 'success' | 'error', message: string) => {
  const item: ToastItem = { id: ++seq, type, message };
  listeners.slice().forEach((l) => l(item));
};

export const toast = {
  success: (message: string) => emit('success', message),
  error: (message: string) => emit('error', message),
};

export function Toaster() {
  const [items, setItems] = useState<ToastItem[]>([]);

  useEffect(() => {
    const listener = (item: ToastItem) => {
      setItems((prev) => [...prev, item]);
      setTimeout(() => setItems((prev) => prev.filter((i) => i.id !== item.id)), 3500);
    };
    listeners.push(listener);
    return () => {
      const idx = listeners.indexOf(listener);
      if (idx >= 0) listeners.splice(idx, 1);
    };
  }, []);

  if (!items.length) return null;

  return (
    <div className="fixed top-4 right-4 z-[200] flex flex-col gap-2 pointer-events-none" style={{ maxWidth: '24rem', width: '100%' }}>
      {items.map((item) => (
        <div
          key={item.id}
          className={`px-4 py-3 rounded-lg shadow-lg text-sm font-medium text-white pointer-events-auto ${item.type === 'success' ? 'bg-green-600' : 'bg-red-600'}`}
        >
          {item.message}
        </div>
      ))}
    </div>
  );
}
