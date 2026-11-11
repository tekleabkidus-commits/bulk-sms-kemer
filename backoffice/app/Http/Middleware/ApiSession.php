<?php
namespace App\Http\Middleware;
use Closure;
use Illuminate\Http\Request;
use App\Support\TokenAuth;
use Illuminate\Support\Facades\DB;
class ApiSession {
    public function handle(Request $request, Closure $next) {
        $session=TokenAuth::session($request);
        if (!$session) return response()->json(['message'=>'Unauthorized'],401);
        $request->attributes->set('app_session',$session);
        $request->attributes->set('app_user',DB::table('users')->where('id',$session->user_id)->first());
        return $next($request);
    }
}

