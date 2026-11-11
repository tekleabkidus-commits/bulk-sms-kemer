<?php
namespace App\Http\Controllers;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use App\Support\Tenant;
class AppApiController {
    public function login(Request $r) {
        $data=$r->validate(['email'=>'required|email','password'=>'required|string']);
        $user=DB::table('users')->where('email',strtolower($data['email']))->first();
        if (!$user || !Hash::check($data['password'],$user->password)) {
            return response()->json(['message'=>'Invalid credentials'],401);
        }
        $secret=bin2hex(random_bytes(32));
        DB::table('app_sessions')->insert([
            'user_id'=>$user->id, 'token_hash'=>hash('sha256',$secret),
            'expires_at'=>now()->addDays(30), 'created_at'=>now(), 'updated_at'=>now()
        ]);
        return response()->json(['token'=>$secret,'user'=>['name'=>$user->name,'email'=>$user->email],
            'workspaces'=>$this->workspaces($user->id)]);
    }
    private function workspaces(int $userId) {
        return DB::table('memberships')->join('workspaces','workspaces.id','=','memberships.workspace_id')
            ->where('memberships.user_id',$userId)->orderBy('workspaces.name')
            ->get(['workspaces.id','workspaces.name','workspaces.type','memberships.role']);
    }
    public function me(Request $r) {
        $u=$r->attributes->get('app_user');
        return response()->json(['user'=>['name'=>$u->name,'email'=>$u->email],
            'workspaces'=>$this->workspaces($u->id)]);
    }
    public function logout(Request $r) {
        DB::table('app_sessions')->where('id',$r->attributes->get('app_session')->id)->delete();
        return response()->json(['ok'=>true]);
    }
    public function linkDevice(Request $r) {
        $data=$r->validate(['workspace_id'=>'required|integer','name'=>'required|string|max:100',
            'sim_label'=>'nullable|string|max:100']);
        $user=$r->attributes->get('app_user');
        $role=Tenant::roleFor($user->id,(int)$data['workspace_id']);
        abort_unless(in_array($role,['owner','admin','manager','operator']),403);
        $secret=bin2hex(random_bytes(32));
        $id=DB::table('devices')->insertGetId([
            'workspace_id'=>$data['workspace_id'],'linked_by'=>$user->id,
            'name'=>$data['name'],'sim_label'=>$data['sim_label'] ?? null,
            'token_hash'=>hash('sha256',$secret),'status'=>'active',
            'created_at'=>now(),'updated_at'=>now()
        ]);
        return response()->json(['device_id'=>$id,'device_token'=>$secret,
            'workspace_id'=>$data['workspace_id']],201);
    }
    public function unlinkDevice(Request $r) {
        $data=$r->validate(['device_id'=>'required|integer']);
        $user=$r->attributes->get('app_user');
        $device=DB::table('devices')->where('id',$data['device_id'])->first();
        abort_unless($device,404);
        $role=Tenant::roleFor($user->id,$device->workspace_id);
        abort_unless(in_array($role,['owner','admin']) || ($role && $device->linked_by==$user->id),403);
        DB::table('devices')->where('id',$device->id)->update(['status'=>'revoked','updated_at'=>now()]);
        return response()->json(['ok'=>true]);
    }
}

