# Bulk SMS Kemer

Personal Android app for sending bulk SMS through your own SIM. Built for the Ethiopian
market — supports Ethio Telecom (`+2519…`) and Safaricom Ethiopia (`+2517…`) numbers, with
the carrier inferred from the number prefix.

This is a sideload-only app. It is not on Google Play (the policy disallows general
bulk-SMS apps with the `SEND_SMS` permission).

---

## What it does

- Import phone numbers from a `.xlsx` or `.csv` file (single column of numbers, header auto-detected).
- Save messages as reusable templates.
- Pick a contact group, a message, a SIM, and a send rate. Confirm. Tap Start.
- Send 10+ hours autonomously, in the background, surviving phone reboot and app updates.
- Live progress with pause / resume / cancel.
- Per-campaign report with paged recipients and `.xlsx` / `.csv` export.
- Cross-campaign "Sent messages" tab with date / status / campaign / SIM / phone-number filters.

The same message body is sent to every recipient in the selected group. There are no merge
fields — by design.

---

## How to push to a private GitHub repo and get an APK

You don't need Android Studio. The repo includes a GitHub Actions workflow that builds an
APK in the cloud every time you push.

### One-time setup

1. **Install Git** if you don't have it. On Windows:
   [git-scm.com](https://git-scm.com/download/win). On Mac/Linux it's likely already installed.
2. **Create a private repo on GitHub.** Go to <https://github.com/new>, name it `bulk-sms-kemer`,
   set it to **Private**, do NOT add a README or `.gitignore` (the zip already has them).
3. **Authenticate.** Easiest path: install the GitHub CLI from <https://cli.github.com> and run
   `gh auth login`. Or use a Personal Access Token (Settings → Developer Settings → Tokens →
   Fine-grained tokens, scoped to your new repo with `Contents: write` permission).

### Push the code

Unzip the project, then in a terminal:

```bash
cd bulk-sms-kemer
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin git@github.com:YOUR_USERNAME/bulk-sms-kemer.git
git push -u origin main
```

(If you used HTTPS instead of SSH, the remote URL is
`https://github.com/YOUR_USERNAME/bulk-sms-kemer.git`.)

### Get the APK

1. Go to your repo on github.com → **Actions** tab.
2. The "Build APK" workflow should be running (or queued). It takes 4-6 minutes the first time.
3. When it's green, click into the run, scroll down to **Artifacts**, and download
   `bulk-sms-kemer-debug.zip`. Inside is `app-debug.apk`.
4. Transfer the APK to your phone (USB, email to yourself, Google Drive, etc.).
5. On the phone, tap the APK. Android will warn about installing from an unknown source —
   allow it for your file manager / email app, then install.

---

## Reliability checklist (do this once, per phone)

Long campaigns get killed by aggressive battery management on most Android skins. The app
shows the relevant checklist for your phone on first launch, but here's the master list:

**Samsung (One UI)**
- Settings → Apps → Bulk SMS Kemer → Battery → **Unrestricted**
- Settings → Battery and device care → Background usage limits → **Never sleeping apps** → add Bulk SMS Kemer
- Settings → Battery and device care → More battery settings → **Adaptive battery → Off**

**Xiaomi / Redmi / Poco (MIUI / HyperOS)**
- Settings → Apps → Manage apps → Bulk SMS Kemer → **Autostart → On**
- Settings → Apps → Manage apps → Bulk SMS Kemer → **Battery saver → No restrictions**
- Recents view → long-press the app card → tap the **lock icon**

**Oppo / Realme (ColorOS) / Vivo / iQOO**
- Settings → Battery → **App battery management** → Bulk SMS Kemer → **Allow background activity**
- Settings → App management → **Auto-launch** → Bulk SMS Kemer → On

**OnePlus**
- Settings → Battery → **Battery optimization** → Bulk SMS Kemer → **Don't optimize**
- Settings → Apps → Bulk SMS Kemer → Battery → **Background battery usage → Allow**

**Huawei / Honor (EMUI / MagicOS)**
- Settings → Apps → Bulk SMS Kemer → Battery → **App launch → Manage manually** → enable
  Auto-launch, Secondary launch, **Run in background**

The app will not let you start a campaign until you've ticked the "I've completed the
reliability steps" box, because without these settings sending will stop after a few hours.

---

## Important warnings

- **The carrier may rate-limit or block your SIM** if you send too fast. The app's lowest
  throttle is 0.5 sec/SMS, but for sustained sending 3 seconds or more is safer. The app
  warns you when you go below 1 sec/SMS.

- **Force-stopping the app is fatal.** Closing the app from Recents is fine. But if you go
  into Settings → Apps → Bulk SMS Kemer → **Force stop**, broadcast receivers stop firing
  on most OEM ROMs, and the campaign won't auto-resume. Don't do that.

- **Multi-segment messages cost more.** Amharic and any non-Latin character forces UCS-2
  encoding, which means 70 characters per segment instead of 160. The new-campaign screen
  shows the segment count before you start. Verify your SIM's balance is enough to cover
  `recipients × segments` SMS units.

- **No personalization.** This app does not do `{Name}` merge fields. The same message body
  is sent to every recipient in the group. If you need per-recipient personalization, this
  app is not the right tool.

- **Test with a small group first.** Create a contact group with 5–10 of your own numbers,
  send a test campaign, verify delivery, then move to the real list.

- **Compliance is your responsibility.** Recipients should have consented to receive these
  messages. The confirmation dialog requires you to tick a consent checkbox before each
  campaign — that's a reminder, not a legal opinion.

---

## What's in the box

- `app/` — the Android app source (Kotlin + Jetpack Compose, minSdk 24 / Android 7+)
- `samples/recipients_sample.csv` — example phone list for testing imports
- `.github/workflows/build.yml` — the Actions workflow that builds the APK
- `gradle/`, `build.gradle.kts`, `settings.gradle.kts` — Gradle config

Tech: Kotlin 2.0, Jetpack Compose, Room (SQLite), Apache POI for `.xlsx`, foreground service
with `WAKE_LOCK`, `AlarmManager` heartbeat, boot + package-replaced receivers.

---

## Troubleshooting

**The Actions build fails on the first push.**
Open the failed run, look at the log. Most common causes: forgot to push the wrapper
properties file, or the runner's Android SDK didn't accept licenses. Re-run from the Actions
UI; it usually passes the second time.

**APK installs but immediately crashes.**
Open Logcat (Android Studio, or `adb logcat`) right after the crash and look for the line
with `bulksms.test.mark`. Most common cause: missing runtime permission on first run — the
permission screen should handle this, but if it doesn't, tap into Settings → Apps → Bulk
SMS Kemer → Permissions and grant SMS, Phone, and Notifications manually.

**Campaign stops after a few hours.**
Almost always battery management. Re-do the reliability checklist; some OEMs reset the
"unrestricted" setting after an OS update.

**Numbers aren't being accepted from my Excel file.**
Open the file in Google Sheets and check the column. The app accepts only Ethiopian mobile
numbers in any of these forms: `+2519XXXXXXXX`, `+2517XXXXXXXX`, `2519...`, `2517...`,
`09XXXXXXXX`, `07XXXXXXXX`, or bare 9 digits starting with `9`/`7`. Anything else (landlines,
foreign numbers, formatting noise that destroys the digits) is silently dropped. The import
preview shows the count of invalid + duplicate rows so you know what got skipped.

---

## Development (optional)

If you want to open the project in Android Studio:

1. Install Android Studio Hedgehog (2023.1.1) or newer.
2. **File → Open** → pick the `bulk-sms-kemer` directory.
3. Let Gradle sync. The IDE will generate `gradle-wrapper.jar` automatically the first time.
4. Plug in a phone with USB debugging on, or start an emulator with API 24+.
5. Click Run.

To run unit tests: `./gradlew testDebugUnitTest`. They cover phone normalization (the strict
+2519/+2517 gate) and SMS segment counting (GSM-7 vs UCS-2 boundaries).
