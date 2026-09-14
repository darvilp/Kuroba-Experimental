#!/usr/bin/env python3
"""Verify signed experimental APKs and prepare public release assets."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

PACKAGE = 'com.github.k1rakishou.chan.experimental'
ABIS = {'universal', 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}


def require_clean_checkout(repo):
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=repo, text=True).strip():
        raise ValueError('A clean checkout is required, including untracked source files')


def package_release(apk_dir, output_dir, commit, signer, version_name, version_code, build_tools):
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Expected a full commit SHA')
    if not re.fullmatch(r'[0-9a-f]{64}', signer):
        raise ValueError('Expected a SHA-256 signer fingerprint')
    if not re.fullmatch(r'v\d+\.\d+\.\d+-experimental\.[1-9]\d*', version_name):
        raise ValueError('Invalid experimental version name')

    metadata = json.loads((apk_dir / 'output-metadata.json').read_text())
    artifacts = []
    sources = []
    for element in metadata['elements']:
        filename = element['outputFile']
        if Path(filename).name != filename or not filename.endswith('.apk'):
            raise ValueError('Invalid APK filename in metadata')
        filters = element['filters']
        abi = filters[0]['value'] if len(filters) == 1 and filters[0]['filterType'] == 'ABI' else None
        if not filters:
            abi = 'universal'
        if abi not in ABIS:
            raise ValueError('Unexpected APK architectures')
        apk = apk_dir / filename
        if apk.resolve() in sources:
            raise ValueError('Duplicate input APK filename')
        badging = subprocess.run([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)],
                                 check=True, capture_output=True, text=True).stdout
        identity = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
        if not identity or identity.groups() != (PACKAGE, str(version_code), version_name):
            raise ValueError(f'Unexpected APK identity: {filename}')
        if 'application-debuggable' in badging:
            raise ValueError(f'Refusing debuggable APK: {filename}')
        certificates = subprocess.run([str(build_tools / 'apksigner'), 'verify', '--print-certs', str(apk)],
                                      check=True, capture_output=True, text=True).stdout
        fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', certificates)
        if fingerprints != [signer]:
            raise ValueError(f'Unexpected APK signer: {filename}')
        with zipfile.ZipFile(apk) as archive:
            native_abis = {Path(name).parts[1] for name in archive.namelist()
                           if name.startswith('lib/') and name.endswith('.so') and len(Path(name).parts) == 3}
            expected_abis = ABIS - {'universal'} if abi == 'universal' else {abi}
            if native_abis != expected_abis:
                raise ValueError(f'APK native architectures do not match metadata: {filename}')
            if not any(commit.encode() in archive.read(name) for name in archive.namelist()
                       if re.fullmatch(r'classes\d*\.dex', name)):
                raise ValueError(f'Expected commit absent from APK: {filename}')
        artifacts.append({'file': f'KurobaEx-{version_name}-{abi}.apk', 'abi': abi,
                          'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                          'bytes': apk.stat().st_size})
        sources.append(apk.resolve())

    if len(artifacts) != len(ABIS) or {item['abi'] for item in artifacts} != ABIS:
        raise ValueError('Missing or duplicate APK architectures')
    # Validate the entire set before copying anything into the public assets directory.
    output_dir.mkdir(parents=True, exist_ok=True)
    expected_files = {item['file'] for item in artifacts} | {'SHA256SUMS.txt', 'release-manifest.json'}
    if any(path.name not in expected_files for path in output_dir.iterdir()):
        raise ValueError('Output directory contains unrelated files')
    for source, artifact in zip(sources, artifacts):
        destination = output_dir / artifact['file']
        if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() != artifact['sha256']:
            raise ValueError(f'Refusing to replace a different existing APK: {destination.name}')
    for source, artifact in zip(sources, artifacts):
        destination = output_dir / artifact['file']
        shutil.copyfile(source, destination)
        if hashlib.sha256(destination.read_bytes()).hexdigest() != artifact['sha256']:
            raise ValueError(f'Copied APK checksum mismatch: {destination.name}')
    manifest = {'commit': commit, 'applicationId': PACKAGE, 'versionName': version_name,
                'versionCode': version_code, 'signerSha256': signer, 'artifacts': artifacts}
    (output_dir / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (output_dir / 'SHA256SUMS.txt').write_text(''.join(f"{a['sha256']}  {a['file']}\n" for a in artifacts))
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    require_clean_checkout(repo)
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
    version = dict(line.split('=', 1) for line in (repo / 'Kuroba/experimental-version.properties').read_text().splitlines()
                   if line and not line.startswith('#'))
    signer = (repo / 'docs/experimental-signing-certificate.sha256').read_text().strip()
    manifest = package_release(args.apk_dir, args.output_dir, commit, signer, version['versionName'],
                               int(version['versionCode']), args.build_tools)
    print(f"Verified {len(manifest['artifacts'])} APKs for {manifest['versionName']} at {commit}")


if __name__ == '__main__':
    main()
