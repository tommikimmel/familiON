import { describe, expect, it } from 'vitest';
import { escapeHtml, parseMarkdown, renderSafeMarkdownHtml } from './markdown-renderer';

describe('markdown-renderer', () => {
  it('parses headings, unordered/ordered lists, paragraphs and bold inline tokens', () => {
    const md = [
      '# Novedades',
      '## Mejoras',
      '- Soporte **offline** mejorado',
      '* Nuevo **centro de notificaciones**',
      '1. Paso **uno**',
      '2. Paso `dos`',
      '',
      'Gracias por usar **FamiliON**.',
    ].join('\n');

    const blocks = parseMarkdown(md);
    expect(blocks).toEqual([
      { type: 'heading', level: 1, tokens: [{ type: 'text', text: 'Novedades' }] },
      { type: 'heading', level: 2, tokens: [{ type: 'text', text: 'Mejoras' }] },
      {
        type: 'unordered-list',
        items: [
          [
            { type: 'text', text: 'Soporte ' },
            { type: 'bold', text: 'offline' },
            { type: 'text', text: ' mejorado' },
          ],
          [
            { type: 'text', text: 'Nuevo ' },
            { type: 'bold', text: 'centro de notificaciones' },
          ],
        ],
      },
      {
        type: 'ordered-list',
        items: [
          [
            { type: 'text', text: 'Paso ' },
            { type: 'bold', text: 'uno' },
          ],
          [
            { type: 'text', text: 'Paso ' },
            { type: 'code', text: 'dos' },
          ],
        ],
      },
      {
        type: 'paragraph',
        tokens: [
          { type: 'text', text: 'Gracias por usar ' },
          { type: 'bold', text: 'FamiliON' },
          { type: 'text', text: '.' },
        ],
      },
    ]);
  });

  it('escapes raw HTML tags and dangerous attributes safely', () => {
    expect(escapeHtml(`<script>alert("xss")</script> & 'test'`)).toBe(
      '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt; &amp; &#39;test&#39;',
    );

    const malicious = [
      '# <img src=x onerror=alert(1)> Título',
      '- **<script>alert(1)</script>** item',
      '<a href="javascript:alert(1)">click</a>',
    ].join('\n');

    const safeHtml = renderSafeMarkdownHtml(malicious);
    expect(safeHtml).not.toContain('<script>');
    expect(safeHtml).not.toContain('<img');
    expect(safeHtml).not.toContain('<a ');
    expect(safeHtml).toContain('&lt;img src=x onerror=alert(1)&gt; Título');
    expect(safeHtml).toContain('<strong>&lt;script&gt;alert(1)&lt;/script&gt;</strong>');
    expect(safeHtml).toContain('&lt;a href=&quot;javascript:alert(1)&quot;&gt;click&lt;/a&gt;');
  });
});
