# MIRA private Google Play release checklist

This project is a pre-release source package. It does **not** contain a signing key, a Google OAuth client secret, or private credentials. Do not publish until the signed AAB builds and the device regression checklist passes.

## 1. Confirm the final Android application ID

The current ID is `com.naveensmk.mira`, selected from the repository owner name. Confirm it before creating the Play Console app: the application ID is the permanent Android/Play identity and cannot be changed after publishing. The prior development builds used `com.example.voicefirst`; they are a different app identity and cannot be updated in place with this package. Uninstall old development builds before installing this identity.

## 2. Create and protect an upload key

Create the upload keystore on a trusted machine. Keep it out of the repository, source ZIP, email, and issue tracker. Store a protected offline backup.

```bash
keytool -genkeypair -v \
  -keystore mira-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias mira-upload
```

For a local build, export these environment variables in the same shell (never hard-code values in Gradle):

```bash
export MIRA_KEYSTORE_PATH=/absolute/path/to/mira-upload-key.jks
export MIRA_KEYSTORE_PASSWORD='your-keystore-password'
export MIRA_KEY_ALIAS='mira-upload'
export MIRA_KEY_PASSWORD='your-key-password'
./gradlew testDebugUnitTest lintRelease bundleRelease
```

The Gradle release tasks deliberately fail if these variables are missing, so an unsigned release bundle is not mistaken for a publishable build. Debug builds do not need the upload key.

To calculate the upload certificate fingerprint: 

```bash
keytool -list -v -keystore mira-upload-key.jks -alias mira-upload
```

Use Google Play App Signing when configuring the Play Console. After Play creates the app-signing certificate, retrieve its SHA-1 fingerprint from **Play Console → App integrity**. Keep the upload certificate and Play app-signing certificate distinct.

## 3. GitHub Actions private release workflow

A manual workflow at `.github/workflows/private-play-release.yml` builds and uploads an Android App Bundle. Add these repository Actions secrets before running it:

- `MIRA_UPLOAD_KEYSTORE_BASE64`: the base64 contents of the upload keystore file.
- `MIRA_KEYSTORE_PASSWORD`: keystore password.
- `MIRA_KEY_ALIAS`: upload-key alias.
- `MIRA_KEY_PASSWORD`: upload-key password.

Example to encode a file locally: `base64 -w 0 mira-upload-key.jks` (on macOS use `base64 mira-upload-key.jks`). Never paste the decoded key contents in logs. Rotate any key immediately if a secret is exposed.

## 4. Configure Gmail OAuth for the final identity

Gmail authorization is controlled by the publisher's Google Cloud project and cannot be repaired from source code alone. In the Cloud project: enable Gmail API, configure the OAuth consent/branding and authorized test users, then create an Android OAuth client using the final package ID and the signing-certificate SHA-1 for the build being installed. For Google Play-installed private builds, ensure the Play App Signing certificate fingerprint is included where appropriate. Test on an internal/private Play track with the exact artifact.

MIRA requests Gmail read-only and compose scopes for unread summaries and draft creation. It does not send or delete email. Don't add scopes without a product reason and matching user disclosure. Do not embed a web-client secret in Android.

## 5. Target API and private distribution

The project targets Android 16 / API 36 and uses Android Gradle Plugin 8.10.1, which supports API 36. As of August 31, 2026, new mobile apps and updates submitted to Google Play generally must target API 36 or higher. Google documents a narrow exception for apps that are permanently private, restricted to a specific organization, and intended only for internal distribution; do not assume an internal-testing track alone qualifies. See [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878).

## 6. Private Play publication gates

- Build a signed `.aab` and verify the upload certificate before upload.
- Run unit tests and Android Lint. Install the Play-delivered build and test upgrades.
- Finish the Data safety form using observed behavior, not assumptions.
- Publish an accessible privacy-policy URL that describes on-device model processing, contacts and explicit-call permission behavior, microphone and speech-service behavior (which may be online), Gmail scopes, optional accessibility behavior, retention, and deletion controls. The draft in `PRIVACY.md` is not automatically a publishable policy.
- Complete Play Console declarations and review requirements for assistant/accessibility capabilities. Only request permissions necessary to disclosed features; never use accessibility to bypass Android security checkpoints.
- Test on the actual target phone: 10 consecutive English requests, 10 Tamil requests, 10 repeated mic sessions, repeated browser/YouTube searches, contact no/one/multiple-match paths, Gmail authorization/read/draft, model restart restoration, low-storage/model-failure handling, and app upgrade from the prior private track.
- Check Play Protect warnings with the signed, Play-distributed build. The source cannot guarantee a warning-free result; use Google's review/remediation process if a warning remains.

## 7. Model storage lifecycle

The model file is not shipped inside the APK/AAB. Import the compatible `.litertlm` bundle once through Settings. MIRA copies it into private app storage and remembers its file name; it can restore that file after a fresh process starts. Android still has to initialize native model weights into memory on a cold process launch. Updating the bundle is done from Settings and only replaces the old bundle after the candidate model initializes successfully.

## 8. Status of this archive

The source safety audits are expected to run in CI. This preparation environment did not have an Android SDK or Gradle installation/wrapper available, so it cannot certify that the complete Android app compiles. No keystore, real OAuth SHA-1, Play Console configuration, or Play Protect review has been performed. Treat all those items as release gates.
