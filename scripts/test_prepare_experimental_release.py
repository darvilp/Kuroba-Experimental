import json
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from prepare_experimental_release import package_release, require_clean_checkout


class PackageReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'apk'
        self.source.mkdir()
        self.output = self.root / 'out'
        self.commit = 'a' * 40
        self.signer = 'b' * 64
        self.badging = "package: name='com.github.k1rakishou.chan.experimental' versionCode='103440001' versionName='v1.3.44-experimental.1'\n"
        self.cert = f'Signer #1 certificate SHA-256 digest: {self.signer}\n'
        self.elements = []
        for abi in ('universal', 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'):
            name = f'app-{abi}.apk'
            with zipfile.ZipFile(self.source / name, 'w') as archive:
                archive.writestr('classes.dex', self.commit.encode())
                native_abis = ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64') if abi == 'universal' else (abi,)
                for native_abi in native_abis:
                    archive.writestr(f'lib/{native_abi}/libtest.so', b'native library')
            self.elements.append({'outputFile': name, 'filters': [] if abi == 'universal' else [{'filterType': 'ABI', 'value': abi}]})
        self.write_metadata()

    def write_metadata(self):
        (self.source / 'output-metadata.json').write_text(json.dumps({'elements': self.elements}))

    def run_tool(self, command, **kwargs):
        output = self.badging if Path(command[0]).name == 'aapt' else self.cert
        return subprocess.CompletedProcess(command, 0, output, '')

    def package(self):
        with patch('prepare_experimental_release.subprocess.run', side_effect=self.run_tool):
            return package_release(self.source, self.output, self.commit, self.signer,
                                   'v1.3.44-experimental.1', 103440001, self.root)

    def test_packages_all_verified_apks_and_checksums(self):
        manifest = self.package()
        self.assertEqual(5, len(manifest['artifacts']))
        self.assertEqual(self.commit, manifest['commit'])
        self.assertEqual(5, len((self.output / 'SHA256SUMS.txt').read_text().splitlines()))
        for artifact in manifest['artifacts']:
            self.assertEqual((self.source / f"app-{artifact['abi']}.apk").read_bytes(),
                             (self.output / artifact['file']).read_bytes())

    def test_rejects_debuggable_apks_before_copying_any_files(self):
        self.badging += 'application-debuggable\n'
        with self.assertRaisesRegex(ValueError, 'debuggable'):
            self.package()
        self.assertFalse(self.output.exists())

    def test_rejects_wrong_signer(self):
        self.cert = 'Signer #1 certificate SHA-256 digest: ' + 'c' * 64
        with self.assertRaisesRegex(ValueError, 'signer'):
            self.package()

    def test_rejects_wrong_package(self):
        self.badging = self.badging.replace('.experimental', '.personal')
        with self.assertRaisesRegex(ValueError, 'identity'):
            self.package()

    def test_rejects_wrong_version(self):
        self.badging = self.badging.replace('103440001', '10344')
        with self.assertRaisesRegex(ValueError, 'identity'):
            self.package()

    def test_rejects_apk_from_another_commit(self):
        self.commit = 'd' * 40
        with self.assertRaisesRegex(ValueError, 'commit'):
            self.package()

    def test_rejects_mislabeled_architecture(self):
        self.elements[1]['filters'][0]['value'] = 'x86'
        self.elements[3]['filters'][0]['value'] = 'arm64-v8a'
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, 'native architectures'):
            self.package()

    def test_rejects_duplicate_source_apk(self):
        self.elements[1]['outputFile'] = self.elements[0]['outputFile']
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, 'Duplicate input'):
            self.package()

    def test_rejects_distinct_apks_claiming_the_same_architecture(self):
        duplicate = self.source / 'app-arm64-copy.apk'
        with zipfile.ZipFile(duplicate, 'w') as archive:
            archive.writestr('classes.dex', self.commit.encode())
            archive.writestr('lib/arm64-v8a/libtest.so', b'native library')
        self.elements[2] = {
            'outputFile': duplicate.name,
            'filters': [{'filterType': 'ABI', 'value': 'arm64-v8a'}],
        }
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, 'duplicate APK architectures'):
            self.package()

    def test_rejects_missing_architecture(self):
        self.elements.pop()
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, 'architectures'):
            self.package()

    def test_rejects_metadata_path_traversal(self):
        self.elements[0]['outputFile'] = '../outside.apk'
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, 'filename'):
            self.package()


class CleanCheckoutTest(unittest.TestCase):
    def test_rejects_untracked_source(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(['git', 'init', '-q', directory], check=True)
            require_clean_checkout(directory)
            (Path(directory) / 'new_source.kt').write_text('uncommitted source')
            with self.assertRaisesRegex(ValueError, 'clean checkout'):
                require_clean_checkout(directory)

    def test_rejects_staged_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(['git', 'init', '-q', directory], check=True)
            (Path(directory) / 'source.kt').write_text('staged source')
            subprocess.run(['git', 'add', 'source.kt'], cwd=directory, check=True)
            with self.assertRaisesRegex(ValueError, 'clean checkout'):
                require_clean_checkout(directory)


if __name__ == '__main__':
    unittest.main()
