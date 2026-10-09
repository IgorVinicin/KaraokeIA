(async()=>{
const targets=await(await fetch('http://127.0.0.1:19222/json/list')).json();
const target=targets.find(t=>t.type==='page'); const ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));let id=0;const pending=new Map();const contexts=[];
ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.executionContextCreated')contexts.push(m.params.context);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id)}});
function call(method,params={}){return new Promise(resolve=>{const n=++id;pending.set(n,resolve);ws.send(JSON.stringify({id:n,method,params}))})}
await call('Runtime.enable');await new Promise(r=>setTimeout(r,500));
for(const c of contexts){if(c.origin==='https://com.araujo.karaoke_ia')await call('Runtime.evaluate',{contextId:c.id,expression:`document.querySelector('style').textContent='html,body{margin:0;width:100vw;height:100vh;overflow:hidden;background:black}#player{position:fixed;top:0;left:0;width:100vw;height:100vh;border:0}'`});const result=await call('Runtime.evaluate',{contextId:c.id,returnByValue:true,expression:`JSON.stringify({url:location.href,viewport:[innerWidth,innerHeight],iframe:document.querySelector('iframe')?.getBoundingClientRect().toJSON(),state:window.player?.getPlayerState?.(),layout:[document.documentElement,document.body,document.querySelector('iframe')].filter(Boolean).map(e=>({tag:e.tagName,rect:e.getBoundingClientRect().toJSON(),height:getComputedStyle(e).height,position:getComputedStyle(e).position})),mode:document.compatMode,video:[...document.querySelectorAll('video')].map(v=>({paused:v.paused,time:v.currentTime,ready:v.readyState,error:v.error?.code}))})`});console.log(JSON.stringify(result.result?.result?.value||result));}
ws.close();
})().catch(e=>{console.error(e);process.exit(1)});


