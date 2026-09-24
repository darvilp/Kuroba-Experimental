#!/usr/bin/env python3
"""Publish verified assets to the fork, with resumable draft uploads."""

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

from prepare_experimental_release import require_clean_checkout

REPOSITORY = 'darvilp/Kuroba-Experimental'


def validate_bundle(directory, commit):
    manifest_path = directory / 'release-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    if manifest['commit'] != commit:
        raise ValueError('Bundle commit differs from the checked-out commit')
    if manifest['applicationId'] != 'com.github.k1rakishou.chan.experimental':
        raise ValueError('Unexpected package in release manifest')
    if not re.fullmatch(r'v\d+\.\d+\.\d+-experimental\.[1-9]\d*', manifest['versionName']):
        raise ValueError('Invalid experimental release tag')
    assets = []
    for artifact in manifest['artifacts']:
        name = artifact['file']
        if Path(name).name != name or not name.endswith('.apk'):
            raise ValueError('Invalid APK filename')
        apk = directory / name
        if hashlib.sha256(apk.read_bytes()).hexdigest() != artifact['sha256']:
            raise ValueError(f'APK checksum mismatch: {name}')
        assets.append(apk)
    if not assets or len({p.name for p in assets}) != len(assets):
        raise ValueError('Empty or duplicate APK assets')
    checksums = directory / 'SHA256SUMS.txt'
    expected = ''.join(f"{a['sha256']}  {a['file']}\n" for a in manifest['artifacts'])
    if checksums.read_text() != expected:
        raise ValueError('Release checksums differ from manifest')
    return manifest, assets + [checksums, manifest_path]


def gh(*args, check=True):
    return subprocess.run(['gh', *args], check=check, capture_output=True, text=True)


def get_release(tag):
    # The by-tag REST endpoint excludes drafts. Authenticated listing includes them,
    # which lets an interrupted draft upload resume without creating another release.
    response = gh('api', f'repos/{REPOSITORY}/releases?per_page=100', '--paginate',
                  '--jq', f'.[] | select(.tag_name == {json.dumps(tag)})')
    return json.loads(response.stdout) if response.stdout.strip() else None


def verify_remote_assets(release, assets):
    remote = {asset['name']: asset for asset in release['assets']}
    if set(remote) != {asset.name for asset in assets}:
        raise ValueError('Remote release assets do not match the verified bundle')
    for asset in assets:
        digest = 'sha256:' + hashlib.sha256(asset.read_bytes()).hexdigest()
        if remote[asset.name].get('digest') != digest:
            raise ValueError(f'Remote asset checksum mismatch: {asset.name}')


def verify_release_commit(release, tag, commit):
    response = gh('api', f'repos/{REPOSITORY}/commits/{tag}', '--jq', '.sha', check=False)
    if response.returncode == 0:
        if response.stdout.strip() != commit:
            raise ValueError('Release tag points to a different commit')
        return
    # A new draft can precede creation of its tag, but its target must be the exact SHA.
    missing_tag = 'HTTP 404' in response.stderr or (
        'HTTP 422' in response.stderr and f'No commit found for SHA: {tag}' in response.stderr
    )
    if missing_tag and release['draft'] and release['target_commitish'] == commit:
        return
    raise ValueError('Cannot verify the release commit')


def publish_bundle(directory, repo, commit):
    manifest, assets = validate_bundle(directory, commit)
    tag = manifest['versionName']
    notes = repo / 'docs/releases' / f'{tag}.md'
    if not notes.is_file():
        raise ValueError(f'Missing release notes: {notes.name}')
    release = get_release(tag)
    if release is None:
        gh('release', 'create', tag, '--repo', REPOSITORY, '--target', commit,
           '--draft', '--prerelease', '--latest=false', '--title', f'KurobaEx {tag}', '--notes-file', str(notes))
        release = get_release(tag)
    # Never overwrite a tag or another commit's release, including an existing draft.
    verify_release_commit(release, tag, commit)
    if not release['draft']:
        if not release['prerelease']:
            raise ValueError('Existing release is not experimental')
        verify_remote_assets(release, assets)
        print(release['html_url'])
        return
    gh('release', 'upload', tag, '--repo', REPOSITORY, '--clobber', *(str(asset) for asset in assets))
    verify_remote_assets(get_release(tag), assets)
    gh('release', 'edit', tag, '--repo', REPOSITORY, '--draft=false', '--prerelease', '--latest=false')
    published = get_release(tag)
    if published['draft'] or not published['prerelease']:
        raise ValueError('Release was not published as a prerelease')
    verify_release_commit(published, tag, commit)
    verify_remote_assets(published, assets)
    print(published['html_url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    require_clean_checkout(repo)
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
    publish_bundle(args.assets, repo, commit)


if __name__ == '__main__':
    main()
