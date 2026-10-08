const fs=require('fs');
(async()=>{
const targets=await(await fetch('http://127.0.0.1:19222/json/list')).json();const target=targets.find(t=>t.type==='page');
const ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));let id=0;const pending=new Map();const contexts=[];
ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.executionContextCreated')contexts.push(m.params.context);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id)}});
function call(method,params={}){return new Promise(resolve=>{const n=++id;pending.set(n,resolve);ws.send(JSON.stringify({id:n,method,params}))})}
await call('Runtime.enable');await new Promise(r=>setTimeout(r,200));let result;
for(let retry=0;retry<10;retry++){
 const snapshots=[];
 for(const c of contexts){const r=await call('Runtime.evaluate',{contextId:c.id,returnByValue:true,expression:`JSON.stringify({url:location.href,viewport:[innerWidth,innerHeight],iframe:document.querySelector('iframe')?.getBoundingClientRect().toJSON(),state:window.player?.getPlayerState?.(),video:[...document.querySelectorAll('video')].map(v=>({paused:v.paused,time:v.currentTime,ready:v.readyState,error:v.error?.code}))})`});if(r.result?.result?.value)snapshots.push(JSON.parse(r.result.result.value));}
 result={stage:process.argv[2],snapshots};
 const main=snapshots.find(s=>s.state!==undefined);const video=snapshots.find(s=>s.video?.length)?.video[0];
 if(main?.iframe?.height>=200&&((process.argv[2]==='playing'&&main.state===1&&video?.time>0&&!video.paused)||(process.argv[2]==='paused'&&main.state===2&&video?.paused)||(process.argv[2]==='ready'&&main.state===-1)))break;
 await new Promise(r=>setTimeout(r,500));
}
fs.writeFileSync(`reports/finalization/online-probe/player-${process.argv[2]}.json`,JSON.stringify(result,null,2));
const main=result.snapshots.find(s=>s.state!==undefined),video=result.snapshots.find(s=>s.video?.length)?.video[0];
if(!main?.iframe||main.iframe.height<200)throw Error('Player viewport is too small');
if(process.argv[2]==='playing'&&(main.state!==1||!(video?.time>0)||video.paused))throw Error('Playback not advancing');
if(process.argv[2]==='paused'&&(main.state!==2||!video?.paused))throw Error('Playback not paused');
console.log('PASS',JSON.stringify(result));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
