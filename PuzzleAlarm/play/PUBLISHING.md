# Publishing Puzzle Alarm on Google Play (Ishtah account)

App ID: `com.ishtah.puzzlealarm` (permanent once the first build is uploaded).
The **Puzzle Alarm release** workflow builds the signed bundle and, once API access is set up,
uploads it to the internal testing track as a draft.

## 1. Create the upload key (once, on your computer)

```bash
keytool -genkeypair -v -keystore puzzle-alarm-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 puzzle-alarm-upload.jks > puzzle-alarm-upload.jks.b64   # macOS: base64 -i puzzle-alarm-upload.jks
```

Keep the `.jks` file and both passwords somewhere safe (a password manager). With Play App
Signing, a lost upload key can be reset through Play support, but it takes days.

## 2. Add repository secrets

simha1982/Personal → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | contents of `puzzle-alarm-upload.jks.b64` |
| `UPLOAD_KEYSTORE_PASSWORD` | keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | key password (same as keystore password if you pressed Enter) |

Never paste these in chat.

## 3. Create the app in Play Console

1. Play Console (Ishtah developer account) → **Create app**: name "Puzzle Alarm", app, free.
2. Fill **Main store listing** from [listing.md](listing.md) and upload the graphics.
3. Host [privacy-policy.md](privacy-policy.md) at a public URL (for example a page on
   ishtahsoft.com) and enter it under **App content → Privacy policy**.
4. Complete the rest of **App content** using [data-safety.md](data-safety.md): data safety, ads,
   app access, target audience, content rating, and the exact alarm, full-screen intent and
   foreground service declarations.

## 4. First upload (manual, once)

Play's API can't create the very first release of a new app, so the first bundle goes in by hand:

1. Actions → **Puzzle Alarm release** → Run workflow (branch with the app).
2. When it finishes, download the `PuzzleAlarm-release` artifact and unzip it.
3. Play Console → Testing → **Internal testing** → Create release → upload `app-release.aab`.
   Accept Play App Signing when asked. Add yourself as a tester and roll out.

## 5. Automatic uploads after that

1. Google Cloud console, project `smarttrading-278207` (or a new one): enable the
   **Google Play Android Developer API**.
2. Play Console → Users and permissions → **Invite new user** →
   the service account email → app permission for Puzzle Alarm with "Release to testing tracks"
   (and "Release to production" if you want CI to promote).
3. Create a JSON key for that service account and add it as the `PLAY_SERVICE_ACCOUNT_JSON`
   repository secret.

From then on, running the workflow (or pushing a tag like `puzzle-alarm-v1.1`) uploads a draft to
internal testing. Promote it to closed/open testing or production in Play Console.

## 6. Production access

New personal developer accounts must run a **closed test with at least 12 testers for 14 days**
before production. Organization accounts (like a company account) are exempt. Check under
Dashboard which applies to the Ishtah account.
