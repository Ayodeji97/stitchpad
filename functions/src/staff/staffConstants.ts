// Shared constants + wire types for the Owner + Staff membership/invite backend.

export const REGION = 'europe-west1';

// Custom-claim role value marking a staff token. Must match the client's
// WorkshopSessionResolver.CLAIM_ROLE_STAFF.
export const STAFF_ROLE = 'staff';

// Max staff per workshop. A constant for now; the entitlement wiring that can
// raise it per tier is a later slice. Kept in lockstep with the client's
// EntitlementsCalculator when that lands.
export const DEFAULT_STAFF_SEAT_CAP = 2;

// Human-typed invite code length (Crockford-ish alphabet, ~31^8 ≈ 8.5e11).
export const INVITE_CODE_LENGTH = 8;

// Invites expire after 7 days.
export const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export type MembershipStatus = 'pending' | 'active' | 'revoked';
export type InviteStatus = 'open' | 'redeemed' | 'expired' | 'revoked';

export function inviteDocPath(code: string): string {
  return `staffInvites/${code}`;
}

export function membershipsCollectionPath(ownerUid: string): string {
  return `users/${ownerUid}/memberships`;
}

export function membershipDocPath(ownerUid: string, staffUid: string): string {
  return `users/${ownerUid}/memberships/${staffUid}`;
}

// A membership counts against the seat cap while it is pending or active.
export function occupiesSeat(status: MembershipStatus): boolean {
  return status === 'pending' || status === 'active';
}
