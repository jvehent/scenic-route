import * as admin from "firebase-admin";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { batchMapAll } from "./batchUtil";
import { indexPrefix } from "./geohash";

interface UserDoc {
  visibility?: "private" | "public";
  displayName?: string;
  anonHandle?: string;
}

/**
 * Mirrors profile-visibility flips (public ↔ private) onto the user's drives:
 *  - rewrites `ownerAnonHandle` on every drive doc and every public-index entry
 *  - per design §11.1, going public → private auto re-anonymizes; the inverse is opt-in
 *    so we only rewrite to the displayName on a public flip when the user explicitly
 *    re-attributes (a future "bulk re-attribute" action). For now: always show
 *    anonHandle on public surfaces unless the profile is currently public.
 */
export const onProfileWritten = onDocumentWritten(
  { document: "users/{uid}", region: "us-central1" },
  async (event) => {
    const uid = event.params.uid;
    const before = event.data?.before.data() as UserDoc | undefined;
    const after = event.data?.after.data() as UserDoc | undefined;
    if (!after) return;

    const wasPublic = before?.visibility === "public";
    const isPublic = after.visibility === "public";
    if (wasPublic === isPublic) return; // visibility unchanged → nothing to do

    const handleNow = isPublic
      ? after.displayName?.trim() || after.anonHandle || `traveler-${uid.slice(0, 6)}`
      : after.anonHandle || `traveler-${uid.slice(0, 6)}`;

    const db = admin.firestore();
    const query = db.collection("drives").where("ownerUid", "==", uid);
    const touched = await batchMapAll(query, (driveDoc) => {
      const ops: { ref: admin.firestore.DocumentReference; update: any }[] = [
        { ref: driveDoc.ref, update: { ownerAnonHandle: handleNow } },
      ];
      const visibility = driveDoc.get("visibility");
      const geohash = driveDoc.get("geohash") as string | undefined;
      if (visibility === "public") {
        const prefix = indexPrefix(geohash);
        if (prefix) {
          ops.push({
            ref: db.doc(`public_drives_index/${prefix}/drives/${driveDoc.id}`),
            update: { ownerAnonHandle: handleNow },
          });
        }
      }
      return ops;
    });
    logger.info(`profileScrub: re-attributed ${touched} drive(s) for visibility flip on user`);
  },
);
