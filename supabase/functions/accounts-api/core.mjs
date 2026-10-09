const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function username(value) {
  if (typeof value !== 'string') throw new ApiError(400, 'invalid_username');
  const result = value.trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9._-]{2,39}$/.test(result)) throw new ApiError(400, 'invalid_username');
  return result;
}
export const accountEmail = name => `${username(name)}@accounts.soltavoz.invalid`;
export class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
function id(value) {
  if (typeof value !== 'string' || !uuid.test(value)) throw new ApiError(400, 'invalid_id');
  return value;
}
function text(value, max) {
  if (typeof value !== 'string' || !value.trim() || value.trim().length > max) throw new ApiError(400, 'invalid_text');
  return value.trim();
}
function password(value, creating = false) {
  if (typeof value !== 'string' || value.length < (creating ? 12 : 1) || value.length > 256) throw new ApiError(400, 'invalid_password');
  return value;
}
function access(body) {
  if (typeof body.enabled !== 'boolean' || !Object.hasOwn(body, 'expires_at') || !Object.hasOwn(body, 'plan_id')) {
    throw new ApiError(400, 'invalid_access');
  }
  const expires = body.expires_at;
  if (expires !== null && (typeof expires !== 'string' || !/^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(expires) || !Number.isFinite(Date.parse(expires)))) {
    throw new ApiError(400, 'invalid_expiration');
  }
  return { enabled: body.enabled, expires_at: expires === null ? null : new Date(expires).toISOString(),
    plan_id: body.plan_id === null ? null : id(body.plan_id) };
}
const sessionBody = body => ({ session: id(body.session_id), installation: id(body.installation_id) });
// Transport-independent handler. Service credentials live only in the adapter.
export function createHandler(store, { allowedOrigins = /** @type {string[]} */ ([]) } = {}) {
  return async request => {
    const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
    const origin = request.headers.get('origin');
    headers.Vary = 'Origin';
    if (origin && allowedOrigins.includes(origin)) {
      headers['Access-Control-Allow-Origin'] = origin;
      headers['Access-Control-Allow-Methods'] = 'POST, GET, OPTIONS';
      headers['Access-Control-Allow-Headers'] = 'authorization, content-type';
    }
    const respond = (status, body) => Response.json(body, { status, headers });
    try {
      if (origin && !allowedOrigins.includes(origin)) throw new ApiError(403, 'origin_not_allowed');
      if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers });
      const url = new URL(request.url);
      const route = url.pathname.split('/accounts-api').pop();
      if (request.method === 'GET' && route === '/health') return respond(200, { status: 'ok' });
      if (request.method !== 'POST') throw new ApiError(405, 'method_not_allowed');
      if (!request.headers.get('content-type')?.startsWith('application/json')) throw new ApiError(415, 'json_required');
      const raw = await request.text();
      if (raw.length > 8192) throw new ApiError(413, 'body_too_large');
      let body;
      try { body = JSON.parse(raw); } catch { throw new ApiError(400, 'invalid_json'); }
      if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ApiError(400, 'invalid_json');
      if (route === '/admin/login') {
        const email = text(body.email, 254).toLowerCase();
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new ApiError(400, 'invalid_email');
        const pass = password(body.password);
        await store.rateLimit(request, email);
        const auth = await store.signIn(email, pass);
        await store.requireAdmin(auth.user.id);
        return respond(200, { auth: store.publicAuth(auth) });
      }
      if (route === '/admin/refresh') {
        await store.rateLimit(request, 'admin-refresh');
        const auth = await store.refresh(text(body.refresh_token, 2048));
        await store.requireAdmin(auth.user.id);
        return respond(200, { auth: store.publicAuth(auth) });
      }
      if (route === '/login') {
        const email = accountEmail(body.username);
        const pass = password(body.password);
        const installation = id(body.installation_id);
        const label = text(body.device_label, 120);
        await store.rateLimit(request, email);
        const auth = await store.signIn(email, pass);
        // User identity always comes from Auth, not a user_id supplied by caller.
        const session = await store.openSession(auth.user.id, installation, label);
        return respond(200, { auth: store.publicAuth(auth), session, offline_lease: await store.signLease(session,auth.user.id) });
      }
      if (route === '/refresh') {
        const { session, installation } = sessionBody(body);
        const refresh = text(body.refresh_token, 2048);
        await store.rateLimit(request, 'refresh');
        const auth = await store.refresh(refresh);
        const lease = await store.validateSession(auth.user.id, session, installation);
        return respond(200, { auth: store.publicAuth(auth), session: lease, offline_lease: await store.signLease(lease,auth.user.id) });
      }
      const bearer = request.headers.get('authorization')?.match(/^Bearer (\S+)$/i)?.[1];
      if (!bearer) throw new ApiError(401, 'unauthorized');
      const user = await store.verifyUser(bearer);
      if (route === '/search') {
        const {session,installation} = sessionBody(body);
        const query = text(body.query,160).replace(/\s+/g,' ');
        if(query.length < 2 || /[\u0000-\u001f\u007f]/.test(query)) throw new ApiError(400,'invalid_query');
        await store.validateSession(user.id,session,installation);
        await store.rateLimit(request,'search:'+user.id);
        return respond(200,{songs:await store.search(query.toLocaleLowerCase('pt-BR'))});
      }
      if (route === '/validate' || route === '/logout') {
        const { session, installation } = sessionBody(body);
        if (route === '/logout') {
          await store.logout(user.id, session, installation);
          return respond(200, { ok: true });
        }
        const lease = await store.validateSession(user.id, session, installation);
        return respond(200, { session:lease, offline_lease: await store.signLease(lease,user.id) });
      }
      if (!route.startsWith('/admin/')) throw new ApiError(404, 'not_found');
      await store.requireAdmin(user.id);
      if (route === '/admin/me') return respond(200, { id: user.id, email: user.email });
      if (route === '/admin/accounts') return respond(200, { accounts: await store.accounts() });
      if (route === '/admin/plans') return respond(200, { plans: await store.plans() });
      if (route === '/admin/devices') return respond(200, { devices: await store.devices(id(body.user_id)) });
      if (route === '/admin/create-plan') return respond(201, { plan: await store.createPlan(text(body.name, 80), user.id) });
      if (route === '/admin/set-plan') {
        if (typeof body.enabled !== 'boolean') throw new ApiError(400, 'invalid_access');
        await store.setPlan(user.id, id(body.plan_id), text(body.name, 80), body.enabled);
        return respond(200, { ok: true });
      }
      if (route === '/admin/set-device') {
        if (typeof body.blocked !== 'boolean') throw new ApiError(400, 'invalid_access');
        await store.setDevice(user.id, id(body.device_id), body.blocked);
        return respond(200, { ok: true });
      }
      if (route === '/admin/reset-password') {
        await store.resetPassword(user.id, id(body.user_id), password(body.password, true));
        return respond(200, { ok: true });
      }
      if (route === '/admin/set-access') {
        await store.setAccess(user.id, id(body.user_id), access(body));
        return respond(200, { ok: true });
      }
      if (route === '/admin/create-account') {
        const name = username(body.username);
        const display = text(body.display_name, 120);
        const pass = password(body.password, true);
        const permission = access(body);
        const account = await store.createAccount(user.id, { username: name, display_name: display,
          password: pass, email: accountEmail(name), ...permission });
        return respond(201, { account });
      }
      throw new ApiError(404, 'not_found');
    } catch (error) {
      // Never return upstream errors, query text, passwords or service tokens.
      const safe = error instanceof ApiError;
      return respond(safe ? error.status : 503, { error: safe ? error.code : 'service_unavailable' });
    }
  };
}
