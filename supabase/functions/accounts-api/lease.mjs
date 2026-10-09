const encode = value => btoa(String.fromCharCode(...new Uint8Array(value))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
let imported;
export async function signLease(session, userId, privateKey) {
  if (!privateKey) throw new Error('Offline signing not configured');
  imported ??= crypto.subtle.importKey('pkcs8', Uint8Array.from(atob(privateKey), c => c.charCodeAt(0)), { name:'RSASSA-PKCS1-v1_5',hash:'SHA-256' }, false, ['sign']);
  const payload = encode(new TextEncoder().encode(JSON.stringify({ version:1, audience:'soltavoz-android',
    user_id:userId, session_id:session.session_id, installation_id:session.installation_id,
    generation:session.generation, validated_at:session.validated_at, offline_until:session.offline_until })));
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5',await imported,new TextEncoder().encode(payload));
  return `${payload}.${encode(signature)}`;
}
