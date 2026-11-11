<?php
namespace Tests\Feature;
use Tests\TestCase;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;

class SmsBackofficeTest extends TestCase {
    use RefreshDatabase;

    private function makeAccount(string $email): array {
        $response=$this->post('/register',[
            'name'=>'Test User','email'=>$email,'password'=>'MySecurePassword-123',
            'password_confirmation'=>'MySecurePassword-123'
        ]);
        $response->assertRedirect('/');
        $user=DB::table('users')->where('email',$email)->first();
        $workspace=DB::table('workspaces')->where('owner_id',$user->id)->first();
        return [$user,$workspace];
    }
    public function test_registration_creates_isolated_personal_workspace(): void {
        [$user,$workspace]=$this->makeAccount('one@example.test');
        $this->assertSame('personal',$workspace->type);
        $this->assertDatabaseHas('memberships',['user_id'=>$user->id,'workspace_id'=>$workspace->id,'role'=>'owner']);
    }
    public function test_login_and_device_pairing_and_receipts(): void {
        [$user,$workspace]=$this->makeAccount('one@example.test');
        $login=$this->postJson('/api/v1/app/login',['email'=>$user->email,'password'=>'MySecurePassword-123']);
        $login->assertOk()->assertJsonStructure(['token','workspaces']);
        $token=$login->json('token');
        $headers=['Authorization'=>'Bearer '.$token];
        $link=$this->postJson('/api/v1/app/devices/link',
            ['workspace_id'=>$workspace->id,'name'=>'Android Test','sim_label'=>'SIM 1'],$headers);
        $link->assertCreated()->assertJsonStructure(['device_id','device_token']);
        $deviceId=$link->json('device_id');
        $deviceHeaders=['Authorization'=>'Bearer '.$link->json('device_token')];
        $id=DB::table('campaigns')->insertGetId([
            'workspace_id'=>$workspace->id,'device_id'=>$deviceId,'created_by'=>$user->id,
            'name'=>'Test','body'=>'Hello','status'=>'running','created_at'=>now(),'updated_at'=>now()
        ]);
        DB::table('campaign_recipients')->insert(['campaign_id'=>$id,'phone'=>'+251912345678','body'=>'Hello',
            'status'=>'queued','created_at'=>now(),'updated_at'=>now()]);
        $claimed=$this->postJson('/api/v1/device/claim',[],$deviceHeaders);
        $claimed->assertOk()->assertJsonStructure(['job'=>['id','lease_uuid','phone']]);
        $row=$claimed->json('job');
        $this->postJson('/api/v1/device/receipt',
            ['id'=>$row['id'],'lease_uuid'=>$row['lease_uuid'],'status'=>'submitted'],$deviceHeaders)
            ->assertOk()->assertJson(['ok'=>true]);
        $this->assertDatabaseHas('campaign_recipients',['id'=>$row['id'],'status'=>'submitted']);
        $this->postJson('/api/v1/device/receipt',
            ['id'=>$row['id'],'lease_uuid'=>$row['lease_uuid'],'status'=>'delivered'],$deviceHeaders)
            ->assertOk();
        $this->assertDatabaseHas('campaign_recipients',['id'=>$row['id'],'status'=>'delivered']);
        $this->postJson('/api/v1/app/devices/unlink',['device_id'=>$deviceId],$headers)->assertOk();
        $this->postJson('/api/v1/device/claim',[],$deviceHeaders)->assertUnauthorized();
    }
    public function test_cross_workspace_link_forbidden(): void {
        [$first,$a]=$this->makeAccount('one@example.test');
        $this->post('/logout');
        [$second,$b]=$this->makeAccount('two@example.test');
        $token=$this->postJson('/api/v1/app/login',[
            'email'=>$second->email,'password'=>'MySecurePassword-123'])->json('token');
        $this->postJson('/api/v1/app/devices/link',['workspace_id'=>$a->id,'name'=>'Unauthorized'],
            ['Authorization'=>'Bearer '.$token])->assertForbidden();
    }
}
