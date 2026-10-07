// Exposes the emulator's ADB-forwarded HTTP control on the PC's LAN address.
const http = require('node:http');
const host = process.argv[2];
if (!host) throw new Error('Informe o IP local do computador.');
const server = http.createServer((req, res) => {
  const upstream = http.request({hostname: '127.0.0.1', port: 18765,
    path: req.url, method: req.method, headers: {...req.headers, host: '127.0.0.1:18765'}}, response => {
    res.writeHead(response.statusCode, response.headers);
    response.pipe(res);
  });
  upstream.setTimeout(600000, () => upstream.destroy(new Error('Tempo de resposta esgotado')));
  upstream.on('error', () => {
    if (!res.headersSent) res.writeHead(502, {'Content-Type': 'text/plain; charset=utf-8'});
    res.end('Não foi possível acessar o Karaokê. Abra o app no emulador e execute novamente o .bat.');
  });
  req.pipe(upstream);
});
server.on('error', error => { console.error(error.message); process.exit(1); });
server.listen(8765, host, () => console.log(`Controle disponível em http://${host}:8765`));
