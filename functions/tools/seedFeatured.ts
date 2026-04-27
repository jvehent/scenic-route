/**
 * Seed iconic public drives + a curator profile.
 *
 * One-off operational script. Uses the Firebase Admin SDK via Application Default
 * Credentials, so before the first run:
 *
 *   gcloud auth application-default login
 *   gcloud config set project <your-firebase-project-id>
 *
 * Then from the functions/ directory:
 *
 *   npm run seed
 *
 * Idempotent: re-running refreshes the docs (uses fixed IDs + set with merge),
 * re-uploads track GeoJSON blobs, and re-writes waypoint subcollection docs.
 */
import * as admin from "firebase-admin";
import * as crypto from "crypto";
import * as fs from "fs";
import * as path from "path";
import * as zlib from "zlib";
import { encodeGeohash } from "../src/geohash";

/**
 * The Admin SDK doesn't auto-detect the Storage bucket when run locally (unlike
 * inside the Functions runtime). Resolve it from, in order:
 *  1. FIREBASE_STORAGE_BUCKET env var
 *  2. The Android app's google-services.json (project_info.storage_bucket)
 *  3. <project-id>.appspot.com derived from GCLOUD_PROJECT
 */
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
  throw new Error(
    "Could not detect Storage bucket. Set FIREBASE_STORAGE_BUCKET=<bucket-name> and re-run.",
  );
}

admin.initializeApp({ storageBucket: detectStorageBucket() });
const db = admin.firestore();
const bucket = admin.storage().bucket();

const CURATOR_UID = "senik-featured";
const CURATOR_HANDLE = "senik";
const CURATOR_NAME = "Senik";

interface NamedWaypoint {
  lat: number;
  lng: number;
  name: string;
  description?: string;
}

interface SeedDrive {
  slug: string;
  title: string;
  location: string;
  description: string;
  centroidLat: number; centroidLng: number;
  startLat: number; startLng: number;
  endLat: number; endLng: number;
  distanceKm: number; durationMin: number;
  tags: string[];
  /** Polyline along the route — [lat, lng] pairs in travel order. */
  trackPoints: Array<[number, number]>;
  /** Named POIs along the route. */
  waypoints: NamedWaypoint[];
}

const FEATURED: SeedDrive[] = [
  {
    slug: "pacific-coast-highway-big-sur",
    title: "Pacific Coast Highway — Big Sur",
    location: "California, USA",
    description:
      "California Highway 1 winding along sea cliffs from Carmel-by-the-Sea to San Simeon. " +
      "Bixby Bridge, McWay Falls, and the Pacific to your right the whole way.",
    centroidLat: 36.27, centroidLng: -121.81,
    startLat: 36.5552, startLng: -121.9233,
    endLat: 35.6435, endLng: -121.1815,
    distanceKm: 145, durationMin: 240,
    tags: ["coastal", "cliffs", "iconic"],
    trackPoints: [
      [36.5552, -121.9233], [36.5170, -121.9450], [36.4575, -121.9347],
      [36.3717, -121.9019], [36.2880, -121.8525], [36.2380, -121.8131],
      [36.1571, -121.6717], [36.0220, -121.5500], [35.9117, -121.4633],
      [35.7833, -121.3239], [35.6435, -121.1815],
    ],
    waypoints: [
      { lat: 36.3717, lng: -121.9019, name: "Bixby Bridge", description: "Iconic concrete arch bridge over Bixby Creek; pull off at the north overlook." },
      { lat: 36.1571, lng: -121.6717, name: "McWay Falls", description: "An 80 ft waterfall plunging onto a beach. Short walk from the trailhead." },
      { lat: 35.6850, lng: -121.1681, name: "Hearst Castle", description: "William Randolph Hearst's hilltop estate. Tours required to enter." },
    ],
  },
  {
    slug: "trollstigen-norway",
    title: "Trollstigen",
    location: "Møre og Romsdal, Norway",
    description:
      "Eleven hairpin bends climbing 850 meters through the Romsdalen valley. " +
      "Closed in winter; spectacular in midsummer light.",
    centroidLat: 62.456, centroidLng: 7.666,
    startLat: 62.4904, startLng: 7.6650,
    endLat: 62.4203, endLng: 7.6680,
    distanceKm: 11, durationMin: 30,
    tags: ["mountain", "hairpins", "alpine"],
    trackPoints: [
      [62.4904, 7.6650], [62.4810, 7.6680], [62.4720, 7.6700],
      [62.4640, 7.6680], [62.4567, 7.6640], [62.4480, 7.6620],
      [62.4380, 7.6660], [62.4290, 7.6700], [62.4203, 7.6680],
    ],
    waypoints: [
      { lat: 62.4530, lng: 7.6640, name: "Stigfossen Falls", description: "The 320 m waterfall the road crosses; pedestrian viewing platform above." },
    ],
  },
  {
    slug: "great-ocean-road",
    title: "Great Ocean Road",
    location: "Victoria, Australia",
    description:
      "243 km of southern Australian coastline. Twelve Apostles sea stacks, rainforest detours " +
      "into the Otways, and lookouts over the Southern Ocean.",
    centroidLat: -38.66, centroidLng: 143.40,
    startLat: -38.3409, startLng: 144.3219,
    endLat: -38.7383, endLng: 142.4886,
    distanceKm: 243, durationMin: 360,
    tags: ["coastal", "iconic", "long-haul"],
    trackPoints: [
      [-38.3409, 144.3219], [-38.3753, 144.2786], [-38.4536, 144.0978],
      [-38.5432, 143.9744], [-38.6336, 143.7831], [-38.7560, 143.6680],
      [-38.7975, 143.5316], [-38.6661, 143.1042], [-38.6422, 143.0683],
      [-38.6201, 142.9989], [-38.7383, 142.4886],
    ],
    waypoints: [
      { lat: -38.6661, lng: 143.1042, name: "Twelve Apostles", description: "Limestone sea stacks; helicopter tours and a dedicated lookout." },
      { lat: -38.6422, lng: 143.0683, name: "Loch Ard Gorge", description: "Dramatic cliff-walled inlet with a sand beach; named after an 1878 shipwreck." },
    ],
  },
  {
    slug: "stelvio-pass",
    title: "Stelvio Pass",
    location: "Lombardy, Italy",
    description:
      "48 numbered hairpin turns over 2,757 m. The classic Alpine pass; " +
      "best driven in early morning before the cyclists arrive.",
    centroidLat: 46.529, centroidLng: 10.453,
    startLat: 46.4906, startLng: 10.4316,
    endLat: 46.5350, endLng: 10.4434,
    distanceKm: 25, durationMin: 75,
    tags: ["mountain", "hairpins", "alpine", "iconic"],
    trackPoints: [
      [46.4671, 10.3711], [46.4750, 10.3850], [46.4900, 10.4100],
      [46.5050, 10.4300], [46.5200, 10.4450], [46.5283, 10.4543],
      [46.5350, 10.4434], [46.5500, 10.4250], [46.5650, 10.4100],
    ],
    waypoints: [
      { lat: 46.5283, lng: 10.4543, name: "Stelvio Summit", description: "2,757 m. Refugio Tibet for coffee; the Bormio side is the classic photo." },
    ],
  },
  {
    slug: "cerro-de-la-muerte",
    title: "Cerro de la Muerte",
    location: "Costa Rica",
    description:
      "Inter-American Highway crossing the highest point in Costa Rica (3,491 m). " +
      "Cloud forest one minute, Pacific valleys the next.",
    centroidLat: 9.560, centroidLng: -83.751,
    startLat: 9.9367, startLng: -84.0833,
    endLat: 9.3756, endLng: -83.7008,
    distanceKm: 100, durationMin: 180,
    tags: ["mountain", "cloud-forest", "iconic"],
    trackPoints: [
      [9.9367, -84.0833], [9.8638, -83.9194], [9.7888, -83.8700],
      [9.7138, -83.8400], [9.6388, -83.8000], [9.5601, -83.7510],
      [9.5000, -83.7300], [9.4500, -83.7150], [9.3756, -83.7008],
    ],
    waypoints: [
      { lat: 9.5601, lng: -83.7510, name: "Cerro de la Muerte Summit", description: "3,491 m, the highest paved road in Costa Rica. Cloud forest páramo at the top." },
    ],
  },
  {
    slug: "ring-road-iceland",
    title: "Ring Road — Iceland",
    location: "Iceland",
    description:
      "1,332 km looping the entire island. Glaciers, black-sand beaches, geothermal valleys, " +
      "Vatnajökull. Plan a week, take two.",
    centroidLat: 64.96, centroidLng: -19.02,
    startLat: 64.1355, startLng: -21.8954,
    endLat: 64.1355, endLng: -21.8954,
    distanceKm: 1332, durationMin: 1500,
    tags: ["coastal", "long-haul", "epic"],
    trackPoints: [
      [64.1355, -21.8954], [63.7833, -20.5333], [63.5333, -19.5167],
      [63.4191, -19.0066], [64.0167, -16.4500], [64.2540, -15.2079],
      [64.7500, -14.5500], [65.2627, -14.3939], [65.4500, -15.7500],
      [65.6839, -18.0915], [65.4833, -19.6500], [64.5388, -21.9201],
      [64.1355, -21.8954],
    ],
    waypoints: [
      { lat: 63.4191, lng: -19.0066, name: "Vík", description: "Black sand beaches and basalt sea stacks at Reynisfjara; plan a stop." },
      { lat: 65.6004, lng: -16.9963, name: "Mývatn", description: "Geothermal lake with bubbling mud pools; nature baths nearby." },
    ],
  },
  {
    slug: "going-to-the-sun-road",
    title: "Going-to-the-Sun Road",
    location: "Glacier National Park, Montana, USA",
    description:
      "80 km through the heart of Glacier NP, crossing Logan Pass at 2,025 m. " +
      "Open early July through mid-October only.",
    centroidLat: 48.74, centroidLng: -113.79,
    startLat: 48.4981, startLng: -113.9926,
    endLat: 48.7967, endLng: -113.4359,
    distanceKm: 80, durationMin: 180,
    tags: ["mountain", "alpine", "national-park"],
    trackPoints: [
      [48.5234, -113.9967], [48.6177, -113.8767], [48.6781, -113.8190],
      [48.7470, -113.7800], [48.7250, -113.7400], [48.6963, -113.7180],
      [48.7074, -113.6760], [48.6928, -113.6303], [48.6925, -113.5117],
      [48.7464, -113.4359],
    ],
    waypoints: [
      { lat: 48.6963, lng: -113.7180, name: "Logan Pass", description: "2,025 m at the continental divide. Hidden Lake Trail starts at the visitor center." },
      { lat: 48.6177, lng: -113.8767, name: "Lake McDonald Lodge", description: "Historic lakeside lodge; rent a kayak or take the boat tour." },
    ],
  },
  {
    slug: "cabot-trail",
    title: "Cabot Trail",
    location: "Cape Breton, Nova Scotia, Canada",
    description:
      "298 km loop around the northern tip of Cape Breton Island. Highland plateau, " +
      "ocean lookouts, and stunning fall foliage in late September.",
    centroidLat: 46.85, centroidLng: -60.55,
    startLat: 46.2300, startLng: -60.8900,
    endLat: 46.2300, endLng: -60.8900,
    distanceKm: 298, durationMin: 360,
    tags: ["coastal", "highland", "loop"],
    trackPoints: [
      [46.1006, -60.7501], [46.2700, -60.9300], [46.4400, -61.0900],
      [46.5800, -61.0500], [46.6276, -61.0145], [46.7500, -60.9100],
      [46.8336, -60.8019], [46.8094, -60.3289], [46.7800, -60.4700],
      [46.6894, -60.3820], [46.4500, -60.4900], [46.2900, -60.5919],
      [46.1006, -60.7501],
    ],
    waypoints: [
      { lat: 46.8336, lng: -60.8019, name: "Pleasant Bay", description: "Whale-watching tours leave from the harbor; pilot whales most common." },
      { lat: 46.7800, lng: -60.4700, name: "Cape Smokey Lookout", description: "Granite headland rising 366 m straight out of the Atlantic. Sunrise here is ridiculous." },
    ],
  },
];

async function ensureCuratorProfile() {
  const ref = db.doc(`users/${CURATOR_UID}`);
  const now = Date.now();
  await ref.set(
    {
      displayName: CURATOR_NAME,
      avatarUrl: null,
      bio: "Curated featured drives from around the world.",
      visibility: "public",
      anonHandle: CURATOR_HANDLE,
      points: 0,
      stats: { drivesPublished: FEATURED.length, helpfulAnswers: 0 },
      createdAt: now,
      updatedAt: now,
    },
    { merge: true },
  );
  console.log(`✓ curator profile users/${CURATOR_UID}`);
}

/** Build a GeoJSON FeatureCollection matching the format the client expects. */
function buildGeoJson(points: Array<[number, number]>): string {
  const feature = {
    type: "FeatureCollection",
    features: [
      {
        type: "Feature",
        // GeoJSON coordinate order is [lng, lat, alt?]
        geometry: {
          type: "LineString",
          coordinates: points.map(([lat, lng]) => [lng, lat]),
        },
        properties: {
          recordedAt: points.map((_, i) => i * 60_000), // 1 min stride; not real timing
          speed: points.map(() => 0),
          accuracy: points.map(() => 0),
        },
      },
    ],
  };
  return JSON.stringify(feature);
}

async function uploadTrack(
  driveId: string,
  points: Array<[number, number]>,
): Promise<{ url: string; bytes: number }> {
  const json = buildGeoJson(points);
  const compressed = zlib.gzipSync(Buffer.from(json, "utf-8"));
  const path = `tracks/${driveId}.geojson.gz`;
  // Generate a Firebase-style download token so the URL is stable, public-readable
  // (because the storage rule already allows public-drive reads), and matches the
  // shape the client uses for user-uploaded tracks.
  const token = crypto.randomUUID();
  const file = bucket.file(path);
  await file.save(compressed, {
    contentType: "application/gzip",
    metadata: { metadata: { firebaseStorageDownloadTokens: token } },
  });
  const url =
    `https://firebasestorage.googleapis.com/v0/b/${bucket.name}` +
    `/o/${encodeURIComponent(path)}?alt=media&token=${token}`;
  return { url, bytes: compressed.length };
}

async function writeWaypoints(driveId: string, waypoints: NamedWaypoint[]) {
  const col = db.collection(`drives/${driveId}/waypoints`);
  // Wipe any prior seeded waypoints first so re-runs don't accumulate stale ones.
  const existing = await col.where("authorUid", "==", CURATOR_UID).get();
  for (const d of existing.docs) await d.ref.delete();
  const baseTime = Date.now() - 7 * 24 * 60 * 60 * 1000;
  for (let i = 0; i < waypoints.length; i++) {
    const wp = waypoints[i];
    const id = `${driveId}-wp-${i}`;
    await col.doc(id).set({
      authorUid: CURATOR_UID,
      lat: wp.lat,
      lng: wp.lng,
      recordedAt: baseTime + i * 60 * 1000,
      note: wp.description ? `${wp.name}\n\n${wp.description}` : wp.name,
      photoUrls: [],
      hidden: false,
    });
  }
}

async function seedDrive(d: SeedDrive) {
  const id = `featured-${d.slug}`;
  const now = Date.now();

  // Upload track first so we can reference its URL on the drive doc.
  const { url: trackUrl, bytes: trackBytes } = await uploadTrack(id, d.trackPoints);

  // TODO(launch): replace Picsum placeholders with curated location photos hosted in
  // Cloud Storage. Picsum gives a stable, scenic-looking landscape per seed but is
  // NOT location-accurate.
  const coverPhotoUrl = `https://picsum.photos/seed/${id}/1200/720`;

  // Bounding box of the entire track (more accurate than start/end/centroid).
  const lats = d.trackPoints.map(([lat]) => lat);
  const lngs = d.trackPoints.map(([, lng]) => lng);

  const doc = {
    ownerUid: CURATOR_UID,
    ownerAnonHandle: CURATOR_HANDLE,
    title: d.title,
    description: `${d.location}\n\n${d.description}`,
    visibility: "public",
    status: "published",
    startLat: d.startLat, startLng: d.startLng,
    endLat: d.endLat, endLng: d.endLng,
    centroidLat: d.centroidLat, centroidLng: d.centroidLng,
    distanceM: Math.round(d.distanceKm * 1000),
    durationS: d.durationMin * 60,
    startedAt: now - 7 * 24 * 60 * 60 * 1000,
    endedAt: now - 7 * 24 * 60 * 60 * 1000 + d.durationMin * 60 * 1000,
    coverPhotoUrl,
    tags: d.tags,
    vehicleSummary: null,
    geohash: encodeGeohash(d.centroidLat, d.centroidLng, 9),
    boundingBox: {
      north: Math.max(...lats),
      south: Math.min(...lats),
      east: Math.max(...lngs),
      west: Math.min(...lngs),
    },
    commentCount: 0,
    // Featured drives explicitly allow comments — that's how visitors give feedback
    // and ask the curator route-condition questions.
    commentsEnabled: true,
    trackUrl,
    trackBytes,
    updatedAt: now,
    deletedAt: null,
    version: 1,
  };
  await db.doc(`drives/${id}`).set(doc, { merge: true });
  await writeWaypoints(id, d.waypoints);
  console.log(`✓ drives/${id} (${d.trackPoints.length} track pts, ${d.waypoints.length} waypoints, ${trackBytes}B)`);
}

async function main() {
  await ensureCuratorProfile();
  for (const d of FEATURED) {
    await seedDrive(d);
  }
  console.log(`\nSeeded ${FEATURED.length} featured drives.`);
  console.log("The onDriveWritten Cloud Function will fan out to public_drives_index automatically.");
}

main().catch((e) => {
  console.error("Seed failed:", e);
  process.exit(1);
});
