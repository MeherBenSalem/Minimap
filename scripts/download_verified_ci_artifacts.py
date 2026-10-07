#!/usr/bin/env python3
"""Download exact GitHub artifact IDs and enforce server SHA256 before extraction."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request
import zipfile
from inspect_release_jar import MATRIX, require

MAX_ARCHIVE_BYTES = 128 * 1024 * 1024
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None

def bounded_read(response):
    data = response.read(MAX_ARCHIVE_BYTES + 1)
    require(len(data) <= MAX_ARCHIVE_BYTES, 'GitHub artifact archive exceeds safe size limit')
    return data

def download_artifact(artifact_id, token):
    opener = urllib.request.build_opener(NoRedirect)
    url = f'https://api.github.com/repos/MeherBenSalem/Minimap/actions/artifacts/{artifact_id}/zip'
    headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'NightBeam-OdysseyMap-release'}
    try:
        with opener.open(urllib.request.Request(url, headers=headers), timeout=120) as response:
            return bounded_read(response)
    except urllib.error.HTTPError as error:
        require(error.code in (301, 302, 303, 307), f'GitHub artifact download failed: HTTP {error.code}')
        location = error.headers.get('Location', '')
        from urllib.parse import urlparse
        parsed = urlparse(location)
        require(parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password,
                'Invalid GitHub artifact download redirect')
        # GitHub returns its signed archive URL. Never forward the GitHub credential.
        with opener.open(urllib.request.Request(location, headers={'User-Agent': 'NightBeam-OdysseyMap-release'}), timeout=120) as response:
            return bounded_read(response)

def extract_verified(archive_bytes, artifact, destination, expected_names=None):
    digest = artifact.get('digest', '')
    require(re.fullmatch(r'sha256:[a-f0-9]{64}', digest), 'Missing/invalid server artifact SHA256 digest')
    actual = 'sha256:' + hashlib.sha256(archive_bytes).hexdigest()
    require(actual == digest, f'GitHub artifact archive digest mismatch: {artifact["id"]}')
    with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
        items = [item for item in archive.infolist() if not item.is_dir()]
        names = [item.filename for item in items]
        require(len(names) == len(set(names)), 'Duplicate artifact ZIP entries')
        require(all('/' not in name and '\\' not in name and ':' not in name and name not in ('', '.', '..') for name in names), 'Unsafe artifact ZIP path')
        if expected_names is not None:
            require(set(names) == set(expected_names), 'Unexpected immutable CI artifact contents')
        else:
            require(names and all(re.fullmatch(r'[A-Za-z0-9_.-]+\.json', name) for name in names), 'Unexpected recovery receipt artifact contents')
        require(sum(item.file_size for item in items) <= MAX_ARCHIVE_BYTES, 'Uncompressed artifact exceeds safe size limit')
        require(archive.testzip() is None, 'Artifact ZIP CRC mismatch')
        destination.mkdir(parents=True, exist_ok=False)
        for item in items:
            require(not (item.external_attr >> 16) & 0o170000 == 0o120000, 'Artifact ZIP symlink rejected')
            (destination / item.filename).write_bytes(archive.read(item))
    return actual

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proof', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--receipts', action='store_true')
    args = parser.parse_args()
    token = os.environ.get('GITHUB_TOKEN')
    require(token, 'GitHub Actions artifact-read token is unavailable; check actions:read permissions')
    proof = json.loads(args.proof.read_text())
    records = proof['artifacts']
    require(records and all(isinstance(item.get('id'), int) and item['id'] > 0 for item in records), 'Invalid exact artifact IDs')
    require(len({item['id'] for item in records}) == len(records), 'Duplicate artifact IDs')
    source_sha, version = proof['source_sha'], proof['version']
    require(re.fullmatch('[a-f0-9]{40}', source_sha) and re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid artifact provenance identity')
    if args.receipts:
        prefix = f'publication-receipts-{version}-{source_sha}'
        require(all(item['name'] == prefix or re.fullmatch(re.escape(prefix) + r'-attempt[1-9][0-9]*', item['name']) for item in records), 'Unexpected recovery artifact identity')
    else:
        expected = {f'odyssey-{game}-{loader}-{source_sha}': (game, loader) for game, loader, java in MATRIX}
        require(len(records) == len(expected) and {item['name'] for item in records} == set(expected), 'Expected exact eight-artifact CI matrix')
    for artifact in records:
        data = download_artifact(artifact['id'], token)
        if args.receipts:
            destination, expected_names = args.output / str(artifact['id']), None
        else:
            game, loader = expected[artifact['name']]
            destination = args.output / artifact['name']
            expected_names = [f'odysseymap-{loader}-{game}-{version}.jar', f'provenance-{game}-{loader}.json']
        digest = extract_verified(data, artifact, destination, expected_names)
        print(f'Verified immutable GitHub artifact {artifact["id"]} {artifact["name"]} {digest}')

if __name__ == '__main__':
    main()
