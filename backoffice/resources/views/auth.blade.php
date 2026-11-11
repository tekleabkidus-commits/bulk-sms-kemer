<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{{ $mode==='register'?'Create account':'Sign in' }} | Bulk SMS Kemer</title>
<style>
*{box-sizing:border-box}body{font:15px/1.5 system-ui,sans-serif;background:#0d182b;color:#f6f8ff;margin:0;min-height:100vh;display:grid;place-items:center}
main{background:#182842;border:1px solid #31445f;border-radius:18px;padding:36px;max-width:440px;width:calc(100% - 32px);box-shadow:0 24px 75px #03091599}
h1{margin:0;font-size:28px}p{color:#b7c7d8}label{display:block;margin-top:15px;font-weight:600}input{width:100%;padding:12px;border-radius:9px;border:1px solid #4c6581;background:#101f33;color:#fff}
button{background:#3e9be8;color:#071627;border:0;border-radius:9px;width:100%;padding:13px;margin:22px 0 8px;font-weight:800;cursor:pointer}
a{color:#78c5ff}.error{color:#ff9a9a}
</style></head><body><main>
<h1>Bulk SMS Kemer</h1><p>{{ $mode==='register'?'Create your personal account. You can create companies afterward.':'Sign in to manage your SMS devices and campaigns.' }}</p>
@if($errors->any())<div class="error">{{ $errors->first() }}</div>@endif
<form method="post" action="{{ $mode==='register'?'/register':'/login' }}">@csrf
@if($mode==='register')<label>Full name<input type="text" name="name" required value="{{ old('name') }}"></label>@endif
<label>Email<input type="email" name="email" required value="{{ old('email') }}" autocomplete="username"></label>
<label>Password<input type="password" name="password" required autocomplete="{{ $mode==='register'?'new-password':'current-password' }}" @if($mode==='register') minlength="12" @endif></label>
@if($mode==='register')<label>Confirm password<input type="password" name="password_confirmation" required autocomplete="new-password"></label>@endif
<button type="submit">{{ $mode==='register'?'Create account':'Sign in' }}</button>
</form><p>{{ $mode==='register'?'Already registered?':'New user?' }} <a href="{{ $mode==='register'?'/login':'/register' }}">{{ $mode==='register'?'Sign in':'Register' }}</a></p>
</main></body></html>

