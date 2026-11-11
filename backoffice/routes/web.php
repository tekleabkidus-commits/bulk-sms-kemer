<?php
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\BackofficeController as Office;

Route::get('/login',[Office::class,'loginPage'])->name('login');
Route::post('/login',[Office::class,'login'])->middleware('throttle:10,1');
Route::get('/register',[Office::class,'registerPage']);
Route::post('/register',[Office::class,'register'])->middleware('throttle:5,1');
Route::middleware('auth')->group(function () {
    Route::post('/logout',[Office::class,'logout']);
    Route::get('/',[Office::class,'dashboard']);
    Route::post('/workspaces',[Office::class,'createWorkspace']);
    Route::post('/workspaces/switch',[Office::class,'switchWorkspace']);
    Route::post('/members',[Office::class,'addMember']);
    Route::post('/groups',[Office::class,'createGroup']);
    Route::post('/groups/{id}/import',[Office::class,'importCsv']);
    Route::post('/contacts/{id}/opt-out',[Office::class,'optOut']);
    Route::post('/templates',[Office::class,'createTemplate']);
    Route::post('/campaigns',[Office::class,'createCampaign']);
    Route::post('/campaigns/{id}/action',[Office::class,'campaignAction']);
    Route::post('/devices/{id}/revoke',[Office::class,'revokeDevice']);
    Route::get('/reports/export',[Office::class,'export']);
});

