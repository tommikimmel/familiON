import * as fs from 'node:fs';
import * as path from 'node:path';
import type { BrowserContext } from '@playwright/test';

export const OUT_DIR = path.resolve(__dirname, 'out');
export const SESSION_FILE = path.join(OUT_DIR, 'session.json');

type StorageState = Awaited<ReturnType<BrowserContext['storageState']>>;

export interface VisualSession {
  createdAt: string;
  seedBaseUrl: string;
  fullName: string;
  familyName: string;
  email: string;
  password: string;
  child: { fullName: string; email: string; password: string } | null;
  storageState: StorageState;
}

export function writeSession(session: VisualSession): void {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(SESSION_FILE, JSON.stringify(session, null, 2), 'utf8');
}

export function readSession(): VisualSession {
  if (!fs.existsSync(SESSION_FILE)) {
    throw new Error(`Missing ${SESSION_FILE}. Run "npm run visual:seed" first.`);
  }
  return JSON.parse(fs.readFileSync(SESSION_FILE, 'utf8')) as VisualSession;
}
