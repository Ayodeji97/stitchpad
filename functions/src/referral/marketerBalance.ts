// Shared money-aggregate arithmetic for the marketer payout buckets (kobo).
// Centralised so the confirm + clawback paths can't drift on coercion/clamping.

/**
 * Subtract `amount` kobo from a possibly-absent aggregate, clamped at 0. A
 * marketer balance must never go negative (a manual admin edit or an out-of-order
 * reversal could otherwise drive pendingAmount/confirmedAmount below zero and
 * corrupt the payout dashboard).
 */
export function subtractKobo(current: unknown, amount: number): number {
  return Math.max(0, ((current as number) ?? 0) - amount);
}

/** Add `amount` kobo to a possibly-absent aggregate. */
export function addKobo(current: unknown, amount: number): number {
  return ((current as number) ?? 0) + amount;
}
