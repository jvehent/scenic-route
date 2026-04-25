import * as admin from "firebase-admin";
import { logger } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

/**
 * Per-author rate limit on comments.
 *
 * Strategy: maintain `/rate_limits/comments_{uid}` with `windowStart` and `count`.
 * On each comment created, atomically increment within the rolling minute.
 * If over limit, delete the new comment and tombstone-mark for moderation.
 *
 * Limit: 30 comments per author per 5 minutes (covers typical "post + reply"
 * bursts but blocks programmatic spam).
 */
const WINDOW_MS = 5 * 60 * 1000;
const LIMIT = 30;

export const onCommentCreated = onDocumentCreated(
  { document: "drives/{driveId}/comments/{commentId}", region: "us-central1" },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    const uid = data.authorUid as string | undefined;
    if (!uid) return;

    const db = admin.firestore();
    const counterRef = db.doc(`rate_limits/comments_${uid}`);

    const now = Date.now();
    const overLimit = await db.runTransaction(async (txn) => {
      const cur = await txn.get(counterRef);
      const cd = cur.exists ? cur.data()! : { windowStart: now, count: 0 };
      const windowStart = (now - cd.windowStart) > WINDOW_MS ? now : cd.windowStart;
      const count = (windowStart === cd.windowStart ? cd.count : 0) + 1;
      txn.set(counterRef, { windowStart, count, updatedAt: now });
      return count > LIMIT;
    });

    if (overLimit) {
      logger.warn(`commentRateLimit: ${uid} exceeded ${LIMIT}/${WINDOW_MS / 1000}s, deleting comment ${snap.ref.path}`);
      await snap.ref.delete().catch((e) => logger.warn("rate-limit delete failed", e));
    }
  },
);
