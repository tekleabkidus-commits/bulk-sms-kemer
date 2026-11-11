<?php
use Illuminate\Support\Facades\Artisan;
Artisan::command('sms:mark-expired', function () {
    $n = \Illuminate\Support\Facades\DB::table('campaign_recipients')
        ->where('status', 'leased')->where('lease_expires_at', '<', now())
        ->update(['status' => 'unknown', 'updated_at' => now()]);
    $this->info("Marked {$n} expired unacknowledged submissions as unknown (not retried).");
})->purpose('Safely reconcile expired SMS leases without risking duplicates');

