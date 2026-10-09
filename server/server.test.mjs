import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createSearchServer } from './server.mjs';

const SECRET = 'server-only-secret';
const fixture = { items: [{ id: { videoId: 'abcdefghijk' }, snippet: { title: 'Evidências · Karaokê', channelTitle: 'Canal' } },
  { id: { videoId: 'invalid' }, snippet: { title: 'Ignorar' } }] };

async function start(t, options = {}) {
  const server = createSearchServer({ apiKey: SECRET, fetchImpl: async () => ({ ok: true, json: async () => fixture }), ...options });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); }));
  return `http://127.0.0.1:${server.address().port}`;
}

test('searches karaoke by name, caches repeats and returns only public song metadata', async t => {
  let calls = 0;
  const base = await start(t, { fetchImpl: async address => {
    calls++;
    assert.equal(address.hostname, 'www.googleapis.com');
    assert.equal(address.searchParams.get('q'), 'evidências karaoke');
    assert.equal(address.searchParams.get('key'), SECRET);
    assert.equal(address.searchParams.get('videoEmbeddable'), 'true');
    return { ok: true, json: async () => fixture };
  } });
  for (const q of ['Evidências', ' EVIDÊNCIAS ']) {
    const response = await fetch(`${base}/search?q=${encodeURIComponent(q)}`);
    assert.equal(response.status, 200);
    const body = await response.text();
    assert.ok(!body.includes(SECRET));
    assert.deepEqual(JSON.parse(body).songs, [{ videoId: 'abcdefghijk', title: 'Evidências · Karaokê', channel: 'Canal' }]);
  }
  assert.equal(calls, 1);
});

test('validates queries and methods before spending upstream quota', async t => {
  let calls = 0;
  const base = await start(t, { fetchImpl: async () => { calls++; throw new Error('unexpected'); } });
  for (const q of ['', 'a', 'a'.repeat(161), 'ab\u0000']) {
    assert.equal((await fetch(`${base}/search?q=${encodeURIComponent(q)}`)).status, 400);
  }
  assert.equal((await fetch(`${base}/search?q=musica`, { method: 'POST' })).status, 405);
  assert.equal((await fetch(`${base}/unknown`)).status, 404);
  assert.equal((await fetch(`${base}/health`)).status, 200);
  assert.equal(calls, 0);
});

test('never leaks the credential when YouTube errors and does not cache failures', async t => {
  let calls = 0;
  const base = await start(t, { fetchImpl: async () => { calls++; throw new Error(`https://upstream?key=${SECRET}`); } });
  for (let i = 0; i < 2; i++) {
    const response = await fetch(`${base}/search?q=musica`);
    assert.equal(response.status, 503);
    assert.ok(!(await response.text()).includes(SECRET));
  }
  assert.equal(calls, 2);
});

test('deduplicates concurrent requests for the same song', async t => {
  let calls = 0;
  const base = await start(t, { fetchImpl: async () => {
    calls++;
    await new Promise(resolve => setTimeout(resolve, 30));
    return { ok: true, json: async () => fixture };
  } });
  const responses = await Promise.all(Array.from({ length: 4 }, () => fetch(`${base}/search?q=musica`)));
  assert.ok(responses.every(response => response.status === 200));
  await Promise.all(responses.map(response => response.text()));
  assert.equal(calls, 1);
});

test('limits quota globally, still serves cached results, resets on the next UTC day', async t => {
  let time = Date.UTC(2026, 9, 9, 12);
  const base = await start(t, { dailyLimit: 1, now: () => time });
  assert.equal((await fetch(`${base}/search?q=primeira`)).status, 200);
  assert.equal((await fetch(`${base}/search?q=segunda`)).status, 429);
  assert.equal((await fetch(`${base}/search?q=primeira`)).status, 200);
  time += 24 * 60 * 60_000;
  assert.equal((await fetch(`${base}/search?q=segunda`)).status, 200);
});

test('rate limits by socket IP; spoofed forwarded headers cannot bypass limits', async t => {
  let time = Date.UTC(2026, 9, 9, 12);
  const base = await start(t, { rateLimit: 1, now: () => time });
  assert.equal((await fetch(`${base}/search?q=musica`, { headers: { 'X-Forwarded-For': '1.1.1.1' } })).status, 200);
  assert.equal((await fetch(`${base}/search?q=outra`, { headers: { 'X-Forwarded-For': '2.2.2.2' } })).status, 429);
  time += 60_001;
  assert.equal((await fetch(`${base}/search?q=outra`)).status, 200);
});

test('expires cache entries and refreshes the song search', async t => {
  let calls = 0;
  let time = Date.UTC(2026, 9, 9, 12);
  const base = await start(t, { now: () => time, fetchImpl: async () => { calls++; return { ok: true, json: async () => fixture }; } });
  assert.equal((await fetch(`${base}/search?q=musica`)).status, 200);
  time += 15 * 60_000;
  assert.equal((await fetch(`${base}/search?q=musica`)).status, 200);
  assert.equal(calls, 2);
});
