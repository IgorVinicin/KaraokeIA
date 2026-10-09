import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';
import { API, accountStatus } from '../app.mjs';
let server, browser;
before(async () => {
  server = spawn(process.execPath, [new URL('../serve.mjs',import.meta.url).pathname.replace(/^\/([A-Z]:)/,'$1')], { stdio:['ignore','pipe','pipe'] });
  await Promise.race([once(server.stdout,'data'), once(server,'exit').then(() => { throw new Error('Preview server exited'); }), new Promise((_,reject) => setTimeout(() => reject(new Error('Preview timeout')),10000).unref())]);
  browser = await chromium.launch({ channel:'chrome',headless:true });
  await mkdir(new URL('../../reports/unified/',import.meta.url),{ recursive:true });
});
after(async () => { await browser?.close(); server?.kill(); });
test('account states reflect blocking and expiration', () => {
  assert.equal(accountStatus({enabled:false,expires_at:null}),'blocked');
  assert.equal(accountStatus({enabled:true,expires_at:'2020-01-01T00:00:00Z'}),'expired');
  assert.equal(accountStatus({enabled:true,expires_at:null}),'active');
});
async function pageFixture(viewport = {width:1440,height:1000}) {
  const page = await browser.newPage({viewport});
  const calls = [], errors = [];
  page.on('pageerror',err => errors.push(err.message));
  const account = {user_id:'00000000-0000-4000-8000-000000000001', username:'cliente',display_name:'Cliente Teste',enabled:true,expires_at:null,plan_id:null};
  const device = {id:'00000000-0000-4000-8000-000000000002',label:'TV da sala',blocked:false,last_seen_at:new Date().toISOString()};
  await page.route(API+'/**',async route => {
    const request = route.request(), path = request.url().slice(API.length);
    if(request.method() === 'OPTIONS') { await route.fulfill({status:204,headers:{'access-control-allow-origin':'http://127.0.0.1:4173','access-control-allow-methods':'POST','access-control-allow-headers':'content-type,authorization'}}); return; }
    const body = request.postDataJSON(); calls.push({path,body});
    let result = {ok:true};
    if(path === '/admin/login') result = {auth:{access_token:'test-access',refresh_token:'test-refresh',expires_at:Math.floor(Date.now()/1000)+3600}};
    if(path === '/admin/accounts') result = {accounts:[account]};
    if(path === '/admin/plans') result = {plans:[]};
    if(path === '/admin/devices') result = {devices:[device]};
    if(path === '/admin/set-device') device.blocked = body.blocked;
    if(path === '/admin/set-access') Object.assign(account,body);
    await route.fulfill({status:200,contentType:'application/json',headers:{'access-control-allow-origin':'http://127.0.0.1:4173'},body:JSON.stringify(result)});
  });
  await page.goto('http://127.0.0.1:4173');
  const login = async () => {
    await page.locator('#login-form input[name=email]').fill('admin@example.test');
    await page.locator('#login-form input[name=password]').fill('TestingPassword123!');
    await page.getByRole('button',{name:'Entrar no painel'}).click();
    await page.locator('#workspace').waitFor({state:'visible'});
  };
  return {page,calls,errors,login,account};
}
test('desktop admin edits access, lists devices, blocks, and resets password',async () => {
  const f = await pageFixture(); await f.login();
  assert.equal(await f.page.locator('#login-form input[name=password]').inputValue(),'');
  await f.page.screenshot({path:new URL('../../reports/unified/painel-contas.png',import.meta.url).pathname.replace(/^\/([A-Z]:)/,'$1')});
  await f.page.getByRole('button',{name:'Editar acesso'}).click();
  await f.page.getByLabel('Acesso ativado').uncheck();
  await f.page.getByRole('button',{name:'Salvar',exact:true}).click();
  await f.page.locator('#modal').waitFor({state:'hidden'});
  assert.equal(f.calls.find(c => c.path === '/admin/set-access').body.enabled,false);
  await f.page.getByRole('button',{name:'Aparelhos',exact:true}).click();
  await f.page.getByText('TV da sala',{exact:true}).waitFor();
  f.page.once('dialog',dialog => dialog.accept());
  await f.page.getByRole('button',{name:'Bloquear aparelho'}).click();
  await f.page.getByRole('button',{name:'Desbloquear',exact:true}).waitFor();
  assert.equal(f.calls.find(c => c.path === '/admin/set-device').body.blocked,true);
  await f.page.getByRole('button',{name:'Fechar',exact:true}).click();
  await f.page.getByRole('button',{name:'Nova senha'}).click();
  await f.page.getByLabel('Nova senha',{exact:true}).fill('ChangedPassword123!');
  await f.page.getByLabel('Confirme a senha').fill('DifferentPassword');
  await f.page.getByRole('button',{name:'Redefinir senha',exact:true}).click();
  assert.equal(f.calls.some(c => c.path === '/admin/reset-password'),false);
  await f.page.getByLabel('Confirme a senha').fill('ChangedPassword123!');
  await f.page.getByRole('button',{name:'Redefinir senha',exact:true}).click();
  await f.page.locator('#modal').waitFor({state:'hidden'});
  assert.equal(f.calls.some(c => c.path === '/admin/reset-password'),true);
  assert.deepEqual(f.errors,[]); await f.page.close();
});
test('mobile creates account with explicit validity and renders server text safely',async () => {
  const f = await pageFixture({width:390,height:844}); f.account.display_name = '<img src=x onerror=alert(1)>';
  await f.login(); assert.equal(await f.page.locator('#accounts-list img').count(),0);
  assert.ok(await f.page.getByText('<img src=x onerror=alert(1)>',{exact:true}).isVisible());
  assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), JSON.stringify(await f.page.evaluate(() => [...document.querySelectorAll('body *')].filter(e => e.getBoundingClientRect().right > window.innerWidth && getComputedStyle(e).display !== 'none').map(e => [e.tagName,e.className,e.getBoundingClientRect().right]).slice(0,15))));
  await f.page.screenshot({path:new URL('../../reports/unified/painel-celular.png',import.meta.url).pathname.replace(/^\/([A-Z]:)/,'$1')});
  await f.page.getByRole('button',{name:'+ Nova conta'}).click();
  await f.page.getByLabel('Usuário',{exact:true}).fill('novo_cliente'); await f.page.getByLabel('Nome do cliente').fill('Novo Cliente');
  await f.page.getByLabel('Senha inicial').fill('InitialPassword123!'); await f.page.getByLabel('Acesso ativado').check();
  await f.page.getByLabel('Sem data de vencimento').uncheck(); await f.page.getByLabel('Vencimento (seu horário local)').fill('2030-12-31T20:00');
  await f.page.getByRole('button',{name:'Salvar',exact:true}).click(); await f.page.locator('#modal').waitFor({state:'hidden'});
  const body = f.calls.find(c => c.path === '/admin/create-account').body;
  assert.equal(body.enabled,true); assert.match(body.expires_at,/Z$/); assert.equal(body.plan_id,null);
  await f.page.getByRole('button',{name:'Sair do painel'}).click(); await f.page.locator('#login').waitFor({state:'visible'});
  assert.equal(await f.page.locator('#accounts-list tr').count(),0); assert.deepEqual(f.errors,[]); await f.page.close();
});
