import http from 'node:http';
import { isIP } from 'node:net';

const validId = /^[A-Za-z0-9_-]{11}$/;

/** The endpoint is public: no shared 'secret' is embedded in the Android client.
 * A bounded cache, IP limit, concurrency cap and daily budget protect API usage.
 * Deploy one instance; use a shared limiter/cache before scaling horizontally. */
export function createSearchServer({ apiKey, fetchImpl = fetch, now = Date.now,
  dailyLimit = 80, rateLimit = 20, trustProxy = false }) {
  const cache = new Map();
  const pending = new Map();
  const clients = new Map();
  let day = '';
  let dailyCalls = 0;

  function reply(response, status, payload, retry = false) {
    response.writeHead(status, {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      ...(retry ? { 'Retry-After': '60' } : {}),
    });
    response.end(JSON.stringify(payload));
  }

  function allowed(ip) {
    const time = now();
    for (const [address, entry] of clients) {
      if (time - entry.started >= 60_000) clients.delete(address);
    }
    const entry = clients.get(ip) ?? { started: time, count: 0 };
    // Reject excess new addresses rather than evicting active limits.
    if (!clients.has(ip) && clients.size >= 5000) return false;
    clients.set(ip, entry);
    return ++entry.count <= rateLimit;
  }

  async function search(query) {
    const address = new URL('https://www.googleapis.com/youtube/v3/search');
    address.search = new URLSearchParams({
      part: 'snippet', type: 'video', videoEmbeddable: 'true', videoSyndicated: 'true',
      maxResults: '15', relevanceLanguage: 'pt', q: `${query} karaoke`, key: apiKey,
      fields: 'items(id/videoId,snippet/title,snippet/channelTitle)',
    }).toString();
    // Errors and URLs are never returned or logged: upstream URLs contain the key.
    const upstream = await fetchImpl(address, { signal: AbortSignal.timeout(10_000), redirect: 'error' });
    if (!upstream.ok) throw new Error('upstream');
    const json = await upstream.json();
    if (!Array.isArray(json.items)) throw new Error('response');
    const seen = new Set();
    return json.items.slice(0, 15).flatMap(item => {
      const id = item?.id?.videoId;
      if (typeof id !== 'string' || !validId.test(id) || seen.has(id) || !item.snippet) return [];
      seen.add(id);
      return [{ videoId: id, title: String(item.snippet.title ?? '').slice(0, 300),
        channel: String(item.snippet.channelTitle ?? '').slice(0, 200) }];
    });
  }

  const server = http.createServer(async (request, response) => {
    try {
      if (request.method !== 'GET') return reply(response, 405, { error: 'Método não permitido.' });
      if ((request.url?.length ?? 0) > 2048) return reply(response, 414, { error: 'Consulta muito longa.' });
      const url = new URL(request.url, 'http://localhost');
      if (url.pathname === '/health') return reply(response, 200, { status: 'ok' });
      if (url.pathname !== '/search') return reply(response, 404, { error: 'Rota não encontrada.' });
      const forwarded = String(request.headers['x-forwarded-for'] ?? '').split(',')[0].trim();
      const ip = trustProxy && isIP(forwarded) ? forwarded : request.socket.remoteAddress;
      if (!allowed(ip)) return reply(response, 429, { error: 'Aguarde antes de pesquisar novamente.' }, true);
      const rawQuery = (url.searchParams.get('q') ?? '').trim().replace(/\s+/g, ' ');
      if (rawQuery.length < 2 || rawQuery.length > 160 || /[\u0000-\u001f\u007f]/.test(rawQuery)) {
        return reply(response, 400, { error: 'Pesquise um nome com 2 a 160 caracteres.' });
      }
      const query = rawQuery.toLocaleLowerCase('pt-BR');
      const time = now();
      const cached = cache.get(query);
      if (cached && cached.expires > time) return reply(response, 200, { songs: cached.songs });
      if (cached) cache.delete(query);
      let result = pending.get(query);
      if (!result) {
        const currentDay = new Date(time).toISOString().slice(0, 10);
        if (day !== currentDay) { day = currentDay; dailyCalls = 0; }
        if (dailyCalls >= dailyLimit) return reply(response, 429, { error: 'Limite diário de pesquisas atingido.' }, true);
        if (pending.size >= 4) return reply(response, 429, { error: 'Busca ocupada. Tente novamente em instantes.' }, true);
        dailyCalls++;
        result = search(query).then(songs => {
          cache.set(query, { songs, expires: now() + 15 * 60_000 });
          while (cache.size > 200) cache.delete(cache.keys().next().value);
          return songs;
        }).finally(() => pending.delete(query));
        pending.set(query, result);
      }
      return reply(response, 200, { songs: await result });
    } catch {
      if (!response.headersSent) reply(response, 503, { error: 'Pesquisa temporariamente indisponível.' });
      else response.end();
    }
  });
  server.requestTimeout = 15_000;
  server.headersTimeout = 10_000;
  server.keepAliveTimeout = 5000;
  server.maxHeadersCount = 30;
  return server;
}
