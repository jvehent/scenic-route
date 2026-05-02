# Installation & Deployment

Reference for setting up Senik on a fresh machine and shipping it to debug or production. Companion to [DESIGN.md](DESIGN.md) (architecture) and [TODO.md](TODO.md) (launch checklist).

---

## 1. Prerequisites

Tools needed on any dev machine:

| Tool | Version | Why |
|------|---------|-----|
| JDK | 17 | AGP 8.7 + Gradle build target |
| Android SDK + Platform 35 + Build-tools 34.0.0 | — | Android toolchain |
| Node.js | 20.x | Cloud Functions runtime + Firebase CLI |
| Firebase CLI | ≥ 13 | rules / functions deploy |
| `gcloud` (Google Cloud SDK) | latest | Application Default Credentials for the seed script |
| `adb` | comes with Android SDK platform-tools | install + log capture |

### 1.1 JDK 17 (Ubuntu/WSL)

```bash
sudo apt install openjdk-17-jdk
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc
javac -version  # must succeed
```

### 1.2 Android SDK (cmdline-tools, no Android Studio required)

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-*.zip
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

cat >> ~/.bashrc <<'EOF'
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
EOF
source ~/.bashrc

yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools"
```

### 1.3 Node.js 20

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node --version  # v20.x
```

### 1.4 Firebase CLI + gcloud

```bash
sudo npm install -g firebase-tools

# Google Cloud SDK
sudo apt install google-cloud-cli   # or follow https://cloud.google.com/sdk/docs/install

firebase login
gcloud auth login
gcloud auth application-default login
```

### 1.5 Generate the Android debug keystore (if missing)

If `~/.android/debug.keystore` doesn't exist (Android Studio's first build creates one; without it you have to do it manually):

```bash
mkdir -p ~/.android
keytool -genkey -v \
  -keystore ~/.android/debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey \
  -dname "CN=Android Debug,O=Android,C=US" \
  -keyalg RSA -keysize 2048 -validity 10000

# Print the SHA-1 — you'll need this for Firebase OAuth
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

---

## 2. Firebase project setup (one-time)

### 2.1 Create / select the project

1. https://console.firebase.google.com → **Add project** (or pick yours).
2. **Build → Authentication → Sign-in method → Google → Enable** + set support email.
3. **Build → Firestore Database → Create database** in production mode, region close to your users (location is permanent).
4. **Build → Storage → Get started** in production mode (requires the **Blaze** pay-as-you-go plan; free tier is generous).
5. **Project settings (gear) → Your apps → Add app → Android** twice:
   - Package name `com.senikroute` (release)
   - Package name `com.senikroute.debug` (debug)
   - For each, paste the corresponding signing-cert SHA-1 fingerprint.
6. **Download `google-services.json`** for either app (it'll contain both clients) and place it at `app/google-services.json`. This file is gitignored.
7. **Project settings → General → Your apps → Web SDK configuration** → copy the **Web client ID**.

### 2.2 Wire local credentials

Create `local.properties` at the repo root (gitignored) with:

```properties
sdk.dir=/home/<you>/Android/Sdk
GOOGLE_WEB_CLIENT_ID=1234567890-xyz...apps.googleusercontent.com
```

`sdk.dir` is auto-written by Android Studio; if you only use the CLI, write it yourself.

### 2.3 Wire Firebase CLI to the project

```bash
firebase use --add        # interactive picker; alias as 'default'
# OR
firebase use <project-id>
```

This creates `.firebaserc` (gitignored).

### 2.4 Set the GCP quota project for ADC

```bash
gcloud config set project <project-id>
gcloud auth application-default set-quota-project <project-id>
```

This is what the seed script uses.

---

## 3. Debug environment

### 3.1 Build + install on a connected device

```bash
# Wireless ADB (WSL2 / no USB):
#   On phone: Settings → System → Developer options → Wireless debugging → Pair device with code
#   On laptop:
adb pair <ip>:<pair-port>          # enter the 6-digit pairing code from the phone
adb connect <ip>:<connect-port>
adb devices                         # phone should appear as 'device'

./gradlew installDebug
adb shell am start -n com.senikroute.debug/com.senikroute.MainActivity
```

### 3.2 Sideload an APK onto an unconnected device

```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
# Email / Drive / cable it to the phone, tap to install.
```

The debug APK uses the package ID `com.senikroute.debug` (note the `.debug` suffix from `applicationIdSuffix`) and is signed with `~/.android/debug.keystore`. Both must match what's registered in Firebase, otherwise Google sign-in fails.

### 3.3 Run from Android Studio

`File → Open` → pick the repo root (must contain `settings.gradle.kts`). Wait for Gradle sync. Pick the device + ▶.

### 3.4 Tail logs

```bash
adb logcat --pid=$(adb shell pidof com.senikroute.debug)
```

### 3.5 Stop the debug build cleanly

```bash
adb shell am force-stop com.senikroute.debug
```

---

## 4. Production environment

The release path adds: a real signing key, a release Firebase app registration, and Cloud Functions + rules deployed.

### 4.1 Generate the release keystore (one-time)

```bash
mkdir -p ~/keystores
keytool -genkeypair -v \
  -keystore ~/keystores/senik-release.jks \
  -alias senik-key \
  -keyalg RSA -keysize 2048 -validity 10000
# Set strong passwords. Save them in your password manager.

# Get the release SHA-1 + SHA-256 — both go into Firebase
keytool -list -v -keystore ~/keystores/senik-release.jks -alias senik-key | \
  grep -E 'SHA1|SHA256'
```

Register both fingerprints in **Firebase Console → Project settings → Your apps → `com.senikroute` → Add fingerprint**. (SHA-1 unlocks Google sign-in; SHA-256 is required for App Check + Play Integrity.)

### 4.2 Wire release signing into Gradle

Create `keystore.properties` at the repo root (gitignored):

```properties
# Replace ${HOME} below with your actual absolute path — Gradle does NOT
# expand shell vars or ~ here. e.g. /home/julien/keystores/senik-release.jks
storeFile=${HOME}/keystores/senik-release.jks
storePassword=<your-store-password>
keyAlias=senik-key
keyPassword=<your-key-password>
```

> ⚠️ The placeholders (`${HOME}`, `<your-store-password>`, `<your-key-password>`) are **literal text** that you must hand-edit. Gradle reads this file as a plain Java `.properties` file with no variable expansion — leaving any placeholder in place will fail with `Keystore file '...' not found for signing config 'release'`.

Then in [app/build.gradle.kts](app/build.gradle.kts), add a `signingConfigs.release` block (the file currently has `release { ... }` under `buildTypes` but no signing config — that's intentional for v1; add this when you're ready to ship):

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile") ?: "")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // existing minify/proguard config stays
        }
    }
}
```

### 4.3 Build the release artifacts

```bash
./gradlew assembleRelease       # APK at app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease         # AAB at app/build/outputs/bundle/release/app-release.aab — Play Store wants this
```

### 4.4 Deploy Cloud Functions + rules + indexes

```bash
cd functions && npm run build && cd ..

firebase deploy --only functions,firestore:rules,firestore:indexes,storage
```

Notes:
- The correct Storage target is bare `storage` — `storage:rules` is **not** a valid Firebase CLI target name and will fail with `Could not find rules for the following storage targets: rules`.
- First-time function deploy can fail with "Eventarc Service Agent" propagation issues — wait 2–5 minutes and re-run.
- Confirm everything landed: `firebase functions:list` should show six ACTIVE functions: `onDriveWritten`, `onDriveDeleted`, `onProfileWritten`, `cleanupTrashedDrives`, `onUserDocCreated`, `onCommentCreated`.

### 4.5 Seed the featured drives (one-time + after each rebuild of the catalog)

```bash
# Authenticate ADC if you haven't:
gcloud auth application-default login
gcloud config set project <project-id>
gcloud auth application-default set-quota-project <project-id>

cd functions
npm run seed
```

The seed writes 8 curator drives + a curator profile. The `onDriveWritten` Cloud Function fan-outs each drive into `/public_drives_index/{prefix}/drives/{id}` automatically. Idempotent: re-running refreshes them.

**If you re-run the seed AFTER changing the curator drive list**, drives you removed from the seed are NOT auto-deleted from Firestore — delete them manually from the console (their `featured-*` IDs make them easy to find).

### 4.5.0 Bulk-seed from KML

For maintaining the catalog at scale, drop curated KML files into `samples/` (one drive per file, named `NN_slug.kml`) and run:

```bash
cd functions
npm run seed:kml                                          # seed everything in samples/
npm run seed:kml -- --dry-run                             # parse-only, no Firestore/Storage writes
npm run seed:kml -- --only=01_pacific_coast_highway      # single file
```

Each KML becomes `drives/featured-{slug}` with the same shape the hand-curated `npm run seed` produces. Track GeoJSON is built from the `<Folder name="Route">` LineString and uploaded to Cloud Storage; the `<Folder name="Scenic Points">` placemarks become waypoints with their `<description>` text attached. The "Waypoints" folder in the KMLs (route markers without descriptions) is intentionally ignored.

The script is idempotent: re-running overwrites existing `featured-{slug}` drives and replaces their waypoint subcollections. As with the hand-curated seed, drives you remove from `samples/` are not auto-pruned from Firestore — delete by ID in the console.

### 4.5.1 Inspect a single drive

Useful when a user reports a missing track or odd metadata. The drive ID is visible in the app at the bottom of the DriveDetailScreen and DriveReviewScreen — tap to copy.

```bash
cd functions
npm run inspect <drive-id>           # pretty-print drive doc + waypoints + track summary
npm run inspect <drive-id> --track   # include all track point coordinates
npm run inspect <drive-id> --save    # also write the full data to ./inspect-<id>.json
```

Pulls the drive doc, all waypoints (including photo URLs), and the gzipped GeoJSON track from Cloud Storage. Sniffs the gzip magic header so it works regardless of whether Firebase Storage auto-decompressed the response. Output includes head/tail track coordinates, full point count, and the per-waypoint photo URL list — enough to diagnose "track points are missing" reports without firing up the Firebase console.

### 4.6 Distribution

Two paths:

**Option A — Firebase App Distribution** (recommended for closed beta + ongoing internal builds):

```bash
# One-time: create a tester group in Firebase Console → App Distribution → Testers & groups (e.g., "internal")
# Each release:
firebase appdistribution:distribute \
  app/build/outputs/apk/release/app-release.apk \
  --app <FIREBASE_ANDROID_APP_ID> \
  --groups "internal" \
  --release-notes "v0.x — what changed"
```

`FIREBASE_ANDROID_APP_ID` is the long string from Firebase Console → Project settings → Your apps → `com.senikroute` → App ID (`1:902...:android:abc...`).

**Option B — Google Play Console** (public release):

1. Pay the one-time $25 dev fee at https://play.google.com/console.
2. Create the app, set up content rating, data safety, privacy policy URL.
3. Upload the AAB from `./gradlew bundleRelease` to the Internal testing track first.
4. Once stable, promote to Closed → Open → Production tracks.
5. **Use Play App Signing** — Play holds the upload key, Google manages the install key. Less risk of losing the keystore.

---

## 5. CI environment

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs on every push/PR with three parallel jobs:

| Job | What |
|-----|------|
| **android** | `lintDebug` → `testDebugUnitTest` (71 tests) → `assembleDebug` → APK uploaded as artifact |
| **functions** | `npm ci` → `npm run build` → `npm test` (8 Jest tests) |
| **rules** | Sanity-checks the rules files have a valid shape |

CI uses a stub `google-services.json` from `.github/ci/google-services.stub.json` and writes a minimal `local.properties` (no real OAuth client). The Android SDK comes from the runner's pre-set `ANDROID_HOME` env var.

If you want CI to also auto-deploy on `main`, add a `deploy` job that runs after `android` + `functions` succeed, gated by `if: github.ref == 'refs/heads/main'`. You'll need to add `FIREBASE_TOKEN` (from `firebase login:ci`) as a GitHub Actions secret. Not enabled today — do it after you have App Check + budget alerts in place.

---

## 6. Post-deploy verification checklist

After any production deploy, smoke-test:

1. `firebase functions:list` — all 6 functions ACTIVE, region `us-central1`.
2. Open the app fresh (anonymous) → Welcome screen shows 8 featured drives. Tap one → public detail loads.
3. Sign in with Google → lands on Home → record a 30-second drive → save as Public → check Firestore Console for `/drives/{id}` with `visibility: "public"`.
4. Wait ~5s → check `/public_drives_index/<5-char-prefix>/drives/{id}` exists (proves `onDriveWritten` fired).
5. Capture a photo on a waypoint → publish → fetch the photo URL from the waypoint doc → run `exiftool` on it → confirm Make / Model / Software / SerialNumber are empty (proves EXIF stripping is in effect).
6. Move drive to Trash → check `/drives/{id}` has `deletedAt` set → confirm the index entry disappeared.
7. Try posting > 30 comments per minute as one user → confirm extras get deleted (proves `onCommentCreated` rate-limit).

---

## 7. Common failure modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| `serverClientId should not be empty` on sign-in | `GOOGLE_WEB_CLIENT_ID` blank in `local.properties` | Set it from Firebase Console → Auth → Google → Web SDK configuration |
| `WM-WorkerFactory: NoSuchMethodException SyncWorker.<init>` | Missing the manifest provider entry that disables WorkManager's default initializer | Already present in [AndroidManifest.xml](app/src/main/AndroidManifest.xml); regenerate from this repo if missing |
| `java.lang.IllegalStateException: Unable to read Kotlin metadata` | Hilt version too old for Kotlin 2.1+ | Bump Hilt in [libs.versions.toml](gradle/libs.versions.toml) to ≥ 2.53 |
| `Lint found errors in the project; aborting build` from `UnusedMaterial3ScaffoldPaddingParameter` | Scaffold `_` lambda param without a `@SuppressLint` justification | Either consume the padding or `@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")` with a comment explaining why |
| `Could not find rules for the following storage targets: rules` on deploy | Used `--only storage:rules` instead of `--only storage` | `firebase deploy --only storage` (and `firestore:rules` for Firestore — they have different CLI semantics) |
| Featured drives don't appear in Welcome / Explore after seed | Functions weren't deployed yet OR `onDriveWritten` errored | `firebase functions:log --only onDriveWritten` and check; then `npm run seed` again to re-trigger |
| Sign-in works in debug but fails in release APK | Release SHA-1 not registered in Firebase | Print release SHA-1 with `keytool`, add to Firebase Console |
| Public photos still carry EXIF metadata | Photos uploaded before the EXIF fix shipped | Re-upload via the latest app build; old photos remain unscrubbed unless explicitly re-uploaded |

---

## 8. Pre-launch checklist (cross-reference)

See [TODO.md](TODO.md) — sections 🔴 (launch-blockers) and 🟡 (launch-prep) MUST be done before opening to non-test users. Highlights:

- **Enable Firebase App Check** (Play Integrity provider) — biggest production hardening.
- **Set a GCP budget alert** (~$50/mo with email at 50/90/100%) so a runaway can't bankrupt you.
- **Replace MapLibre demo tiles** with a real provider (MapTiler / Stadia / Mapbox / self-hosted) before any non-test traffic — the demo server is rate-limited and leaks viewing region.
- **Document a manual account-deletion process** in your privacy policy until the in-app flow ships.
