import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";

const PURGE_AFTER_MS = 30 * 24 * 60 * 60 * 1000;
const BATCH_LIMIT = 200;

/**
 * Daily sweep: hard-delete drives that have been in trash for more than 30 days.
 * The existing onDriveDeleted trigger handles cascading (track blob, photos,
 * subcollections, public index entries).
 */
export const cleanupTrashedDrives = onSchedule(
  { schedule: "every 24 hours", region: "us-central1", timeZone: "Etc/UTC" },
  async () => {
    const cutoff = Date.now() - PURGE_AFTER_MS;
    const db = admin.firestore();
    const snap = await db.collection("drives")
      .where("deletedAt", "<=", cutoff)
      .limit(BATCH_LIMIT)
      .get();

    if (snap.empty) {
      logger.info(`cleanupTrashedDrives: nothing to purge (cutoff=${cutoff})`);
      return;
    }

    logger.info(`cleanupTrashedDrives: purging ${snap.size} drive(s) older than ${PURGE_AFTER_MS / (24 * 3600 * 1000)} days`);

    // Delete sequentially so each fires the onDriveDeleted cascade individually.
    for (const doc of snap.docs) {
      try {
        await doc.ref.delete();
      } catch (e) {
        logger.warn(`cleanupTrashedDrives: failed to delete ${doc.id}`, e);
      }
    }
  },
);
