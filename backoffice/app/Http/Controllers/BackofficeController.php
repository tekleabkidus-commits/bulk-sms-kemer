<?php
namespace App\Http\Controllers;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;
use App\Models\User;
use App\Support\Tenant;
use Symfony\Component\HttpFoundation\StreamedResponse;

class BackofficeController {
    public function loginPage() { return view('auth',['mode'=>'login']); }
    public function registerPage() { return view('auth',['mode'=>'register']); }
    public function register(Request $r) {
        $r->merge(['email'=>strtolower(trim((string)$r->input('email')))]);
        $data=$r->validate(['name'=>'required|string|max:100','email'=>'required|email|unique:users,email','password'=>'required|string|min:12|confirmed']);
        $id=DB::transaction(function() use ($data) {
            $user=User::create(['name'=>$data['name'],'email'=>strtolower($data['email']),'password'=>$data['password']]);
            $w=DB::table('workspaces')->insertGetId(['name'=>$user->name.' Personal','type'=>'personal','owner_id'=>$user->id,'created_at'=>now(),'updated_at'=>now()]);
            DB::table('memberships')->insert(['workspace_id'=>$w,'user_id'=>$user->id,'role'=>'owner','created_at'=>now(),'updated_at'=>now()]);
            return [$user,$w];
        });
        Auth::login($id[0]); $r->session()->regenerate(); session(['workspace_id'=>$id[1]]);
        return redirect('/');
    }
    public function login(Request $r) {
        $credentials=$r->validate(['email'=>'required|email','password'=>'required|string']);
        $credentials['email']=strtolower($credentials['email']);
        if (!Auth::attempt($credentials)) return back()->withErrors(['email'=>'Invalid credentials.']);
        $r->session()->regenerate();
        $w=DB::table('memberships')->where('user_id',Auth::id())->orderBy('id')->value('workspace_id');
        session(['workspace_id'=>$w]);
        return redirect('/');
    }
    public function logout(Request $r) {
        Auth::logout(); $r->session()->invalidate(); $r->session()->regenerateToken(); return redirect('/login');
    }
    public function dashboard() {
        $w=Tenant::current();
        $workspaces=DB::table('memberships')->join('workspaces','workspaces.id','=','memberships.workspace_id')
            ->where('memberships.user_id',Auth::id())->select('workspaces.*','memberships.role')->get();
        $devices=DB::table('devices')->where('workspace_id',$w->id)->orderByDesc('id')->get();
        $groups=DB::table('contact_groups')->where('workspace_id',$w->id)->orderBy('name')->get();
        $templates=DB::table('templates')->where('workspace_id',$w->id)->orderByDesc('id')->get();
        $campaigns=DB::table('campaigns')->where('workspace_id',$w->id)->orderByDesc('id')->limit(50)->get();
        $counts=DB::table('campaign_recipients as r')->join('campaigns as c','c.id','=','r.campaign_id')
            ->where('c.workspace_id',$w->id)->selectRaw('r.status, COUNT(*) as total')->groupBy('r.status')->pluck('total','status');
        $campaignStats=DB::table('campaign_recipients')->whereIn('campaign_id',$campaigns->pluck('id'))
            ->selectRaw('campaign_id, status, COUNT(*) as total')->groupBy('campaign_id','status')
            ->get()->groupBy('campaign_id');
        $members=DB::table('memberships')->join('users','users.id','=','memberships.user_id')
            ->where('workspace_id',$w->id)->get(['users.email','users.name','memberships.role']);
        return view('dashboard',compact('w','workspaces','devices','groups','templates','campaigns','campaignStats','counts','members'));
    }
    public function switchWorkspace(Request $r) {
        $data=$r->validate(['workspace_id'=>'required|integer']);
        abort_unless(Tenant::roleFor(Auth::id(),(int)$data['workspace_id']),403);
        session(['workspace_id'=>(int)$data['workspace_id']]); return redirect('/');
    }
    public function createWorkspace(Request $r) {
        $data=$r->validate(['name'=>'required|string|max:100']);
        $id=DB::transaction(function() use ($data) {
            $id=DB::table('workspaces')->insertGetId(['name'=>$data['name'],'type'=>'company','owner_id'=>Auth::id(),'created_at'=>now(),'updated_at'=>now()]);
            DB::table('memberships')->insert(['workspace_id'=>$id,'user_id'=>Auth::id(),'role'=>'owner','created_at'=>now(),'updated_at'=>now()]);
            return $id;
        });
        session(['workspace_id'=>$id]); return redirect('/')->with('ok','Company created.');
    }
    public function addMember(Request $r) {
        $w=Tenant::current(); Tenant::requireAdmin($w);
        $data=$r->validate(['email'=>'required|email','role'=>'required|in:admin,manager,operator,viewer']);
        $user=DB::table('users')->where('email',strtolower($data['email']))->first();
        if (!$user) return back()->withErrors(['email'=>'User must register first.']);
        // The organization owner cannot be demoted through staff administration.
        $existing=DB::table('memberships')->where('workspace_id',$w->id)->where('user_id',$user->id)->first();
        abort_if($existing && $existing->role==='owner',403,'Cannot change owner role.');
        DB::table('memberships')->updateOrInsert(['workspace_id'=>$w->id,'user_id'=>$user->id],
            ['role'=>$data['role'],'updated_at'=>now(),'created_at'=>now()]);
        Tenant::log($w,'member.upsert',$user->id);
        return back()->with('ok','Member saved.');
    }
    public function createGroup(Request $r) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        $data=$r->validate(['name'=>'required|string|max:100']);
        $id=DB::table('contact_groups')->insertGetId(['workspace_id'=>$w->id,'name'=>$data['name'],'created_at'=>now(),'updated_at'=>now()]);
        Tenant::log($w,'group.create',$id);
        return back()->with('ok','Group created.');
    }
    private function phone(string $input): ?string {
        $digits=preg_replace('/\D+/','',$input);
        if (preg_match('/^0([79]\d{8})$/',$digits,$m)) return '+251'.$m[1];
        if (preg_match('/^251([79]\d{8})$/',$digits,$m)) return '+251'.$m[1];
        if (preg_match('/^([79]\d{8})$/',$digits,$m)) return '+251'.$m[1];
        return null;
    }

    public function createContact(Request $r) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        $data=$r->validate(['group_id'=>'required|integer','phone'=>'required|string|max:32',
            'name'=>'nullable|string|max:100','player_id'=>'nullable|string|max:100','consent'=>'accepted']);
        abort_unless(DB::table('contact_groups')->where('workspace_id',$w->id)->where('id',$data['group_id'])->exists(),404);
        $phone=$this->phone($data['phone']);
        if (!$phone) return back()->withErrors(['phone'=>'Invalid Ethiopian mobile phone number.']);
        $contact=DB::table('contacts')->where('workspace_id',$w->id)->where('phone',$phone)->first();
        if ($contact && (!$contact->opted_in || $contact->opted_out_at))
            return back()->withErrors(['phone'=>'This contact previously opted out. Do not add without separately recorded renewed consent.']);
        $id=DB::transaction(function() use ($w,$data,$phone,$contact) {
            if($contact) {
                $id=$contact->id;
                DB::table('contacts')->where('id',$id)->update([
                    'name'=>$data['name']??$contact->name,'player_id'=>$data['player_id']??$contact->player_id,'updated_at'=>now()]);
            } else {
                $id=DB::table('contacts')->insertGetId([
                    'workspace_id'=>$w->id,'phone'=>$phone,'name'=>$data['name']??null,
                    'player_id'=>$data['player_id']??null,'opted_in'=>true,'created_at'=>now(),'updated_at'=>now()]);
            }
            DB::table('group_contacts')->insertOrIgnore(['group_id'=>$data['group_id'],'contact_id'=>$id]);
            return $id;
        });
        Tenant::log($w,'contact.add',$id);
        return back()->with('ok','Contact saved and added to group.');
    }
    public function optOutByPhone(Request $r) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        $data=$r->validate(['phone'=>'required|string|max:32']);
        $phone=$this->phone($data['phone']);
        if (!$phone) return back()->withErrors(['phone'=>'Invalid Ethiopian phone.']);
        $ct=DB::table('contacts')->where('workspace_id',$w->id)->where('phone',$phone)->first();
        if (!$ct) return back()->withErrors(['phone'=>'Number not in this workspace.']);
        DB::table('contacts')->where('id',$ct->id)->where('workspace_id',$w->id)
            ->update(['opted_in'=>false,'opted_out_at'=>now(),'updated_at'=>now()]);
        Tenant::log($w,'contact.opt_out',$ct->id);
        return back()->with('ok','Recipient suppressed from future device claims.');
    }
    public function importCsv(Request $r, int $id) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        abort_unless(DB::table('contact_groups')->where('workspace_id',$w->id)->where('id',$id)->exists(),404);
        $r->validate(['file'=>'required|file|mimes:csv,txt|max:10240','consent'=>'accepted']);
        $fh=fopen($r->file('file')->getRealPath(),'rb'); $added=0; $skipped=0;
        DB::transaction(function() use ($fh,$w,$id,&$added,&$skipped) {
            while(($row=fgetcsv($fh))!==false) {
                $phone=$this->phone((string)($row[0]??''));
                if (!$phone) { $skipped++; continue; }
                DB::table('contacts')->insertOrIgnore(['workspace_id'=>$w->id,'phone'=>$phone,
                    'name'=>substr((string)($row[1]??''),0,100) ?: null,'opted_in'=>true,'created_at'=>now(),'updated_at'=>now()]);
                $contact=DB::table('contacts')->where('workspace_id',$w->id)->where('phone',$phone)->first();
                if ($contact && $contact->opted_in && !$contact->opted_out_at) {
                    DB::table('group_contacts')->insertOrIgnore(['group_id'=>$id,'contact_id'=>$contact->id]); $added++;
                } else $skipped++;
            }
        });
        fclose($fh); Tenant::log($w,'group.import',$id);
        return back()->with('ok',"Imported/assigned {$added}; skipped {$skipped}. Existing opt-outs retained.");
    }
    public function optOut(Request $r,int $id) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        DB::table('contacts')->where('id',$id)->where('workspace_id',$w->id)
            ->update(['opted_in'=>false,'opted_out_at'=>now(),'updated_at'=>now()]);
        Tenant::log($w,'contact.opt_out',$id); return back()->with('ok','Contact opted out.');
    }
    public function createTemplate(Request $r) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        $data=$r->validate(['name'=>'required|string|max:100','body'=>'required|string|max:1600']);
        $id=DB::table('templates')->insertGetId(['workspace_id'=>$w->id,'name'=>$data['name'],
            'body'=>$data['body'],'created_at'=>now(),'updated_at'=>now()]);
        Tenant::log($w,'template.create',$id); return back()->with('ok','Template created.');
    }
    public function createCampaign(Request $r) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        $data=$r->validate(['name'=>'required|string|max:100','body'=>'required|string|max:1600',
            'group_id'=>'required|integer','device_id'=>'required|integer','consent'=>'accepted',
            'scheduled_at'=>'nullable|date']);
        abort_unless(DB::table('devices')->where('id',$data['device_id'])->where('workspace_id',$w->id)->where('status','active')->exists(),422);
        abort_unless(DB::table('contact_groups')->where('id',$data['group_id'])->where('workspace_id',$w->id)->exists(),422);
        $contacts=DB::table('group_contacts as gc')->join('contacts as ct','ct.id','=','gc.contact_id')
            ->where('gc.group_id',$data['group_id'])->where('ct.workspace_id',$w->id)
            ->where('ct.opted_in',true)->whereNull('ct.opted_out_at')
            ->select('ct.id','ct.phone','ct.name','ct.player_id')->get();
        if ($contacts->isEmpty()) return back()->withErrors(['group_id'=>'No opted-in recipients in this group.']);
        foreach($contacts as $ct) {
            if ((str_contains($data['body'],'{name}') && !$ct->name) ||
                (str_contains($data['body'],'{player_id}') && !$ct->player_id))
                return back()->withErrors(['body'=>'Some contacts lack required template fields.']);
        }
        DB::transaction(function() use ($w,$data,$contacts) {
            $campaign=DB::table('campaigns')->insertGetId([
                'workspace_id'=>$w->id,'device_id'=>$data['device_id'],'created_by'=>Auth::id(),
                'name'=>$data['name'],'body'=>$data['body'],'status'=>'running',
                'scheduled_at'=>$data['scheduled_at']??null,'approved_at'=>now(),
                'created_at'=>now(),'updated_at'=>now()
            ]);
            foreach($contacts->chunk(500) as $chunk) {
                DB::table('campaign_recipients')->insert($chunk->map(function($ct) use ($campaign,$data) {
                    return ['campaign_id'=>$campaign,'contact_id'=>$ct->id,'phone'=>$ct->phone,
                        'body'=>strtr($data['body'],['{name}'=>$ct->name??'', '{player_id}'=>$ct->player_id??'']),
                        'status'=>'queued','created_at'=>now(),'updated_at'=>now()];
                })->all());
            }
            Tenant::log($w,'campaign.create',$campaign);
        });
        return back()->with('ok','Campaign created and queued for connected device.');
    }
    public function campaignAction(Request $r,int $id) {
        $w=Tenant::current(); Tenant::requireWrite($w);
        $data=$r->validate(['action'=>'required|in:pause,resume,cancel,retry_failed']);
        $c=DB::table('campaigns')->where('workspace_id',$w->id)->where('id',$id)->first();
        abort_unless($c,404);
        if($data['action']==='retry_failed') {
            Tenant::requireAdmin($w);
            DB::table('campaign_recipients')->where('campaign_id',$id)->where('status','failed')
                ->update(['status'=>'queued','error'=>null,'lease_uuid'=>null,'lease_expires_at'=>null,'updated_at'=>now()]);
            DB::table('campaigns')->where('id',$id)->update(['status'=>'running','updated_at'=>now()]);
        } else {
            $status=['pause'=>'paused','resume'=>'running','cancel'=>'cancelled'][$data['action']];
            DB::table('campaigns')->where('id',$id)->update(['status'=>$status,'updated_at'=>now()]);
            if($status==='cancelled') DB::table('campaign_recipients')->where('campaign_id',$id)->where('status','queued')
                ->update(['status'=>'cancelled','updated_at'=>now()]);
        }
        Tenant::log($w,'campaign.'.$data['action'],$id);
        return back()->with('ok','Campaign updated. In-flight sends may still complete.');
    }
    public function revokeDevice(Request $r,int $id) {
        $w=Tenant::current(); Tenant::requireAdmin($w);
        DB::table('devices')->where('workspace_id',$w->id)->where('id',$id)
            ->update(['status'=>'revoked','updated_at'=>now()]);
        Tenant::log($w,'device.revoke',$id);
        return back()->with('ok','Device revoked.');
    }
    public function export() {
        $w=Tenant::current();
        $rows=DB::table('campaign_recipients as r')->join('campaigns as c','c.id','=','r.campaign_id')
            ->where('c.workspace_id',$w->id)->orderBy('r.id')
            ->select('c.name as campaign','r.phone','r.status','r.error','r.submitted_at','r.delivered_at');
        return response()->streamDownload(function() use ($rows) {
            $out=fopen('php://output','w');
            fputcsv($out,['Campaign','Phone','Status','Error','Submitted','Delivered']);
            foreach($rows->cursor() as $r) fputcsv($out,[$r->campaign,$r->phone,$r->status,$r->error,$r->submitted_at,$r->delivered_at]);
            fclose($out);
        },'sms-report.csv',['Content-Type'=>'text/csv']);
    }
}

