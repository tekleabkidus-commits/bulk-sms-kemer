<?php
namespace App\Http\Controllers;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
class DeviceApiController {
    public function heartbeat(Request $r) {
        $d=$r->attributes->get('gateway_device');
        $data=$r->validate(['battery'=>'nullable|integer|min:0|max:100',
            'sim_label'=>'nullable|string|max:100','app_version'=>'nullable|string|max:40']);
        DB::table('devices')->where('id',$d->id)->update([
            'battery'=>$data['battery'] ?? null,
            'sim_label'=>$data['sim_label'] ?? $d->sim_label,
            'app_version'=>$data['app_version'] ?? $d->app_version,
            'last_seen_at'=>now(), 'updated_at'=>now()
        ]);
        return response()->json(['ok'=>true]);
    }
    public function status(Request $r) {
        $d=$r->attributes->get('gateway_device');
        return response()->json(['active'=>true,'device_id'=>$d->id,'workspace_id'=>$d->workspace_id]);
    }
    public function claim(Request $r) {
        $d=$r->attributes->get('gateway_device');
        $result=DB::transaction(function () use ($d) {
            // No blind retry of expired claims. A previous SMS might have reached the carrier.
            DB::table('campaign_recipients')->where('status','leased')
                ->where('lease_expires_at','<',now())
                ->whereIn('campaign_id',DB::table('campaigns')->select('id')->where('device_id',$d->id))
                ->update(['status'=>'unknown','error'=>'Receipt timeout; manual reconciliation required','updated_at'=>now()]);
            $row=DB::table('campaign_recipients as r')
                ->join('campaigns as c','c.id','=','r.campaign_id')
                ->where('c.device_id',$d->id)->where('c.workspace_id',$d->workspace_id)
                ->where('c.status','running')
                ->where(function($q) { $q->whereNull('c.scheduled_at')->orWhere('c.scheduled_at','<=',now()); })
                ->where('r.status','queued')
                ->orderBy('c.id')->orderBy('r.id')
                ->select('r.*')->lockForUpdate()->first();
            if (!$row) return null;
            $lease=(string) Str::uuid();
            $updated=DB::table('campaign_recipients')->where('id',$row->id)->where('status','queued')
                ->update(['status'=>'leased','lease_uuid'=>$lease,'lease_expires_at'=>now()->addMinutes(3),
                    'attempts'=>DB::raw('attempts + 1'),'updated_at'=>now()]);
            if (!$updated) return null;
            return ['id'=>$row->id,'lease_uuid'=>$lease,'phone'=>$row->phone,
                'body'=>$row->body,'campaign_id'=>$row->campaign_id];
        },3);
        return response()->json(['job'=>$result]);
    }
    public function receipt(Request $r) {
        $d=$r->attributes->get('gateway_device');
        $data=$r->validate(['id'=>'required|integer','lease_uuid'=>'required|uuid',
            'status'=>'required|in:submitted,delivered,failed,unknown',
            'sms_result_code'=>'nullable|integer','error'=>'nullable|string|max:200']);
        $result=DB::transaction(function () use ($d,$data) {
            $row=DB::table('campaign_recipients as r')
                ->join('campaigns as c','c.id','=','r.campaign_id')
                ->where('r.id',$data['id'])->where('r.lease_uuid',$data['lease_uuid'])
                ->where('c.device_id',$d->id)->where('c.workspace_id',$d->workspace_id)
                ->select('r.id','r.status')->lockForUpdate()->first();
            if (!$row) return false;
            if ($row->status==='delivered') return true;
            if ($row->status==='submitted' && in_array($data['status'],['failed','unknown'])) return true;
            if (in_array($row->status,['failed','unknown']) && in_array($data['status'],['failed','unknown'])) return true;
            if (!in_array($row->status,['leased','unknown','submitted','failed'])) return false;
            $update=['status'=>$data['status'],'sms_result_code'=>$data['sms_result_code']??null,
                'error'=>$data['error']??null,'updated_at'=>now()];
            if (in_array($data['status'],['submitted','delivered'])) $update['submitted_at']=now();
            if ($data['status']==='delivered') $update['delivered_at']=now();
            DB::table('campaign_recipients')->where('id',$row->id)->update($update);
            return true;
        },3);
        return response()->json(['ok'=>$result],$result?200:409);
    }
}

