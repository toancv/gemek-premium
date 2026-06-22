import React from 'react';

interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {
  children: React.ReactNode;
  required?: boolean;
}

export function Label({ children, required, className = '', ...props }: LabelProps) {
  return (
    <label className={`block text-sm font-medium text-gray-700 mb-1 ${className}`} {...props}>
      {children}
      {required && <span className="text-red-500 ml-1">*</span>}
    </label>
  );
}

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: string;
}

export function Input({ error, className = '', ...props }: InputProps) {
  return (
    <>
      <input
        className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 disabled:bg-gray-50 disabled:text-gray-500 ${error ? 'border-red-300' : 'border-gray-300'} ${className}`}
        {...props}
      />
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </>
  );
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: string;
  children: React.ReactNode;
}

export function Select({ error, className = '', children, ...props }: SelectProps) {
  return (
    <>
      <select
        className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white disabled:bg-gray-50 ${error ? 'border-red-300' : 'border-gray-300'} ${className}`}
        {...props}
      >
        {children}
      </select>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </>
  );
}

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: string;
}

export function Textarea({ error, className = '', ...props }: TextareaProps) {
  return (
    <>
      <textarea
        rows={3}
        className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 resize-y disabled:bg-gray-50 ${error ? 'border-red-300' : 'border-gray-300'} ${className}`}
        {...props}
      />
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </>
  );
}
