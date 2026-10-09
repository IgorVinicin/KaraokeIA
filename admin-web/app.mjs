export const API = 'https://luqgrtfqlywchlyasaog.supabase.co/functions/v1/accounts-api';
export function accountStatus(account, now = Date.now()) {
  if (!account.enabled) return 'blocked';
  return account.expires_at && Date.parse(account.expires_at) <= now ? 'expired' : 'active';
}
const messages = { unauthorized:'Sua sessão terminou. Entre novamente.', invalid_credentials:'E-mail ou senha incorretos.', admin_required:'Esta conta não tem acesso de administrador.', already_exists:'Esse usuário ou nome de plano já existe.', invalid_password:'Use uma senha com pelo menos 12 caracteres.', too_many_attempts:'Muitas tentativas. Aguarde um minuto.', service_unavailable:'Não foi possível concluir. Tente novamente.', origin_not_allowed:'Este endereço do painel ainda não foi autorizado.', account_creation_failed:'Não foi possível criar a conta. Confira os dados.', password_reset_failed:'A senha não foi alterada. As sessões foram encerradas; tente novamente.' };
if (typeof document !== 'undefined') start();
function start() {
  const $ = selector => document.querySelector(selector);
  let auth = null, accounts = [], plans = [], view = 'accounts', submitModal = null, busy = false, refreshing = null, revision = 0;
  const node = (tag, text, cls) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; };
  const errorText = e => messages[e.code] ?? e.message ?? 'Não foi possível concluir.';
  function notice(text, error = false) { $('#notice').textContent = text; $('#notice').className = error ? 'error' : ''; }
  function signOut() {
    revision++;
    auth = null; accounts = []; plans = []; $('#workspace').hidden = true; $('#login').hidden = false;
    $('#accounts-list').replaceChildren(); $('#plans-list').replaceChildren(); $('#modal').close(); $('#modal-body').replaceChildren();
    $('#login-form').reset(); $('#admin-email').textContent = '';
  }
  async function send(route, body, token) {
    let response;
    try { response = await fetch(API + route, { method:'POST', headers:{ 'content-type':'application/json', ...(token ? { authorization:`Bearer ${token}` } : {}) }, body:JSON.stringify(body), signal:AbortSignal.timeout(20000) }); }
    catch { throw new Error('Sem conexão com o servidor. Confira a internet e tente novamente.'); }
    const data = await response.json();
    if (!response.ok) { const e = new Error(messages[data.error] ?? 'Não foi possível concluir a operação.'); e.code = data.error; throw e; }
    return data;
  }
  async function api(route, body = {}) {
    if (!auth) throw new Error('Entre novamente.');
    const requestRevision = revision;
    if (auth.expires_at * 1000 <= Date.now() + 60000) {
      refreshing ??= send('/admin/refresh', { refresh_token:auth.refresh_token }).then(data => {
        if(requestRevision !== revision) throw new Error('Sessão encerrada.'); auth = data.auth;
      }).finally(() => { refreshing = null; });
      try { await refreshing; } catch (e) { if (e.code === 'unauthorized' || e.code === 'admin_required') signOut(); throw e; }
    }
    if (!auth || requestRevision !== revision) throw new Error('Sessão encerrada.');
    try { const result = await send(route, body, auth.access_token); if(requestRevision !== revision) throw new Error('Sessão encerrada.'); return result; }
    catch (e) { if (e.code === 'unauthorized' || e.code === 'admin_required') { signOut(); $('#login-error').textContent = errorText(e); } throw e; }
  }
  async function action(button, callback) {
    if (button.disabled) return;
    button.disabled = true;
    try { await callback(); } catch (e) { notice(errorText(e), true); } finally { button.disabled = false; }
  }
  const dateText = value => value ? new Date(value).toLocaleString('pt-BR', { dateStyle:'short', timeStyle:'short' }) : 'Sem vencimento';
  const planName = id => plans.find(p => p.id === id)?.name ?? 'Sem plano';
  function button(label, callback) { const b = node('button', label, 'secondary'); b.type = 'button'; b.addEventListener('click', () => action(b, callback)); return b; }
  function render() {
    const count = status => accounts.filter(a => accountStatus(a) === status).length;
    $('#total').textContent = accounts.length; $('#active-count').textContent = count('active'); $('#blocked-count').textContent = count('blocked'); $('#expired-count').textContent = count('expired');
    const query = $('#search').value.toLocaleLowerCase('pt-BR'); const filter = $('#status-filter').value;
    const list = accounts.filter(a => `${a.display_name} ${a.username}`.toLocaleLowerCase('pt-BR').includes(query) && (filter === 'all' || accountStatus(a) === filter));
    const tbody = $('#accounts-list'); tbody.replaceChildren(); $('#accounts-empty').hidden = list.length > 0;
    for (const a of list) {
      const row = node('tr'), client = node('td'); client.append(node('strong', a.display_name), node('small', a.username)); row.append(client);
      const status = accountStatus(a), state = node('td'); state.append(node('span', {active:'Ativa',blocked:'Bloqueada',expired:'Vencida'}[status], `badge ${status}`));
      row.append(state, node('td', planName(a.plan_id)), node('td', dateText(a.expires_at)));
      const cell = node('td'), actions = node('div', undefined, 'actions');
      actions.append(button('Editar acesso', () => accountDialog(a)), button('Aparelhos', () => deviceDialog(a)), button('Nova senha', () => passwordDialog(a)));
      cell.append(actions); row.append(cell); tbody.append(row);
    }
    const grid = $('#plans-list'); grid.replaceChildren();
    if (!plans.length) grid.append(node('p', 'Crie seu primeiro plano para organizar as contas.', 'muted'));
    for (const p of plans) {
      const card = node('article', undefined, 'plan-card'); card.append(node('h2', p.name), node('span', p.enabled ? 'Disponível' : 'Desativado', `badge ${p.enabled ? '' : 'blocked'}`), node('p', `${accounts.filter(a => a.plan_id === p.id).length} contas neste plano`, 'muted'), button('Editar plano', () => planDialog(p))); grid.append(card);
    }
  }
  async function load() {
    const [a,p] = await Promise.all([api('/admin/accounts'), api('/admin/plans')]);
    if (!auth) return; accounts = a.accounts; plans = p.plans; render();
  }
  function openModal(title, content, callback, save = 'Salvar') {
    if (busy) return;
    $('#modal-title').textContent = title; $('#modal-body').replaceChildren(content); $('#modal-error').textContent = '';
    $('#save-modal').textContent = save; $('#modal-actions').hidden = !callback; submitModal = callback;
    if (!$('#modal').open) $('#modal').showModal();
  }
  function field(form, title, name, type = 'text', value = '', required = true) {
    const label = node('label', title), input = node('input'); input.name = name; input.type = type; input.value = value; input.required = required; label.append(input); form.append(label); return input;
  }
  function check(form, title, name, checked) {
    const label = node('label', undefined, 'check'), input = node('input'); input.type = 'checkbox'; input.name = name; input.checked = checked; label.append(input, document.createTextNode(title)); form.append(label); return input;
  }
  function accountDialog(a = null) {
    const content = node('div');
    const name = field(content, 'Usuário', 'username', 'text', a?.username ?? ''); name.pattern = '[a-zA-Z0-9][a-zA-Z0-9._-]{2,39}'; name.maxLength = 40; name.readOnly = !!a;
    if (!a) { field(content,'Nome do cliente','display_name').maxLength = 120; const pass = field(content,'Senha inicial','password','password'); pass.minLength = 12; pass.maxLength = 256; pass.autocomplete = 'new-password'; }
    const label = node('label', 'Plano'), select = node('select'); select.name = 'plan_id'; const empty = node('option','Sem plano'); empty.value = ''; select.append(empty);
    for (const p of plans.filter(p => p.enabled || p.id === a?.plan_id)) { const option = node('option',p.name + (p.enabled ? '' : ' (desativado)')); option.value = p.id; select.append(option); }
    select.value = a?.plan_id ?? ''; label.append(select); content.append(label);
    check(content, 'Acesso ativado', 'enabled', a?.enabled ?? false);
    const unlimited = check(content, 'Sem data de vencimento', 'unlimited', !a?.expires_at);
    let local = '';
    if (a?.expires_at) { const d = new Date(a.expires_at); local = new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16); }
    const expires = field(content,'Vencimento (seu horário local)','expires_at','datetime-local',local,false);
    const sync = () => { expires.disabled = unlimited.checked; expires.required = !unlimited.checked; }; sync(); unlimited.addEventListener('change',sync);
    content.append(node('p', a ? 'Salvar alterações encerra as sessões atuais. O cliente deverá entrar novamente.' : 'Guarde a senha inicial para entregar ao cliente.', 'muted small'));
    openModal(a ? `Acesso de ${a.username}` : 'Nova conta', content, async form => {
      const value = form.elements;
      const permission = { enabled:value.enabled.checked, expires_at:value.unlimited.checked ? null : new Date(value.expires_at.value).toISOString(), plan_id:value.plan_id.value || null };
      if (a) await api('/admin/set-access',{user_id:a.user_id,...permission});
      else await api('/admin/create-account',{ username:value.username.value,display_name:value.display_name.value,password:value.password.value,...permission });
      notice(a ? 'Acesso atualizado.' : 'Conta criada.'); await load();
    });
  }
  function planDialog(p = null) {
    const content = node('div'); field(content,'Nome do plano','name','text',p?.name ?? '').maxLength = 80;
    if (p) { check(content,'Disponível para novas escolhas','enabled',p.enabled); content.append(node('p','Desativar o plano não bloqueia as contas que já o utilizam.','muted small')); }
    openModal(p ? 'Editar plano' : 'Novo plano',content,async form => {
      if(p) await api('/admin/set-plan',{plan_id:p.id,name:form.elements.name.value,enabled:form.elements.enabled.checked});
      else await api('/admin/create-plan',{name:form.elements.name.value});
      notice('Plano salvo.'); await load();
    });
  }
  function passwordDialog(a) {
    const content = node('div'); content.append(node('p',`Defina uma nova senha para ${a.username}. As sessões atuais serão encerradas. Aparelhos offline só perceberão a revogação ao reconectar ou terminar a tolerância.`, 'muted'));
    const pass = field(content,'Nova senha','password','password'); pass.minLength = 12; pass.maxLength = 256; pass.autocomplete = 'new-password';
    const repeat = field(content,'Confirme a senha','confirmation','password'); repeat.autocomplete = 'new-password';
    openModal('Redefinir senha',content,async form => {
      if(form.elements.password.value !== form.elements.confirmation.value) throw new Error('As senhas precisam ser iguais.');
      await api('/admin/reset-password',{user_id:a.user_id,password:form.elements.password.value}); notice('Senha redefinida. Entregue a nova senha ao cliente.');
    },'Redefinir senha');
  }
  async function deviceDialog(a) {
    const { devices } = await api('/admin/devices',{user_id:a.user_id}); if (!auth) return;
    const content = node('div'); content.append(node('p','Bloquear impede novos logins deste aparelho e revoga sua sessão. Desbloquear permite um novo login.', 'muted small'));
    if(!devices.length) content.append(node('p','Esta conta ainda não entrou em nenhum aparelho.','empty'));
    for(const d of devices) {
      const box = node('article',undefined,'device'); box.append(node('strong',d.label), node('p',`Última conexão: ${dateText(d.last_seen_at)}`,'small muted'), node('span',d.blocked ? 'Bloqueado' : 'Liberado',`badge ${d.blocked ? 'blocked' : ''}`));
      box.append(button(d.blocked ? 'Desbloquear' : 'Bloquear aparelho',async () => {
        if (!confirm(`${d.blocked ? 'Desbloquear' : 'Bloquear'} ${d.label}?`)) return;
        await api('/admin/set-device',{device_id:d.id,blocked:!d.blocked}); await deviceDialog(a); notice('Aparelho atualizado.');
      })); content.append(box);
    }
    openModal(`Aparelhos de ${a.username}`,content,null);
  }
  $('#login-form').addEventListener('submit',e => { e.preventDefault(); const form = e.currentTarget; action(form.querySelector('button'),async () => {
    $('#login-error').textContent = '';
    try { const data = await send('/admin/login',{email:form.elements.email.value,password:form.elements.password.value}); auth = data.auth;
      $('#admin-email').textContent = form.elements.email.value; form.elements.password.value = ''; await load(); $('#login').hidden = true; $('#workspace').hidden = false; notice('');
    } catch(err) { auth = null; form.elements.password.value = ''; $('#login-error').textContent = errorText(err); }
  }); });
  $('#logout').addEventListener('click',signOut);
  $('#refresh').addEventListener('click',e => action(e.currentTarget,async () => { await load(); notice('Dados atualizados.'); }));
  $('#search').addEventListener('input',render); $('#status-filter').addEventListener('change',render);
  $('#new-account').addEventListener('click',() => accountDialog()); $('#new-plan').addEventListener('click',() => planDialog());
  for(const b of document.querySelectorAll('[data-view]')) b.addEventListener('click',() => {
    view = b.dataset.view; $('#accounts-view').hidden = view !== 'accounts'; $('#plans-view').hidden = view !== 'plans';
    $('#page-title').textContent = view === 'accounts' ? 'Contas' : 'Planos'; $('#page-description').textContent = view === 'accounts' ? 'Gerencie quem pode entrar no palco.' : 'Uma organização para cada tipo de cliente.';
    for(const n of document.querySelectorAll('[data-view]')) n.classList.toggle('active',n === b);
  });
  const close = () => { if(!busy) { $('#modal').close(); $('#modal-body').replaceChildren(); submitModal = null; } };
  $('#close-modal').addEventListener('click',close); $('#cancel-modal').addEventListener('click',close);
  $('#modal').addEventListener('cancel',e => { e.preventDefault(); close(); });
  $('#modal-form').addEventListener('submit',async e => {
    e.preventDefault(); if(busy || !submitModal) return; busy = true; $('#save-modal').disabled = true; $('#modal-error').textContent = '';
    try { await submitModal(e.currentTarget); busy = false; close(); }
    catch(err) { $('#modal-error').textContent = errorText(err); }
    finally { busy = false; $('#save-modal').disabled = false; }
  });
}
