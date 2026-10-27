"""Read private logs locally; emit only public code class names and fixed diagnostic flags."""
import pathlib
import re
import subprocess

state = subprocess.run(['systemctl', 'is-active', 'carimbai.service'], capture_output=True, text=True)
print('service.active=' + ('APROVADO' if state.returncode == 0 else 'REPROVADO'))
log = pathlib.Path('/var/log/carimbai/application.log')
content = ''
if log.is_file():
    with log.open('rb') as handle:
        handle.seek(max(0, log.stat().st_size - 128000))
        content = handle.read().decode('utf-8', errors='replace')
recent = subprocess.run(['journalctl', '-u', 'carimbai.service', '-n', '250', '--no-pager', '-o', 'cat'],
                        capture_output=True, text=True)
content += '\n' + recent.stdout
for name in sorted(set(re.findall(r'(?:Caused by: |^)([a-zA-Z_$][a-zA-Z0-9_.$]{4,160}(?:Exception|Error))\b', content, re.M))):
    print('startup.cause_class=' + name)
for bean in ['privacyNoticeService', 'passwordRecoveryService', 'entityManagerFactory',
             'flywayInitializer', 'pushNotificationService', 'jwtService', 'privacyProperties']:
    print('startup.bean.' + bean + '=' + ('REPROVADO' if "bean with name '" + bean + "'" in content else 'APROVADO'))
for label, fragment in {'migration_checksum': 'checksum mismatch', 'missing_placeholder': 'Could not resolve placeholder',
    'draft_publication': 'Review and replace draft notice', 'recovery_url': 'Invalid password recovery base URL',
    'unrecognized_property': 'Unrecognized field', 'database_privilege': 'permission denied for',
    'flyway_validation': 'Validate failed', 'missing_column': 'does not exist',
    'unsupported_property': 'Failed to bind properties'}.items():
    print('startup.' + label + '=' + ('REPROVADO' if fragment in content else 'APROVADO'))
