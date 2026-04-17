# Scenic Route — Design Document

*Draft v0.2 — for review*

## 1. Vision

Scenic Route is an app for travelers who love the journey as much as the destination. It lets drivers record a segment of road — a GPS trace, photos, and notes — and preserve it as a personal memory or share it with others. When on the road, it surfaces *other* recorded scenic drives nearby, so travelers can discover the beautiful detours locals and past visitors have marked as worth the trip.

It is explicitly **not** a replacement for Google Maps or Waze. There is no turn-by-turn navigation, no traffic routing, no ETA. Scenic Route answers a different question: *"what's worth driving here?"*

## 2. Goals & Non-Goals

### Goals
- Let a user record a driving segment in one tap, with minimal friction and reasonable battery cost. The app keeps track of the previous X minutes of locations so the user can record after the fact.
- Let the user annotate a segment — inline photos and text waypoints — during or after the drive. Annotations should include vehicle recommandations or requirements.
- Support conversations on segments. Users should be able to ask questions and discuss particular segments among themselves.
- Users should have a profile that is private but can be made public. Recording segments and answering questions earn points on their profiles.
- Store drives locally (offline-first) and sync to a central backend when online.
- Let the user browse their own drives and share any drive publicly or with a link.
- In "tracking" mode, recommend public drives near the user's current location within a configurable radius.
- Authenticate with Google.
- Design the backend and data model to support iOS and web clients in the future.

### Non-Goals (for v1)
- Turn-by-turn navigation, traffic-aware routing, or ETA.
- Social graph (follow/friends/likes). Drives are discoverable by location, not by author.
- Monetization, ads, premium tiers.
- Video recording (photos only).
- Real-time collaborative recording.

## 3. Users & Scenarios

- **The Recorder (primary).** Driving a scenic route, wants to capture it with a few photo stops and voice-dictated notes, then review it at home and optionally publish it.
- **The Explorer (primary).** Arriving in an unfamiliar region, wants to see which nearby roads others have flagged as beautiful and pick one for the afternoon.
- **The Archivist (secondary).** Keeps private drives as a personal travel log, does not share publicly.
- **The Guide (secondary).** A local or a repeat traveler who answers questions on others' drives ("is the road paved all the way?", "OK for a sprinter van?") and earns points for helpful answers. Probably also a Recorder.

## 4. Core Concepts

- **Drive** — a recorded segment. Has a polyline (the GPS trace), metadata (title, description, cover photo, distance, duration, start/end location, tags), a visibility setting (private / unlisted / public), a vehicle-suitability summary, and lists of waypoints and comments.
- **Waypoint** — a point on or near a drive that the user annotated. Has a location, a timestamp relative to the drive, optional text, zero or more photos, and optional **vehicle annotations** — structured hints such as "4WD required," "max width 2.0 m," "not suitable for RVs," "tight switchbacks" — that callers of the drive can filter on.
- **Location buffer** — a short rolling history of the user's recent GPS fixes (configurable, default 30 min), retained locally so a drive can be started *retroactively*: "the last 15 minutes were beautiful — save them as a drive."
- **Comment / Thread** — a message attached to a drive, optionally anchored to a specific waypoint. Comments thread (reply-to-reply) and are the mechanism for asking questions and discussing a drive.
- **Profile** — the public face of a user. **Private by default**, can be switched to public. A private profile is not searchable and the user's public drives attribute to an anonymized handle. A public profile shows display name, avatar, bio, public drives, and points.
- **Points** — a counter on a profile, awarded for contributions (publishing a public drive, posting an answer that the asker marks helpful). Server-computed, not client-writable. Details in §11.
- **Discovery query** — "what public drives intersect a circle of radius R around point P?"

## 5. High-Level Architecture

```
┌──────────────────────────┐     ┌──────────────────────────┐
│    Android app (v1)      │     │   iOS / Web (future)     │
│  Kotlin + Compose        │     │   Swift / React          │
└──────────┬───────────────┘     └──────────┬───────────────┘
           │                                │
           │   Firebase Auth (Google)       │
           │   Firestore (metadata)         │
           │   Cloud Storage (photos)       │
           │   Cloud Functions:             │
           │    - geohash index fan-out     │
           │    - comment notifications     │
           │    - points aggregation        │
           │    - deletion cascade          │
           │    - moderation hooks          │
           │   FCM (push for comments)      │
           └────────────────┬───────────────┘
                            │
                    ┌───────▼────────┐
                    │  Firebase/GCP  │
                    └────────────────┘
```

Clients are offline-first: they read/write to a local store and a sync layer reconciles with Firestore when the network is available.

## 6. Tech Stack (Android v1)

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard Android. |
| UI | Jetpack Compose | Modern declarative UI, easier iteration. |
| DI | Hilt | Standard for Compose + Android. |
| Async | Kotlin Coroutines + Flow | Natural fit for GPS streams and sync. |
| Local DB | Room | Typed SQLite for drives, waypoints, sync queue. |
| Map rendering | **MapLibre GL** (OSM tiles) | Decision: MapLibre for v1 (no per-request billing, portable to iOS/web). Planned migration to Google Maps SDK once we understand the UX gap that matters. Isolate map usage behind a thin abstraction to keep the swap cheap. |
| Location | FusedLocationProviderClient | Battery-tuned, well-supported. |
| Backend | Firebase (Auth, Firestore, Storage, Functions) | Fast to ship, built-in offline cache, handles auth. |
| Geo queries | GeoFire / geohash fields in Firestore | Firestore has no native radius query; geohash prefix query is the standard workaround. |
| Image handling | Coil (loading), CameraX (capture) | Compose-native. |
| Background work | Foreground service for recording; WorkManager for sync | Recording needs a persistent notification; sync is opportunistic. |

## 7. Data Model

### 7.1 Local (Room)

```
profiles(
  uid TEXT PK,                    // Firebase UID (the current user; remote profiles cached below)
  display_name TEXT,
  avatar_url TEXT,
  bio TEXT,
  visibility TEXT,                // 'private' | 'public'   (default: 'private')
  anon_handle TEXT,               // shown on public drives when visibility='private'
  points INTEGER,                 // server-authoritative; client mirrors
  drives_published INTEGER,       // server-authoritative stats
  helpful_answers INTEGER,
  updated_at INTEGER
)

remote_profiles_cache(
  uid TEXT PK,                    // other users' public profiles we've viewed
  display_name TEXT,
  avatar_url TEXT,
  bio TEXT,
  points INTEGER,
  fetched_at INTEGER
)

drives(
  id TEXT PK,                    // UUID v4, client-generated
  owner_uid TEXT,                // Firebase UID
  title TEXT,
  description TEXT,
  visibility TEXT,               // 'private' | 'unlisted' | 'public'
  start_lat REAL, start_lng REAL,
  end_lat REAL, end_lng REAL,
  bounding_box TEXT,             // JSON: {north, south, east, west}
  distance_m INTEGER,
  duration_s INTEGER,
  started_at INTEGER,            // epoch ms
  ended_at INTEGER,
  cover_waypoint_id TEXT NULL,
  tags TEXT,                     // JSON array
  vehicle_summary TEXT,          // JSON: derived from waypoint vehicle_reqs
                                 //   e.g. {"requires_4wd": true, "max_recommended_width_m": 2.0,
                                 //         "rv_friendly": false, "notes": ["tight switchbacks"]}
  geohash TEXT,                  // of centroid, for discovery
  comment_count INTEGER,         // denormalized, updated via Cloud Function
  sync_state TEXT,               // 'local' | 'syncing' | 'synced' | 'dirty'
  server_version INTEGER
)

track_points(
  drive_id TEXT,
  seq INTEGER,
  lat REAL, lng REAL,
  alt REAL, speed REAL, accuracy REAL,
  recorded_at INTEGER,
  PK (drive_id, seq)
)

waypoints(
  id TEXT PK,
  drive_id TEXT,
  lat REAL, lng REAL,
  recorded_at INTEGER,            // drive-relative via offset from drive.started_at
  note TEXT,
  vehicle_reqs TEXT,              // JSON, nullable. Structured fields + free-form.
                                  //   { "requires_4wd": bool?, "max_width_m": num?,
                                  //     "max_height_m": num?, "rv_friendly": bool?,
                                  //     "tags": ["steep_grade", "narrow", "unpaved", ...],
                                  //     "notes": "free-form text" }
  sync_state TEXT
)

waypoint_photos(
  id TEXT PK,
  waypoint_id TEXT,
  local_path TEXT,
  remote_url TEXT NULL,
  width INTEGER, height INTEGER,
  taken_at INTEGER,
  sync_state TEXT
)

comments(
  id TEXT PK,                     // UUID
  drive_id TEXT,
  waypoint_id TEXT NULL,          // if non-null, comment is anchored to a waypoint
  parent_comment_id TEXT NULL,    // for threaded replies
  author_uid TEXT,
  body TEXT,
  created_at INTEGER,
  edited_at INTEGER NULL,
  is_helpful_answer INTEGER,      // 0/1, set by the drive owner or asker
  deleted INTEGER,                // 0/1, tombstone for moderation
  sync_state TEXT
)

location_buffer(
  // Local-only ring buffer, NEVER synced. Feeds retroactive-recording.
  seq INTEGER PK AUTOINCREMENT,
  lat REAL, lng REAL,
  alt REAL, speed REAL, accuracy REAL,
  recorded_at INTEGER
)
// Retention: delete WHERE recorded_at < now - buffer_minutes (default 30, user-configurable 0–120).
// Setting buffer_minutes = 0 disables the buffer entirely.

sync_queue(
  id INTEGER PK,
  entity_type TEXT,               // 'drive' | 'track_chunk' | 'waypoint' | 'photo' | 'comment' | 'profile'
  entity_id TEXT,
  op TEXT,                        // 'upsert' | 'delete'
  enqueued_at INTEGER,
  attempts INTEGER
)
```

Notes on the schema:
- **Track points** are stored separately from drive metadata because they're bulky and only needed when a drive is opened in detail — keeping them out of the drive row keeps list queries cheap.
- **`location_buffer`** is deliberately isolated from `track_points` and never enters the sync queue. It's a rolling local cache, not user-generated content. On "Record from N minutes ago," we copy the relevant window out of it into a new drive's `track_points` (and discard the rest on the normal retention schedule).
- **`vehicle_reqs`** is intentionally a small structured object with a free-text `notes` field. We start structured (booleans + dimensions + enum tags) so we can filter in discovery, but accept freeform for edge cases.
- **`comments`** is a flat table with `parent_comment_id` for threading; the client assembles the tree at render time. This avoids recursive schema in Firestore too.
- **`profiles`** (the singleton for the current user) mirrors server-authoritative fields like `points`. The client never writes `points` directly — a Cloud Function computes it.

### 7.2 Remote (Firestore)

```
/users/{uid}
  // Authoritative profile. Security rules gate read based on `visibility`.
  displayName, avatarUrl, bio,
  visibility,                    // 'private' | 'public'
  anonHandle,                    // stable pseudonym for private-profile attribution
  points,                        // written only by Cloud Functions
  stats: { drivesPublished, helpfulAnswers, totalDistanceKm },
  createdAt, updatedAt

/drives/{driveId}
  ownerUid, ownerAnonHandle,     // snapshot at publish time (stable even if user renames)
  title, description, visibility,
  startLat, startLng, endLat, endLng,
  boundingBox, distanceM, durationS,
  startedAt, endedAt, coverPhotoUrl,
  tags,
  vehicleSummary: {              // derived server-side from waypoint vehicleReqs
    requires4wd, maxRecommendedWidthM, maxRecommendedHeightM,
    rvFriendly, tagSet, notes
  },
  geohash, centroidLat, centroidLng,
  commentCount,                  // maintained by a Cloud Function trigger
  version, updatedAt

// Track geometry is stored as a GeoJSON blob in Cloud Storage, NOT in Firestore.
// Path: /tracks/{driveId}.geojson.gz   (gzipped FeatureCollection)
//   - Feature: LineString of [lng, lat, alt] with a `properties` object carrying
//     parallel arrays { recordedAt[], speed[], accuracy[] } keyed by coordinate index.
//   - The `/drives/{driveId}` doc stores `trackUrl` (a signed or public Storage URL)
//     and `trackBytes` so clients can decide whether to download (e.g. on cell).
// Rationale: a 4-hour drive ≈ 200 KB compressed, which is cheap in Storage
// and one round-trip to fetch vs N Firestore reads for chunks. Also natural
// export format (§13).

/drives/{driveId}/waypoints/{waypointId}
  lat, lng, note, recordedAt, photoUrls, order,
  vehicleReqs: {                 // nullable; same shape as the local field
    requires4wd, maxWidthM, maxHeightM, rvFriendly, tags, notes
  }

/drives/{driveId}/comments/{commentId}
  authorUid, authorAnonHandle,   // snapshot at comment time
  waypointId,                    // nullable; anchor to a waypoint
  parentCommentId,               // nullable; threading
  body,
  createdAt, editedAt,
  isHelpfulAnswer,               // bool; only the drive owner can flip this
  deleted                        // tombstone for moderation

/public_drives_index/{geohashPrefix}/drives/{driveId}
  // Denormalized for efficient discovery queries;
  // populated by a Cloud Function on drive publish/unpublish.
  // Carries enough for list rendering + vehicle filtering:
  //   title, coverPhotoUrl, distanceM, durationS, vehicleSummary,
  //   centroidLat, centroidLng, geohash, ownerAnonHandle, updatedAt
```

Photos go to Cloud Storage at `/users/{uid}/drives/{driveId}/waypoints/{waypointId}/{photoId}.jpg`.

**Attribution note.** When a user's profile is **private**, `ownerAnonHandle` (e.g., `"traveler-7f3a9c"`) is shown on their public drives and comments instead of `displayName`. The handle is stable per user so "that person who wrote the great Costa Rica drive" remains recognizable across their public drives, even though they're not identifiable. If the user later flips the profile to public, new drives/comments will carry `displayName`; existing ones can optionally be re-attributed in a background job.

## 8. Recording Pipeline

### 8.1 Active Recording

1. User taps **Record**. App requests fine-location permission if needed (and background-location for Android 10+).
2. Foreground service starts with a persistent notification ("Recording drive — 12 km · 0:34").
3. `FusedLocationProviderClient` emits location updates. Sampling strategy:
   - Request at 1 s intervals.
   - Persist a point only if (a) ≥2 s have passed *and* (b) the user has moved ≥10 m from the last persisted point, or (c) heading changed by >20°.
   - Accuracy filter: drop fixes with accuracy > 30 m when we already have a better recent fix.
   - At highway speeds this yields roughly one point every ~100 m; on winding mountain roads the heading filter captures the curves.
4. Points append to Room in batches (every 5–10 points or every 5 s) to avoid write amplification.
5. User can tap **Add waypoint** at any time — this captures current GPS and opens a quick-entry sheet (camera, text, vehicle annotations).
6. User taps **Stop**. App computes distance, duration, bounding box, centroid, geohash, vehicle summary (from waypoint vehicleReqs), and writes a draft Drive.
7. User is taken to a review screen to edit title/description/visibility before publish.
8. On sync, track points are serialized locally as gzipped GeoJSON (the blob format described in §7.2) and uploaded to Cloud Storage at `/tracks/{driveId}.geojson.gz`; the resulting URL + byte count are written onto the `/drives/{driveId}` Firestore doc. Firestore holds metadata only.

Battery: foreground service + 1 Hz GPS over a 4-hour drive is roughly 5–8% on a modern phone. We'll verify in practice.

### 8.2 Lookback Buffer (retroactive recording)

The lookback buffer lets the user say "actually, that *last* stretch was beautiful — save it" without having pressed Record ahead of time. It's the critical UX for the Costa Rica use case: you come around a bend and realize 10 minutes ago was the spot.

**Capture.** A lightweight location listener runs in the background whenever the app has location permission and the buffer is enabled (default: on, 30-minute window). It uses coarser parameters than active recording — 5 s request interval, 30 m displacement, accuracy filter at 50 m — to keep battery cost low. Measured impact target: < 2% battery per hour while idle.

**Lifecycle.** The listener runs as a short-lived job scheduled by `WorkManager` when the device is moving (detected via activity recognition: `DetectedActivity.IN_VEHICLE` or `ON_BICYCLE` — plus a manual override for walks/motorcycle). When the user is stationary for >10 min, the listener unregisters and re-registers on next motion. This avoids "always-on GPS" semantics, which is both a battery and a privacy concern.

**Storage.** Points go into the `location_buffer` table (never `track_points`, never `sync_queue`). Retention: rows older than `buffer_minutes` are pruned on every write. Size budget: at 5 s sampling, 30 min ≈ 360 rows ≈ tens of KB.

**Promotion to a Drive.** From the home screen, user taps **"Record from earlier"**. UI shows a slider (e.g., "from 14 min ago to now") over a map preview of the buffered path; user confirms. Promotion:
1. Copy selected buffer rows into a new drive's `track_points`.
2. Start active recording in-place if the user is still driving ("keep going" toggle), so the drive extends from X minutes ago through whenever they stop.
3. Take the user to the waypoint/review flow.

**Privacy.** The buffer is local-only — it's never synced, never backed up, never leaves the device. It's wiped on sign-out and when the user disables it in settings. **Decision:** on by default, 30-minute window; the onboarding flow includes a short, prominent explainer ("we keep the last 30 min of your GPS on-device so you can save a scenic stretch after the fact — you can turn this off anytime in Settings"). The Settings toggle offers 0 (off) / 15 / 30 / 60 / 120 minutes.

## 9. Discovery / Tracking Mode

- User opens **Tracking** tab; sees a map centered on their location with public drives overlayed.
- A slider sets the discovery radius (1–100 km, default 25 km). Persisted per user.
- Implementation: compute the set of geohash prefixes that cover the search circle; issue parallel Firestore queries on `/public_drives_index/{prefix}/drives`; client filters by exact distance (haversine).
- Results ranked by: overlap with the user's likely direction of travel (if moving) → distance → recency. v1 can start with distance-only and add direction later.
- Tapping a drive shows its full trace and waypoints; a "Navigate to start" button deep-links to Google Maps (we don't navigate ourselves).

Battery during tracking: we only need coarse location every 30–60 s, which is very cheap. We requery the index when the user has moved ~10% of the radius.

**Vehicle filter.** Explore supports filtering by vehicle suitability: "hide drives that require 4WD," "only RV-friendly," "max vehicle width 2.2 m." Filters read `vehicleSummary` on the index doc, so the server-side denormalization in §7.2 pays off here.

## 10. Conversations on Drives

Only **public** drives are open for comments. **Unlisted** drives are shareable by link but closed to discussion (link holders can view, but not post). **Private** drives have no comments.

- **Structure.** A comment belongs to a drive and optionally anchors to a waypoint (`waypointId`) so questions can reference a specific spot ("what's the road surface here?"). Comments thread via `parentCommentId`.
- **Realtime.** The detail screen subscribes to the drive's `comments` subcollection via a Firestore snapshot listener, so new replies appear live while viewing. Offline writes are queued through `sync_queue` like everything else.
- **Notifications.** A Cloud Function on comment create sends FCM pushes to: the drive owner (unless they muted the drive), everyone else in the thread (parent author + siblings), minus the author of the new comment. Users can mute per-drive.
- **Helpful answer.** The drive owner (the "asker," effectively) can mark one reply per question-thread as *helpful*. This is the hook that awards points to the answerer (§11) and surfaces good answers visually.
- **Moderation.** Comments can be reported; reports queue into a server-side review collection. Soft-delete via the `deleted` tombstone (preserves thread shape). v1 posture is manual review, same as drives.

## 11. Profiles & Points

### 11.1 Profile visibility

- **Private (default).** The user's profile page is not reachable from other users' UI. Their public drives and comments still exist under an **anonymous handle** (`ownerAnonHandle`, e.g., `traveler-7f3a9c`) — stable and consistent, but not linked to a viewable profile. The handle is generated on account creation and persists.
- **Public.** The profile is reachable by handle/display name from any drive or comment they authored. Shows their display name, avatar, bio, public drives, and points. Private drives remain private regardless.
- **public → private retract (automatic re-anonymization).** Flipping profile visibility from public to private runs a Cloud Function scrub over all of the user's public-surface content: every `ownerAnonHandle`/`authorAnonHandle` snapshot is re-asserted as the canonical byline, and any cached `displayName`/`avatarUrl` snapshots on drives, comments, and index entries are overwritten with the anon handle. After the scrub, no public surface displays the user's real identity — the profile is private *and* every piece of their past contribution reads as authored by the anon handle. Consistent with the drive-retract posture in §11.3: going private is a real withdrawal, not a cosmetic toggle.
- **private → public.** New drives and comments carry the user's `displayName`. The scrub is *not* reversed automatically — past contributions stay anon-handle-attributed unless the user runs a bulk "re-attribute my past contributions" action from Settings (mirror of the inverse escape hatch). Rationale: re-surfacing identity retroactively is a different decision from protecting it, and should be a deliberate step.

### 11.2 Points

Points reward contribution, not engagement. The initial economy (tunable later):

| Action | Points | Trigger |
|---|---|---|
| Publish a public drive | +10 | On `visibility` flip to `public`, once per drive. |
| Drive receives a "helpful" comment marker | +2 | When someone else's comment on *their* drive is marked helpful. (Optional; debatable.) |
| Post a comment marked *helpful answer* | +5 | When the drive owner marks your reply as helpful. |
| Drive deleted | -10 | Reverses the publish bonus. |

**Authority.** Points are computed by Cloud Functions only. Clients read the count from `/users/{uid}.points` but cannot write it. Firestore security rules forbid client writes to `points`, `stats`, and `anonHandle`.

**Anti-gaming.** Because "helpful" requires another user's drive owner to mark it, the obvious self-dealing loop (make a throwaway question and mark your own answer) is blocked. We'll still track rate limits and pattern-detect on the server (e.g., circular helpful-marking between a small set of accounts).

**Display.** Points appear on public profiles and on drive/comment bylines when the profile is public. Not shown for private profiles (would partially de-anonymize them).

### 11.3 Drive visibility lifecycle

Drives are **private by default** and must be *explicitly* flipped to `unlisted` or `public`. The reverse transitions are scoped to protect the owner's intent:

- **private → unlisted / public.** Drive metadata and track blob become readable per §13. For public, a Cloud Function populates the `public_drives_index` geohash entries so the drive appears in discovery. Comments become possible only on public.
- **public → private.** This is treated as a *retract*, not a simple flag flip. A Cloud Function runs a scrub pass:
  1. **Segment (the GPS trace) is preserved** — the `/drives/{driveId}` doc remains and stays readable to the owner; `trackUrl` keeps pointing at the same Storage blob.
  2. **Waypoints and their photos are hidden** — set `hidden=true` on each waypoint doc; security rules return them only to the owner. Photos remain in Storage but URLs are rotated so any previously-shared direct links stop working.
  3. **Comments are soft-deleted** — `deleted=true` tombstone on every comment. Thread structure is preserved for the owner's view.
  4. The drive is removed from `public_drives_index`.
  5. If the owner flips back to public later, waypoints can be un-hidden in bulk by the owner (one-click restore), but **comments are NOT automatically restored** — commenters likely expected their content to be gone after the retract. Owner can toggle individual comments back if desired.
- **public / unlisted → unlisted / public** (sideways moves) are simple flag flips; they don't trigger the scrub pass. The scrub is specific to *leaving* the public/unlisted surface.
- **Bulk retract "make all my public drives private"** is available in Settings (matches the destructive-but-recoverable posture above).

Rationale: the user's own creative record (the trace, the waypoints, the photos) should survive a retract because it's *their* memory. Other people's comments shouldn't quietly persist attached to a drive that's no longer public.

*(Note: this answers a question about **drive** visibility flips. The earlier §11.1 stance on **profile** visibility flips — not retroactively anonymizing past contributions — stands, but is worth revisiting in light of this decision. Listed as an open question in §17.)*

## 12. Offline & Sync Strategy

Core principle: **every user action writes to Room first and succeeds without network.** Sync is a background reconciliation.

- `WorkManager` job runs when connectivity is available, processing `sync_queue` in FIFO.
- Drives upload in order: drive metadata → track GeoJSON blob (Cloud Storage) → drive metadata update with `trackUrl` → waypoints → photos (photos last because they're heaviest; a drive's metadata and trace are viewable before photos finish).
- Conflict resolution: last-writer-wins on metadata using monotonic `version`. Waypoints and photos are append-mostly; edits are rare, so LWW is acceptable for v1.
- Firestore's built-in offline cache is kept *disabled* for our own data paths — we have Room for that — but used for read-through on discovery queries.
- Photo uploads are resumable (Firebase Storage supports this natively) and respect a user "Wi-Fi only" preference.

## 13. Auth & Privacy

- Firebase Auth with Google sign-in. **Account creation requires a verified email** — Google accounts come pre-verified, but we still check `firebaseUser.isEmailVerified` before any write (record, comment, annotate). If a future identity provider yields an unverified email, we gate the user behind a verification link. Verified email is our primary abuse control.
- **Read access tiers:**
  - **Anonymous (not signed in)** — can browse the Explore map and view public drives (including track + waypoints + comments read-only). Cannot record, annotate, comment, or save anything. All record/annotate/comment CTAs route to a sign-in prompt.
  - **Signed-in, verified email** — full read + write for their own account.
- Visibility per drive (owner-controlled, **private by default**):
  - **Private** — only owner sees it. Stored in Firestore with rules restricting read to `ownerUid`.
  - **Unlisted** — shareable by link (long random ID); not returned in discovery; **no comments**.
  - **Public** — indexed for discovery; any viewer (including anonymous) can read; signed-in users can comment.
- Publishing is always **explicit** — creating a drive produces a private draft; the user must deliberately flip visibility to public or unlisted. (See §11.3 for lifecycle of visibility changes.)
- User can delete a drive; delete cascades to track blob, waypoints, photos, comments, and index entries (Cloud Function handles this fan-out).
- Photo EXIF is stripped of camera serial before upload, but GPS is retained (that's the point).
- Users can export all their drives as GeoJSON + a photo zip (GDPR-style "data portability"). This is also our insurance against backend lock-in.

## 14. Security Rules (Firestore, sketch)

```
// Helper: every write requires a signed-in user with a verified email.
function verified() {
  return request.auth != null && request.auth.token.email_verified == true;
}

function driveDoc(id) {
  return get(/databases/$(database)/documents/drives/$(id)).data;
}

match /users/{uid} {
  // Public profile is world-readable; private profile is owner-only.
  // The client never writes points/stats/anonHandle.
  allow read: if resource.data.visibility == 'public'
              || request.auth.uid == uid;
  allow create: if verified() && request.auth.uid == uid;
  allow update: if verified() && request.auth.uid == uid
              && !request.resource.data.diff(resource.data).affectedKeys()
                    .hasAny(['points', 'stats', 'anonHandle']);
  allow delete: if request.auth.uid == uid;
}

match /drives/{id} {
  // Anonymous read allowed for public & unlisted (unlisted relies on the unguessable id).
  allow read: if resource.data.visibility == 'public'
              || resource.data.visibility == 'unlisted'
              || request.auth.uid == resource.data.ownerUid;
  allow create: if verified() && request.auth.uid == request.resource.data.ownerUid;
  allow update, delete: if verified() && request.auth.uid == resource.data.ownerUid;

  match /waypoints/{wpId} {
    // Owner always sees. Others see only when drive is non-private AND waypoint is not hidden
    // (hidden=true is set by the public→private retract scrub — see §11.3).
    allow read: if request.auth.uid == driveDoc(id).ownerUid
                || (driveDoc(id).visibility != 'private'
                    && resource.data.hidden != true);
    allow write: if verified() && request.auth.uid == driveDoc(id).ownerUid;
  }

  match /comments/{cId} {
    // Readable if the drive is readable. (Comments only ever exist on public drives
    // per §10, but the rule still gates reads cleanly if a drive is retracted.)
    allow read: if driveDoc(id).visibility != 'private'
                || request.auth.uid == driveDoc(id).ownerUid;

    // Commenting requires verified sign-in AND a public drive.
    // Unlisted is explicitly excluded (per resolved Q10).
    allow create: if verified()
                && request.auth.uid == request.resource.data.authorUid
                && driveDoc(id).visibility == 'public';

    // Authors can edit their body; drive owner can flip isHelpfulAnswer & deleted.
    allow update: if verified() && (
                  (request.auth.uid == resource.data.authorUid
                   && !request.resource.data.diff(resource.data).affectedKeys()
                          .hasAny(['authorUid', 'createdAt', 'isHelpfulAnswer']))
               || (request.auth.uid == driveDoc(id).ownerUid
                   && request.resource.data.diff(resource.data).affectedKeys()
                          .hasOnly(['isHelpfulAnswer', 'deleted'])));

    allow delete: if verified() && (request.auth.uid == resource.data.authorUid
                                 || request.auth.uid == driveDoc(id).ownerUid);
  }
}

match /public_drives_index/{prefix}/drives/{id} {
  allow read: if true;   // anonymous Explore
  allow write: if false; // only Cloud Functions write here
}
```

Cloud Storage rules follow the same pattern: `/tracks/{driveId}.geojson.gz` is readable when the corresponding drive is readable (checked via a Storage rule that reads the Firestore doc), writable only by the owner. Photos at `/users/{uid}/drives/{driveId}/waypoints/{...}` follow the same gate plus the waypoint-hidden check.

## 15. UX / Screen Inventory (v1)

1. **Cold open (anonymous)** — the app opens directly into Explore. A persistent "Sign in" CTA in the top bar unlocks recording, annotating, and commenting. Tapping any write action (Record, Record-from-earlier, Add comment, Ask a question) routes to sign-in.
2. **Onboarding / sign-in** — Google sign-in (pre-verified email), email-verification step if the identity provider returns an unverified email, location permission rationale, lookback-buffer explainer ("on by default — turn off anytime").
3. **Home (signed in)** — three primary actions: **Record**, **Record from earlier** (active only when buffer has data), and **Explore nearby**. Below: list of user's recent drives.
4. **Recording** — big map, elapsed time/distance, waypoint button, stop button.
5. **Record-from-earlier** — map preview of buffered path, time-range slider ("from N min ago to now"), optional "keep going" toggle to continue recording live.
6. **Waypoint sheet** — note field, camera/photo picker, vehicle annotations panel (predefined tag chips + free-text tags + structured fields).
7. **Drive review** — edit title, description, tags, cover photo, visibility (defaults to private), confirm computed vehicle summary. Save.
8. **Drive detail** — map with trace + waypoint pins; scrollable list of waypoints (photo + note + vehicle tags); comments panel (public drives only; threaded, with "Ask a question" input); share button.
9. **Explore** — map with nearby public drives; radius slider; vehicle filter; list view toggle. Available to anonymous users.
10. **Profile (own)** — user's drives (grouped by trip?), points, visibility toggle, settings entry, sign out.
11. **Profile (other user, public only)** — public display name, avatar, bio, points, public drives.
12. **Settings** — discovery radius default, Wi-Fi-only uploads, photo quality, **lookback buffer on/off + window length**, export data, delete account.

## 16. Cross-Platform Forward-Compatibility

- The data model is defined in a platform-agnostic way (this doc + Firestore schema). iOS and web clients will read the same Firestore collections.
- Server-side logic (geohash maintenance, index fan-out, deletion cascade, moderation) lives in **Cloud Functions**, not in the Android client, so it's reused by iOS and web from day one.
- Any client-heavy logic (sampling strategy, sync queue, geohash coverage computation) should be prototyped in Kotlin with an eye on clean interfaces, but we're **not** building a KMM shared module in v1 — the cost/benefit doesn't pay off until the second client exists. We'll extract shared pieces when we build iOS.

## 17. Decisions & Open Questions

### 17.1 Resolved (v1)

1. **Maps provider → MapLibre.** MapLibre GL for v1; plan to migrate to Google Maps SDK later once we know which UX gap matters. Isolate map use behind a thin abstraction so the swap is cheap.
2. **Track storage → GeoJSON on Cloud Storage.** Per-drive gzipped GeoJSON blob at `/tracks/{driveId}.geojson.gz`; Firestore stores metadata + `trackUrl` only. Doubles as the natural export format.
3. **Moderation posture → verified-email gate + report-and-remove.** Writing (record, annotate, comment) requires a verified email; unauthenticated users get read-only access. Manual triage of reports; no automated moderation in v1.
4. **Anonymous browse.** Users that are not signed in can browse the Explore map and view public drives read-only. Writing of any kind requires a signed-in, email-verified account. All write CTAs route to sign-in.
5. **Lookback buffer default → on, 30 min.** Default-on with a prominent onboarding explainer. Window configurable in Settings (0 / 15 / 30 / 60 / 120 min).
6. **Vehicle annotation schema → hybrid.** Structured fields (`requires_4wd`, `max_width_m`, `max_height_m`, `rv_friendly`) + a tag field that's "open with predefined suggestions": UI shows a curated chip palette (`steep_grade`, `narrow`, `unpaved`, `tight_switchbacks`, `rough_surface`, `river_crossing`, `toll`, ...) and also accepts free-text tags. Predefined tags are filterable in Explore; free-text tags are searchable but not filterable.
7. **Points economy → as specified in §11.2.** Ship the initial values; revisit after beta feedback.
8. **Comments on unlisted drives → disabled.** Unlisted drives are share-by-link read only; commenting is a public-drive-only feature.
9. **Drive visibility lifecycle → private by default + scrub-on-retract.** See §11.3. Making a drive public is always explicit; flipping public → private runs a scrub that hides waypoints and soft-deletes comments while preserving the GPS segment.
10. **Profile visibility flip → auto re-anonymize on retract.** Going profile public → private runs a scrub that re-anonymizes all past drives and comments to the anon handle. Going back to public does *not* auto-reattribute; the user must run an explicit bulk re-attribution from Settings. See §11.1.

### 17.2 Deferred (revisit post-v1)

11. **Attribution / credit** — if a user re-drives someone else's public drive, link the new recording to the original? Deferred — revisit in v2.
12. **Offline map tiles** — caching along a planned drive. Deferred — revisit in v2.

## 18. Milestones (rough)

- **M0 — Scaffolding (1 wk).** Empty Android project, Firebase hooked up, Google sign-in working, Compose navigation skeleton, CI.
- **M1 — Record & review local (2 wks).** Foreground recording service, Room persistence, recording UI, drive review & detail screens, waypoint vehicle annotations. No backend yet.
- **M2 — Lookback buffer (0.5 wk).** Background listener + activity recognition, buffer table with retention, "Record from earlier" UI.
- **M3 — Sync to Firestore (1.5 wks).** Sync queue, Cloud Functions (geohash fan-out, deletion cascade), security rules, photo upload, data export.
- **M4 — Discovery (1.5 wks).** Geohash indexing, Explore screen, tracking mode, vehicle-summary filtering.
- **M5 — Profiles & Conversations (1.5 wks).** Profile screens (own + other), visibility toggle, anonymous-handle attribution, comments (threads + realtime), FCM notifications, helpful-answer mechanic, points aggregator function.
- **M6 — Polish & beta (1 wk).** Battery profiling (including buffer cost), error states, empty states, share links, crash reporting, moderation report flow, closed beta.

Total to beta: ~9 weeks of focused work.

## 19. What's Not in This Document (Yet)

- Analytics / telemetry approach.
- Specific copy / voice / visual design (typography, palette, iconography).
- iOS-specific platform guidelines.
- Web client (SSR vs SPA, map library choice).
- Licensing of user-contributed content (default: user retains copyright, grants us a license to host and display — needs lawyer review before public launch).

---

*Please mark sections you want to revise, expand, or cut. Open questions in §15 are the ones that most need your input before we start implementing.*
