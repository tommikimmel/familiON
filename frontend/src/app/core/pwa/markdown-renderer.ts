export type MarkdownInlineToken =
  | { type: 'text'; text: string }
  | { type: 'bold'; text: string }
  | { type: 'code'; text: string };

export type MarkdownBlock =
  | { type: 'heading'; level: 1 | 2 | 3; tokens: readonly MarkdownInlineToken[] }
  | { type: 'unordered-list'; items: readonly (readonly MarkdownInlineToken[])[] }
  | { type: 'ordered-list'; items: readonly (readonly MarkdownInlineToken[])[] }
  | { type: 'paragraph'; tokens: readonly MarkdownInlineToken[] };

const HTML_ESCAPE_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

const INLINE_PATTERN = /(\*\*[^*\n]+?\*\*|`[^`\n]+?`)/g;
const HEADING_PATTERN = /^(#{1,3})\s+(.+)$/;
const UNORDERED_LIST_PATTERN = /^[-*]\s+(.+)$/;
const ORDERED_LIST_PATTERN = /^\d+\.\s+(.+)$/;

export function escapeHtml(input: string): string {
  return input.replace(/[&<>"']/g, (char) => HTML_ESCAPE_MAP[char] ?? char);
}

export function parseInlineMarkdown(text: string): MarkdownInlineToken[] {
  const tokens: MarkdownInlineToken[] = [];
  let cursor = 0;

  for (const match of text.matchAll(INLINE_PATTERN)) {
    const index = match.index ?? 0;
    if (index > cursor) {
      tokens.push({ type: 'text', text: text.slice(cursor, index) });
    }

    const segment = match[0];
    if (segment.startsWith('**') && segment.endsWith('**') && segment.length > 4) {
      tokens.push({ type: 'bold', text: segment.slice(2, -2) });
    } else if (segment.startsWith('`') && segment.endsWith('`') && segment.length > 2) {
      tokens.push({ type: 'code', text: segment.slice(1, -1) });
    } else {
      tokens.push({ type: 'text', text: segment });
    }

    cursor = index + segment.length;
  }

  if (cursor < text.length) {
    tokens.push({ type: 'text', text: text.slice(cursor) });
  }

  return tokens.length > 0 ? tokens : [{ type: 'text', text: '' }];
}

export function parseMarkdown(markdown: string): MarkdownBlock[] {
  if (!markdown || !markdown.trim()) {
    return [];
  }

  const lines = markdown.replace(/\r\n?/g, '\n').split('\n');
  const blocks: MarkdownBlock[] = [];

  let paragraphLines: string[] = [];
  let unorderedItems: MarkdownInlineToken[][] = [];
  let orderedItems: MarkdownInlineToken[][] = [];

  const flushParagraph = (): void => {
    if (paragraphLines.length > 0) {
      blocks.push({
        type: 'paragraph',
        tokens: parseInlineMarkdown(paragraphLines.join(' ')),
      });
      paragraphLines = [];
    }
  };

  const flushUnordered = (): void => {
    if (unorderedItems.length > 0) {
      blocks.push({ type: 'unordered-list', items: unorderedItems });
      unorderedItems = [];
    }
  };

  const flushOrdered = (): void => {
    if (orderedItems.length > 0) {
      blocks.push({ type: 'ordered-list', items: orderedItems });
      orderedItems = [];
    }
  };

  const flushAll = (): void => {
    flushParagraph();
    flushUnordered();
    flushOrdered();
  };

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed) {
      flushAll();
      continue;
    }

    const headingMatch = HEADING_PATTERN.exec(trimmed);
    if (headingMatch) {
      flushAll();
      const level = Math.min(3, Math.max(1, headingMatch[1].length)) as 1 | 2 | 3;
      blocks.push({
        type: 'heading',
        level,
        tokens: parseInlineMarkdown(headingMatch[2].trim()),
      });
      continue;
    }

    const unorderedMatch = UNORDERED_LIST_PATTERN.exec(trimmed);
    if (unorderedMatch) {
      flushParagraph();
      flushOrdered();
      unorderedItems.push(parseInlineMarkdown(unorderedMatch[1].trim()));
      continue;
    }

    const orderedMatch = ORDERED_LIST_PATTERN.exec(trimmed);
    if (orderedMatch) {
      flushParagraph();
      flushUnordered();
      orderedItems.push(parseInlineMarkdown(orderedMatch[1].trim()));
      continue;
    }

    flushUnordered();
    flushOrdered();
    paragraphLines.push(trimmed);
  }

  flushAll();
  return blocks;
}

function renderInlineToSafeHtml(tokens: readonly MarkdownInlineToken[]): string {
  return tokens
    .map((token) => {
      const safeText = escapeHtml(token.text);
      switch (token.type) {
        case 'bold':
          return `<strong>${safeText}</strong>`;
        case 'code':
          return `<code>${safeText}</code>`;
        default:
          return safeText;
      }
    })
    .join('');
}

export function renderSafeMarkdownHtml(markdown: string): string {
  const blocks = parseMarkdown(markdown);
  return blocks
    .map((block) => {
      switch (block.type) {
        case 'heading': {
          const tag = block.level === 1 ? 'h3' : block.level === 2 ? 'h4' : 'h5';
          return `<${tag}>${renderInlineToSafeHtml(block.tokens)}</${tag}>`;
        }
        case 'unordered-list':
          return `<ul>${block.items.map((item) => `<li>${renderInlineToSafeHtml(item)}</li>`).join('')}</ul>`;
        case 'ordered-list':
          return `<ol>${block.items.map((item) => `<li>${renderInlineToSafeHtml(item)}</li>`).join('')}</ol>`;
        case 'paragraph':
          return `<p>${renderInlineToSafeHtml(block.tokens)}</p>`;
      }
    })
    .join('\n');
}
