/** Notification money uses major units and the currency stamped on the order, never the user's current country. */
const CURRENCIES = {
  NGN: { symbol: '₦', decimals: 2 },
  GHS: { symbol: 'GH₵', decimals: 2 },
  KES: { symbol: 'KSh', decimals: 2 },
  XOF: { symbol: 'F CFA', decimals: 0 },
  XAF: { symbol: 'FCFA', decimals: 0 },
  GNF: { symbol: 'FG', decimals: 0 },
  CDF: { symbol: 'FC', decimals: 2 },
} as const;

export type NotificationCurrency = keyof typeof CURRENCIES;

export function notificationCurrency(value: unknown): NotificationCurrency {
  const code = typeof value === 'string' ? value.trim().toUpperCase() : '';
  return Object.prototype.hasOwnProperty.call(CURRENCIES, code) ? code as NotificationCurrency : 'NGN';
}

/** Match the client's fraction policy: whole amounts omit .00, nonzero minor units retain both digits. */
export function formatNotificationMoney(amount: number, savedCurrency?: unknown): string {
  const currency = CURRENCIES[notificationCurrency(savedCurrency)];
  const scale = 10 ** currency.decimals;
  const rounded = Math.round((Number.isFinite(amount) ? amount : 0) * scale) / scale;
  const number = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: Number.isInteger(rounded) ? 0 : currency.decimals,
    maximumFractionDigits: currency.decimals,
  }).format(Math.abs(rounded));
  const space = /[A-Za-z]$/.test(currency.symbol) ? ' ' : '';
  return `${rounded < 0 ? '-' : ''}${currency.symbol}${space}${number}`;
}
