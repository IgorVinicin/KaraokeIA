import { createSearchServer } from './server.mjs';

const key = (process.env.YOUTUBE_API_KEY ?? '').trim();
if (!/^AIza[A-Za-z0-9_-]{35}$/.test(key)) {
  console.error('Configure YOUTUBE_API_KEY em server/.env com uma chave válida.');
  process.exit(1);
}
function positiveInteger(name, fallback) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isSafeInteger(value) || value < 1) throw new Error(`Configuração inválida: ${name}`);
  return value;
}
const server = createSearchServer({
  apiKey: key,
  dailyLimit: positiveInteger('MAX_UPSTREAM_SEARCHES_PER_DAY', 80),
  rateLimit: positiveInteger('REQUESTS_PER_IP_PER_MINUTE', 20),
  trustProxy: process.env.TRUST_PROXY === 'true',
});
server.listen(positiveInteger('PORT', 8080), process.env.HOST ?? '127.0.0.1', () => {
  console.log('SoltaVoz: serviço de busca iniciado.');
});
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
