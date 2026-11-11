<?php
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\AppApiController;
use App\Http\Controllers\DeviceApiController;

Route::prefix('v1/app')->middleware('throttle:30,1')->group(function () {
    Route::post('/login', [AppApiController::class, 'login']);
    Route::middleware('api.session')->group(function () {
        Route::get('/me', [AppApiController::class, 'me']);
        Route::post('/logout', [AppApiController::class, 'logout']);
        Route::post('/devices/link', [AppApiController::class, 'linkDevice']);
        Route::post('/devices/unlink', [AppApiController::class, 'unlinkDevice']);
    });
});
Route::prefix('v1/device')->middleware(['device.auth', 'throttle:120,1'])->group(function () {
    Route::post('/heartbeat', [DeviceApiController::class, 'heartbeat']);
    Route::post('/claim', [DeviceApiController::class, 'claim']);
    Route::post('/receipt', [DeviceApiController::class, 'receipt']);
    Route::get('/status', [DeviceApiController::class, 'status']);
});

