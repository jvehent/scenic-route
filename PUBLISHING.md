# Publishing Senik to the Google Play Store

A complete walkthrough from "we have a working APK" to "users can install from Play Store." Companion to [INSTALL.md](INSTALL.md) (dev setup) and [TODO.md](TODO.md) (launch checklist).

This is a sequential guide — each section depends on the prior ones being done.

---

## A. Pre-flight checklist

Before you open the Play Console, have these ready:

| Item | Why | Where to get it |
|------|-----|-----------------|
| **Privacy policy hosted at a public HTTPS URL** | Google rejects every app without one | See section F |
| **A real domain for the share-link strategy** | Required for App Links verification + privacy policy hosting | Buy at registrar (~$15/yr) |
| **Firebase App Check enabled** | Production hardening — without it, anyone can hit your backend from a fake client | Firebase Console → App Check (Play Integrity provider) |
| **GCP budget alert** | Without this, a single runaway can run up four-figure bills | GCP Console → Billing → Budgets & alerts ($50/mo with alerts at 50/90/100%) |
| **Real map tile provider** | Already done — using Stadia Maps Stamen Terrain (free up to 200k req/mo, no key) |
| **Release signing keystore** | Required to upload an AAB | Section B below |
| **App icon at 512×512 PNG (no alpha)** | Play Console listing | Export from `app/src/main/res/mipmap-*/ic_launcher_foreground.xml` rasterized, or design new |
| **Feature graphic 1024×500 PNG** | Top of Play Store listing | Design — first impression for installs |
| **Phone screenshots (2–8, 16:9 or 9:16)** | App listing — heavily affects install rate | Capture on a real device |
| **Manual account-deletion process documented** | Google now enforces in-app or "easy" account deletion | Add a Settings entry that emails support, OR build the in-app flow first (TODO.md) |

---

## B. Release signing setup (one-time)

### B.1 Generate the release keystore

```bash
mkdir -p ~/keystores
keytool -genkeypair -v \
  -keystore ~/keystores/senik-release.jks \
  -alias senik-key \
  -keyalg RSA -keysize 2048 -validity 10000
```

Use strong passwords. **Save them in your password manager immediately** — losing this keystore means you can never update the app under the same identity.

### B.2 Register the release fingerprints in Firebase

> ⚠️ **You must register two different keys' fingerprints, not one.** When you upload an AAB to Google Play, Google re-signs the APKs delivered to user phones with their **App Signing key**, which is different from your local **upload key**. Firebase needs both fingerprints registered or Google Sign-In will fail with `"No credentials available"` for users who installed from the Play Store. Forgetting the App Signing fingerprint is the #1 reason new releases ship with broken sign-in.

#### B.2.a Upload key — for sideloaded testing builds

This is the keystore you generated in B.1 and use for `bundleRelease` / `assembleRelease`:

```bash
keytool -list -v -keystore ~/keystores/senik-release.jks -alias senik-key | grep -E 'SHA1|SHA256'
```

Firebase Console → Project settings → Your apps → `com.senikroute` → **Add fingerprint**, twice (once for the SHA-1 line, once for the SHA-256 line).

This unlocks Google Sign-In for builds you `adb install` directly from the AAB / APK output, or distribute via Firebase App Distribution.

#### B.2.b App Signing key — for Play-Store-installed builds

After your first AAB upload, Google Play creates an **App Signing key** that lives only in Play. Every APK Google delivers from the Play Store is signed with this key, not your upload key, so Firebase has to know about it separately.

1. **Google Play Console** → your app → **Test and release** → **App integrity** → **App signing**.
2. Under "App signing key certificate", copy the **SHA-1** and **SHA-256** values (NOT the ones under "Upload key certificate" — those duplicate B.2.a).
3. Firebase Console → same `com.senikroute` app → **Add fingerprint**, twice.

You don't need to re-upload anything to Play. Once these fingerprints land in Firebase (~30 s of propagation), users who installed from Play can sign in.

#### B.2.c Debug key — for `assembleDebug` builds

For local development against the `com.senikroute.debug` Firebase app:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
  -storepass android -keypass android | grep -E 'SHA1|SHA256'
```

Register against the `com.senikroute.debug` app in Firebase, NOT `com.senikroute`.

#### Final tally

You should end up with at minimum:
- 2 fingerprints (SHA-1 + SHA-256) on `com.senikroute.debug` (debug key)
- 4 fingerprints on `com.senikroute` (2 from upload key + 2 from Play App Signing key)

After adding, **re-download `google-services.json`** from the Firebase Console and replace `app/google-services.json`. (Fingerprints aren't actually written into that file — they live on Firebase's servers — but re-downloading keeps the file in sync if any other config changed.)

#### Diagnosing "No credentials available" later

If a user reports `"No credentials available"` on sign-in, the most likely cause is a missing fingerprint registration. To confirm whether it's the upload-key or App-Signing-key fingerprint that's missing, ask them to run (or run on the same install yourself):

```bash
adb shell dumpsys package com.senikroute | grep -E 'signatures|versionName'
```

Compare the cert signature against the SHA-256 lines from `keytool` (upload key) and the Play Console (App Signing key). Whichever one matches the installed APK, that fingerprint needs to be in Firebase.

### B.3 Wire signing into Gradle

Create `keystore.properties` at the repo root (already gitignored):

```properties
# Replace ${HOME} below with your actual absolute path — Gradle does NOT
# expand shell vars or ~ here. e.g. /home/julien/keystores/senik-release.jks
storeFile=${HOME}/keystores/senik-release.jks
storePassword=<store-password>
keyAlias=senik-key
keyPassword=<key-password>
```

> ⚠️ The placeholders (`${HOME}`, `<store-password>`, `<key-password>`) are **literal text** that you must hand-edit. Gradle reads this file as a plain Java `.properties` file with no variable expansion — leaving any placeholder in place will fail with `Keystore file '...' not found for signing config 'release'`.

Add this block to [app/build.gradle.kts](app/build.gradle.kts) (above the `android { ... }` block):

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
```

Inside `android { ... }`, add a `signingConfigs` block:

```kotlin
signingConfigs {
    create("release") {
        if (keystorePropsFile.exists()) {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
}
```

And inside `buildTypes.release { ... }`, add:

```kotlin
signingConfig = signingConfigs.getByName("release")
```

Verify by building:

```bash
./gradlew bundleRelease
# AAB ends up at: app/build/outputs/bundle/release/app-release.aab
```

If `bundleRelease` fails with "keystore not found" or "wrong password," fix `keystore.properties` and retry.

### B.4 Sanity-test the release build on your phone

Before uploading, install the release APK on your phone to make sure Google Sign-In actually works with the registered SHA-1:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.senikroute/com.senikroute.MainActivity
# Sign in. If it errors with "developer error", the SHA-1 isn't registered correctly.
```

Note the package name is `com.senikroute` (not `.debug`) for release builds.

---

## C. Build the upload artifact

Play Store requires **AAB** (Android App Bundle), not APK, for new apps:

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

This single AAB will be uploaded to Play Console, and Google generates the per-device APKs from it.

---

## D. Create the Play Console developer account

1. Go to https://play.google.com/console.
2. Sign in with the Google account you want to own this app permanently. **This account is tied to the app forever** — pick a long-lived shared account, not a personal one if you might leave the project.
3. Pay the **one-time $25 registration fee**.
4. Choose **Personal** or **Organization** account type. For "I'm a single dev shipping this," Personal is fine. For "we may add team members or have a company," Organization (requires DUNS number).
5. Verify your identity (Google asks for photo ID + bank info for payouts; payouts unused if app is free, but they still ask).
6. Wait — first-time accounts can take a few days to approve. Plan for this.

---

## E. Create the app in Play Console

In the Console:

1. **All apps → Create app**.
2. Fill out:
   - **App name**: `Senik`
   - **Default language**: English (or your primary)
   - **App or game**: App
   - **Free or paid**: Free (paid is a one-way flip)
   - Tick the declarations (developer policies, US export laws)
3. **Create app**.

You're now in the Console for your app, with a long left sidebar of required tasks. Go through them top-down.

---

## F. Privacy policy

Required. Google rejects apps without one.

For our app, the policy must cover:
- **What data we collect**: email (via Google Sign-In), display name, profile photo URL, drives recorded (GPS traces, photos, notes), comments posted, device IDs (Firebase Crashlytics if enabled).
- **Where it's stored**: Firebase Firestore + Cloud Storage in `<region>` (your project's region).
- **How long**: drives kept until user deletes; trash auto-purges after 30 days; profile + comments persist until account deletion.
- **Third parties**: Google Firebase (Auth, Firestore, Storage, Cloud Functions, FCM), Google Play Services, Stadia Maps (map tile rendering — sees your approximate viewing region).
- **User rights**: how to delete account, request data export, contact for issues.
- **Children**: app is not directed at children under 13 (assuming COPPA compliance is N/A; otherwise specify).

Quick path to host one:
1. Use a free privacy-policy generator (e.g., https://app-privacy-policy-generator.firebaseapp.com or https://www.iubenda.com).
2. Customize it for the data points above.
3. Host on a public URL — GitHub Pages, Netlify, Vercel free tier all work. The simplest is a single `index.html` deployed to a `senikroute.com/privacy` path.
4. Paste the URL into Play Console → App content → Privacy policy.

**Don't link to a Notion page or Google Doc** — Google sometimes rejects those.

---

## G. App content (left sidebar tasks)

### G.1 App access

If parts of the app require sign-in (which ours do for recording), check **All or some functionality is restricted**, then provide test credentials Google can use to log in. Create a dummy Google account just for Play Store reviewers; share its email + password.

### G.2 Ads

We have none → **No, my app does not contain ads**.

### G.3 Content rating

Fill the questionnaire. For Senik the answers are mostly "No" — no violence, no sexual content, no profanity, no gambling, no drugs. The result will be **Everyone / PEGI 3 / IARC equivalent**.

### G.4 Target audience and content

- **Age groups**: 13+ (or 18+ if you don't want any minor-data-handling complications). Apps targeting under-13 trigger Google Designed for Families program with strict requirements — avoid unless you specifically want it.
- **Are children a target audience?**: No.

### G.5 News app

No.

### G.6 COVID-19 contact tracing

No.

### G.7 Government app

No.

### G.8 Data safety

This is the longest form. You must declare every data type collected. For Senik:

| Data type | Collected? | Shared with 3rd parties? | Optional? | Purpose |
|-----------|-----------|--------------------------|-----------|---------|
| Email address | Yes | No (only Google for auth) | No | Account creation |
| Name | Yes | No | Yes (display name editable) | Account / profile display |
| User photos (avatar) | Yes | No | Yes | Profile display |
| Photos taken in-app | Yes | No | Yes | Drive waypoints |
| Approximate location | Yes | No | No | Required for recording |
| Precise location | Yes | No | No | Required for recording GPS traces |
| App interactions | Yes (Firebase Analytics if enabled) | Yes (Google Analytics) | Yes | App functionality + analytics |
| Crash logs | Yes (if Crashlytics enabled) | Yes (Firebase) | Yes | Diagnostics |

Also declare:
- Data is encrypted in transit (HTTPS): **Yes**.
- Users can request data deletion: **Yes** (manual via support email until in-app delete-account ships).

### G.9 Government/Financial features

N/A.

### G.10 Health Connect

N/A — we don't read Health Connect.

---

## H. Store presence (left sidebar)

### H.1 Main store listing

- **App name**: `Senik`
- **Short description (80 chars max)**: e.g. *"Discover scenic drives around the world. Capture your road, share the view."*
- **Full description (4000 chars max)**: Describe the app in detail. Include the origin story (Costa Rica drive), key features (record, annotate, share, explore), platforms supported (Android first; iOS planned).
- **App icon**: 512×512 PNG, no alpha channel.
- **Feature graphic**: 1024×500 PNG. Shown at the top of your Play Store listing — high-impact image (a great drive photo + the app name).
- **Phone screenshots**: 2–8 required. Capture on a real device. Show the Welcome screen, Explore with featured drives, Recording in progress, Drive detail with map + waypoints + photos, Profile.
- **Tablet screenshots (optional)**: Skip for v1 unless you want tablet listing.
- **App category**: Travel & Local.
- **Tags**: travel, navigation, gps, journal, road-trip.
- **Email + website + phone**: Email required; website + phone optional.

### H.2 Translations

Skip for v1; add later.

### H.3 Custom store listings

Skip for v1.

### H.4 Promotional content / In-app events

Skip.

---

## I. Releases — track strategy

Play Console releases ladder up through tracks:

1. **Internal testing** — up to 100 testers, instant. Use this for your own QA. **Start here.**
2. **Closed testing** — invite-only, larger group. Review takes ~1 day.
3. **Open testing** — anyone can opt-in. Public beta program.
4. **Production** — live on the Play Store. First production review takes 1–7 days. Subsequent updates: hours.

### I.1 Internal testing

1. **Testing → Internal testing → Create new release**.
2. Choose **Use Play App Signing** (recommended — Google holds the master key, you keep an upload key).
3. Upload `app/build/outputs/bundle/release/app-release.aab`.
4. **Release name**: e.g. `0.1.0 (1)` (matches the versionName + versionCode in app/build.gradle.kts).
5. **Release notes**: short description of what's in this release.
6. **Save → Review release**. Fix any policy errors flagged.
7. **Start rollout to Internal testing**.
8. **Testers tab → Email list → Add 100 emails** (your own + a few real users).
9. Each tester opens the **opt-in URL** (provided in the Console) on their Android device, then can install via Play Store.

### I.2 Closed → Open → Production

When internal testing is solid:

1. **Promote a release** from Internal → Closed → Open → Production via the Console UI (no rebuild needed; the AAB is reused).
2. The first **production submission** triggers a Play review (1–7 days). After it's approved, you set a **rollout percentage** (start at 5–20% to catch crashes; ramp to 100% once Vitals look clean).
3. Subsequent updates can be reviewed in hours, not days.

---

## J. Required pre-launch report

Play automatically runs your AAB on a few real devices and gathers:
- Crashes
- ANRs (App Not Responding)
- Accessibility issues
- Security warnings

**Always check the pre-launch report after each upload.** Crashes here will block production approval.

---

## K. Versioning conventions

In [app/build.gradle.kts](app/build.gradle.kts):

```kotlin
defaultConfig {
    versionCode = 1     // monotonically increasing integer; bump every Play upload
    versionName = "0.1.0"   // human-readable semver; reset between releases as you like
}
```

Each upload to Play **must** have a higher `versionCode` than any previous build, including ones you didn't release. Conventions:

- Patch fix: `versionName "0.1.1"`, `versionCode 2`
- Minor feature: `versionName "0.2.0"`, `versionCode 3`
- Major release: `versionName "1.0.0"`, `versionCode 10`

Skip numbers liberally; the only constraint is monotonic.

---

## L. Common rejection reasons (and how to avoid them)

| Reason | Fix |
|--------|-----|
| Missing privacy policy URL | Provide a public HTTPS URL with the actual policy text |
| Permissions not justified | The Play Console prompts you to justify foreground location and background location separately. Have a clear written explanation ready ("background location used to record drives while phone is locked") |
| Account deletion not provided | Add either an in-app delete-account flow OR a clearly described support email path; document both in your privacy policy |
| Data safety inconsistencies | Make sure the manifest permissions match what you declare in Data Safety. Don't claim you don't collect location while requesting `ACCESS_FINE_LOCATION` |
| Background location use | Google heavily scrutinizes apps requesting background location. You'll likely need to record a "prominent disclosure" video showing how the app discloses it before requesting. Be prepared for back-and-forth |
| App crashes on review device | Check the pre-launch report and fix before submitting |

---

## M. After launch

- **Watch Play Console → Quality → Android vitals** weekly. Crash rate >2% triggers Play warnings; >8% can lead to delisting.
- **Read every review** the first month. Respond to negative reviews via Console; reviewers + future installers see your responses.
- **Bumps + updates**: increment `versionCode` and rebuild. The release flow re-uses the same Play Console app entry forever.
- **iOS**: when you ship iOS, both stores can share the same Firebase backend (already designed for this in §16 of DESIGN.md). The Play app and App Store app become two clients of the same service.

---

## N. The realistic timeline

- Account approval: 1–3 business days
- First production review: 3–7 business days
- Privacy policy hosting + drafting: 1 day
- Screenshots + assets prep: 1 day  
- Iterating on content rating / data safety / rejection reasons: 1–7 days

**Plan two weeks** between deciding to publish and "live in the store," much of it spent waiting on Google.

---

## O. The shortest-path version

If you want to ship the minimum viable thing and iterate publicly:

1. Generate keystore + register SHA-1 (Section B).
2. Build AAB (Section C).
3. Pay $25, create developer account (Section D).
4. Create app in Console (Section E).
5. Host a minimal privacy policy on `senikroute.com/privacy` (Section F).
6. Fill the **required** Console tasks; defer translations / promo / open testing.
7. Upload the AAB to **Internal testing** track.
8. Add yourself + 5 friends as testers; live test for a week.
9. Promote to **Production** when you're confident; start rollout at 20%.

That's the v1 path — done in a focused weekend if all the prep is ready.
