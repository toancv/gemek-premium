import React from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import remarkBreaks from 'remark-breaks';

/**
 * The XSS-safe Markdown configuration — single source of truth shared by the resident
 * detail view and the admin authoring preview so the two can never drift apart.
 *
 * Security posture (all mandatory):
 * - No `rehype-raw` / no rehypePlugins → embedded raw HTML is NOT rendered as HTML.
 * - No `dangerouslySetInnerHTML` (react-markdown renders React elements, not innerHTML).
 * - Element allowlist restricts output to the intended subset; `img` is excluded (images
 *   land in C2) so image markdown renders as nothing rather than an <img>.
 * - URLs are scheme-filtered: only http/https/mailto and relative/anchor links survive;
 *   `javascript:`, `data:`, etc. are neutralised to an empty href.
 */

/** Elements the renderer is permitted to emit. Everything else is dropped. */
export const MARKDOWN_ALLOWED_ELEMENTS = [
  'p', 'br', 'strong', 'em',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'a',
  'blockquote', 'code', 'pre',
];

/** URL schemes permitted on links. Anything outside this set is neutralised. */
const SAFE_URL_SCHEMES = ['http:', 'https:', 'mailto:'];

/**
 * Returns the URL unchanged when it is safe (allowed scheme, or scheme-less relative/anchor),
 * otherwise returns an empty string so the dangerous URL never reaches the DOM.
 */
function safeUrlTransform(url: string): string {
  const value = (url ?? '').trim();
  if (value === '') return '';
  // Relative paths, anchors, and query-only links carry no scheme — safe by construction.
  if (value.startsWith('/') || value.startsWith('#') || value.startsWith('?')) return value;
  // A scheme is present only when a ':' appears before any '/', '?' or '#'.
  const schemeMatch = /^([a-zA-Z][a-zA-Z0-9+.-]*):/.exec(value);
  if (!schemeMatch) return value;
  const scheme = schemeMatch[1].toLowerCase() + ':';
  return SAFE_URL_SCHEMES.includes(scheme) ? value : '';
}

/** Per-element Tailwind classes — mobile-first resident type scale, no typography plugin. */
const components: Components = {
  p: ({ children }) => <p className="mb-2 last:mb-0 leading-relaxed">{children}</p>,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  h1: ({ children }) => <h1 className="text-lg font-semibold mt-3 mb-1.5">{children}</h1>,
  h2: ({ children }) => <h2 className="text-base font-semibold mt-3 mb-1.5">{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-semibold mt-2 mb-1">{children}</h3>,
  h4: ({ children }) => <h4 className="text-sm font-semibold mt-2 mb-1">{children}</h4>,
  h5: ({ children }) => <h5 className="text-sm font-semibold mt-2 mb-1">{children}</h5>,
  h6: ({ children }) => <h6 className="text-sm font-semibold mt-2 mb-1">{children}</h6>,
  ul: ({ children }) => <ul className="list-disc pl-5 mb-2 space-y-0.5">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal pl-5 mb-2 space-y-0.5">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  blockquote: ({ children }) => (
    <blockquote className="border-l-4 border-gray-200 pl-3 italic text-gray-600 my-2">{children}</blockquote>
  ),
  code: ({ children }) => (
    <code className="bg-gray-100 rounded px-1 py-0.5 text-[0.85em] font-mono">{children}</code>
  ),
  pre: ({ children }) => (
    <pre className="bg-gray-100 rounded-md p-3 overflow-x-auto text-[0.85em] font-mono my-2">{children}</pre>
  ),
  a: ({ href, children }) => {
    const safeHref = safeUrlTransform(typeof href === 'string' ? href : '');
    // External http(s) links open in a new tab; mailto/relative stay in-context.
    const isExternal = safeHref.startsWith('http:') || safeHref.startsWith('https:');
    return (
      <a
        href={safeHref || undefined}
        className="text-blue-600 underline break-words"
        rel="noopener noreferrer"
        target={isExternal ? '_blank' : undefined}
      >
        {children}
      </a>
    );
  },
};

/** Props for {@link MarkdownContent}. */
export interface MarkdownContentProps {
  /** Raw Markdown source to render safely. */
  content: string;
  /** Optional wrapper class to control base typography (e.g. text size / color). */
  className?: string;
}

/**
 * Renders Markdown as safe React elements. The ONLY Markdown render surface in the app —
 * both the resident announcement detail and the admin authoring preview use this component,
 * so the locked-down security config above is enforced identically everywhere.
 */
export function MarkdownContent({ content, className }: MarkdownContentProps): JSX.Element {
  return (
    <div className={className ?? 'text-sm text-gray-700'}>
      <ReactMarkdown
        remarkPlugins={[remarkBreaks]}
        allowedElements={MARKDOWN_ALLOWED_ELEMENTS}
        urlTransform={safeUrlTransform}
        components={components}
      >
        {content ?? ''}
      </ReactMarkdown>
    </div>
  );
}
