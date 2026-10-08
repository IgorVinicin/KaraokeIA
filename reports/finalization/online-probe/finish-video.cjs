// Exercise the real YouTube ended event by seeking near the end of the test video.
(async () => {
  const targets = await (await fetch('http://127.0.0.1:19222/json/list')).json();
  const target = targets.find(item => item.type === 'page');
  if (!target) throw new Error('No active player');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  const timeout = setTimeout(() => { socket.close(); throw new Error('Player did not respond'); }, 10000);
  socket.addEventListener('message', event => {
    const response = JSON.parse(event.data);
    if (response.id !== 1) return;
    clearTimeout(timeout);
    if (response.error || response.result?.exceptionDetails) {
      console.error('Failed to seek to end'); process.exitCode = 1;
    } else {
      console.log(response.result.result.value);
    }
    socket.close();
  });
  socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
    returnByValue: true,
    expression: `(() => { const duration = player.getDuration(); if (!(duration > 3)) throw Error('Duration unavailable'); player.seekTo(duration - 2, true); player.playVideo(); return JSON.stringify({duration, seekTo: duration - 2}); })()`
  }}));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
