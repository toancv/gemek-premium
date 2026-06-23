import React from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import remarkBreaks from 'remark-breaks';

/**
 * The XSS-safe Markdown configuration — single source of truth shared by the resident
 * detail view and the admin authoring preview so the two can never drift apart.
 *
 * Security posture (all mandatory):
 * - No `rehype-raw` / no rehypePlugins → embedded raw HTML (e.g. `<img onerror=…>`) is NOT
 *   rendered as HTML; it stays inert literal text.
 * - No `dangerouslySetInnerHTML` (react-markdown renders React elements, not innerHTML).
 * - Element allowlist restricts output to the intended subset.
 * - URLs are scheme-filtered: only http/https/mailto and relative/anchor links survive;
 *   `javascript:`, `data:`, etc. are neutralised to an empty href.
 * - `img` (C2.3a) renders ONLY for the internal `announcement-media:{id}` placeholder when
 *   that id resolves to a server-minted manifest entry. An arbitrary author-supplied src
 *   (`![](https://evil/x.png)`, `![](javascript:…)`, raw HTML `<img>`) NEVER yields a live
 *   `<img>` — no external/SSRF/tracking surface, no author-controlled handlers.
 */

/** Internal scheme an authoring placeholder uses to reference an announcement's own media. */
export const ANNOUNCEMENT_MEDIA_SCHEME = 'announcement-media:';

/** One server-minted manifest entry: a media id mapped to a short-lived presigned URL. */
export interface AnnouncementMediaManifestEntry {
  /** Media row id — the {id} in an `announcement-media:{id}` placeholder. */
  mediaId: string;
  /** Cover (rendered as a banner by the page) or inline (resolved inside the body). */
  kind: 'COVER' | 'INLINE';
  /** Fresh presigned GET URL minted per request through the C2.1 scope gate. */
  url: string;
}

/** Elements the renderer is permitted to emit. Everything else is dropped. */
export const MARKDOWN_ALLOWED_ELEMENTS = [
  'p', 'br', 'strong', 'em',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'a',
  'blockquote', 'code', 'pre', 'img',
];

/** URL schemes permitted on links. Anything outside this set is neutralised. */
const SAFE_URL_SCHEMES = ['http:', 'https:', 'mailto:'];

/**
 * Returns the URL unchanged when it is safe (allowed scheme, or scheme-less relative/anchor),
 * otherwise returns an empty string so the dangerous URL never reaches the DOM. Used by the
 * link renderer; the internal `announcement-media:` scheme is NOT exempt here, so an
 * `[text](announcement-media:id)` link is neutralised (placeholders are images, not links).
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

/**
 * Global url transform applied to every link/image url BEFORE it reaches a component.
 * The internal `announcement-media:` placeholder is passed through untouched so the custom
 * {@link makeImgComponent} can resolve it against the manifest; it never reaches a live DOM
 * attribute (the img component maps it to a presigned URL, the link component re-strips it).
 * Everything else delegates to {@link safeUrlTransform}.
 */
function urlTransform(url: string): string {
  const value = (url ?? '').trim();
  if (value.toLowerCase().startsWith(ANNOUNCEMENT_MEDIA_SCHEME)) return value;
  return safeUrlTransform(value);
}

/**
 * Builds the custom `img` renderer bound to a media manifest. It renders a live `<img>` ONLY
 * when the markdown image src is an `announcement-media:{id}` placeholder whose id is present
 * in the manifest; the src is then the server-minted presigned URL, never the author's string.
 * Any other src (external URL, neutralised `javascript:`/`data:`, unknown id) renders nothing
 * (or the alt text) — never a live img to an author-controlled URL. Only safe attributes are
 * set: src, escaped alt, loading="lazy"; no author-controlled event handlers or styles.
 *
 * @param manifest the announcement's media manifest (empty when none / unauthorized).
 * @returns a react-markdown `img` component.
 */
function makeImgComponent(manifest: AnnouncementMediaManifestEntry[]): Components['img'] {
  return ({ src, alt }) => {
    const altText = typeof alt === 'string' ? alt : '';
    const value = typeof src === 'string' ? src.trim() : '';
    // Only the internal placeholder may ever become a live <img>.
    if (!value.toLowerCase().startsWith(ANNOUNCEMENT_MEDIA_SCHEME)) {
      return altText ? <>{altText}</> : null;
    }
    const mediaId = value.slice(ANNOUNCEMENT_MEDIA_SCHEME.length);
    const entry = manifest.find((m) => m.mediaId === mediaId);
    // Unknown / deleted / foreign id → render nothing (or alt), never a broken/arbitrary img.
    if (!entry) {
      return altText ? <>{altText}</> : null;
    }
    return (
      <img
        src={entry.url}
        alt={altText}
        loading="lazy"
        className="rounded-lg my-2 max-w-full h-auto"
      />
    );
  };
}

/** Per-element Tailwind classes — mobile-first resident type scale, no typography plugin. */
const baseComponents: Components = {
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
  /**
   * Media manifest resolving `announcement-media:{id}` placeholders to presigned URLs.
   * Omit (or pass empty) when the surface has no internal media — placeholders then render
   * as nothing. The resident detail passes the announcement's manifest; the admin authoring
   * preview (C2.3b) will pass the draft's uploaded-media manifest.
   */
  mediaManifest?: AnnouncementMediaManifestEntry[];
}

/**
 * Renders Markdown as safe React elements. The ONLY Markdown render surface in the app —
 * both the resident announcement detail and the admin authoring preview use this component,
 * so the locked-down security config above is enforced identically everywhere. Inline images
 * resolve ONLY from the {@link MarkdownContentProps.mediaManifest}; the cover image is rendered
 * separately as a banner by the page (it is not duplicated inline).
 */
export function MarkdownContent({ content, className, mediaManifest }: MarkdownContentProps): JSX.Element {
  // Bind the img renderer to this surface's manifest; the rest of the config is static.
  const components: Components = React.useMemo(
    () => ({ ...baseComponents, img: makeImgComponent(mediaManifest ?? []) }),
    [mediaManifest],
  );
  return (
    <div className={className ?? 'text-sm text-gray-700'}>
      <ReactMarkdown
        remarkPlugins={[remarkBreaks]}
        allowedElements={MARKDOWN_ALLOWED_ELEMENTS}
        urlTransform={urlTransform}
        components={components}
      >
        {content ?? ''}
      </ReactMarkdown>
    </div>
  );
}
