<?php
namespace App\Support;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
class TokenAuth {
    public static function token(Request $request): string { return (string) $request->bearerToken(); }
    public static function session(Request $request): ?object {
        $token = self::token($request);
        if (strlen($token) !== 64 || !ctype_xdigit($token)) return null;
        return DB::table('app_sessions')->where('token_hash',hash('sha256',$token))
            ->where('expires_at','>',now())->first();
    }
}

