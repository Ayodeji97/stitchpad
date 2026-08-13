#!/usr/bin/env node
/*
 * One-off heal for team roster rows missed by the PRE-Phase-2a approveStaffMember
 * that was live in prod until 2026-08-13 (functions last deployed 2026-07-30, but
 * the roster write shipped with Phase 2a on 2026-08-09). For every ACTIVE
 * membership without a matching users/{owner}/team/{staffUid} row, writes the row
 * exactly as the current approveStaffMember transaction would have.
 *
 * Usage:
 *   # dry run (default) — prints what would be written:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/healTeamRosterRow.js
 *   # apply:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/healTeamRosterRow.js --commit
 */
const admin = require('firebase-admin');

// Mirrors functions/src/staff/staffConstants.ts colorSeedFor.
function colorSeedFor(id) {
  let h = 0;
  for (let i = 0; i < id.length; i += 1) {
    h = (h * 31 + id.charCodeAt(i)) >>> 0;
  }
  return h % 10;
}

async function main() {
  if (!process.env.GOOGLE_CLOUD_PROJECT) {
    console.error('GOOGLE_CLOUD_PROJECT is not set — re-run with it pinned explicitly.');
    process.exit(1);
  }
  const commit = process.argv.includes('--commit');
  admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT });
  const db = admin.firestore();

  const memberships = await db.collectionGroup('memberships').where('status', '==', 'active').get();
  let healed = 0;
  let ok = 0;
  for (const doc of memberships.docs) {
    const ownerUid = doc.ref.parent.parent.id;
    const staffUid = doc.id;
    const teamRef = db.doc(`users/${ownerUid}/team/${staffUid}`);
    const teamSnap = await teamRef.get();
    if (teamSnap.exists) {
      ok += 1;
      continue;
    }
    const staffName = ((doc.data().staffName ?? '').trim()) || 'Staff member';
    const nowMs = Date.now();
    console.log(`${commit ? 'HEALING' : 'WOULD HEAL'} users/${ownerUid}/team/${staffUid} name="${staffName}"`);
    if (commit) {
      await teamRef.set(
        {
          name: staffName,
          kind: 'staff',
          status: 'active',
          colorSeed: colorSeedFor(staffUid),
          createdAt: nowMs,
          updatedAt: nowMs,
        },
        { merge: true },
      );
    }
    healed += 1;
  }
  console.log(`${commit ? 'COMMITTED' : 'DRY RUN'} — activeMemberships=${memberships.size} rowsPresent=${ok} rowsHealed=${healed}`);
  if (!commit) console.log('Re-run with --commit to apply.');
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err.message || err);
    process.exit(1);
  },
);
