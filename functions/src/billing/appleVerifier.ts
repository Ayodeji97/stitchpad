import * as fs from 'fs';
import * as path from 'path';
import {
  SignedDataVerifier,
  AppStoreServerAPIClient,
  Environment,
} from '@apple/app-store-server-library';
import { AppleNotification, AppleTransaction, AppleVerifier } from './appleBilling';

// Real, library-backed Apple verifier + App Store Server API client. Kept apart
// from appleBilling.ts so the handlers stay unit-testable with a fake verifier
// (no Apple certs needed in tests). Everything here reads config lazily, so
// importing this module is cheap and never touches the filesystem/secrets until
// a real Apple Cloud Function actually runs.

interface RawTransaction {
  transactionId?: string;
  originalTransactionId?: string;
  productId?: string;
  expiresDate?: number;
  appAccountToken?: string;
  revocationDate?: number;
  signedDate?: number;
}

// DER-encoded Apple root CA certificates. Add the four Apple root certs as
// .cer/.der files under functions/certs/apple/ before deploy (download from
// https://www.apple.com/certificateauthority/). Read lazily + cached.
let cachedRootCerts: Buffer[] | null = null;
function loadAppleRootCerts(): Buffer[] {
  if (cachedRootCerts) return cachedRootCerts;
  const dir = path.join(__dirname, '..', '..', 'certs', 'apple');
  let files: string[] = [];
  try {
    files = fs.readdirSync(dir).filter((f) => /\.(cer|der)$/i.test(f));
  } catch {
    files = [];
  }
  if (files.length === 0) {
    throw new Error(
      'Apple root certificates not found in functions/certs/apple (.cer/.der expected).',
    );
  }
  cachedRootCerts = files.map((f) => fs.readFileSync(path.join(dir, f)));
  return cachedRootCerts;
}

function bundleId(): string {
  const id = process.env.APPLE_BUNDLE_ID;
  if (!id) throw new Error('APPLE_BUNDLE_ID is not configured');
  return id;
}

function appAppleId(): number | undefined {
  const raw = process.env.APPLE_APP_APPLE_ID;
  return raw ? Number(raw) : undefined;
}

function verifierFor(environment: Environment): SignedDataVerifier {
  return new SignedDataVerifier(
    loadAppleRootCerts(),
    true, // enable online checks (revocation + expiry against current date)
    environment,
    bundleId(),
    environment === Environment.PRODUCTION ? appAppleId() : undefined,
  );
}

// Sandbox and production deliver to the same endpoint. Verify against production
// first, then fall back to sandbox — Apple's recommended ordering.
async function tryBothEnvironments<T>(run: (v: SignedDataVerifier) => Promise<T>): Promise<T> {
  try {
    return await run(verifierFor(Environment.PRODUCTION));
  } catch (productionError) {
    try {
      return await run(verifierFor(Environment.SANDBOX));
    } catch {
      throw productionError;
    }
  }
}

function mapTransaction(p: RawTransaction): AppleTransaction {
  return {
    transactionId: p.transactionId ?? '',
    originalTransactionId: p.originalTransactionId ?? '',
    productId: p.productId ?? '',
    expiresDate: p.expiresDate,
    appAccountToken: p.appAccountToken,
    revocationDate: p.revocationDate,
    signedDate: p.signedDate,
  };
}

export function createAppleVerifier(): AppleVerifier {
  return {
    async verifyTransaction(signedTransactionJws: string): Promise<AppleTransaction> {
      const decoded = await tryBothEnvironments((v) => v.verifyAndDecodeTransaction(signedTransactionJws));
      return mapTransaction(decoded as RawTransaction);
    },

    async verifyNotification(signedPayload: string): Promise<AppleNotification> {
      const payload = await tryBothEnvironments((v) => v.verifyAndDecodeNotification(signedPayload));
      const data = payload.data;
      const environment = (data?.environment as Environment) ?? Environment.PRODUCTION;
      const verifier = verifierFor(environment);

      let transaction: AppleTransaction | undefined;
      if (data?.signedTransactionInfo) {
        transaction = mapTransaction(
          (await verifier.verifyAndDecodeTransaction(data.signedTransactionInfo)) as RawTransaction,
        );
      }

      let autoRenewStatus: number | undefined;
      if (data?.signedRenewalInfo) {
        const renewal = await verifier.verifyAndDecodeRenewalInfo(data.signedRenewalInfo);
        autoRenewStatus = typeof renewal.autoRenewStatus === 'number' ? renewal.autoRenewStatus : undefined;
      }

      return {
        notificationType: String(payload.notificationType ?? ''),
        subtype: payload.subtype ? String(payload.subtype) : undefined,
        notificationUUID: payload.notificationUUID,
        signedDate: payload.signedDate,
        transaction,
        autoRenewStatus,
      };
    },
  };
}

// ── App Store Server API (reconciliation cron) ─────────────────────────────

export interface AppStoreApi {
  // Latest signed transaction JWS for a subscription, or null if not found.
  latestTransactionJws(originalTransactionId: string): Promise<string | null>;
}

function privateKey(): string {
  const key = process.env.APPLE_IAP_PRIVATE_KEY;
  if (!key) throw new Error('APPLE_IAP_PRIVATE_KEY is not configured');
  return key;
}

function keyId(): string {
  const id = process.env.APPLE_IAP_KEY_ID;
  if (!id) throw new Error('APPLE_IAP_KEY_ID is not configured');
  return id;
}

function issuerId(): string {
  const id = process.env.APPLE_IAP_ISSUER_ID;
  if (!id) throw new Error('APPLE_IAP_ISSUER_ID is not configured');
  return id;
}

function apiClientFor(environment: Environment): AppStoreServerAPIClient {
  return new AppStoreServerAPIClient(privateKey(), keyId(), issuerId(), bundleId(), environment);
}

export function createAppStoreApi(): AppStoreApi {
  return {
    async latestTransactionJws(originalTransactionId: string): Promise<string | null> {
      for (const environment of [Environment.PRODUCTION, Environment.SANDBOX]) {
        try {
          const response = await apiClientFor(environment).getAllSubscriptionStatuses(originalTransactionId);
          for (const group of response.data ?? []) {
            for (const last of group.lastTransactions ?? []) {
              if (last.signedTransactionInfo) return last.signedTransactionInfo;
            }
          }
        } catch {
          // Not in this environment — try the next.
        }
      }
      return null;
    },
  };
}
