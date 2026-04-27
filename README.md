<p align="center">
  <img src="icon.svg" alt="Senik" width="120" height="120">
</p>

<h1 align="center">Senik</h1>

<p align="center"><em>For travelers who love the journey as much as the destination.</em></p>

<p align="center">
  <a href="https://senikroute.com">senikroute.com</a> ·
  <a href="DESIGN.md">Design doc</a> ·
  <a href="INSTALL.md">Setup</a> ·
  <a href="PRIVACY.md">Privacy</a>
</p>

---

## What it is

Senik is an Android app for recording, annotating, and sharing scenic driving segments — the back roads, mountain passes, coastal drives, and unmarked detours that are worth the trip even when they're not the fastest way to anywhere.

You record a stretch of road. You drop waypoints with photos and notes at the moments worth remembering. You decide whether it stays private, becomes a share-by-link memory for the friends you drove with, or goes public so someone showing up in the same valley two summers from now can find it.

When *you're* the one passing through somewhere new, Senik shows you the scenic drives others have left behind nearby, so the question shifts from *"what's the fastest route?"* to *"what's worth driving here?"*.

## What it isn't

Senik is **not** a navigation app. There is no turn-by-turn, no traffic routing, no ETA, no rerouting around accidents. Google Maps and Waze already do those things very well, and replacing them is not the goal.

Senik answers a different question. Most map apps are built around the destination. Senik is built around the road between destinations.

## Philosophy

A few things we think matter, and that the codebase reflects:

- **The journey is the product.** Distance, ETA, and efficiency aren't the primary metrics. The drive is.
- **Privacy is not optional.** The lookback buffer (which keeps the last few minutes of GPS on-device so you can save a stretch *after* you finish driving it) is local-only — never synced, never backed up, wiped on sign-out. EXIF metadata is stripped from photos before upload. Anonymous handles are server-assigned and unlinkable from your account.
- **Slow software.** Recording is foreground-service GPS at a user-controlled cadence (1 s to 60 s, default 10 s). Sync is opportunistic and Wi-Fi-aware. The app does not poll, does not background-track outside an active recording, and does not phone home.
- **Local-first.** Drives live in a Room database on the device first; cloud sync is an upload, not a source of truth. You can use Senik with no signal, and your drives still work.
- **No turn-by-turn means no surveillance.** We don't need to know where you're going. We only need to know where you've already been, and only the parts you choose to keep.

## Architecture at a glance

- **Android (Kotlin + Jetpack Compose).** Single-module Android app. Material 3, Compose Navigation, MapLibre for tiles.
- **On-device storage.** Room (drives, track points, waypoints, photos), DataStore (settings), Cloud Storage refs for photos.
- **Backend.** Firebase Auth (Google Sign-In via Credential Manager), Firestore for drives/comments/profiles, Cloud Storage for photos and gzipped GeoJSON tracks, Cloud Functions (Node 20 / TypeScript) for fan-out and cleanup.
- **Maps.** MapLibre GL Native, currently rendering an OpenFreeMap style; configurable to Stadia Maps Stamen Terrain when an API key is provided.

A more detailed walkthrough lives in [DESIGN.md](DESIGN.md).

## Getting started

### Run the app locally

You need: JDK 17, Android SDK 35, a Firebase project of your own (the bundled `google-services.json` is gitignored).

```bash
git clone <this repo>
cd senik
# follow INSTALL.md for the Firebase + keystore setup
./gradlew installDebug
```

Full step-by-step for a fresh machine is in [INSTALL.md](INSTALL.md).

### Run the cloud functions

```bash
cd functions
npm install
npm run build
firebase deploy --only functions,firestore:rules,firestore:indexes,storage
```

### Seed featured drives

```bash
cd functions
npm run seed
```

## Contributing

Senik is in pre-launch and the code is moving fast, but contributions are very welcome. Some specific places to start:

- **Bug reports and crash logs.** If something goes wrong on your device, a `logcat` excerpt and the device model is gold.
- **iOS port.** Android is the primary platform today, but the data model is platform-neutral by design. An iOS client is on the post-launch roadmap (see [TODO.md](TODO.md) §🟢).
- **Web companion.** A read-only landing page that renders shared drive links would let people preview a route before installing the app.
- **Map tiles.** If you have experience with self-hosted Tileserver-GL or a strong opinion on which map style best fits the "hand-drawn travel journal" feel, we'd love to hear it.
- **Translations.** All user-facing strings live in [strings.xml](app/src/main/res/values/strings.xml).

### Code conventions

- Kotlin source uses 4-space indent, 120-col soft limit. The existing files are the reference.
- New screens follow the `ui/<feature>/<Feature>Screen.kt` + `<Feature>ViewModel.kt` pattern; ViewModels are Hilt-injected.
- Tests live next to the code under test in `app/src/test/`. Robolectric is set up for tests that need Android resources; pure-Kotlin tests don't need it.
- Run `./gradlew :app:testDebugUnitTest` before opening a PR. CI runs the full lint + test matrix.

### Pull requests

1. Branch from `main`.
2. Keep PRs focused — one feature or one fix per PR is much easier to review than ten things bundled.
3. If you're adding a screen or a Firestore-touching feature, mention any new permissions or data fields in the PR body so the privacy implications can be reviewed.
4. By contributing, you agree your contributions are licensed under the same terms as the project (TBD — currently treated as all-rights-reserved while the licensing decision is pending; will land before the public launch).

## Roadmap

The full launch checklist is in [TODO.md](TODO.md), broken into:

- 🔴 **launch-blockers** — App Check enforcement, release signing, GCP budget alert, real map tiles.
- 🟡 **launch-prep** — manual account-deletion process, log retention caps, EXIF verification, separating debug/release into distinct Firebase projects.
- 🟢 **post-launch** — in-app account deletion, points system, comment moderation, FCM push, two-pane tablet layouts, iOS app, web companion.

## Privacy

The full privacy policy lives at [PRIVACY.md](PRIVACY.md) (also rendered at <https://senikroute.com/privacy-policy.html>). The short version: we collect what's needed to make the app work, we strip what we don't need, and your private drives stay private.

## Contact

- Website: <https://senikroute.com>
- Issues / feature requests: GitHub issues on this repo
- Privacy / data requests: senikroute@googlegroups.com

---

<p align="center"><sub>Built by a family of road-trippers, somewhere between a Costa Rican mountain pass and the next overlook.</sub></p>
