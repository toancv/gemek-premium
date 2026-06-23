import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MarkdownContent } from './MarkdownContent';

describe('MarkdownContent — XSS posture', () => {
  it('does NOT emit a <script> element from raw HTML in content', () => {
    const { container } = render(<MarkdownContent content={'<script>alert(1)</script>'} />);
    // Raw HTML must never become live HTML (no rehype-raw, no dangerouslySetInnerHTML).
    expect(container.querySelector('script')).toBeNull();
    // The dangerous markup must not appear as a real DOM tag anywhere.
    expect(container.innerHTML).not.toContain('<script');
  });

  it('neutralises a javascript: link href', () => {
    const { container } = render(<MarkdownContent content={'[x](javascript:alert(1))'} />);
    const anchors = Array.from(container.querySelectorAll('a'));
    // Either no anchor, or an anchor whose href is not the javascript: scheme.
    for (const a of anchors) {
      const href = a.getAttribute('href') ?? '';
      expect(href.toLowerCase().startsWith('javascript:')).toBe(false);
    }
    expect(container.innerHTML.toLowerCase()).not.toContain('javascript:');
  });

  it('neutralises a data: link href', () => {
    const { container } = render(
      <MarkdownContent content={'[x](data:text/html;base64,PHNjcmlwdD4=)'} />,
    );
    expect(container.innerHTML.toLowerCase()).not.toContain('data:text/html');
  });
});

describe('MarkdownContent — formatting', () => {
  it('renders bold, italic, heading, list and a safe link', () => {
    const md = [
      '# Heading',
      '',
      'Some **bold** and *italic* text.',
      '',
      '- one',
      '- two',
      '',
      '[VTIT](https://vtit.vn)',
    ].join('\n');
    const { container } = render(<MarkdownContent content={md} />);

    expect(container.querySelector('h1')?.textContent).toContain('Heading');
    expect(container.querySelector('strong')?.textContent).toBe('bold');
    expect(container.querySelector('em')?.textContent).toBe('italic');
    expect(container.querySelectorAll('ul li').length).toBe(2);

    const link = container.querySelector('a');
    expect(link?.getAttribute('href')).toBe('https://vtit.vn');
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(link?.getAttribute('target')).toBe('_blank');
  });

  it('renders a single newline as a <br> (legacy plain-text behaviour)', () => {
    const { container } = render(<MarkdownContent content={'line one\nline two'} />);
    // remark-breaks turns a single \n into a hard break.
    expect(container.querySelector('br')).not.toBeNull();
    expect(container.textContent).toContain('line one');
    expect(container.textContent).toContain('line two');
  });

  it('does NOT render an <img> for image markdown without a manifest', () => {
    const { container } = render(<MarkdownContent content={'![alt](https://x/y.png)'} />);
    expect(container.querySelector('img')).toBeNull();
  });
});

describe('MarkdownContent — safe internal images (C2.3a)', () => {
  const MANIFEST = [
    { mediaId: 'inline-1', kind: 'INLINE' as const, url: 'https://minio.local/presigned/inline-1?sig=abc' },
    { mediaId: 'cover-1', kind: 'COVER' as const, url: 'https://minio.local/presigned/cover-1?sig=def' },
  ];

  it('renders a live <img> with the presigned src for an in-manifest placeholder', () => {
    const { container } = render(
      <MarkdownContent content={'![hồ bơi](announcement-media:inline-1)'} mediaManifest={MANIFEST} />,
    );
    const img = container.querySelector('img');
    expect(img).not.toBeNull();
    expect(img?.getAttribute('src')).toBe('https://minio.local/presigned/inline-1?sig=abc');
    expect(img?.getAttribute('alt')).toBe('hồ bơi');
    expect(img?.getAttribute('loading')).toBe('lazy');
    // No author-controlled handlers ever land on the element.
    expect(img?.getAttribute('onerror')).toBeNull();
    expect(img?.getAttribute('onload')).toBeNull();
  });

  it('renders NO img for a placeholder id absent from the manifest', () => {
    const { container } = render(
      <MarkdownContent content={'![x](announcement-media:deleted-99)'} mediaManifest={MANIFEST} />,
    );
    expect(container.querySelector('img')).toBeNull();
  });

  it('does NOT render a live <img> to an arbitrary external URL even with a manifest', () => {
    const { container } = render(
      <MarkdownContent content={'![x](https://evil.com/track.png)'} mediaManifest={MANIFEST} />,
    );
    const imgs = Array.from(container.querySelectorAll('img'));
    expect(imgs.every((i) => i.getAttribute('src') !== 'https://evil.com/track.png')).toBe(true);
    expect(container.querySelector('img')).toBeNull();
  });

  it('keeps a javascript: image src inert (no img, no scheme in DOM)', () => {
    const { container } = render(
      <MarkdownContent content={'![x](javascript:alert(1))'} mediaManifest={MANIFEST} />,
    );
    expect(container.querySelector('img')).toBeNull();
    expect(container.innerHTML.toLowerCase()).not.toContain('javascript:');
  });

  it('leaves raw <img onerror=…> HTML inert text (no live img, no handler)', () => {
    const { container } = render(
      <MarkdownContent content={'<img src=x onerror=alert(1)>'} mediaManifest={MANIFEST} />,
    );
    // No live element exists — no rehype-raw, so the markup is escaped to text, not parsed.
    expect(container.querySelector('img')).toBeNull();
    expect(container.innerHTML).toContain('&lt;img');
    // The raw tag is inert text; nothing carries a real event-handler attribute.
    expect(container.querySelector('[onerror]')).toBeNull();
  });

  it('renders NO live img for an announcement-media link (placeholders are images, not links)', () => {
    const { container } = render(
      <MarkdownContent content={'[click](announcement-media:inline-1)'} mediaManifest={MANIFEST} />,
    );
    const anchors = Array.from(container.querySelectorAll('a'));
    // The internal scheme is stripped on links — never reaches a live href.
    for (const a of anchors) {
      expect((a.getAttribute('href') ?? '').toLowerCase().startsWith('announcement-media:')).toBe(false);
    }
  });
});
