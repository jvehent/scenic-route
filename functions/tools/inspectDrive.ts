/**
 * Pull a drive record from Firestore + Cloud Storage and print it locally for
 * inspection. Used for support / debugging — given a drive ID (visible in the
 * app via DriveIdRow), fetch every artifact attached to it: the drive doc, all
 * waypoints, photo URLs, and the gzipped GeoJSON track (decompressed inline).
 *
 * Usage (from the functions/ directory):
 *
 *   npm run inspect <drive-id>
 *   npm run inspect <drive-id> --save        # dump full data to ./inspect-<id>.json
 *   npm run inspect <drive-id> --track       # also print the full track points
 *
 * Auth: same as the seed script — uses ADC, so run
 *   gcloud auth application-default login
 *   gcloud config set project <firebase-project-id>
 * once before the first run.
 */
import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";
import * as zlib from "zlib";
import * as https from "https";

function detectStorageBucket(): string {
  if (process.env.FIREBASE_STORAGE_BUCKET) return process.env.FIREBASE_STORAGE_BUCKET;
  try {
    const gsPath = path.resolve(__dirname, "../../app/google-services.json");
    const data = JSON.parse(fs.readFileSync(gsPath, "utf-8"));
    const fromGs = data?.project_info?.storage_bucket;
    if (typeof fromGs === "string" && fromGs.length > 0) return fromGs;
  } catch (_) { /* fall through */ }
  const proj = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
  if (proj) return `${proj}.appspot.com`;
  throw new Error("Could not detect Storage bucket. Set FIREBASE_STORAGE_BUCKET=<bucket>.");
}

admin.initializeApp({ storageBucket: detectStorageBucket() });
const db = admin.firestore();

const args = process.argv.slice(2);
const driveId = args.find((a) => !a.startsWith("--"));
const flagSave = args.includes("--save");
const flagTrack = args.includes("--track");

if (!driveId) {
  console.error("usage: npm run inspect <drive-id> [--save] [--track]");
  process.exit(1);
}

interface InspectResult {
  driveId: string;
  drive: FirebaseFirestore.DocumentData | null;
  waypoints: Array<FirebaseFirestore.DocumentData & { id: string }>;
  trackUrl: string | null;
  trackBytes: number | null;
  trackPointCount: number | null;
  trackHeadCoords: Array<[number, number]>;
  trackTailCoords: Array<[number, number]>;
  trackPoints?: Array<[number, number, number?]>;
}

async function downloadAndDecompress(url: string): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    https
      .get(url, (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} fetching track`));
          return;
        }
        const chunks: Buffer[] = [];
        res.on("data", (c: Buffer) => chunks.push(c));
        res.on("end", () => {
          const raw = Buffer.concat(chunks);
          // Sniff gzip magic header — Firebase sometimes auto-decompresses, sometimes not.
          const isGzipped = raw.length >= 2 && raw[0] === 0x1f && raw[1] === 0x8b;
          try {
            resolve(isGzipped ? zlib.gunzipSync(raw) : raw);
          } catch (e) {
            reject(e);
          }
        });
        res.on("error", reject);
      })
      .on("error", reject);
  });
}

async function inspect(driveId: string): Promise<InspectResult> {
  const driveRef = db.doc(`drives/${driveId}`);
  const snap = await driveRef.get();
  if (!snap.exists) {
    throw new Error(`drives/${driveId} does not exist in Firestore`);
  }
  const drive = snap.data() ?? null;

  const wpSnap = await driveRef.collection("waypoints").get();
  const waypoints = wpSnap.docs.map((d) => ({ id: d.id, ...d.data() }));

  const trackUrl = (drive?.trackUrl as string) ?? null;
  const trackBytes = (drive?.trackBytes as number) ?? null;
  let trackPointCount: number | null = null;
  let trackHeadCoords: Array<[number, number]> = [];
  let trackTailCoords: Array<[number, number]> = [];
  let allTrackPoints: Array<[number, number, number?]> | undefined;

  if (trackUrl) {
    try {
      const decompressed = await downloadAndDecompress(trackUrl);
      const geojson = JSON.parse(decompressed.toString("utf-8"));
      const coords: Array<[number, number, number?]> =
        geojson?.features?.[0]?.geometry?.coordinates ?? [];
      trackPointCount = coords.length;
      trackHeadCoords = coords.slice(0, 3).map((c) => [c[1], c[0]]); // display [lat,lng]
      trackTailCoords = coords.slice(-3).map((c) => [c[1], c[0]]);
      if (flagTrack) allTrackPoints = coords.map((c) => [c[1], c[0], c[2]]);
    } catch (e) {
      console.warn(`! could not download/parse track: ${(e as Error).message}`);
    }
  }

  return {
    driveId,
    drive,
    waypoints,
    trackUrl,
    trackBytes,
    trackPointCount,
    trackHeadCoords,
    trackTailCoords,
    trackPoints: allTrackPoints,
  };
}

function printPretty(r: InspectResult) {
  const d = r.drive ?? {};
  console.log("─── Drive ─────────────────────────────────────────────────");
  console.log(`  id:             ${r.driveId}`);
  console.log(`  title:          ${d.title || "(untitled)"}`);
  console.log(`  description:    ${truncate((d.description as string) || "", 200)}`);
  console.log(`  ownerUid:       ${d.ownerUid || "(unknown)"}`);
  console.log(`  ownerHandle:    ${d.ownerAnonHandle || "(unknown)"}`);
  console.log(`  visibility:     ${d.visibility}`);
  console.log(`  status:         ${d.status}`);
  console.log(`  commentsOn:     ${d.commentsEnabled === true}`);
  console.log(`  distance:       ${d.distanceM ?? 0}m (${((d.distanceM ?? 0) / 1000).toFixed(1)} km)`);
  console.log(`  duration:       ${d.durationS ?? 0}s (${Math.round((d.durationS ?? 0) / 60)} min)`);
  console.log(`  startedAt:      ${formatTime(d.startedAt)}`);
  console.log(`  endedAt:        ${formatTime(d.endedAt)}`);
  console.log(`  start:          ${d.startLat}, ${d.startLng}`);
  console.log(`  end:            ${d.endLat}, ${d.endLng}`);
  console.log(`  centroid:       ${d.centroidLat}, ${d.centroidLng}`);
  console.log(`  geohash:        ${d.geohash}`);
  console.log(`  tags:           ${(d.tags as string[] | undefined)?.join(", ") || "(none)"}`);
  console.log(`  deletedAt:      ${d.deletedAt ? formatTime(d.deletedAt) : "—"}`);
  console.log(`  updatedAt:      ${formatTime(d.updatedAt)}`);
  console.log(`  serverVersion:  ${d.version}`);
  console.log();
  console.log("─── Track ─────────────────────────────────────────────────");
  console.log(`  trackUrl:       ${r.trackUrl ? r.trackUrl.substring(0, 120) + "…" : "(none)"}`);
  console.log(`  trackBytes:     ${r.trackBytes ?? "(unknown)"}`);
  console.log(`  pointCount:     ${r.trackPointCount ?? "(could not download)"}`);
  if (r.trackPointCount && r.trackPointCount > 0) {
    console.log(`  first 3:        ${JSON.stringify(r.trackHeadCoords)}`);
    console.log(`  last 3:         ${JSON.stringify(r.trackTailCoords)}`);
  }
  console.log();
  console.log(`─── Waypoints (${r.waypoints.length}) ──────────────────────────────────────`);
  for (const wp of r.waypoints) {
    const note = (wp.note as string | undefined)?.split("\n")[0] || "(no note)";
    const photos = ((wp.photoUrls as string[] | undefined) ?? []).length;
    console.log(`  · ${wp.id}`);
    console.log(`      ${wp.lat}, ${wp.lng}  ·  ${photos} photo(s)  ·  hidden=${wp.hidden ?? false}`);
    console.log(`      ${truncate(note, 80)}`);
    for (const url of (wp.photoUrls as string[] | undefined) ?? []) {
      console.log(`      photo: ${url.substring(0, 100)}${url.length > 100 ? "…" : ""}`);
    }
  }
  console.log();
}

function truncate(s: string, n: number): string {
  if (s.length <= n) return s;
  return s.substring(0, n - 1) + "…";
}

function formatTime(t: number | undefined | null): string {
  if (!t) return "—";
  return new Date(t).toISOString();
}

inspect(driveId)
  .then((result) => {
    printPretty(result);

    if (flagSave) {
      const out = path.resolve(`inspect-${driveId}.json`);
      fs.writeFileSync(out, JSON.stringify(result, null, 2), "utf-8");
      console.log(`✓ full data written to ${out}`);
    } else {
      console.log("(pass --save to write the full data including raw drive doc to disk)");
    }
    if (!flagTrack && result.trackPointCount) {
      console.log("(pass --track to print all track point coordinates)");
    }
    process.exit(0);
  })
  .catch((err) => {
    console.error(`ERROR: ${err.message}`);
    process.exit(1);
  });
