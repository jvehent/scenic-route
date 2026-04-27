import * as admin from "firebase-admin";
import { logger } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as crypto from "crypto";

/**
 * Server-side profile completion. The Firestore create rule for /users/{uid}
 * restricts the field set the client may write. This function fills in the
 * privileged fields (anonHandle, points, stats) so the client cannot inject
 * them at create time.
 *
 * The anon handle is generated with crypto.randomBytes — NOT derivable from uid,
 * unlike the client-side fallback in Profile.kt.
 */

const ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/O/1/l/I ambiguity

function randomHandle(): string {
  const buf = crypto.randomBytes(8);
  let s = "";
  for (let i = 0; i < 8; i++) s += ALPHABET[buf[i] % ALPHABET.length];
  return `traveler-${s}`;
}

export const onUserDocCreated = onDocumentCreated(
  { document: "users/{uid}", region: "us-central1" },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();

    if (data.anonHandle) return; // idempotency — already bootstrapped

    // One retry on collision (random space is huge but be defensive).
    let handle = randomHandle();
    const db = admin.firestore();
    const collision = await db
      .collection("users")
      .where("anonHandle", "==", handle)
      .limit(1)
      .get();
    if (!collision.empty) handle = randomHandle();

    // createdAt + updatedAt are stamped server-side here, ignoring whatever the
    // client might have written. The Firestore rules also forbid those fields
    // at create time, but defense-in-depth in case the rules ever drift.
    const now = Date.now();
    await snap.ref.update({
      anonHandle: handle,
      points: 0,
      stats: { drivesPublished: 0, helpfulAnswers: 0 },
      createdAt: now,
      updatedAt: now,
    });
    logger.info("onUserDocCreated: bootstrapped profile");
  },
);
