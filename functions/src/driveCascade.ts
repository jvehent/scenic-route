import * as admin from "firebase-admin";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { indexPrefix } from "./geohash";

interface DriveDoc {
  ownerUid?: string;
  visibility?: string;
  geohash?: string | null;
}

export const onDriveDeleted = onDocumentDeleted(
  { document: "drives/{driveId}", region: "us-central1" },
  async (event) => {
    const driveId = event.params.driveId;
    const before = event.data?.data() as DriveDoc | undefined;
    if (!before) return;

    const db = admin.firestore();

    // 1. Delete subcollections: waypoints, comments.
    await deleteCollection(db, `drives/${driveId}/waypoints`);
    await deleteCollection(db, `drives/${driveId}/comments`);

    // 2. Delete from public index (only exists if it was public).
    if (before.visibility === "public") {
      const prefix = indexPrefix(before.geohash);
      if (prefix) {
        await db.doc(`public_drives_index/${prefix}/drives/${driveId}`).delete();
      }
    }

    // 3. Delete Storage: track blob + all photos under the drive.
    const bucket = admin.storage().bucket();
    await bucket.file(`tracks/${driveId}.geojson.gz`).delete({ ignoreNotFound: true } as any);

    if (before.ownerUid) {
      const prefix = `users/${before.ownerUid}/drives/${driveId}/`;
      const [files] = await bucket.getFiles({ prefix });
      await Promise.all(files.map((f) => f.delete().catch((e) => logger.warn(`delete ${f.name}`, e))));
    }
  },
);

async function deleteCollection(db: admin.firestore.Firestore, path: string, batchSize = 200) {
  const col = db.collection(path);
  // Loop in case there are many docs.
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const snap = await col.limit(batchSize).get();
    if (snap.empty) return;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    if (snap.size < batchSize) return;
  }
}
