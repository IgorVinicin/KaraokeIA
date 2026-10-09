import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,verify} from 'node:crypto';
import {signLease} from '../functions/accounts-api/lease.mjs';
test('offline authorization signature binds account, session, device and deadline',async () => {
 const keys=generateKeyPairSync('rsa',{modulusLength:2048});
 const session={session_id:'session',installation_id:'box',generation:3,validated_at:'2026-10-09T10:00:00Z',offline_until:'2026-10-10T10:00:00Z'};
 const token=await signLease(session,'account',keys.privateKey.export({type:'pkcs8',format:'der'}).toString('base64'));
 const [payload,signature]=token.split('.');
 assert.ok(verify('sha256',Buffer.from(payload),keys.publicKey,Buffer.from(signature,'base64url')));
 assert.equal(verify('sha256',Buffer.from(payload+'A'),keys.publicKey,Buffer.from(signature,'base64url')),false);
 const claims=JSON.parse(Buffer.from(payload,'base64url').toString());
 assert.equal(claims.user_id,'account'); assert.equal(claims.installation_id,'box'); assert.equal(claims.session_id,'session'); assert.equal(claims.audience,'soltavoz-android');
});
