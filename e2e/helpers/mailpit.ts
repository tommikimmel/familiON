export interface MailpitAddress {
  Name: string;
  Address: string;
}

export interface MailpitMessageSummary {
  ID: string;
  MessageID: string;
  From: MailpitAddress;
  To: MailpitAddress[];
  Subject: string;
  Created: string;
  Snippet?: string;
}

export interface MailpitMessagesResponse {
  total: number;
  unread: number;
  count: number;
  messages: MailpitMessageSummary[];
}

export interface MailpitMessageDetail {
  ID: string;
  MessageID: string;
  From: MailpitAddress;
  To: MailpitAddress[];
  Subject: string;
  Date: string;
  Text: string;
  HTML: string;
}

export interface WaitForMessageOptions {
  subjectIncludes?: string;
  excludeIds?: ReadonlySet<string>;
  timeoutMs?: number;
  pollIntervalMs?: number;
}

const DEFAULT_MAILPIT_URL = 'http://localhost:8025';

function mailpitBaseUrl(): string {
  return (process.env.E2E_MAILPIT_URL ?? DEFAULT_MAILPIT_URL).replace(/\/+$/, '');
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Mailpit request failed (${response.status}) for ${url}`);
  }
  return (await response.json()) as T;
}

export async function listMessages(limit = 50): Promise<MailpitMessageSummary[]> {
  const data = await fetchJson<MailpitMessagesResponse>(
    `${mailpitBaseUrl()}/api/v1/messages?limit=${limit}`,
  );
  return data.messages ?? [];
}

export async function getMessage(id: string): Promise<MailpitMessageDetail> {
  return fetchJson<MailpitMessageDetail>(
    `${mailpitBaseUrl()}/api/v1/message/${encodeURIComponent(id)}`,
  );
}

function recipientMatches(message: MailpitMessageSummary, recipient: string): boolean {
  const normalized = recipient.trim().toLowerCase();
  return (message.To ?? []).some(
    (addr) => addr.Address?.trim().toLowerCase() === normalized,
  );
}

function stripHtmlTags(html: string): string {
  return html
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export async function waitForLatestMessage(
  recipient: string,
  options: WaitForMessageOptions = {},
): Promise<MailpitMessageDetail> {
  const timeoutMs = options.timeoutMs ?? 20_000;
  const pollIntervalMs = options.pollIntervalMs ?? 500;
  const subjectFilter = options.subjectIncludes?.trim().toLowerCase();
  const excludeIds = options.excludeIds;
  const deadline = Date.now() + timeoutMs;

  let lastError: unknown = null;

  while (Date.now() < deadline) {
    try {
      const messages = await listMessages(50);
      const matching = messages
        .filter((msg) => recipientMatches(msg, recipient))
        .filter((msg) => !excludeIds || !excludeIds.has(msg.ID))
        .filter((msg) =>
          subjectFilter ? msg.Subject.toLowerCase().includes(subjectFilter) : true,
        )
        .sort((a, b) => Date.parse(b.Created) - Date.parse(a.Created));

      if (matching.length > 0) {
        return await getMessage(matching[0].ID);
      }
    } catch (error) {
      lastError = error;
    }

    await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
  }

  const subjectHint = options.subjectIncludes
    ? ` with subject containing "${options.subjectIncludes}"`
    : '';
  const causeHint =
    lastError instanceof Error ? ` (last error: ${lastError.message})` : '';
  throw new Error(
    `Timed out after ${timeoutMs}ms waiting for Mailpit email to "${recipient}"${subjectHint}${causeHint}`,
  );
}

export function extractSixDigitCode(message: MailpitMessageDetail): string {
  const textBody = message.Text?.trim();

  if (textBody) {
    const standaloneLineMatch = textBody.match(/^\s*(\d{6})\s*$/m);
    if (standaloneLineMatch) {
      return standaloneLineMatch[1];
    }
  }

  const candidates = [textBody, message.HTML ? stripHtmlTags(message.HTML) : ''];

  for (const body of candidates) {
    if (!body) {
      continue;
    }
    const match = body.match(/(?<!\d)(\d{6})(?!\d)/);
    if (match) {
      return match[1];
    }
  }

  throw new Error(
    `Could not find a 6-digit code in email "${message.Subject}" (ID=${message.ID})`,
  );
}

export function extractLink(
  message: MailpitMessageDetail,
  urlPattern?: RegExp,
): string {
  const sources = [message.Text ?? '', message.HTML ?? ''];
  const urlRegex = /https?:\/\/[^\s"'<>)]+/g;

  for (const source of sources) {
    const matches = source.match(urlRegex) ?? [];
    for (const rawUrl of matches) {
      const cleaned = rawUrl.replace(/&amp;/g, '&').replace(/[.,;]+$/, '');
      if (!urlPattern || urlPattern.test(cleaned)) {
        return cleaned;
      }
    }
  }

  throw new Error(
    `Could not find a matching link in email "${message.Subject}" (ID=${message.ID})`,
  );
}

export async function waitForSixDigitCode(
  recipient: string,
  options: WaitForMessageOptions = {},
): Promise<{ code: string; messageId: string }> {
  const message = await waitForLatestMessage(recipient, options);
  return {
    code: extractSixDigitCode(message),
    messageId: message.ID,
  };
}

export async function waitForLink(
  recipient: string,
  urlPattern?: RegExp,
  options: WaitForMessageOptions = {},
): Promise<{ link: string; messageId: string }> {
  const message = await waitForLatestMessage(recipient, options);
  return {
    link: extractLink(message, urlPattern),
    messageId: message.ID,
  };
}
