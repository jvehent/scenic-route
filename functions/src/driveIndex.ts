import * as admin from "firebase-admin";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { indexPrefix } from "./geohash";

interface DriveDoc {
  ownerUid?: string;
  ownerAnonHandle?: string | null;
  title?: string;
  description?: string;
  visibility?: "private" | "unlisted" | "public";
  distanceM?: number;
  durationS?: number;
  coverPhotoUrl?: string | null;
  tags?: string[];
  vehicleSummary?: string | null;
  geohash?: string | null;
  centroidLat?: number | null;
  centroidLng?: number | null;
  trackUrl?: string | null;
  updatedAt?: number;
  deletedAt?: number | null;
}

export const onDriveWritten = onDocumentWritten(
  { document: "drives/{driveId}", region: "us-central1" },
  async (event) => {
    const driveId = event.params.driveId;
    const before = event.data?.before.data() as DriveDoc | undefined;
    const after = event.data?.after.data() as DriveDoc | undefined;

    const wasPublic = before?.visibility === "public";
    const isPublic = after?.visibility === "public";
    const isTrashed = (after?.deletedAt ?? null) !== null;

    // Maintain the index.
    if (isTrashed) {
      // Tombstoned drives leave the public index regardless of visibility.
      if (wasPublic && before) await removeFromIndex(driveId, before);
    } else if (!isPublic && wasPublic) {
      await removeFromIndex(driveId, before!);
    } else if (isPublic) {
      await addToIndex(driveId, after!);
    }

    // Scrub on leaving the public/unlisted surface (public|unlisted -> private).
    const wasShared = before?.visibility === "public" || before?.visibility === "unlisted";
    const isPrivate = after?.visibility === "private";
    if (wasShared && isPrivate) {
      await scrubWaypoints(driveId);
      await scrubComments(driveId);
    }
  },
);

async function addToIndex(driveId: string, drive: DriveDoc) {
  const prefix = indexPrefix(drive.geohash);
  if (!prefix) {
    logger.warn(`addToIndex: drive ${driveId} has no geohash, skipping`);
    return;
  }
  const db = admin.firestore();
  const ref = db.doc(`public_drives_index/${prefix}/drives/${driveId}`);
  await ref.set(
    {
      ownerUid: drive.ownerUid ?? null,
      ownerAnonHandle: drive.ownerAnonHandle ?? null,
      title: drive.title ?? "",
      distanceM: drive.distanceM ?? 0,
      durationS: drive.durationS ?? 0,
      coverPhotoUrl: drive.coverPhotoUrl ?? null,
      tags: drive.tags ?? [],
      vehicleSummary: drive.vehicleSummary ?? null,
      geohash: drive.geohash ?? null,
      centroidLat: drive.centroidLat ?? null,
      centroidLng: drive.centroidLng ?? null,
      trackUrl: drive.trackUrl ?? null,
      updatedAt: drive.updatedAt ?? Date.now(),
    },
    { merge: false },
  );
}

async function removeFromIndex(driveId: string, drive: DriveDoc) {
  const prefix = indexPrefix(drive.geohash);
  if (!prefix) return;
  const db = admin.firestore();
  await db.doc(`public_drives_index/${prefix}/drives/${driveId}`).delete();
}

async function scrubWaypoints(driveId: string) {
  const db = admin.firestore();
  const { batchUpdateAll } = await import("./batchUtil");
  await batchUpdateAll(db.collection(`drives/${driveId}/waypoints`), { hidden: true });
}

async function scrubComments(driveId: string) {
  const db = admin.firestore();
  const { batchUpdateAll } = await import("./batchUtil");
  await batchUpdateAll(db.collection(`drives/${driveId}/comments`), { deleted: true });
}
