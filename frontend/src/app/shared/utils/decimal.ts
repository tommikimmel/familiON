/**
 * Parses a backend decimal string (e.g. "36000.0000") into a finite JS number.
 * Returns 0 for null, undefined, blank, or non-numeric inputs.
 */
export function toNumber(decimal: string | null | undefined): number {
  if (decimal == null) {
    return 0;
  }
  const trimmed = decimal.trim();
  if (trimmed === '') {
    return 0;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : 0;
}
