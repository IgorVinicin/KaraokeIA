const {chromium}=require('C:/Users/admin.igor/AppData/Local/npm-cache/_npx/db89d7302a373f10/node_modules/playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const base=process.env.KARAOKE_QA_URL||'http://127.0.0.1:18765';
const output=__dirname;
(async()=>{
 const browser=await chromium.launch({headless:true});const context=await browser.newContext();
 const initial=await(await context.request.get(base+'/api/queue')).json();let added=null;
 const report={base,initialQueue:initial.map(s=>s.code),viewports:[],errors:[]};
 try{
  report.fonts=[];for(const name of ['body','heading']){const response=await context.request.get(base+'/fonts/'+name);const bytes=await response.body();report.fonts.push({name,status:response.status(),bytes:bytes.length,signature:bytes.subarray(0,4).toString('hex')});if(!response.ok()||bytes.length<1000)throw Error('Font resource unavailable '+name)}
  const songs=await(await context.request.get(base+'/api/songs',{timeout:240000})).json();report.songs=songs.length;
  if(!songs.length)throw Error('Catálogo vazio');
  for(const width of [320,390,768]){
   const page=await context.newPage();await page.setViewportSize({width,height:844});page.on('pageerror',e=>report.errors.push(e.message));
   await page.goto(base,{waitUntil:'domcontentloaded'});await page.waitForFunction(()=>document.querySelectorAll('#songs .song').length>0,null,{timeout:240000});await page.waitForTimeout(3500);
   await page.evaluate(()=>document.fonts.ready);const stats=await page.evaluate(()=>({fontsLoaded:document.fonts.status==='loaded',bodyFont:document.fonts.check('500 14px Manrope'),headingFont:document.fonts.check('800 20px Unbounded'),overflow:document.documentElement.scrollWidth>innerWidth,cards:document.querySelectorAll('#songs .song').length,artwork:document.querySelectorAll('#songs .art img').length,dimensions:[...document.querySelectorAll('#songs .art img')].map(i=>[i.naturalWidth,i.naturalHeight])}));
   if(stats.overflow)throw Error('Overflow '+width);
   await page.screenshot({path:path.join(output,'qr-'+width+'.png'),fullPage:true});report.viewports.push({width,...stats});await page.close();
  }
  const page=await context.newPage();await page.setViewportSize({width:390,height:844});await page.goto(base);await page.waitForFunction(()=>document.querySelectorAll('#songs .song').length>0,null,{timeout:240000});
  const accentSong=songs.find(s=>/[^\u0000-\u007f]/.test(s.title+' '+s.artist))||songs[0];
  const query=accentSong.title.normalize('NFD').replace(/[\u0300-\u036f]/g,'');await page.locator('#search').fill(query);await page.waitForTimeout(200);report.accentSearch={query,matches:await page.locator('#songs .song').count()};if(!report.accentSearch.matches)throw Error('Busca normalizada sem resultado');
  const candidate=songs.find(s=>!initial.some(q=>q.code===s.code));if(!candidate)throw Error('Sem faixa disponível para adição sem alterar fila existente');
  await page.locator('#search').fill(candidate.code);const row=page.locator('#songs .song').filter({has:page.locator('.code',{hasText:'CÓD. '+candidate.code})}).first();added=candidate.code;await row.locator('.add').click();
  await page.waitForFunction(code=>queue.some(s=>s.code===code),candidate.code);report.added=candidate.code;await page.locator('#queue-tab').click();await page.getByRole('button',{name:'Remover '+candidate.title+' da fila',exact:true}).click();await page.waitForFunction(code=>!queue.some(s=>s.code===code),candidate.code);added=null;report.removed=true;
  const art=songs.find(s=>s.code===initial[0]?.code)||songs[0];const first=await context.request.get(base+'/api/artwork?code='+encodeURIComponent(art.code),{timeout:120000});report.cache={code:art.code,status:first.status(),headers:first.headers()};if(first.ok()){const bytes=await first.body();const second=await context.request.get(base+'/api/artwork?code='+encodeURIComponent(art.code),{timeout:120000});report.cache.sameBytes=crypto.createHash('sha256').update(bytes).digest('hex')===crypto.createHash('sha256').update(await second.body()).digest('hex');if(!report.cache.sameBytes)throw Error('Cache alterou miniatura')}
  await page.close();
 }catch(e){report.errors.push(e.message);throw e}finally{
  if(added){try{await context.request.get(base+'/api/remove?code='+encodeURIComponent(added),{timeout:15000})}catch(e){report.errors.push('Rollback unavailable for '+added+': '+e.message)}};
  try{const final=await(await context.request.get(base+'/api/queue',{timeout:15000})).json();report.finalQueue=final.map(s=>s.code);report.preservedInitial=initial.every(s=>final.some(q=>q.code===s.code))}catch(e){report.errors.push('Final queue unavailable: '+e.message)};fs.writeFileSync(path.join(output,'qr-qa.json'),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report));
 }
})().catch(e=>{console.error(e);process.exitCode=1});
