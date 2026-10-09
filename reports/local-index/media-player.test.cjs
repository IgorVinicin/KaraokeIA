// Run from any directory: node reports/local-index/media-player.test.cjs
// Exercises the actual embedded script without network or a WebView.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root,
  'app/src/main/java/com/araujo/karaoke_ia/online/YouTubePlayer.kt'), 'utf8');
const match = source.match(/<script>(.*?)<\/script>/s);
assert.ok(match, 'Player must contain an inline iframe API script');
const script = match[1].replaceAll('$videoId', 'abcdefghijk')
  .replaceAll('$origin', 'https://test.app');
const events = [];
let config;
let dimensions;
const sandbox = {
  KaraokePlayer: { event: (...event) => events.push(event) },
  window: { addEventListener() {} }, innerWidth: 1280, innerHeight: 720,
  document: {
    documentElement: { style: {} }, body: { style: {} },
    getElementById: () => ({ style: {} })
  },
  YT: { Player: function (id, options) {
    assert.equal(id, 'player');
    config = options;
    return { setSize: (width, height) => { dimensions = [width, height]; } };
  } }
};
vm.createContext(sandbox);
vm.runInContext(script, sandbox, { timeout: 1000 });
sandbox.onYouTubeIframeAPIReady();
assert.equal(config.videoId, 'abcdefghijk');
assert.equal(config.playerVars.origin, 'https://test.app');
assert.equal(config.playerVars.autoplay, 0);
// Browser global variables are properties of window.
sandbox.window.player = sandbox.player;
config.events.onReady();
assert.deepEqual(dimensions, [1280, 720]);
for (const state of [3, 1, 2, 0]) config.events.onStateChange({ data: state });
config.events.onAutoplayBlocked();
config.events.onError({ data: 5 });
assert.deepEqual(events, [
  ['ready', 0], ['state', 3], ['state', 1], ['playing', 1],
  ['state', 2], ['state', 0], ['ended', 0], ['blocked', 0], ['error', 5]
]);
console.log('PASS iframe: ready, buffering, playing, paused, ended, autoplayBlocked, error; origin, autoplay and resize');
