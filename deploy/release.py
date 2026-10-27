"""Runs only on the authorized VPS. Backups and restored data never leave it."""
import hashlib
import json
import os
import pathlib
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import zipfile


class ReleaseFailure(Exception):
    pass


def command(label, args, **kwargs):
    result = subprocess.run(args, capture_output=True, timeout=300, **kwargs)
    if result.returncode:
        raise ReleaseFailure(label + '=REPROVADO')
    return result.stdout


def digest(path):
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()


def database_environment(env):
    url = urllib.parse.urlsplit(env['DB_URL'].removeprefix('jdbc:'))
    if url.scheme != 'postgresql' or not url.hostname or not url.path.strip('/'):
        raise ReleaseFailure('database.url=REPROVADO')
    query = urllib.parse.parse_qs(url.query)
    connection = os.environ.copy()
    connection.update(PGHOST=url.hostname, PGPORT=str(url.port or 5432),
                      PGDATABASE=urllib.parse.unquote(url.path.lstrip('/')),
                      PGUSER=env['DB_USERNAME'], PGPASSWORD=env['DB_PASSWORD'],
                      PGSSLMODE=env.get('DB_SSL_MODE', query.get('sslmode', ['require'])[0]),
                      PGCONNECT_TIMEOUT='10')
    return connection


def runtime_environment():
    restarted = False
    for _ in range(40):
        pid = int(command('service.pid', ['systemctl', 'show', 'carimbai.service',
                                         '--property', 'MainPID', '--value']) or b'0')
        try:
            if pid > 0:
                entries = pathlib.Path(f'/proc/{pid}/environ').read_bytes().split(b'\0')
                env = dict(item.decode().split('=', 1) for item in entries if b'=' in item)
                if all(env.get(key) for key in ['DB_URL', 'DB_USERNAME', 'DB_PASSWORD']):
                    return env
        except (FileNotFoundError, ProcessLookupError):
            pass
        if not restarted:
            command('service.recovery.start', ['sudo', '-n', 'systemctl', 'restart', 'carimbai.service'])
            restarted = True
        time.sleep(0.1)
    raise ReleaseFailure('service.configuration=REPROVADO')


def database_checks(connection):
    checks = {
        'version': "SELECT current_setting('server_version_num')::int / 10000 = 16",
        'tls': 'SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()',
        'migrations': 'SELECT count(*)>0 AND bool_and(success) FROM public.flyway_schema_history',
        'social_integrity': "SELECT NOT EXISTS (SELECT 1 FROM fidelity.customers WHERE provider_id IS NOT NULL AND btrim(provider_id)<>'' GROUP BY provider_id HAVING count(*)>1)",
        'email_integrity': "SELECT NOT EXISTS (SELECT 1 FROM fidelity.customers WHERE email IS NOT NULL AND btrim(email)<>'' GROUP BY lower(btrim(email)) HAVING count(*)>1)",
        'merchant_admins': "SELECT NOT EXISTS (SELECT 1 FROM core.merchants m WHERE m.active AND NOT EXISTS (SELECT 1 FROM core.staff_user_merchants l JOIN core.staff_users s ON s.id=l.staff_user_id WHERE l.merchant_id=m.id AND l.active AND s.active AND l.role='ADMIN'))"
    }
    for label, sql in checks.items():
        value = command('database.' + label, ['psql', '-X', '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql], env=connection)
        if value.strip() != b't':
            raise ReleaseFailure('database.' + label + '=REPROVADO')
        print('database.' + label + '=APROVADO', flush=True)


def restore_check(backup):
    # A separate PGDATA and private Unix socket, with TCP disabled. Never target the live DB.
    tools = pathlib.Path('/usr/lib/postgresql/16/bin')
    env = os.environ.copy()
    env['POSTGRES_PASSWORD'] = secrets.token_urlsafe(40)
    directory = tempfile.mkdtemp(prefix='carimbai-restore-', dir='/tmp')
    root = pathlib.Path(directory)
    data = root / 'data'
    try:
        root.chmod(0o700)
        password = root / 'password'
        password.write_text(env.pop('POSTGRES_PASSWORD'), encoding='ascii')
        password.chmod(0o600)
        command('backup.restore.init', [str(tools / 'initdb'), '-D', str(data),
            '--username=restore_operator', '--auth-local=scram-sha-256', '--auth-host=scram-sha-256',
            '--pwfile', str(password)])
        env.update(PGHOST=directory, PGPORT='5432', PGUSER='restore_operator',
                   PGDATABASE='postgres', PGPASSWORD=password.read_text(encoding='ascii'),
                   PGSSLMODE='disable', PGCONNECT_TIMEOUT='10')
        command('backup.restore.start', [str(tools / 'pg_ctl'), '-D', str(data),
            '-l', str(root / 'postgres.log'), '-o',
            f"-c listen_addresses='' -c unix_socket_directories='{directory}' -c shared_buffers=16MB -c max_connections=10", '-w', 'start'])
        command('backup.restore', [str(tools / 'pg_restore'), '--clean', '--if-exists',
            '--no-owner', '--no-privileges', '--exit-on-error', '-d', 'postgres', str(backup)], env=env)
        valid = command('backup.restore.schema', ['psql', '-X', '-At', '-v', 'ON_ERROR_STOP=1',
            '-c', 'SELECT count(*)>0 AND bool_and(success) FROM public.flyway_schema_history'], env=env).strip()
        if valid != b't':
            raise ReleaseFailure('backup.restore.schema=REPROVADO')
        print('backup.restore=APROVADO', flush=True)
    finally:
        if (data / 'postmaster.pid').is_file():
            command('backup.restore.stop', [str(tools / 'pg_ctl'), '-D', str(data), '-m', 'fast', '-w', 'stop'])
        shutil.rmtree(root)


def release(sha, expected_digest):
    if not re.fullmatch('[0-9a-f]{40}', sha) or not re.fullmatch('[0-9a-f]{64}', expected_digest):
        raise ReleaseFailure('release.identity=REPROVADO')
    if os.geteuid() == 0:
        raise ReleaseFailure('release.executor=REPROVADO')
    candidate = pathlib.Path('/opt/app/incoming') / sha / 'app.jar'
    current = pathlib.Path('/opt/app/carimbai-0.0.1-SNAPSHOT.jar')
    if candidate.is_symlink() or current.is_symlink() or digest(candidate) != expected_digest:
        raise ReleaseFailure('release.artifact=REPROVADO')
    with zipfile.ZipFile(candidate) as archive:
        if 'Spring-Boot-Version: 4.0.9' not in archive.read('META-INF/MANIFEST.MF').decode():
            raise ReleaseFailure('release.boot=REPROVADO')
    env = runtime_environment()
    if env.get('SPRING_PROFILES_ACTIVE') != 'prod':
        raise ReleaseFailure('service.profile=REPROVADO')
    port = int(env.get('SERVER_PORT', '1234'))
    if not 1 <= port <= 65535:
        raise ReleaseFailure('service.port=REPROVADO')
    connection = database_environment(env)
    database_checks(connection)
    size = int(command('backup.capacity', ['psql', '-X', '-At', '-v', 'ON_ERROR_STOP=1',
        '-c', 'SELECT pg_database_size(current_database())'], env=connection))
    if size > 64 * 1024 * 1024 or shutil.disk_usage(current.parent).free < size * 4 + current.stat().st_size * 2 + 1024**3:
        raise ReleaseFailure('backup.capacity=REPROVADO')
    backup_dir = pathlib.Path('/opt/app/backups') / (sha + '-' + str(time.time_ns()))
    backup_dir.mkdir(mode=0o700, parents=True)
    backup = backup_dir / 'database.dump'
    command('backup.database', ['pg_dump', '--format=custom', '--no-owner', '--no-privileges',
                               '--file', str(backup)], env=connection)
    backup.chmod(0o600)
    command('backup.archive', ['pg_restore', '--list', str(backup)])
    shutil.copy2(current, backup_dir / 'previous.jar')
    (backup_dir / 'previous.jar').chmod(0o600)
    restore_check(backup)
    manifest = {'commit': sha, 'previous_jar_sha256': digest(backup_dir / 'previous.jar'),
                'backup_sha256': digest(backup), 'candidate_sha256': expected_digest,
                'restoration_verified': True}
    manifest_path = backup_dir / 'manifest.json'
    manifest_path.write_text(json.dumps(manifest), encoding='ascii')
    manifest_path.chmod(0o600)
    original = current.stat()
    if candidate.stat().st_uid != original.st_uid:
        raise ReleaseFailure('release.ownership=REPROVADO')
    os.chmod(candidate, original.st_mode & 0o777)
    os.replace(candidate, current)
    command('release.restart', ['sudo', '-n', 'systemctl', 'restart', 'carimbai.service'])
    for _ in range(90):
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health', timeout=3) as response:
                healthy = json.load(response).get('status') == 'UP'
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/privacy/notice', timeout=3) as response:
                draft = json.load(response).get('published') is False
            if healthy and draft:
                print('release.health=APROVADO', flush=True)
                print('release.sha256=' + digest(current), flush=True)
                return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    # Do not automatically restore a known-vulnerable release or rewind privacy data.
    raise ReleaseFailure('release.health=REPROVADO; operator intervention required; backup preserved')


if __name__ == '__main__':
    os.umask(0o077)
    try:
        release(*sys.argv[1:])
    except ReleaseFailure as error:
        raise SystemExit(str(error)) from None
    except Exception:
        raise SystemExit('release=REPROVADO; private error details withheld; inspect on VPS') from None
