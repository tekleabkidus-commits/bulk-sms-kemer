# Bulk SMS Kemer — Laravel Backoffice (development branch)

The Android app continues to send SMS using its physical SIM. The Laravel application
manages personal/company workspaces, employees, groups, templates, remote campaigns,
device authorization, and reports.

## Setup (PHP >=8.2, Composer, SQLite or MySQL)

1. Deploy the `backoffice/` directory as a Laravel application. Point the web root at `backoffice/public` **only**.
2. Run `composer install --no-dev --optimize-autoloader` within `backoffice/`.
3. Copy `.env.example` to `.env`; set `APP_URL` to your valid HTTPS domain and configure database credentials.
4. Run `php artisan key:generate`, `php artisan migrate --force`, `php artisan optimize`.
5. Ensure `storage` and `bootstrap/cache` directories are writable by PHP.
6. Visit `/register` to create an individual account. Create a company and add already-registered staff in **Accounts & teams**.
7. Build/install the APK (GitHub Actions). On the Android **Account** tab, enter the HTTPS URL and log in. Select personal/company workspace and SIM; link the device, then tap **Start gateway**.
8. In the backoffice, create a group, import opted-in contacts from a CSV, add a template, then queue a campaign assigned to that device.

### SMS status model
- `queued`: not handed to any Android device.
- `leased`: device claimed job, not yet acknowledged.
- `submitted`: Android SMS sent callback reported success; **not confirmed delivered**.
- `delivered`: confirmed by carrier/device delivery receipt.
- `failed`: radio/send callback confirmed failure, or synchronous dispatch error.
- `unknown`: callback missing or some multipart fragments possibly sent; **never automatically retried**.

Run `php artisan sms:mark-expired` from a scheduler (e.g. every minute) to reconcile missing callbacks. Claims also mark expired jobs on that device as unknown. Reports are available at `/reports/export`.

### Credentials and account separation
- Android app login token lasts 30 days, is revocable using logout and is AES-GCM encrypted in Android Keystore.
- Every linked phone gets a separate random device credential. The server stores only a SHA-256 hash.
- Device unlink/revocation immediately blocks further API calls.
- App/device API routes verify tenant membership or that a device belongs to its workspace.
- A user can belong to multiple workspaces; campaign and contact data are workspace-scoped.

### Important constraints and safety
- Android 15 and newer restrict `dataSync` foreground services and restart after reboot. **Do not promise 24/7 unattended operation or zero SMS failure.**
- The remote gateway is manually started by a signed-in user. If stopped/offline, the web queue remains pending.
- Never run a local campaign and remote gateway simultaneously. The app includes a safeguard for this.
- SMS content, recipients and delivery reports contain personal data. Access is restricted by workspace membership; use HTTPS, strong passwords, backups and narrow staff permissions.
- Campaign import currently supports **CSV**, not XLSX. Local Android file import remains unchanged.
- Server-sent messages use the physically selected Android SIM. Throttling is conservative (one in-flight claim per client).
- Production deployments need runtime tests on each Android version/carrier and legal compliance checks for consent and messaging policies.

### Not implemented in this first version
- Automatic XLSX parsing in the Laravel backoffice, staff invitations by email, interactive per-player edit/history UI,
  device load-balancing across multiple phones, server-side campaigns synced into the local Android Room screens,
  push wake-up notifications, configurable high-throughput adaptive throttling, and a guaranteed 24/7 sender.

These should be reviewed and implemented before treating this as a production-scale messaging service.
