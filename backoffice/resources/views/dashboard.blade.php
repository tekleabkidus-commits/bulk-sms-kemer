<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="csrf-token" content="{{ csrf_token() }}">
<title>Dashboard | Bulk SMS Kemer</title>
<style>
:root{--bg:#0b1424;--surface:#17253b;--edge:#31415a;--text:#f3f6fc;--muted:#a9bed2;--brand:#43b5e6;--ok:#4fd19d;--warn:#ffd47d}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}
header{padding:22px max(18px,calc((100vw - 1400px)/2));background:#142237;border-bottom:1px solid var(--edge);display:flex;gap:24px;align-items:center;justify-content:space-between;flex-wrap:wrap}
header strong{font-size:24px}header small{display:block;color:var(--muted)}.wrap{max-width:1400px;margin:0 auto;padding:25px 18px 80px}
nav{display:flex;gap:7px;flex-wrap:wrap;margin-bottom:22px}nav a{color:#bdd2e5;padding:9px 13px;border:1px solid var(--edge);border-radius:9px;text-decoration:none}nav a:hover{color:white;border-color:var(--brand)}
h2{font-size:21px;margin:4px 0 6px}h3{font-size:16px;margin:5px 0 14px}p,small{color:var(--muted)}.notice{border-radius:9px;padding:12px;margin:15px 0;background:#234535;color:#baf1d5}.error{background:#572c38;color:#ffced5}
.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin:18px 0}.stats{padding:20px;background:var(--surface);border:1px solid var(--edge);border-radius:12px}.stats b{font-size:30px;display:block}.stats span{color:var(--muted)}
.panels{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.panel{min-width:0;background:var(--surface);border:1px solid var(--edge);border-radius:14px;padding:20px;margin-top:18px;scroll-margin-top:25px}
label{display:block;font-weight:600;margin-top:9px;margin-bottom:5px}
input,select,textarea{width:100%;border:1px solid #49607c;background:#0d1b2b;color:#fff;padding:11px;border-radius:9px;font:inherit}
textarea{min-height:95px;resize:vertical}select option{background:#14233a}
button,.btn{cursor:pointer;background:var(--brand);color:#061727;border:0;padding:10px 15px;border-radius:8px;font-weight:800}
button:hover{filter:brightness(1.12)}button.danger{background:#ffb0ac}button.secondary{background:#314862;color:#e0ebf7}
.inline{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.inline select,.inline input{width:auto;min-width:130px;flex:1}
table{width:100%;border-collapse:collapse;margin-top:12px}th,td{text-align:left;border-bottom:1px solid #2f4056;padding:10px 7px;vertical-align:top}th{color:#a9bed2;font-size:12px;text-transform:uppercase}
.tablewrap{overflow:auto}.tag{display:inline-block;border-radius:6px;padding:2px 7px;background:#2c4a65;font-size:12px}.good{color:var(--ok)}.hint{font-size:12px;color:var(--muted)}
.checkbox{display:flex;gap:9px;align-items:flex-start;margin:12px 0}.checkbox input{width:auto;margin-top:4px}
.mini form{display:inline-block;margin:2px}.mini button{font-size:12px;padding:6px 9px}
.footer{padding-top:24px;font-size:12px;color:#8ca9c4}
@media(max-width:900px){.grid{grid-template-columns:repeat(2,1fr)}.panels{grid-template-columns:1fr}}@media(max-width:480px){.grid{grid-template-columns:1fr 1fr;gap:8px}.stats{padding:12px}.stats b{font-size:22px}header strong{font-size:20px}.panel{padding:13px}}
</style></head><body>
<header><div><strong>✉ Bulk SMS Kemer</strong><small>Multi-tenant SMS management</small></div>
<div class="inline"><form method="post" action="/workspaces/switch">@csrf<select name="workspace_id" onchange="this.form.submit()" aria-label="Current workspace">
@foreach($workspaces as $item)<option value="{{ $item->id }}" @selected($item->id==$w->id)>{{ $item->name }} ({{ $item->type }})</option>@endforeach
</select></form>
<span>{{ auth()->user()->name }} · {{ $w->role }}</span><form method="post" action="/logout">@csrf<button class="secondary">Log out</button></form></div>
</header><div class="wrap">
<nav>
<a href="#overview">Overview</a><a href="#campaigns">Campaigns</a><a href="#groups">Players & Groups</a><a href="#templates">Templates</a><a href="#devices">Devices</a><a href="#accounts">Accounts & Users</a><a href="/reports/export">Export CSV ↓</a>
</nav>
@if(session('ok'))<div class="notice">{{ session('ok') }}</div>@endif
@if($errors->any())<div class="notice error">{{ $errors->first() }}</div>@endif
<section id="overview"><h2>{{ $w->name }} · Overview</h2><p>Each workspace has isolated users, contacts, devices, templates and campaigns. The app connects using a separate token per device.</p>
<div class="grid">
<div class="stats"><span>Queued</span><b>{{ number_format(($counts['queued']??0)+($counts['leased']??0)) }}</b></div>
<div class="stats"><span>Submitted to carrier</span><b>{{ number_format($counts['submitted']??0) }}</b></div>
<div class="stats"><span>Confirmed delivered</span><b class="good">{{ number_format($counts['delivered']??0) }}</b></div>
<div class="stats"><span>Failed / Unknown</span><b>{{ number_format(($counts['failed']??0)+($counts['unknown']??0)) }}</b></div>
</div><p class="hint">Submitted is NOT the same as delivered. Unknown transmissions are not automatically retried.</p></section>
<div class="panels">
<section class="panel" id="campaigns"><h2>Create campaign</h2><p>Send only to contacts with recorded permission. The Android device must be linked and the gateway started in the app.</p>
<form method="post" action="/campaigns">@csrf
<label>Campaign name</label><input name="name" required maxlength="100" placeholder="November update">
<label>Player group</label><select name="group_id" required><option value="">Choose group</option>@foreach($groups as $g)<option value="{{ $g->id }}">{{ $g->name }}</option>@endforeach</select>
<label>Sending device</label><select name="device_id" required><option value="">Choose device</option>@foreach($devices->where('status','active') as $d)<option value="{{ $d->id }}">{{ $d->name }} · {{ $d->sim_label ?? 'SIM not reported' }}</option>@endforeach</select>
<label>Template (click to fill)</label><select onchange="document.getElementById('body').value=this.options[this.selectedIndex].dataset.body||''">
<option value="">Write custom message</option>@foreach($templates as $t)<option data-body="{{ $t->body }}">{{ $t->name }}</option>@endforeach
</select>
<label>SMS body</label><textarea id="body" name="body" maxlength="1600" required placeholder="Type your message... Use {name} or {player_id} when populated."></textarea>
<label>Schedule (optional, local time)</label><input type="datetime-local" name="scheduled_at">
<label class="checkbox"><input type="checkbox" name="consent" value="1" required><span>I confirm these recipients consented to these messages.</span></label>
<button type="submit">Queue campaign</button></form></section>
<section class="panel" id="groups"><h2>Player groups</h2>
<form method="post" action="/groups">@csrf<label>New group</label><div class="inline"><input name="name" maxlength="100" required placeholder="VIP players"><button>Create group</button></div></form>
<div class="tablewrap"><table><thead><tr><th>Group</th><th>Import consented contacts</th></tr></thead><tbody>
@forelse($groups as $g)<tr><td>{{ $g->name }}<br><span class="hint">ID {{ $g->id }}</span></td><td>
<form method="post" action="/groups/{{ $g->id }}/import" enctype="multipart/form-data">@csrf
<input type="file" name="file" accept=".csv,.txt" required aria-label="CSV phone list">
<label class="checkbox"><input type="checkbox" name="consent" value="1" required><span class="hint">Confirm opt-in for uploaded contacts</span></label>
<button class="secondary" type="submit">Import CSV</button></form></td></tr>
@empty<tr><td colspan="2">Create your first group.</td></tr>@endforelse</tbody></table></div>
<p class="hint">CSV columns: phone, optional name. Ethiopian 09/07, 2519/2517, +2519/+2517 supported. Unsubscribed contacts cannot be re-added by importing.</p></section>
<section class="panel" id="templates"><h2>SMS templates</h2>
<form method="post" action="/templates">@csrf<label>Name</label><input name="name" maxlength="100" required placeholder="Account announcement">
<label>Message</label><textarea name="body" required maxlength="1600"></textarea><button>Save template</button></form>
<div class="tablewrap"><table><tr><th>Name</th><th>Preview</th></tr>@forelse($templates as $t)<tr><td>{{ $t->name }}</td><td>{{ Str::limit($t->body,90) }}</td></tr>@empty<tr><td colspan="2">No templates yet.</td></tr>@endforelse</table></div></section>
<section class="panel" id="devices"><h2>Android SMS devices</h2><p>On the Android app, sign in with your web account, select this workspace and link the phone. Remote campaign sending must be started on the phone.</p>
<div class="tablewrap"><table><tr><th>Device</th><th>Status</th><th>Last seen</th><th>Action</th></tr>
@forelse($devices as $d)<tr><td>{{ $d->name }}<br><span class="hint">{{ $d->sim_label ?: 'SIM unknown' }} · Battery {{ $d->battery ?? '—' }}%</span></td>
<td><span class="tag">{{ $d->status }}</span></td><td>{{ $d->last_seen_at ?? 'Never' }}</td><td>
@if(in_array($w->role,['owner','admin']) && $d->status==='active')
<form method="post" action="/devices/{{ $d->id }}/revoke">@csrf<button class="danger">Revoke</button></form>
@endif</td></tr>
@empty<tr><td colspan="4">No linked phones.</td></tr>@endforelse</table></div></section>
</div>
<section class="panel" id="history"><h2>Campaign history</h2><p>Pause, resume and cancel take effect on new claims. Already-submitted SMS cannot be recalled.</p>
<div class="tablewrap"><table><tr><th>ID</th><th>Campaign</th><th>Status</th><th>Created</th><th>Actions</th></tr>
@forelse($campaigns as $c)<tr><td>#{{ $c->id }}</td><td>{{ $c->name }}</td><td><span class="tag">{{ $c->status }}</span></td><td>{{ $c->created_at }}</td>
<td class="mini">@if(in_array($w->role,['owner','admin','manager','operator']))
@foreach(['pause','resume','cancel'] as $action)<form method="post" action="/campaigns/{{ $c->id }}/action">@csrf<input type="hidden" name="action" value="{{ $action }}"><button class="{{ $action==='cancel'?'danger':'secondary' }}">{{ ucfirst($action) }}</button></form>@endforeach
@if(in_array($w->role,['owner','admin']))<form method="post" action="/campaigns/{{ $c->id }}/action">@csrf<input type="hidden" name="action" value="retry_failed"><button>Retry confirmed failures</button></form>@endif
@endif</td></tr>@empty<tr><td colspan="5">No campaigns.</td></tr>@endforelse</table></div>
<a class="btn" href="/reports/export" style="display:inline-block;margin-top:13px;text-decoration:none">Download recipient report (CSV)</a></section>
<section class="panel" id="accounts"><h2>Accounts & teams</h2><div class="panels">
<div><h3>Create company</h3><form method="post" action="/workspaces">@csrf<label>Company name</label><input name="name" maxlength="100" required placeholder="Business name"><button>Create workspace</button></form>
</div><div><h3>Current workspace members</h3><div class="tablewrap"><table><tr><th>User</th><th>Role</th></tr>@foreach($members as $m)<tr><td>{{ $m->name }}<br><span class="hint">{{ $m->email }}</span></td><td>{{ $m->role }}</td></tr>@endforeach</table></div>
@if(in_array($w->role,['owner','admin']))<form method="post" action="/members">@csrf<label>Add existing registered user</label><input type="email" name="email" required placeholder="staff@example.com"><label>Role</label><select name="role"><option value="operator">Operator</option><option value="manager">Manager</option><option value="viewer">Viewer</option><option value="admin">Admin</option></select><button>Add member</button></form>@endif
</div></div></section>
<p class="footer">© Bulk SMS Kemer. Send only permission-based messages. Phone/carrier delivery and background execution are not guaranteed.</p>
</div></body></html>

