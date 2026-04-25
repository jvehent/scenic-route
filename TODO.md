# scenic-route — pre-launch & post-launch TODO

Captured from the security audit (DESIGN.md §17 covers the design-level open questions; this list is operational and post-MVP feature work).

Severity:
- 🔴 **launch-blocker** — do not open to non-test users without this
- 🟡 **launch-prep** — needed for a clean public launch, do during pre-launch window
- 🟢 **post-launch** — ship v0.x without it, plan into the first sprint after launch

---

## 🔴 Launch-blockers

### Deploy the latest rules + functions
- `firebase deploy --only functions,firestore:rules,storage:rules`
- Verify deploy succeeded (`firebase functions:list` shows `onUserDocCreated`, `onCommentCreated`, `cleanupTrashedDrives`, etc. all `ACTIVE`).
- Smoke test: sign up new account → confirm `/users/{uid}` has a server-assigned `anonHandle` that is **not** derivable from the uid hash.

### Enable Firebase App Check
- Provider: **Play Integrity** for Android.
- Enforce on Firestore, Storage, Cloud Functions.
- Without it, anyone can hit the backend from a fake client and the security rules are the only line of defense — App Check raises that bar to "needs a real Play Integrity-attested device".
- Effort: ~1 hour. Single highest-leverage hardening before public release.
- Docs: <https://firebase.google.com/docs/app-check/android/play-integrity-provider>

### Replace MapLibre demo tiles with a real provider
- Current: `https://demotiles.maplibre.org/style.json` — rate-limited, third-party host sees viewing region.
- Options: MapTiler (free tier ~100k tile loads/month), Stadia Maps (no key on free tier with attribution), Mapbox Studio (free tier), self-hosted via Tileserver-GL.
- Update the `STYLE_URL` constant in `app/src/main/java/com/scenicroute/ui/map/ScenicMap.kt`.

### Generate a release signing keystore and register the release SHA-1
- See `keytool` instructions in the Option 2 install guide we did earlier.
- Add the release SHA-1 fingerprint (and SHA-256, for App Check + future Play Integrity) in Firebase Console → Project settings → Your apps → `com.scenicroute` → Add fingerprint.
- Wire up a `signingConfigs.release { ... }` block in `app/build.gradle.kts` reading from `keystore.properties`.
- Without this: Play Store upload will be rejected, OR Google sign-in will silently fail in release builds because the app is signed with an unknown cert.

### Set a GCP budget alert
- GCP Console → Billing → Budgets & alerts → Create.
- Budget around $50/month for early launch with email alerts at 50/90/100%.
- Without this, a single runaway query, photo upload abuse, or scrape can run up four-figure bills before you notice.

---

## 🟡 Launch-prep (do before opening to public, but not blockers in the abstract)

### Document the manual account-deletion process
- No in-app delete-account flow yet (see post-launch item below).
- Until then: have a documented support email + an SLA (e.g., "we'll process within 7 days") to honor GDPR right-to-erasure requests.
- Add to your privacy policy and the Settings screen as a sentence with the email address.

### Cap Cloud Logging retention to ≤30 days
- Reduces blast radius of the few remaining `logger.info` calls in the Cloud Functions.
- GCP Console → Logging → Logs Storage → reduce default `_Default` bucket retention from the standard 30 days down (or set a separate, shorter bucket if you want fine control).

### Threat-model the Web Client ID being public
- It's currently in `local.properties` (gitignored) but it's also embedded in every APK we ship. That's by design (it's not a hard secret) but document for the team that the OAuth web client has only the Firebase project as its trust boundary; do not add scopes or grants to that client.

### Verify EXIF stripping end-to-end
- After deploy, capture a photo on your phone, publish a public drive, then download the photo URL via `curl` and run `exiftool` on it.
- Make/Model/Software/Serial fields should be empty. GPS should remain.
- If anything device-identifying leaks, add the missing tag name to `IDENTIFYING_TAGS` in [ExifScrubber.kt](app/src/main/java/com/scenicroute/data/storage/ExifScrubber.kt).

### Pre-launch chaos test on the rules
- Open the Firestore Rules Playground and run the malicious-write scenarios from the audit:
  - Anonymous read of `/tracks/{driveId}` for a private drive → must be denied.
  - Verified user creating `/users/{uid}` with `points: 99999` → must be denied (rule restricts field set).
  - Verified user posting comment on a drive with `deletedAt` set → must be denied.
  - Verified user uploading 11 MB photo → must be denied.

---

## 🟢 Post-launch (v0.2+)

### In-app account deletion (GDPR)
- New "Delete account" entry in Settings (with confirmation modal, "type DELETE to confirm").
- Soft-delete all the user's drives (sets `deletedAt`).
- Trigger an immediate `cleanupTrashedDrives` invocation for that user (or a one-shot Cloud Function `purgeAccount(uid)`).
- Delete `/users/{uid}` doc.
- Sign out + clear local state via `SignOutCleaner`.

### Implement the points system
- DESIGN.md §11.2 specifies the values (+10 publish public drive, +5 helpful answer, -10 drive deleted).
- Cloud Function: trigger on `/drives/{id}` write (visibility flip to public) and `/drives/{id}/comments/{id}` update (`isHelpfulAnswer` flip).
- Increment `/users/{uid}.points` via `FieldValue.increment(...)`.
- Update `/users/{uid}.stats.drivesPublished` and `helpfulAnswers` in the same transaction.

### Comment moderation flow
- "Report" action on each comment in the public drive view.
- Writes to `/reports/{id}` (new collection, server-only).
- Cloud Function notifies an admin Slack/email channel.
- Initial moderation: manual review.

### FCM push notifications
- Token registration on the client (FirebaseMessaging.getToken).
- Cloud Function on comment create → notify drive owner + thread participants (minus the author).
- Mute-per-drive setting on Drive Detail.

### App Check enforcement on Cloud Functions
- App Check on Firestore + Storage covers most of the surface; for the few `onCall` functions we'll eventually add (e.g., `purgeAccount`), set `enforceAppCheck = true` in their options.

### Two-pane tablet layouts
- Already-adaptive on width class but a real two-pane (list + detail side-by-side) on Expanded width would meaningfully improve tablet UX. Compose Material3 has `NavigationSuiteScaffold` and adaptive navigation that fits.

### iOS app
- Per DESIGN.md §16 the data layer is platform-agnostic. iOS would reuse Firestore + Storage + Cloud Functions verbatim. UI in SwiftUI, sync logic in Swift (or shared via KMM if pain warrants).

### Web companion (read-only landing page for shared links)
- Static site at `scenicroute.app` (when the domain is registered).
- Receives traffic from `https://scenicroute.app/d/{driveId}` shared links, fetches the public drive from Firestore, renders the trace + waypoints + photos.
- Hosts `/.well-known/assetlinks.json` so Android App Links verify and the deep link goes straight to the installed app instead of the chooser.
- Replace the `scenicroute://drive/{id}` custom scheme with the verified https link as the canonical share URL.

### Release-notes-driven Firebase App Distribution
- Use `firebase appdistribution:distribute` for tester builds (the "Option 3" install path we discussed). Saves the manual sideload friction.

### Cost & abuse monitoring
- Cloud Logging metric on `cleanupTrashedDrives` invocation count + duration; alert if the queue grows beyond the daily batch limit (currently 200).
- Cloud Logging metric on `onCommentCreated` rate-limit triggers; spike = bot signal.
- Storage bucket size alert via Cloud Monitoring.

### Re-evaluate scrub semantics for retroactive bulk re-attribution
- DESIGN.md §11.1 left this as resolved-with-asymmetry: profile public→private auto-anonymizes, the inverse requires manual "re-attribute past contributions". UI for that bulk action doesn't exist yet — add a Settings entry that calls a new Cloud Function `bulkReattribute(uid)`.

### Encrypted local storage
- Currently relying on Android FDE. SQLCipher + EncryptedSharedPreferences would harden against rooted-device extraction.
- Trade-off: meaningful runtime cost on Room queries; only add if a threat model demands it.

### Test harness
- No tests exist today. At minimum: Firestore rules tests via `@firebase/rules-unit-testing`, run in CI on every rules change. The audit found multiple subtle rule conditions that should not silently regress.

---

## How to use this list

- Items here are **ordered by severity, not by sequence**. Within a severity, pick by impact-to-effort ratio.
- Anything blocking real-money risk (cost, account compromise, data leak) is 🔴 — no exceptions.
- Update this file as you ship items. Keep done items below a `## Done` heading rather than deleting them — it's a useful audit trail.
