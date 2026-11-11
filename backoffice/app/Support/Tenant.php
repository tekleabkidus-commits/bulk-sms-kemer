<?php
namespace App\Support;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Auth;
use Illuminate\Http\Exceptions\HttpResponseException;
class Tenant {
    public static function current(): object {
        $id = session('workspace_id');
        $membership = DB::table('memberships')->join('workspaces','workspaces.id','=','memberships.workspace_id')
            ->where('memberships.user_id', Auth::id())->where('memberships.workspace_id', $id)
            ->select('workspaces.*','memberships.role')->first();
        abort_unless($membership, 403, 'Select a workspace.');
        return $membership;
    }
    public static function requireWrite(object $workspace): void {
        abort_unless(in_array($workspace->role, ['owner','admin','manager','operator']),403);
    }
    public static function requireAdmin(object $workspace): void {
        abort_unless(in_array($workspace->role, ['owner','admin']),403);
    }
    public static function roleFor(int $userId, int $workspaceId): ?string {
        return DB::table('memberships')->where('user_id',$userId)->where('workspace_id',$workspaceId)->value('role');
    }
    public static function log(object $workspace, string $action, ?int $subjectId=null): void {
        DB::table('audit_logs')->insert(['workspace_id'=>$workspace->id,'user_id'=>Auth::id(),'action'=>$action,'subject_id'=>$subjectId,'created_at'=>now()]);
    }
}

