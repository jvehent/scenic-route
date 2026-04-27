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
  /** Optional photo URLs for this waypoint. Empty array = no photos. */
  photoUrls?: string[];
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
  /**
   * Hand-picked "anchor" coordinates along the route in travel order. The seed
   * script interpolates intermediate points so the final track has at least
   * ~1 point per kilometer of distance. Pick anchors at every major bend / pass /
   * landmark so the densified polyline reflects the road's actual geometry.
   */
  anchors: Array<[number, number]>;
  /** Named POIs along the route. */
  waypoints: NamedWaypoint[];
  /** Optional cover photo URL override; defaults to a Picsum seed. */
  coverPhotoUrl?: string;
}

/**
 * Linearly interpolates between [anchors] until the output has at least
 * [minPoints] points. The result starts with anchors[0] and ends with
 * anchors[last]; intermediate samples are evenly spaced per segment.
 *
 * Caveat: this produces straight-line segments between anchors, so a road
 * with sharp curves between two anchors will visually clip those curves.
 * Add more anchors at major bends to keep the displayed polyline faithful.
 */
function densify(anchors: Array<[number, number]>, minPoints: number): Array<[number, number]> {
  if (anchors.length < 2) return anchors;
  if (anchors.length >= minPoints) return anchors;
  const segments = anchors.length - 1;
  const totalNeeded = minPoints - anchors.length;
  const perSegment = Math.max(1, Math.ceil(totalNeeded / segments));
  const out: Array<[number, number]> = [];
  for (let i = 0; i < anchors.length - 1; i++) {
    const [aLat, aLng] = anchors[i];
    const [bLat, bLng] = anchors[i + 1];
    out.push([aLat, aLng]);
    for (let k = 1; k <= perSegment; k++) {
      const t = k / (perSegment + 1);
      out.push([aLat + (bLat - aLat) * t, aLng + (bLng - aLng) * t]);
    }
  }
  out.push(anchors[anchors.length - 1]);
  return out;
}

/**
 * Picsum CDN serves real photos from Unsplash photographers, picked at random
 * by seed. Stable URL for a given seed (so re-seeding doesn't shuffle covers).
 * NOT location-accurate — replace with curated Wikimedia/Unsplash URLs before
 * public launch if location-faithful imagery matters.
 */
function picsum(seed: string, w = 1200, h = 720): string {
  return `https://picsum.photos/seed/${encodeURIComponent(seed)}/${w}/${h}`;
}

const FEATURED: SeedDrive[] = [
  {
    slug: "pacific-coast-highway-big-sur",
    title: "Pacific Coast Highway — Big Sur to Hearst Castle",
    location: "California, USA",
    description:
      "California Highway 1 winding along sea cliffs from Carmel-by-the-Sea to San Simeon. " +
      "Bixby Bridge, McWay Falls, redwoods, and the Pacific to your right the whole way.",
    centroidLat: 36.10, centroidLng: -121.65,
    startLat: 36.5552, startLng: -121.9233,
    endLat: 35.6850, endLng: -121.1681,
    distanceKm: 145, durationMin: 240,
    tags: ["coastal", "cliffs", "iconic"],
    anchors: [
      [36.5552, -121.9233], [36.5170, -121.9450], [36.4575, -121.9347],
      [36.4180, -121.9236], [36.3860, -121.9024], [36.3717, -121.9019],
      [36.3300, -121.8810], [36.2880, -121.8525], [36.2380, -121.8131],
      [36.1830, -121.7325], [36.1571, -121.6717], [36.1090, -121.6240],
      [36.0220, -121.5500], [35.9117, -121.4633], [35.8350, -121.3920],
      [35.7833, -121.3239], [35.7115, -121.2580], [35.6435, -121.1815],
      [35.6850, -121.1681],
    ],
    waypoints: [
      {
        lat: 36.3717, lng: -121.9019,
        name: "Bixby Creek Bridge",
        description: "Iconic 86-meter concrete arch bridge built in 1932. The pull-off at the north overlook is the postcard angle.",
        photoUrls: [picsum("bixby-bridge-aerial", 1024, 768), picsum("bixby-bridge-side", 1024, 768)],
      },
      {
        lat: 36.1571, lng: -121.6717,
        name: "McWay Falls",
        description: "An 80-foot waterfall plunging onto a beach in a hidden cove. Short walk from the Julia Pfeiffer Burns trailhead.",
        photoUrls: [picsum("mcway-falls", 1024, 768)],
      },
      {
        lat: 36.0220, lng: -121.5500,
        name: "Lucia Lodge",
        description: "Tiny inn perched 200 meters above the Pacific. Coffee stop with a view that justifies the detour.",
        photoUrls: [picsum("lucia-lodge", 1024, 768)],
      },
      {
        lat: 35.7833, lng: -121.3239,
        name: "Ragged Point",
        description: "The southern gateway to Big Sur. Cliff overlook and a 60-meter waterfall down a switchback trail.",
        photoUrls: [picsum("ragged-point", 1024, 768)],
      },
      {
        lat: 35.6850, lng: -121.1681,
        name: "Hearst Castle",
        description: "William Randolph Hearst's hilltop estate. Tours required to enter the gardens and rooms.",
        photoUrls: [picsum("hearst-castle", 1024, 768)],
      },
    ],
  },
  {
    slug: "cabot-trail-cape-breton",
    title: "Cabot Trail — Cape Breton Loop",
    location: "Nova Scotia, Canada",
    description:
      "298 km loop around the northern tip of Cape Breton Island. Highland plateaus, Acadian fishing villages, " +
      "Atlantic on one side and Gulf of St. Lawrence on the other.",
    centroidLat: 46.75, centroidLng: -60.65,
    startLat: 46.1010, startLng: -60.7470,
    endLat: 46.1010, endLng: -60.7470,
    distanceKm: 298, durationMin: 450,
    tags: ["coastal", "highlands", "loop", "long-haul"],
    anchors: [
      [46.1010, -60.7470], [46.2400, -60.8350], [46.3700, -60.9450],
      [46.5300, -60.9920], [46.6480, -61.0090], [46.7400, -60.9820],
      [46.8170, -60.8730], [46.8530, -60.7480], [46.8850, -60.6300],
      [46.8730, -60.4870], [46.8330, -60.3940], [46.7900, -60.3950],
      [46.7340, -60.4040], [46.6710, -60.3680], [46.6240, -60.3340],
      [46.5500, -60.4200], [46.4520, -60.5400], [46.3500, -60.5670],
      [46.2400, -60.6580], [46.1500, -60.7180], [46.1010, -60.7470],
    ],
    waypoints: [
      {
        lat: 46.7400, lng: -60.9820,
        name: "Skyline Trail",
        description: "Headland boardwalk with cliff-edge views over the Gulf of St. Lawrence. Moose and bald eagles common.",
        photoUrls: [picsum("skyline-trail-cape-breton", 1024, 768)],
      },
      {
        lat: 46.8530, lng: -60.7480,
        name: "Pleasant Bay",
        description: "Whaling-village turned whale-watching hub. Pilot whales and minkes from June to October.",
        photoUrls: [picsum("pleasant-bay-cape-breton", 1024, 768)],
      },
      {
        lat: 46.8330, lng: -60.3940,
        name: "Neil's Harbour Lighthouse",
        description: "Red-and-white lighthouse on a granite point; ice-cream shop attached, somehow.",
        photoUrls: [picsum("neils-harbour", 1024, 768)],
      },
      {
        lat: 46.6710, lng: -60.3680,
        name: "Ingonish Beach",
        description: "Dual-personality beach — saltwater Atlantic on one side, freshwater pond on the other, sand bar between.",
        photoUrls: [picsum("ingonish-beach", 1024, 768)],
      },
    ],
  },
  {
    slug: "great-ocean-road",
    title: "Great Ocean Road",
    location: "Victoria, Australia",
    description:
      "243 km of southern Australian coastline. Twelve Apostles sea stacks, rainforest detours into the Otways, " +
      "and lookouts over the Southern Ocean.",
    centroidLat: -38.66, centroidLng: 143.40,
    startLat: -38.3409, startLng: 144.3219,
    endLat: -38.3825, endLng: 142.4836,
    distanceKm: 243, durationMin: 360,
    tags: ["coastal", "iconic", "long-haul"],
    anchors: [
      [-38.3409, 144.3219], [-38.3753, 144.2786], [-38.4536, 144.0978],
      [-38.5060, 143.9967], [-38.5432, 143.9744], [-38.5780, 143.8830],
      [-38.6336, 143.7831], [-38.6940, 143.7080], [-38.7560, 143.6680],
      [-38.7975, 143.5316], [-38.7700, 143.3990], [-38.7080, 143.2300],
      [-38.6661, 143.1042], [-38.6422, 143.0683], [-38.6201, 142.9989],
      [-38.5810, 142.8950], [-38.5300, 142.7890], [-38.4730, 142.6620],
      [-38.4280, 142.5710], [-38.3825, 142.4836],
    ],
    waypoints: [
      {
        lat: -38.4536, lng: 144.0978,
        name: "Memorial Arch",
        description: "Wooden arch built by returning WWI soldiers who constructed the road. Photo stop at km 30.",
        photoUrls: [picsum("memorial-arch-great-ocean-road", 1024, 768)],
      },
      {
        lat: -38.5432, lng: 143.9744,
        name: "Lorne",
        description: "Beach town with a cliff-side fish-and-chips shop and the Erskine Falls trailhead a few minutes inland.",
        photoUrls: [picsum("lorne-victoria", 1024, 768)],
      },
      {
        lat: -38.6661, lng: 143.1042,
        name: "Twelve Apostles",
        description: "Limestone sea stacks rising from the Southern Ocean; helicopter tours and a dedicated lookout.",
        photoUrls: [picsum("twelve-apostles-1", 1024, 768), picsum("twelve-apostles-2", 1024, 768)],
      },
      {
        lat: -38.6422, lng: 143.0683,
        name: "Loch Ard Gorge",
        description: "Cliff-walled inlet with a sand beach; named after an 1878 shipwreck.",
        photoUrls: [picsum("loch-ard-gorge", 1024, 768)],
      },
      {
        lat: -38.6201, lng: 142.9989,
        name: "London Arch",
        description: "Sea arch that lost its land connection in a 1990 collapse, stranding two tourists who had to be helicoptered out.",
        photoUrls: [picsum("london-arch", 1024, 768)],
      },
    ],
  },
  {
    slug: "stelvio-pass-bormio-innsbruck",
    title: "Stelvio Pass — Bormio to Innsbruck",
    location: "Italy / Austria",
    description:
      "Up the 48 hairpins of the eastern Stelvio ramp at 2,758 m, down through the South Tyrol vineyards, " +
      "across the Reschenpass into Austria's Inn valley to Innsbruck.",
    centroidLat: 46.85, centroidLng: 10.85,
    startLat: 46.4669, startLng: 10.3712,
    endLat: 47.2692, endLng: 11.4041,
    distanceKm: 175, durationMin: 300,
    tags: ["mountain", "alpine", "hairpins", "iconic"],
    anchors: [
      [46.4669, 10.3712], [46.4960, 10.4070], [46.5145, 10.4310],
      [46.5290, 10.4530], [46.5275, 10.4540], [46.6090, 10.4940],
      [46.6680, 10.5570], [46.7340, 10.5860], [46.7950, 10.5870],
      [46.8580, 10.6230], [46.9230, 10.6660], [46.9900, 10.7050],
      [47.0470, 10.7720], [47.0890, 10.8540], [47.1250, 10.9700],
      [47.1620, 11.0780], [47.2030, 11.2200], [47.2440, 11.3160],
      [47.2692, 11.4041],
    ],
    waypoints: [
      {
        lat: 46.5275, lng: 10.4540,
        name: "Stelvio Summit",
        description: "Highest paved pass in the eastern Alps at 2,758 m. The 48 numbered hairpins on the descent are the postcard.",
        photoUrls: [picsum("stelvio-summit", 1024, 768), picsum("stelvio-hairpins", 1024, 768)],
      },
      {
        lat: 46.7340, lng: 10.5860,
        name: "Prato allo Stelvio",
        description: "South Tyrol valley town at the bottom of the Italian descent. Apple orchards as far as the eye can see.",
        photoUrls: [picsum("prato-allo-stelvio", 1024, 768)],
      },
      {
        lat: 46.8580, lng: 10.6230,
        name: "Reschen Lake",
        description: "Reservoir with a 14th-century church bell tower poking out — submerged when the valley was dammed in 1950.",
        photoUrls: [picsum("reschen-lake-bell-tower", 1024, 768)],
      },
      {
        lat: 47.2030, lng: 11.2200,
        name: "Landeck",
        description: "Confluence of the Sanna and Inn rivers, dominated by a 13th-century castle on a rock above the road.",
        photoUrls: [picsum("landeck-castle", 1024, 768)],
      },
    ],
  },
  {
    slug: "transfagarasan-romania",
    title: "Transfăgărășan",
    location: "Romania",
    description:
      "Built in the 1970s as a strategic mountain crossing, this 90 km stretch climbs to 2,042 m through the Făgăraș range. " +
      "Glacial lakes, a tunnel through the spine of the Carpathians, and Vlad the Impaler's actual castle along the way.",
    centroidLat: 45.55, centroidLng: 24.62,
    startLat: 45.0930, startLng: 24.6940,
    endLat: 45.8060, endLng: 24.6010,
    distanceKm: 150, durationMin: 240,
    tags: ["mountain", "alpine", "iconic"],
    anchors: [
      [45.0930, 24.6940], [45.1480, 24.6750], [45.2100, 24.6520],
      [45.2820, 24.6320], [45.3340, 24.6280], [45.3670, 24.6440],
      [45.4070, 24.6230], [45.4480, 24.6160], [45.4970, 24.6040],
      [45.5440, 24.6080], [45.5910, 24.6160], [45.6020, 24.6160],
      [45.6510, 24.6190], [45.6890, 24.6090], [45.7240, 24.5980],
      [45.7590, 24.5950], [45.7820, 24.6020], [45.8060, 24.6010],
    ],
    waypoints: [
      {
        lat: 45.2100, lng: 24.6520,
        name: "Poenari Castle",
        description: "Vlad III's real fortress (not the Bran tourist trap). 1,480 steps up a ridge over the Argeș gorge.",
        photoUrls: [picsum("poenari-castle", 1024, 768)],
      },
      {
        lat: 45.3340, lng: 24.6280,
        name: "Vidraru Dam",
        description: "Arched concrete dam built in 1966; walking the crest is a vertigo test with great views down the gorge.",
        photoUrls: [picsum("vidraru-dam", 1024, 768)],
      },
      {
        lat: 45.6020, lng: 24.6160,
        name: "Bâlea Lake",
        description: "Glacial lake at 2,034 m, fed by snowmelt. Cable car runs in winter when the road is closed Nov–Jun.",
        photoUrls: [picsum("balea-lake", 1024, 768), picsum("balea-lake-winter", 1024, 768)],
      },
      {
        lat: 45.6510, lng: 24.6190,
        name: "Bâlea Tunnel North Portal",
        description: "884 m tunnel through the watershed ridge. Switchback after switchback emptying you out into Transylvania.",
        photoUrls: [picsum("balea-tunnel", 1024, 768)],
      },
    ],
  },
  {
    slug: "trollstigen-atlantic-road",
    title: "Trollstigen + Atlantic Road",
    location: "Møre og Romsdal, Norway",
    description:
      "Eleven hairpin bends climbing 850 m up the Romsdalen valley wall, then north across fjord ferries to the Atlantic Road's " +
      "wave-battered island-hopping bridges. The road and the sea are the same thing for the last 8 km.",
    centroidLat: 62.85, centroidLng: 7.45,
    startLat: 62.5673, startLng: 7.6850,
    endLat: 63.0140, endLng: 7.6620,
    distanceKm: 165, durationMin: 240,
    tags: ["mountain", "coastal", "hairpins", "iconic", "ferries"],
    anchors: [
      [62.5673, 7.6850], [62.5290, 7.6760], [62.4904, 7.6650],
      [62.4640, 7.6680], [62.4530, 7.6640], [62.4380, 7.6660],
      [62.4203, 7.6680], [62.4830, 7.4250], [62.5280, 7.3380],
      [62.5570, 7.2050], [62.6320, 7.1530], [62.6960, 7.1240],
      [62.7370, 7.1490], [62.7745, 7.1620], [62.8190, 7.2200],
      [62.8590, 7.2950], [62.9080, 7.4360], [62.9590, 7.5620],
      [63.0140, 7.6620],
    ],
    waypoints: [
      {
        lat: 62.4530, lng: 7.6640,
        name: "Stigfossen Falls",
        description: "320 m waterfall the road crosses at the midpoint of the Trollstigen climb. Pedestrian platform above the spray.",
        photoUrls: [picsum("stigfossen-falls", 1024, 768)],
      },
      {
        lat: 62.4203, lng: 7.6680,
        name: "Trollstigen Viewpoint",
        description: "Cantilevered steel-and-concrete platform jutting over the void. The 11 hairpins wind out under your feet.",
        photoUrls: [picsum("trollstigen-viewpoint", 1024, 768), picsum("trollstigen-hairpins", 1024, 768)],
      },
      {
        lat: 62.7745, lng: 7.1620,
        name: "Geirangerfjord",
        description: "UNESCO-listed fjord with seven sister waterfalls down its south wall. Ferry crossing along the route.",
        photoUrls: [picsum("geirangerfjord", 1024, 768)],
      },
      {
        lat: 62.9590, lng: 7.5620,
        name: "Storseisundet Bridge",
        description: "The Atlantic Road's signature bridge — a curved ramp that looks like a ski jump from one approach.",
        photoUrls: [picsum("storseisundet-bridge", 1024, 768), picsum("atlantic-road-storm", 1024, 768)],
      },
    ],
  },
  {
    slug: "going-to-the-sun-extended",
    title: "Going-to-the-Sun + Flathead Valley",
    location: "Montana, USA",
    description:
      "St. Mary on the prairie east, up over Logan Pass at 2,026 m, down through old-growth cedar forest to West Glacier, " +
      "then south past Lake McDonald and Hungry Horse Reservoir to Kalispell.",
    centroidLat: 48.65, centroidLng: -113.95,
    startLat: 48.7456, startLng: -113.4377,
    endLat: 48.1958, endLng: -114.3137,
    distanceKm: 140, durationMin: 240,
    tags: ["mountain", "alpine", "iconic", "national-park"],
    anchors: [
      [48.7456, -113.4377], [48.7280, -113.4710], [48.7110, -113.5260],
      [48.6960, -113.5740], [48.6960, -113.6280], [48.6985, -113.6730],
      [48.6970, -113.7180], [48.6890, -113.7650], [48.6790, -113.8120],
      [48.6240, -113.8770], [48.5910, -113.9210], [48.5485, -113.9870],
      [48.5040, -114.0080], [48.4660, -114.0540], [48.4170, -114.0980],
      [48.3640, -114.1690], [48.3080, -114.2330], [48.2520, -114.2840],
      [48.1958, -114.3137],
    ],
    waypoints: [
      {
        lat: 48.6960, lng: -113.6280,
        name: "Wild Goose Island Overlook",
        description: "Tiny island in St. Mary Lake with the Rockies as a backdrop. The most photographed view in the park.",
        photoUrls: [picsum("wild-goose-island", 1024, 768)],
      },
      {
        lat: 48.6985, lng: -113.6730,
        name: "Logan Pass",
        description: "Continental Divide at 2,026 m. Mountain goats on the boardwalks, snowmelt streams crossing the lot.",
        photoUrls: [picsum("logan-pass", 1024, 768), picsum("logan-pass-goats", 1024, 768)],
      },
      {
        lat: 48.6240, lng: -113.8770,
        name: "Lake McDonald",
        description: "Glacier-carved lake with multicolored stones visible through the meltwater-clear bottom.",
        photoUrls: [picsum("lake-mcdonald", 1024, 768)],
      },
      {
        lat: 48.4660, lng: -114.0540,
        name: "Hungry Horse Dam",
        description: "172 m concrete arch dam on the South Fork of the Flathead River. Walkable crest, picnic spot above.",
        photoUrls: [picsum("hungry-horse-dam", 1024, 768)],
      },
    ],
  },
  {
    slug: "amalfi-coast-sorrento-salerno",
    title: "Amalfi Coast — Sorrento to Salerno",
    location: "Campania, Italy",
    description:
      "SS163 Amalfitana clinging to limestone cliffs above the Tyrrhenian Sea. Lemon groves, pastel villages, " +
      "a cathedral every 8 km, and one of the slowest 120 km drives you'll ever do — in the best way.",
    centroidLat: 40.65, centroidLng: 14.55,
    startLat: 40.6263, startLng: 14.3758,
    endLat: 40.6824, endLng: 14.7681,
    distanceKm: 120, durationMin: 210,
    tags: ["coastal", "cliffs", "iconic", "winding"],
    anchors: [
      [40.6263, 14.3758], [40.6210, 14.4120], [40.6140, 14.4480],
      [40.6090, 14.4810], [40.6010, 14.5040], [40.6280, 14.4960],
      [40.6300, 14.5210], [40.6280, 14.5470], [40.6360, 14.5790],
      [40.6340, 14.6020], [40.6470, 14.6080], [40.6540, 14.6260],
      [40.6580, 14.6570], [40.6500, 14.6800], [40.6540, 14.7050],
      [40.6620, 14.7220], [40.6750, 14.7430], [40.6824, 14.7681],
    ],
    waypoints: [
      {
        lat: 40.6263, lng: 14.3758,
        name: "Sorrento",
        description: "Cliffside town overlooking the Bay of Naples. Lemon groves, ferry terminal to Capri, and the start of the SS163.",
        photoUrls: [picsum("sorrento", 1024, 768)],
      },
      {
        lat: 40.6280, lng: 14.4960,
        name: "Positano",
        description: "Vertical village of pastel buildings stacked up a cliff. Pull off at Le Sirenuse for the postcard angle.",
        photoUrls: [picsum("positano", 1024, 768), picsum("positano-cliff", 1024, 768)],
      },
      {
        lat: 40.6340, lng: 14.6020,
        name: "Amalfi Cathedral",
        description: "Striped Romanesque-Arab-Norman cathedral with a 62-step staircase from the piazza. Bronze doors from 1066.",
        photoUrls: [picsum("amalfi-cathedral", 1024, 768)],
      },
      {
        lat: 40.6580, lng: 14.6570,
        name: "Villa Cimbrone (Ravello)",
        description: "Cliff-top garden with the 'Terrace of Infinity' — busts and a railing over a 350 m drop to the sea.",
        photoUrls: [picsum("ravello-villa-cimbrone", 1024, 768)],
      },
    ],
  },
  {
    slug: "iceland-south-coast",
    title: "Iceland Ring Road — South Coast",
    location: "Iceland",
    description:
      "Route 1 from Reykjavík east along the south coast to Höfn. Black-sand beaches, two roadside waterfalls you can walk behind, " +
      "a glacier lagoon dotted with icebergs, and lava fields in every direction.",
    centroidLat: 63.95, centroidLng: -19.20,
    startLat: 64.1466, startLng: -21.9426,
    endLat: 64.2538, endLng: -15.2069,
    distanceKm: 460, durationMin: 480,
    tags: ["coastal", "glacier", "waterfalls", "long-haul", "iconic"],
    anchors: [
      [64.1466, -21.9426], [64.0420, -21.7060], [63.9840, -21.4840],
      [63.9320, -21.1430], [63.9020, -20.7790], [63.7050, -20.3920],
      [63.6310, -20.0030], [63.6160, -19.9920], [63.5320, -19.5110],
      [63.4194, -19.0064], [63.4060, -18.9230], [63.5320, -18.5810],
      [63.5950, -18.3540], [63.7060, -17.9650], [63.8810, -17.4280],
      [63.9560, -16.7740], [64.0480, -16.4380], [64.0470, -16.1810],
      [64.1300, -15.9420], [64.1850, -15.5340], [64.2538, -15.2069],
    ],
    waypoints: [
      {
        lat: 63.6160, lng: -19.9920,
        name: "Seljalandsfoss",
        description: "60 m waterfall you can walk behind on a path cut into the cliff. Bring a rain jacket; you will get soaked.",
        photoUrls: [picsum("seljalandsfoss", 1024, 768), picsum("seljalandsfoss-behind", 1024, 768)],
      },
      {
        lat: 63.5320, lng: -19.5110,
        name: "Skógafoss",
        description: "60 m waterfall at the foot of an old sea cliff, with a 527-step staircase to the lookout above the lip.",
        photoUrls: [picsum("skogafoss", 1024, 768)],
      },
      {
        lat: 63.4194, lng: -19.0064,
        name: "Reynisfjara Black Sand Beach",
        description: "Basalt-column sea cave and the Reynisdrangar sea stacks. Sneaker waves are real — do NOT turn your back.",
        photoUrls: [picsum("reynisfjara", 1024, 768), picsum("reynisfjara-stacks", 1024, 768)],
      },
      {
        lat: 64.0480, lng: -16.4380,
        name: "Jökulsárlón Glacier Lagoon",
        description: "Icebergs calving off the Breiðamerkurjökull glacier, drifting through a tidal lagoon to the sea.",
        photoUrls: [picsum("jokulsarlon", 1024, 768)],
      },
      {
        lat: 64.0470, lng: -16.1810,
        name: "Diamond Beach",
        description: "Black sand beach littered with translucent iceberg fragments washed back ashore by the tide.",
        photoUrls: [picsum("diamond-beach-iceland", 1024, 768)],
      },
    ],
  },
  {
    slug: "tail-of-the-dragon-cherohala",
    title: "Tail of the Dragon + Cherohala Skyway",
    location: "Tennessee / North Carolina, USA",
    description:
      "318 curves in 17.7 km on the Tail of the Dragon, then a long contemplative climb up the Cherohala Skyway through " +
      "Cherokee and Nantahala National Forests. Motorcycles on one half, eagles on the other.",
    centroidLat: 35.45, centroidLng: -83.85,
    startLat: 35.4731, startLng: -83.9197,
    endLat: 35.3570, endLng: -84.3580,
    distanceKm: 135, durationMin: 240,
    tags: ["mountain", "winding", "switchbacks", "forest", "iconic"],
    anchors: [
      [35.4731, -83.9197], [35.4670, -83.9320], [35.4570, -83.9460],
      [35.4480, -83.9610], [35.4395, -83.9750], [35.4310, -83.9900],
      [35.4220, -84.0040], [35.4130, -84.0210], [35.4030, -84.0370],
      [35.3940, -84.0550], [35.3550, -84.0730], [35.3320, -84.1090],
      [35.3210, -84.1530], [35.3260, -84.1980], [35.3380, -84.2370],
      [35.3470, -84.2790], [35.3540, -84.3170], [35.3570, -84.3580],
    ],
    waypoints: [
      {
        lat: 35.4731, lng: -83.9197,
        name: "Deals Gap",
        description: "Start of the Dragon. The Tree of Shame is decked with parts from bikes that didn't make it.",
        photoUrls: [picsum("deals-gap", 1024, 768)],
      },
      {
        lat: 35.4395, lng: -83.9750,
        name: "Tail of the Dragon Mid-Section",
        description: "318 curves in 11 miles. No driveways, no intersections, no cell service — just road and trees.",
        photoUrls: [picsum("tail-of-the-dragon", 1024, 768), picsum("dragon-curves", 1024, 768)],
      },
      {
        lat: 35.3940, lng: -84.0550,
        name: "Robbinsville",
        description: "Halfway-point town with a diner, gas, and the only motel between the Dragon and the Skyway.",
        photoUrls: [picsum("robbinsville-nc", 1024, 768)],
      },
      {
        lat: 35.3320, lng: -84.1090,
        name: "Cherohala Skyway Overlook",
        description: "1,400 m crest of the Skyway. Three states visible on a clear day; rhododendron in bloom every June.",
        photoUrls: [picsum("cherohala-skyway", 1024, 768)],
      },
      {
        lat: 35.3570, lng: -84.3580,
        name: "Tellico Plains",
        description: "Western terminus. Charleston Hotel for coffee, Tellico River for trout, both within walking distance of Main Street.",
        photoUrls: [picsum("tellico-plains", 1024, 768)],
      },
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
      photoUrls: wp.photoUrls ?? [],
      hidden: false,
    });
  }
}

async function seedDrive(d: SeedDrive) {
  const id = `featured-${d.slug}`;
  const now = Date.now();

  // Densify the hand-picked anchors into ≥1 track point per kilometer of distance.
  // The route-detail map renders the polyline; without densification the long-haul
  // drives would show up as straight chords between far-apart anchors.
  const trackPoints = densify(d.anchors, Math.max(d.distanceKm, d.anchors.length));

  // Upload track first so we can reference its URL on the drive doc.
  const { url: trackUrl, bytes: trackBytes } = await uploadTrack(id, trackPoints);

  // TODO(launch): replace Picsum cover placeholders with curated location photos
  // hosted in Cloud Storage. Picsum serves real photos from Unsplash photographers
  // (so they're "from the public internet") but they are NOT location-accurate.
  const coverPhotoUrl = d.coverPhotoUrl ?? picsum(`${id}-cover`);

  // Bounding box of the entire track (more accurate than start/end/centroid).
  const lats = trackPoints.map(([lat]) => lat);
  const lngs = trackPoints.map(([, lng]) => lng);

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
  console.log(
    `✓ drives/${id} (${d.distanceKm} km · ${trackPoints.length} pts ` +
      `· ${d.waypoints.length} waypoints · ${trackBytes}B)`,
  );
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
