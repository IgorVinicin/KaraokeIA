// Separate from Android release signing. Keep private key in private backup.
import { generateKeyPairSync } from 'node:crypto';
import { existsSync,mkdirSync,writeFileSync } from 'node:fs';
const secret='.signing/offline-signing.env', pub='supabase/offline-public-key.txt';
if(existsSync(secret)||existsSync(pub)) {
  if(!existsSync(secret)||!existsSync(pub)) throw new Error('Offline key pair incomplete; restore the matching backup.');
  console.log('Existing offline signing key preserved.');
} else {
  const pair=generateKeyPairSync('rsa',{modulusLength:3072});
  mkdirSync('.signing',{recursive:true});
  writeFileSync(secret,'OFFLINE_SIGNING_KEY='+pair.privateKey.export({type:'pkcs8',format:'der'}).toString('base64')+'\n',{mode:0o600});
  writeFileSync(pub,pair.publicKey.export({type:'spki',format:'der'}).toString('base64')+'\n');
  console.log('Offline signing key generated. Private file excluded from Git; public key ready for APK.');
}
