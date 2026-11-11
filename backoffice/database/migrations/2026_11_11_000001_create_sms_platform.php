<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void {
        Schema::create('users', function(Blueprint $t) {
            $t->id(); $t->string('name'); $t->string('email')->unique(); $t->string('password');
            $t->rememberToken(); $t->timestamps();
        });
        Schema::create('workspaces',function(Blueprint $t) {
            $t->id(); $t->string('name'); $t->enum('type',['personal','company']);
            $t->foreignId('owner_id')->constrained('users'); $t->timestamps();
        });
        Schema::create('memberships',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->string('role',20); $t->timestamps(); $t->unique(['workspace_id','user_id']);
        });
        Schema::create('app_sessions',function(Blueprint $t) {
            $t->id(); $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->char('token_hash',64)->unique(); $t->timestamp('expires_at');
            $t->timestamps();
        });
        Schema::create('devices',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->foreignId('linked_by')->constrained('users'); $t->string('name',100);
            $t->string('sim_label')->nullable(); $t->char('token_hash',64)->unique();
            $t->string('status',20)->default('active'); $t->timestamp('last_seen_at')->nullable();
            $t->unsignedTinyInteger('battery')->nullable(); $t->string('app_version',40)->nullable();
            $t->timestamps(); $t->index(['workspace_id','status']);
        });
        Schema::create('contact_groups',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->string('name'); $t->timestamps();
        });
        Schema::create('contacts',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->string('phone',16); $t->string('name')->nullable();
            $t->string('player_id')->nullable(); $t->boolean('opted_in')->default(false);
            $t->timestamp('opted_out_at')->nullable(); $t->timestamps();
            $t->unique(['workspace_id','phone']);
        });
        Schema::create('group_contacts',function(Blueprint $t) {
            $t->foreignId('group_id')->constrained('contact_groups')->cascadeOnDelete();
            $t->foreignId('contact_id')->constrained('contacts')->cascadeOnDelete();
            $t->primary(['group_id','contact_id']);
        });
        Schema::create('templates',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->string('name'); $t->text('body'); $t->timestamps();
        });
        Schema::create('campaigns',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->foreignId('device_id')->constrained('devices');
            $t->foreignId('created_by')->constrained('users');
            $t->string('name'); $t->text('body'); $t->string('status',20)->default('draft');
            $t->timestamp('scheduled_at')->nullable(); $t->timestamp('approved_at')->nullable();
            $t->timestamps(); $t->index(['device_id','status']);
        });
        Schema::create('campaign_recipients',function(Blueprint $t) {
            $t->id(); $t->foreignId('campaign_id')->constrained()->cascadeOnDelete();
            $t->foreignId('contact_id')->nullable()->constrained('contacts')->nullOnDelete();
            $t->string('phone',16); $t->text('body'); $t->string('status',20)->default('queued');
            $t->uuid('lease_uuid')->nullable()->unique(); $t->timestamp('lease_expires_at')->nullable();
            $t->unsignedTinyInteger('attempts')->default(0); $t->integer('sms_result_code')->nullable();
            $t->string('error')->nullable(); $t->timestamp('submitted_at')->nullable();
            $t->timestamp('delivered_at')->nullable(); $t->timestamps();
            $t->unique(['campaign_id','phone']); $t->index(['campaign_id','status']);
        });
        Schema::create('audit_logs',function(Blueprint $t) {
            $t->id(); $t->foreignId('workspace_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->nullable()->constrained('users')->nullOnDelete();
            $t->string('action'); $t->unsignedBigInteger('subject_id')->nullable();
            $t->timestamp('created_at');
        });
    }
    public function down(): void {
        foreach(['audit_logs','campaign_recipients','campaigns','templates','group_contacts','contacts','contact_groups','devices','app_sessions','memberships','workspaces','users'] as $table) Schema::dropIfExists($table);
    }
};

