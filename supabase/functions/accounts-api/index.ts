import { createClient } from 'npm:@supabase/supabase-js@2.57.4';
import { ApiError, createHandler } from './core.mjs';
import { signLease } from './lease.mjs';

const base = Deno.env.get('SUPABASE_URL')!;
const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const anonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
const options = { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } };
const db = createClient(base, serviceKey, options);
const authClient = () => createClient(base, anonKey, options);
const known = new Set(['account_blocked', 'account_expired', 'device_blocked', 'session_replaced', 'admin_required', 'account_not_found', 'device_not_found', 'plan_not_found']);
function check(error: any) {
  if (!error) return;
  if (known.has(error.message)) throw new ApiError(error.message === 'admin_required' ? 403 : 409, error.message);
  if (error.code === '23505') throw new ApiError(409, 'already_exists');
  throw new ApiError(503, 'service_unavailable');
}
async function rpc(name: string, args: Record<string, unknown>) {
  const { data, error } = await db.rpc(name, args); check(error); return data;
}
const handler = createHandler({
  signLease: (session: any, user: string) => signLease(session,user,Deno.env.get('OFFLINE_SIGNING_KEY')),
  async search(query: string) {
    const cached = await rpc('sv_search_cache',{p_query:query,p_songs:null});
    if(cached) return cached;
    if(!await rpc('sv_search_budget',{})) throw new ApiError(429,'search_daily_limit');
    const key = Deno.env.get('YOUTUBE_API_KEY');
    if(!key) throw new ApiError(503,'search_not_configured');
    const url = new URL('https://www.googleapis.com/youtube/v3/search');
    url.search = new URLSearchParams({part:'snippet',type:'video',videoEmbeddable:'true',videoSyndicated:'true',
      maxResults:'15',relevanceLanguage:'pt',q:query+' karaoke',key,fields:'items(id/videoId,snippet/title,snippet/channelTitle)'}).toString();
    let json;
    try { const response = await fetch(url,{signal:AbortSignal.timeout(10000),redirect:'error'}); if(!response.ok) throw new Error(); json=await response.json(); }
    catch { throw new ApiError(503,'search_unavailable'); }
    const seen = new Set();
    const songs = (Array.isArray(json.items)?json.items:[]).slice(0,15).flatMap((item:any) => {
      const id=item?.id?.videoId;
      if(typeof id!=='string'||!/^[A-Za-z0-9_-]{11}$/.test(id)||seen.has(id))return [];
      seen.add(id); return [{videoId:id,title:String(item.snippet?.title??'').slice(0,300),channel:String(item.snippet?.channelTitle??'').slice(0,200)}];
    });
    await rpc('sv_search_cache',{p_query:query,p_songs:songs}); return songs;
  },
  async rateLimit(_request: Request, identity: string) {
    // Global persistent cap + per-identity cap, independent of spoofable client IPs.
    const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(identity)))].map(v => v.toString(16).padStart(2, '0')).join('');
    const allowed = await rpc('sv_auth_attempt', { p_key: hash });
    if (!allowed) throw new ApiError(429, 'too_many_attempts');
  },
  async signIn(email: string, password: string) {
    const { data, error } = await authClient().auth.signInWithPassword({ email, password });
    if (error || !data.session || !data.user) throw new ApiError(401, 'invalid_credentials');
    return { ...data.session, user: data.user };
  },
  async refresh(refresh_token: string) {
    const { data, error } = await authClient().auth.refreshSession({ refresh_token });
    if (error || !data.session || !data.user) throw new ApiError(401, 'unauthorized');
    return { ...data.session, user: data.user };
  },
  publicAuth(auth: any) { return { access_token: auth.access_token, refresh_token: auth.refresh_token, expires_at: auth.expires_at }; },
  async verifyUser(token: string) {
    const { data, error } = await db.auth.getUser(token);
    if (error || !data.user) throw new ApiError(401, 'unauthorized');
    return data.user;
  },
  async requireAdmin(user: string) {
    const { data, error } = await db.from('sv_admins').select('user_id').eq('user_id', user).maybeSingle();
    check(error); if (!data) throw new ApiError(403, 'admin_required');
  },
  openSession: (user: string, installation: string, label: string) => rpc('sv_open_session', { p_user: user, p_installation: installation, p_label: label }),
  validateSession: (user: string, session: string, installation: string) => rpc('sv_validate_session', { p_user: user, p_session: session, p_installation: installation }),
  logout: (user: string, session: string, installation: string) => rpc('sv_close_session', { p_user: user, p_session: session, p_installation: installation }),
  setAccess: (admin: string, user: string, access: any) => rpc('sv_set_access', { p_admin: admin, p_user: user, p_enabled: access.enabled, p_expires: access.expires_at, p_plan: access.plan_id }),
  setDevice: (admin: string, device: string, blocked: boolean) => rpc('sv_set_device', { p_admin: admin, p_device: device, p_blocked: blocked }),
  setPlan: (admin: string, plan: string, name: string, enabled: boolean) => rpc('sv_set_plan', { p_admin: admin, p_plan: plan, p_name: name, p_enabled: enabled }),
  async resetPassword(admin: string, user: string, password: string) {
    await rpc('sv_revoke_account_sessions', { p_admin: admin, p_user: user });
    const { error } = await db.auth.admin.updateUserById(user, { password });
    if (error) throw new ApiError(503, 'password_reset_failed');
    const audit = await db.from('sv_audit').insert({ actor_id: admin, account_id: user, action: 'password_reset_completed' });
    check(audit.error);
  },
  async accounts() {
    const { data, error } = await db.from('sv_accounts').select('user_id,username,display_name,enabled,expires_at,plan_id,created_at').order('created_at', { ascending: false }).limit(200);
    check(error); return data;
  },
  async plans() { const { data, error } = await db.from('sv_plans').select('*').order('name').limit(200); check(error); return data; },
  async devices(user: string) { const { data, error } = await db.from('sv_devices').select('*').eq('user_id', user).order('last_seen_at', { ascending: false }).limit(200); check(error); return data; },
  async createPlan(name: string, actor: string) {
    return await rpc('sv_create_plan', { p_admin: actor, p_name: name });
  },
  async createAccount(actor: string, input: any) {
    const { data, error } = await db.auth.admin.createUser({ email: input.email, password: input.password, email_confirm: true });
    if (error || !data.user) {
      if (error?.code === 'email_exists') throw new ApiError(409, 'already_exists');
      throw new ApiError(400, 'account_creation_failed');
    }
    const userId = data.user.id;
    try {
      // Account + audit are committed together by SQL. Auth is a separate service.
      return await rpc('sv_create_account', { p_admin: actor, p_user: userId, p_username: input.username,
        p_display: input.display_name, p_enabled: input.enabled, p_expires: input.expires_at, p_plan: input.plan_id });
    } catch (failure) {
      const cleanup = await db.auth.admin.deleteUser(userId);
      if (cleanup.error) throw new ApiError(503, 'account_cleanup_required');
      throw failure;
    }
  },
}, { allowedOrigins: (Deno.env.get('ADMIN_PANEL_ORIGINS') ?? 'http://localhost:4173,http://127.0.0.1:4173').split(',').map(v => v.trim()).filter(Boolean) });
Deno.serve(handler);
