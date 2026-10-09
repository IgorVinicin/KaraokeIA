import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler, ApiError, accountEmail } from '../functions/accounts-api/core.mjs';
const uid = '00000000-0000-4000-8000-000000000001';
const sid = '00000000-0000-4000-8000-000000000002';
const device = '00000000-0000-4000-8000-000000000003';
function fixture(overrides = {}) {
  const calls = [];
  const store = {
    signLease: async () => 'signed.test',
    rateLimit: async () => {},
    signIn: async (email, pass) => { calls.push(['signIn', email, pass]); return { user: { id: uid }, access_token: 'token', refresh_token: 'refresh', secret: 'hidden' }; },
    publicAuth: a => ({ access_token: a.access_token, refresh_token: a.refresh_token }),
    verifyUser: async token => { calls.push(['verifyUser', token]); return { id: uid }; },
    requireAdmin: async user => calls.push(['admin', user]),
    openSession: async (...args) => { calls.push(['open', ...args]); return { session_id: sid }; },
    validateSession: async (...args) => { calls.push(['validate', ...args]); return { session_id: sid }; },
    refresh: async () => ({ user: { id: uid }, access_token: 'new', refresh_token: 'new-refresh' }),
    logout: async (...args) => calls.push(['logout', ...args]),
    accounts: async () => [], plans: async () => [], devices: async () => [],
    setAccess: async (...args) => calls.push(['setAccess', ...args]),
    createAccount: async (actor, body) => { calls.push(['create', actor, body]); return { user_id: uid, username: body.username }; },
    ...overrides,
  };
  const handle = createHandler(store);
  async function post(path, body, token = 'verified-token') {
    return handle(new Request(`https://example.test/accounts-api${path}`, { method: 'POST', headers: {
      'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}),
    }, body: JSON.stringify(body) }));
  }
  return { calls, post, handle };
}
test('normalizes username with no public email input', () => {
  assert.equal(accountEmail(' Cliente.1 '), 'cliente.1@accounts.soltavoz.invalid');
  assert.throws(() => accountEmail('x@other.test'));
});
test('login uses verified Auth identity and filters token response', async () => {
  const f = fixture();
  const r = await f.post('/login', { username: 'Cliente', password: 'password', installation_id: device, device_label: 'TV', user_id: sid }, null);
  assert.equal(r.status, 200);
  assert.deepEqual(f.calls[1], ['open', uid, device, 'TV']);
  assert.equal(JSON.stringify(await r.json()).includes('hidden'), false);
  assert.equal(r.headers.get('cache-control'), 'no-store');
});
test('bad input cannot reach Auth or open a session', async () => {
  const f = fixture(); const r = await f.post('/login', { username: 'user', password: 'a', installation_id: 'bad', device_label: 'TV' });
  assert.equal(r.status, 400); assert.deepEqual(f.calls, []);
});
test('admin endpoints reject missing bearer', async () => {
  const f = fixture(); const r = await f.post('/admin/accounts', {}, null);
  assert.equal(r.status, 401); assert.deepEqual(f.calls, []);
});
test('client cannot create accounts even with valid Auth token', async () => {
  const f = fixture({ requireAdmin: async () => { throw new ApiError(403, 'admin_required'); } });
  const r = await f.post('/admin/create-account', {});
  assert.equal(r.status, 403); assert.equal(f.calls.some(c => c[0] === 'create'), false);
});
test('creation validates explicit expiration and strong initial password', async () => {
  const f = fixture(); const body = { username: 'client', display_name: 'Client', password: 'LongPassword123!', enabled: true, expires_at: null, plan_id: null };
  const r = await f.post('/admin/create-account', body);
  assert.equal(r.status, 201); assert.equal(f.calls.at(-1)[1], uid);
  assert.equal(f.calls.at(-1)[2].email, 'client@accounts.soltavoz.invalid');
  assert.equal((await f.post('/admin/create-account', { ...body, password: 'short' })).status, 400);
  assert.equal((await f.post('/admin/create-account', { ...body, expires_at: 'tomorrow' })).status, 400);
});
test('access mutation refuses omitted fields to avoid unintended unlimited access', async () => {
  const f = fixture(); assert.equal((await f.post('/admin/set-access', { user_id: uid, enabled: true })).status, 400);
  assert.equal(f.calls.some(c => c[0] === 'setAccess'), false);
});
test('revoked session is an explicit denial, never offline permission', async () => {
  const f = fixture({ validateSession: async () => { throw new ApiError(409, 'session_replaced'); } });
  const r = await f.post('/validate', { session_id: sid, installation_id: device });
  assert.equal(r.status, 409); assert.deepEqual(await r.json(), { error: 'session_replaced' });
});
test('refresh validates existing session and never opens a replacement', async () => {
  const f = fixture(); const r = await f.post('/refresh', { refresh_token: 'refresh', session_id: sid, installation_id: device }, null);
  assert.equal(r.status, 200); assert.deepEqual(f.calls[0], ['validate', uid, sid, device]);
  assert.equal(f.calls.some(c => c[0] === 'open'), false);
});
test('upstream errors do not reveal credentials', async () => {
  const f = fixture({ verifyUser: async () => { throw new Error('service-key-password'); } });
  const r = await f.post('/admin/me', {}); assert.equal(r.status, 503);
  assert.equal((await r.text()).includes('service-key'), false);
});
test('persistent limiter stops login before password check', async () => {
  const f = fixture({ rateLimit: async () => { throw new ApiError(429, 'too_many_attempts'); } });
  const r = await f.post('/login', { username: 'user', password: 'a', installation_id: device, device_label: 'TV' });
  assert.equal(r.status, 429); assert.deepEqual(f.calls, []);
});
test('logout derives user from token', async () => {
  const f = fixture(); const r = await f.post('/logout', { session_id: sid, installation_id: device, user_id: device });
  assert.equal(r.status, 200); assert.deepEqual(f.calls.at(-1), ['logout', uid, sid, device]);
});
test('admin login requires admin membership before returning tokens', async () => {
  const f = fixture({ requireAdmin: async () => { throw new ApiError(403, 'admin_required'); } });
  const r = await f.post('/admin/login', { email: 'admin@example.test', password: 'Password' }, null);
  assert.equal(r.status, 403); assert.deepEqual(await r.json(), { error: 'admin_required' });
  assert.equal(f.calls.some(c => c[0] === 'open'), false);
});
test('CORS permits only configured panel origins, including errors and preflight', async () => {
  const handle = createHandler({}, { allowedOrigins: ['http://127.0.0.1:4173'] });
  const preflight = await handle(new Request('https://example.test/accounts-api/admin/me', { method:'OPTIONS', headers:{ origin:'http://127.0.0.1:4173' } }));
  assert.equal(preflight.status,204); assert.equal(preflight.headers.get('access-control-allow-origin'),'http://127.0.0.1:4173');
  const forbidden = await handle(new Request('https://example.test/accounts-api/health', { headers:{ origin:'https://other.test' } }));
  assert.equal(forbidden.status,403); assert.equal(forbidden.headers.get('access-control-allow-origin'),null);
});
test('device mutation rejects untyped blocked state', async () => {
  const f = fixture(); assert.equal((await f.post('/admin/set-device',{device_id:device,blocked:'false'})).status,400);
});
test('password reset rejects weak passwords before mutation', async () => {
  const f = fixture(); assert.equal((await f.post('/admin/reset-password',{user_id:uid,password:'short'})).status,400);
});
test('admin refresh cannot return tokens to a removed admin', async () => {
  const f = fixture({ requireAdmin: async () => { throw new ApiError(403,'admin_required'); } });
  assert.equal((await f.post('/admin/refresh',{refresh_token:'refresh'},null)).status,403);
});
test('search validates session before using YouTube',async () => {
 let searched=false;
 const f=fixture({validateSession:async()=>{throw new ApiError(409,'session_replaced');},search:async()=>{searched=true;return [];}});
 const r=await f.post('/search',{session_id:sid,installation_id:device,query:'karaoke'});
 assert.equal(r.status,409);assert.equal(searched,false);
});
