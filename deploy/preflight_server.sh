#!/usr/bin/env bash
# Read-only diagnostics. Never print service arguments or environment values.
set -euo pipefail
systemctl is-active carimbai.service
df -Pk /opt/app
for tool in python3 pg_dump pg_restore psql docker; do
  if command -v "$tool" >/dev/null 2>&1; then
    printf 'tool.%s=present\n' "$tool"
  else
    printf 'tool.%s=absent\n' "$tool"
  fi
done
pid="$(systemctl show carimbai.service --property MainPID --value)"
test "$pid" -gt 0
python3 - "$pid" <<'PY'
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import urllib.request
import zipfile

pid = int(sys.argv[1])
entries = pathlib.Path(f'/proc/{pid}/environ').read_bytes().split(b'\0')
env = dict(item.decode().split('=', 1) for item in entries if b'=' in item)
required = ['DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'CARIMBAI_JWT_SECRET',
            'CARIMBAI_HMAC_SECRET', 'CARIMBAI_RESEND_API_KEY', 'CARIMBAI_MAIL_FROM',
            'CARIMBAI_VAPID_PUBLIC_KEY', 'CARIMBAI_VAPID_PRIVATE_KEY',
            'CARIMBAI_GOOGLE_CLIENT_ID', 'CARIMBAI_APPLE_CLIENT_ID',
            'CARIMBAI_FACEBOOK_APP_ID', 'CARIMBAI_FACEBOOK_APP_SECRET']
for key in required:
    print(f'env.{key}.present={bool(env.get(key))}')
profile = env.get('SPRING_PROFILES_ACTIVE', 'not-set')
print('profile=' + (profile if profile in ['prod', 'dev', 'local', 'not-set'] else 'other'))
print('db.ssl.explicit=' + str(bool(env.get('DB_SSL_MODE'))))
print('db.loopback=' + str(any(host in env.get('DB_URL', '')
      for host in ['//localhost:', '//127.0.0.1:', '//[::1]:'])))
java = os.path.realpath(f'/proc/{pid}/exe')
subprocess.run([java, '-version'], check=True)
jar = pathlib.Path('/opt/app/carimbai-0.0.1-SNAPSHOT.jar')
with jar.open('rb') as handle:
    print('jar.sha256=' + hashlib.file_digest(handle, 'sha256').hexdigest())
with zipfile.ZipFile(jar) as archive:
    manifest = archive.read('META-INF/MANIFEST.MF').decode()
    for line in manifest.splitlines():
        if line.startswith(('Spring-Boot-Version:', 'Build-Jdk-Spec:')):
            print(line)
with urllib.request.urlopen('http://127.0.0.1:1234/actuator/health', timeout=10) as response:
    print('local.health=' + str(json.load(response).get('status') == 'UP'))
PY
if command -v docker >/dev/null 2>&1; then
  docker ps --format 'container.image={{.Image}} state={{.State}}'
fi
