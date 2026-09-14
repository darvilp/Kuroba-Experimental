import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from publish_experimental_release import publish_bundle, validate_bundle, verify_release_commit


class ValidateBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.commit = 'a' * 40
        apk = self.root / 'test.apk'
        apk.write_bytes(b'verified apk')
        self.digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        self.manifest = {'commit': self.commit, 'applicationId': 'com.github.k1rakishou.chan.experimental',
                         'versionName': 'v1.3.44-experimental.1', 'artifacts':
                         [{'file': 'test.apk', 'sha256': self.digest}]}
        self.write_manifest()
        (self.root / 'SHA256SUMS.txt').write_text(f'{self.digest}  test.apk\n')

    def write_manifest(self):
        (self.root / 'release-manifest.json').write_text(json.dumps(self.manifest))

    def test_accepts_verified_bundle(self):
        manifest, assets = validate_bundle(self.root, self.commit)
        self.assertEqual('v1.3.44-experimental.1', manifest['versionName'])
        self.assertEqual({'test.apk', 'SHA256SUMS.txt', 'release-manifest.json'}, {p.name for p in assets})

    def test_rejects_changed_apk(self):
        (self.root / 'test.apk').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            validate_bundle(self.root, self.commit)

    def test_rejects_other_commit(self):
        with self.assertRaisesRegex(ValueError, 'commit'):
            validate_bundle(self.root, 'b' * 40)

    def test_rejects_changed_checksums(self):
        (self.root / 'SHA256SUMS.txt').write_text('wrong\n')
        with self.assertRaisesRegex(ValueError, 'checksums'):
            validate_bundle(self.root, self.commit)

    def test_rejects_unexpected_package(self):
        self.manifest['applicationId'] = 'com.github.k1rakishou.chan'
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, 'package'):
            validate_bundle(self.root, self.commit)

    def test_accepts_exact_draft_target_before_tag_exists(self):
        with patch('publish_experimental_release.gh', return_value=subprocess.CompletedProcess([], 1, '', 'HTTP 404')):
            verify_release_commit({'draft': True, 'target_commitish': self.commit}, 'tag', self.commit)

    def test_rejects_draft_with_a_moving_branch_target(self):
        with patch('publish_experimental_release.gh', return_value=subprocess.CompletedProcess([], 1, '', 'HTTP 404')):
            with self.assertRaisesRegex(ValueError, 'commit'):
                verify_release_commit({'draft': True, 'target_commitish': 'release/experimental'}, 'tag', self.commit)

    def test_rejects_preexisting_tag_at_another_commit(self):
        with patch('publish_experimental_release.gh', return_value=subprocess.CompletedProcess([], 0, 'b' * 40, '')):
            with self.assertRaisesRegex(ValueError, 'different commit'):
                verify_release_commit({'draft': True, 'target_commitish': self.commit}, 'tag', self.commit)

    def test_does_not_treat_network_failure_as_an_absent_tag(self):
        with patch('publish_experimental_release.gh', return_value=subprocess.CompletedProcess([], 1, '', 'timeout')):
            with self.assertRaisesRegex(ValueError, 'commit'):
                verify_release_commit({'draft': True, 'target_commitish': self.commit}, 'tag', self.commit)

    def test_publishes_new_draft_and_accepts_identical_retry(self):
        notes = self.root / 'docs/releases'
        notes.mkdir(parents=True)
        (notes / 'v1.3.44-experimental.1.md').write_text('Release notes')
        release = None
        creations = 0
        upload_attempts = 0

        def github(*args, **kwargs):
            nonlocal release, creations, upload_attempts
            if args[0] == 'api' and '/releases?' in args[1]:
                return subprocess.CompletedProcess(args, 0, json.dumps(release) if release else '', '')
            if args[0] == 'api' and '/commits/' in args[1]:
                return subprocess.CompletedProcess(args, 0 if not release['draft'] else 1,
                                                   self.commit if not release['draft'] else '',
                                                   '' if not release['draft'] else 'HTTP 404')
            if args[:2] == ('release', 'create'):
                creations += 1
                release = {'draft': True, 'prerelease': True, 'target_commitish': self.commit,
                           'assets': [], 'html_url': 'https://example.test/release'}
            elif args[:2] == ('release', 'upload'):
                upload_attempts += 1
                paths = [Path(arg) for arg in args[6:]]
                release['assets'] = [{'name': path.name, 'digest': 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()}
                                     for path in paths]
                if upload_attempts == 1:
                    release['assets'] = release['assets'][:1]
                    raise subprocess.CalledProcessError(1, args, stderr='interrupted upload')
            elif args[:2] == ('release', 'edit'):
                release['draft'] = False
            else:
                self.fail(f'Unexpected GitHub operation: {args}')
            return subprocess.CompletedProcess(args, 0, '', '')

        with patch('publish_experimental_release.gh', side_effect=github):
            with self.assertRaises(subprocess.CalledProcessError):
                publish_bundle(self.root, self.root, self.commit)
            self.assertTrue(release['draft'])
            publish_bundle(self.root, self.root, self.commit)
            publish_bundle(self.root, self.root, self.commit)
        self.assertFalse(release['draft'])
        self.assertTrue(release['prerelease'])
        self.assertEqual(1, creations)
        self.assertEqual(2, upload_attempts)


if __name__ == '__main__':
    unittest.main()
