export interface E2eUserSeed {
  fullName: string;
  familyName: string;
  email: string;
  password: string;
}

let lastTimestamp = 0;

function nextUniqueTimestamp(): number {
  const now = Date.now();
  lastTimestamp = now > lastTimestamp ? now : lastTimestamp + 1;
  return lastTimestamp;
}

export function uniqueEmail(prefix = 'e2e'): string {
  const ts = nextUniqueTimestamp();
  const normalizedPrefix = prefix.replace(/[^a-z0-9]/gi, '').toLowerCase() || 'e2e';
  if (normalizedPrefix === 'e2e') {
    return `e2e+${ts}@familion.local`;
  }
  return `e2e+${normalizedPrefix}-${ts}@familion.local`;
}

export function buildUserSeed(label = 'user'): E2eUserSeed {
  const ts = nextUniqueTimestamp();
  const alphaTag = ts
    .toString(26)
    .replace(/[0-9]/g, (d) => String.fromCharCode(97 + Number(d)))
    .slice(-5);
  return {
    fullName: `Ana ${label} ${alphaTag}`,
    familyName: `Familia ${alphaTag}`,
    email: `e2e+${ts}@familion.local`,
    password: `Familion${alphaTag}1A!`,
  };
}

export function buildChildEmail(): string {
  return uniqueEmail('child');
}
