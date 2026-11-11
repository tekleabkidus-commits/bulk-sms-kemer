<?php
namespace App\Http\Middleware;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
class DeviceAuth {
    public function handle(Request $request, Closure $next) {
        $token = (string) $request->bearerToken();
        if(strlen($token)!==64 || !ctype_xdigit($token)) return response()->json(['message'=>'Unauthorized'],401);
        $device = DB::table('devices')->where('token_hash',hash('sha256',$token))
            ->where('status','active')->first();
        if (!$device) return response()->json(['message'=>'Device disconnected'],401);
        $request->attributes->set('gateway_device',$device);
        return $next($request);
    }
}

