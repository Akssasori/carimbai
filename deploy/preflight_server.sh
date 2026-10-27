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
import urllib.parse
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
port = int(env.get('SERVER_PORT', '1234'))
if not 1 <= port <= 65535:
    raise SystemExit('service.health=REPROVADO')
with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health', timeout=10) as response:
    print('local.health=' + str(json.load(response).get('status') == 'UP'))
url = urllib.parse.urlsplit(env['DB_URL'].removeprefix('jdbc:'))
if url.scheme != 'postgresql' or not url.hostname or not url.path.strip('/'):
    raise SystemExit('database.connection=REPROVADO')
query = urllib.parse.parse_qs(url.query)
connection = os.environ.copy()
connection.update(PGHOST=url.hostname, PGPORT=str(url.port or 5432),
                  PGDATABASE=urllib.parse.unquote(url.path.lstrip('/')),
                  PGUSER=env['DB_USERNAME'], PGPASSWORD=env['DB_PASSWORD'],
                  PGSSLMODE=env.get('DB_SSL_MODE', query.get('sslmode', ['require'])[0]),
                  PGCONNECT_TIMEOUT='10')
checks = {
    'version': "SELECT current_setting('server_version_num')::int / 10000 = 16",
    'tls': 'SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()',
    'migrations': 'SELECT count(*)>0 AND bool_and(success) FROM public.flyway_schema_history',
    'social_integrity': '''SELECT NOT EXISTS (
        SELECT 1 FROM fidelity.customers WHERE provider_id IS NOT NULL AND btrim(provider_id)<>''
        GROUP BY provider_id HAVING count(*)>1)''',
    'email_integrity': '''SELECT NOT EXISTS (
        SELECT 1 FROM fidelity.customers WHERE email IS NOT NULL AND btrim(email)<>''
        GROUP BY lower(btrim(email)) HAVING count(*)>1)''',
    'merchant_admins': '''SELECT NOT EXISTS (
        SELECT 1 FROM core.merchants m WHERE m.active AND NOT EXISTS (
        SELECT 1 FROM core.staff_user_merchants l JOIN core.staff_users s ON s.id=l.staff_user_id
        WHERE l.merchant_id=m.id AND l.active AND s.active AND l.role='ADMIN'))'''
}
passed = True
for name, sql in checks.items():
    result = subprocess.run(['psql', '-X', '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql],
                            env=connection, capture_output=True, text=True, timeout=30)
    ok = result.returncode == 0 and result.stdout.strip() == 't'
    print(f'database.{name}=' + ('APROVADO' if ok else 'REPROVADO'))
    passed = passed and ok
if not passed:
    raise SystemExit(1)
print('logs.directory=' + ('APROVADO' if pathlib.Path('/var/log/carimbai').is_dir() else 'REPROVADO'))
tools = pathlib.Path('/usr/lib/postgresql/16/bin')
if not all((tools / name).is_file() for name in ['initdb', 'pg_ctl', 'pg_restore']):
    raise SystemExit('backup.tools=REPROVADO')
print('backup.tools=APROVADO')
PY
