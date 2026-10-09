"""Verify local secrets/configuration files were not bundled in the APK."""
from pathlib import Path
import re
import sys
import zipfile

project = Path(__file__).resolve().parent.parent
apk = Path(sys.argv[1])
env_file = project / 'app/.env'
key = None
if env_file.is_file():
    match = re.search(r"(?m)^\s*YOUTUBE_(?:API_)?KEY\s*=\s*[\"']?(AIza[A-Za-z0-9_-]{35})[\"']?\s*$",
                      env_file.read_text(encoding='utf-8-sig'))
    if match:
        key = match.group(1)
signatures = [] if key is None else [key.encode('utf-8'), key.encode('utf-16-le')]
offline_file = project / '.signing/offline-signing.env'
if offline_file.is_file():
    match = re.search(r'(?m)^OFFLINE_SIGNING_KEY=(\S+)\s*$', offline_file.read_text(encoding='utf-8-sig'))
    if match:
        private_key = match.group(1)
        import base64
        signatures.extend([private_key.encode('utf-8'), private_key.encode('utf-16-le'), base64.b64decode(private_key)])
with zipfile.ZipFile(apk) as archive:
    for entry in archive.infolist():
        name = entry.filename.lower()
        if Path(name).name in ('.env', 'signing.properties', 'youtube_config', 'offline-signing.env') or name.endswith(('.jks', '.keystore')):
            raise SystemExit('FAIL: arquivo privado encontrado no APK.')
        data = archive.read(entry)
        if any(secret in data for secret in signatures):
            raise SystemExit('FAIL: chave privada do arquivo local encontrada no APK.')
print('PASS: APK sem arquivos privados' + (' e sem a chave do .env.' if key else '; chave local ausente para comparação.'))
