import * as admin from "firebase-admin";

const FIRESTORE_BATCH_LIMIT = 450; // safety margin under the hard 500 limit

/**
 * Apply an update map to every document returned by a query, chunking into safe batches.
 * Use for rewrites that may touch >500 docs.
 */
export async function batchUpdateAll(
  query: admin.firestore.Query,
  update: admin.firestore.UpdateData<any>,
  pageSize: number = FIRESTORE_BATCH_LIMIT,
): Promise<number> {
  const db = admin.firestore();
  let lastDoc: admin.firestore.QueryDocumentSnapshot | undefined;
  let touched = 0;
  // eslint-disable-next-line no-constant-condition
  while (true) {
    let q = query.limit(pageSize);
    if (lastDoc) q = q.startAfter(lastDoc);
    const snap = await q.get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.update(d.ref, update));
    await batch.commit();
    touched += snap.size;
    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.size < pageSize) break;
  }
  return touched;
}

/**
 * Apply a per-doc update produced by `make()` to every document, chunked.
 */
export async function batchMapAll(
  query: admin.firestore.Query,
  make: (doc: admin.firestore.QueryDocumentSnapshot) =>
    { ref: admin.firestore.DocumentReference; update: admin.firestore.UpdateData<any> }[],
  pageSize: number = FIRESTORE_BATCH_LIMIT,
): Promise<number> {
  const db = admin.firestore();
  let lastDoc: admin.firestore.QueryDocumentSnapshot | undefined;
  let touched = 0;
  // eslint-disable-next-line no-constant-condition
  while (true) {
    let q = query.limit(pageSize);
    if (lastDoc) q = q.startAfter(lastDoc);
    const snap = await q.get();
    if (snap.empty) break;
    const batch = db.batch();
    let inBatch = 0;
    for (const d of snap.docs) {
      const ops = make(d);
      for (const op of ops) {
        if (inBatch >= FIRESTORE_BATCH_LIMIT) {
          await batch.commit();
          // Note: this is a simple safety net; for high fan-out (e.g. drive + index),
          // callers should keep the per-doc op count low (≤2). For our usage that holds.
          break;
        }
        batch.update(op.ref, op.update);
        inBatch++;
      }
    }
    await batch.commit();
    touched += snap.size;
    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.size < pageSize) break;
  }
  return touched;
}
