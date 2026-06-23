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

  it('does NOT render an <img> for image markdown (images deferred to C2)', () => {
    const { container } = render(<MarkdownContent content={'![alt](https://x/y.png)'} />);
    expect(container.querySelector('img')).toBeNull();
  });
});
