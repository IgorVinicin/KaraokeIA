// Explicit integration test: creates isolated temporary Auth users, then removes them.
// CLI credentials/API keys stay in process memory; no tokens or passwords are printed.
import { execFileSync } from 'node:child_process';
import { randomUUID, randomBytes, verify, createPublicKey } from 'node:crypto';
import { writeFileSync, unlinkSync, readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const ref = 'luqgrtfqlywchlyasaog';
const base = `https://${ref}.supabase.co`;
function cli(command) {
  try {
    const result = execFileSync('cmd.exe', ['/d', '/s', '/c', `npx.cmd --yes supabase ${command} --agent no --output json`], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 60000 });
    return JSON.parse(result);
  } catch { throw new Error('Supabase CLI request failed; credentials were not printed'); }
}
const keys = cli(`projects api-keys --project-ref ${ref}`);
const service = keys.find(k => k.name === 'service_role')?.api_key;
if (!service) throw new Error('Service credential unavailable');
const createdUsers = [];
let planId;
async function call(path, body, token, method = 'POST') {
  const headers = { 'content-type': 'application/json' };
  if (token) { headers.authorization = `Bearer ${token}`; headers.apikey = service; }
  const response = await fetch(`${base}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000) });
  let json; try { json = await response.json(); } catch { json = {}; }
  return { status: response.status, json };
}
const api = (route, body, token) => call(`/functions/v1/accounts-api${route}`, body, token);
const pass = randomBytes(24).toString('base64url') + 'Aa1!';
const changedPass = randomBytes(24).toString('base64url') + 'Aa1!';
const suffix = randomUUID().replaceAll('-', '').slice(0, 20);
try {
  assert.equal((await call('/functions/v1/accounts-api/health', undefined, undefined, 'GET')).status, 200);
  assert.equal((await api('/admin/accounts', {})).status, 401);
  const adminEmail = `sv-test-${suffix}@example.com`;
  const admin = await call('/auth/v1/admin/users', { email: adminEmail, password: pass, email_confirm: true }, service);
  assert.equal(admin.status, 200, 'Temporary admin creation failed');
  const adminId = admin.json.id ?? admin.json.user?.id;
  assert.ok(adminId); createdUsers.push(adminId);
  assert.equal((await call('/rest/v1/sv_admins', { user_id: adminId }, service)).status, 201);
  const adminLogin = await api('/admin/login', { email: adminEmail, password: pass });
  assert.equal(adminLogin.status, 200, 'Admin login failed');
  const adminToken = adminLogin.json.auth.access_token;
  assert.equal((await api('/admin/refresh',{refresh_token:adminLogin.json.auth.refresh_token})).status,200);
  const plan = await api('/admin/create-plan', { name: `API test ${suffix}` }, adminToken);
  assert.equal(plan.status, 201); planId = plan.json.plan.id;
  assert.equal((await api('/admin/set-plan',{plan_id:planId,name:`Changed ${suffix}`,enabled:true},adminToken)).status,200);
  const name = `test_${suffix}`;
  const account = await api('/admin/create-account', { username: name, display_name: 'Temporary integration account', password: pass,
    enabled: true, expires_at: null, plan_id: planId }, adminToken);
  assert.equal(account.status, 201, `Account creation failed: ${account.json.error ?? 'unknown'}`);
  const clientId = account.json.account.user_id; createdUsers.push(clientId);
  const d1 = randomUUID(), d2 = randomUUID();
  const first = await api('/login', { username: name, password: pass, installation_id: d1, device_label: 'Test box one' });
  assert.equal(first.status, 200);
  const [payload, signature] = first.json.offline_lease.split('.');
  const offlineKey = createPublicKey({key:Buffer.from(readFileSync('supabase/offline-public-key.txt','utf8').trim(),'base64'),format:'der',type:'spki'});
  assert.ok(verify('sha256',Buffer.from(payload),offlineKey,Buffer.from(signature,'base64url')));
  const claims = JSON.parse(Buffer.from(payload,'base64url').toString('utf8'));
  assert.equal(claims.installation_id,d1); assert.equal(claims.session_id,first.json.session.session_id);
  if(process.env.SOLTAVOZ_TEST_YOUTUBE === '1') {
    const songs = await api('/search',{session_id:first.json.session.session_id,installation_id:d1,query:'evidencias'},first.json.auth.access_token);
    assert.equal(songs.status,200,`Protected search failed: ${songs.json.error??'unknown'}`);
    assert.ok(Array.isArray(songs.json.songs)&&songs.json.songs.length>0&&songs.json.songs.length<=15);
    console.log('PASS: protected YouTube search and offline signature');
  }
  assert.equal((await api('/admin/accounts', {}, first.json.auth.access_token)).status, 403);
  const second = await api('/login', { username: name, password: pass, installation_id: d2, device_label: 'Test box two' });
  assert.equal(second.status, 200);
  const oldBody = { session_id: first.json.session.session_id, installation_id: d1 };
  const newBody = { session_id: second.json.session.session_id, installation_id: d2 };
  const stale = await api('/validate', oldBody, first.json.auth.access_token);
  assert.equal(stale.status, 409); assert.equal(stale.json.error, 'session_replaced');
  const staleSearch = await api('/search',{...oldBody,query:'evidencias'},first.json.auth.access_token);
  assert.equal(staleSearch.json.error,'session_replaced');
  assert.equal((await api('/logout', oldBody, first.json.auth.access_token)).status, 200);
  assert.equal((await api('/validate', newBody, second.json.auth.access_token)).status, 200);
  const refreshed = await api('/refresh', { ...newBody, refresh_token: second.json.auth.refresh_token });
  assert.equal(refreshed.status, 200);
  assert.equal(refreshed.json.session.session_id, second.json.session.session_id);
  const devices = await api('/admin/devices',{user_id:clientId},adminToken);
  const currentDevice = devices.json.devices.find(d => d.installation_id === d2);
  assert.ok(currentDevice);
  assert.equal((await api('/admin/set-device',{device_id:currentDevice.id,blocked:true},adminToken)).status,200);
  const deniedDevice = await api('/login',{username:name,password:pass,installation_id:d2,device_label:'Blocked box'});
  assert.equal(deniedDevice.json.error,'device_blocked');
  assert.equal((await api('/admin/set-device',{device_id:currentDevice.id,blocked:false},adminToken)).status,200);
  const beforeReset = await api('/login',{username:name,password:pass,installation_id:d2,device_label:'Reset test box'});
  assert.equal(beforeReset.status,200);
  assert.equal((await api('/admin/reset-password',{user_id:clientId,password:changedPass},adminToken)).status,200);
  const resetDenied = await api('/validate',{session_id:beforeReset.json.session.session_id,installation_id:d2},beforeReset.json.auth.access_token);
  assert.ok(['session_replaced','unauthorized'].includes(resetDenied.json.error));
  assert.equal((await api('/login',{username:name,password:pass,installation_id:d2,device_label:'Old password'})).status,401);
  const afterReset = await api('/login',{username:name,password:changedPass,installation_id:d2,device_label:'New password'});
  assert.equal(afterReset.status,200);
  assert.equal((await api('/admin/set-access', { user_id: clientId, enabled: false, expires_at: null, plan_id: planId }, adminToken)).status, 200);
  const blocked = await api('/validate', {session_id:afterReset.json.session.session_id,installation_id:d2}, afterReset.json.auth.access_token);
  assert.equal(blocked.json.error, 'account_blocked');
  assert.equal((await api('/admin/set-access', { user_id: clientId, enabled: true, expires_at: new Date(Date.now() - 60000).toISOString(), plan_id: planId }, adminToken)).status, 200);
  const expired = await api('/login', { username: name, password: changedPass, installation_id: d1, device_label: 'Expired box' });
  assert.equal(expired.json.error, 'account_expired');
  console.log('PASS: live admin/create/login/replacement/refresh/logout/block/expiration/client permissions');
} finally {
  let clean = true;
  for (const uid of [...createdUsers].reverse()) {
    const deleted = await call(`/auth/v1/admin/users/${uid}`, undefined, service, 'DELETE');
    if (deleted.status !== 200) clean = false;
  }
  if (planId) {
    const removed = await call(`/rest/v1/sv_plans?id=eq.${planId}`, undefined, service, 'DELETE');
    if (removed.status !== 204) clean = false;
  }
  if (createdUsers.length) {
    for (const uid of createdUsers) assert.match(uid, /^[0-9a-f-]{36}$/);
    const values = createdUsers.map(u => `'${u}'`).join(',');
    const file = 'supabase/.temp/remote-test-cleanup.sql';
    writeFileSync(file, `delete from public.sv_audit where actor_id in (${values}) or account_id in (${values});`);
    try { cli(`db query --linked --file ${file}`); } finally { unlinkSync(file); }
  }
  if (!clean) throw new Error('Temporary test cleanup requires review');
  console.log('PASS: temporary integration data removed');
}
