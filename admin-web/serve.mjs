import http from 'node:http';
import { readFile } from 'node:fs/promises';
const files = new Map([
  ['/', ['index.html','text/html; charset=utf-8']], ['/app.mjs',['app.mjs','text/javascript; charset=utf-8']],
  ['/style.css',['style.css','text/css; charset=utf-8']], ['/brand.svg',['brand.svg','image/svg+xml']],
]);
const server = http.createServer(async (req,res) => {
  const entry = files.get(new URL(req.url,'http://localhost').pathname);
  if(!entry || !['GET','HEAD'].includes(req.method)) { res.writeHead(404); res.end(); return; }
  try {
    const body = await readFile(new URL(entry[0],import.meta.url));
    res.writeHead(200,{'Content-Type':entry[1],'Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
      'Content-Security-Policy':"default-src 'self'; connect-src https://luqgrtfqlywchlyasaog.supabase.co; img-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"});
    res.end(req.method === 'HEAD' ? undefined : body);
  } catch { res.writeHead(500); res.end('Não foi possível abrir o painel.'); }
});
server.listen(4173,'127.0.0.1',() => console.log('Painel SoltaVoz: http://127.0.0.1:4173'));
