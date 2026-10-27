import hashlib
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import release


class ReleaseTests(unittest.TestCase):
    def test_database_defaults_to_tls_without_exposing_credentials(self):
        env = release.database_environment({'DB_URL': 'jdbc:postgresql://localhost:5432/example',
                                            'DB_USERNAME': 'app', 'DB_PASSWORD': 'private-test-value'})
        self.assertEqual(env['PGSSLMODE'], 'require')
        self.assertEqual(env['PGPASSWORD'], 'private-test-value')
        self.assertEqual(env['PGDATABASE'], 'example')

    def test_invalid_database_url_is_rejected(self):
        with self.assertRaises(release.ReleaseFailure):
            release.database_environment({'DB_URL': 'https://example.test/'})

    def test_failed_commands_do_not_expose_server_errors(self):
        result = subprocess.CompletedProcess(['test'], 1, b'private-output', b'private-error')
        with patch('release.subprocess.run', return_value=result):
            with self.assertRaisesRegex(release.ReleaseFailure, '^backup=REPROVADO$'):
                release.command('backup', ['test'])

    def test_invalid_commit_is_rejected_before_any_operation(self):
        with patch('release.command') as command:
            with self.assertRaises(release.ReleaseFailure):
                release.release('../main', 'a' * 64)
            command.assert_not_called()

    def test_database_checks_fail_closed(self):
        with patch('release.command', return_value=b'f'):
            with self.assertRaises(release.ReleaseFailure):
                release.database_checks({'PGSSLMODE': 'require'})

    def test_failed_service_can_capture_configuration_without_saving_secrets(self):
        calls = []

        def command(label, args, **kwargs):
            calls.append(label)
            if label == 'service.pid':
                return b'0' if calls.count('service.pid') == 1 else b'123'
            return b''

        with patch('release.command', side_effect=command), \
             patch('release.pathlib.Path.read_bytes', return_value=b'DB_URL=test\0DB_USERNAME=app\0DB_PASSWORD=test-only\0'), \
             patch('release.time.sleep'):
            env = release.runtime_environment()
        self.assertEqual(env['DB_PASSWORD'], 'test-only')
        self.assertEqual(calls.count('service.recovery.start'), 1)

    def test_streaming_digest_matches_content(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'fixture'
            path.write_bytes(b'fixture')
            self.assertEqual(release.digest(path), hashlib.sha256(b'fixture').hexdigest())

    def test_restore_uses_separate_data_and_private_socket_only(self):
        factory = tempfile.TemporaryDirectory
        directory_factory = tempfile.mkdtemp
        calls = []

        def command(label, args, **kwargs):
            calls.append((label, args, kwargs))
            if label == 'backup.restore.init':
                data = pathlib.Path(args[args.index('-D') + 1])
                data.mkdir()
                (data / 'postmaster.pid').write_text('test-only')
            return b't' if label == 'backup.restore.schema' else b''

        with factory() as directory:
            backup = pathlib.Path(directory) / 'database.dump'
            backup.write_bytes(b'test-only-archive')
            with patch('release.tempfile.mkdtemp', side_effect=lambda **kw: directory_factory(prefix='carimbai-restore-')), \
                 patch('release.command', side_effect=command):
                release.restore_check(backup)
        start = next(args for label, args, kw in calls if label == 'backup.restore.start')
        self.assertIn("listen_addresses=''", start[start.index('-o') + 1])
        restore = next((args, kw) for label, args, kw in calls if label == 'backup.restore')
        self.assertEqual(restore[1]['env']['PGSSLMODE'], 'disable')
        self.assertNotIn(restore[1]['env']['PGHOST'], ['localhost', '127.0.0.1'])
        self.assertEqual(restore[1]['env']['PGUSER'], 'restore_operator')
        self.assertIn('--exit-on-error', restore[0])
        self.assertEqual(calls[-1][0], 'backup.restore.stop')


if __name__ == '__main__':
    unittest.main()
