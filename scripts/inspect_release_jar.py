#!/usr/bin/env python3
"""Fail-closed inspection and provenance for the eight packaged mod JARs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import tomllib
import zipfile

MATRIX = [(game, loader, java) for game, loaders, java in [
    ('1.20.1', ('fabric', 'forge'), 17), ('1.21.1', ('fabric', 'neoforge'), 21),
    ('26.1.2', ('fabric', 'neoforge'), 25), ('26.2', ('fabric', 'neoforge'), 25),
] for loader in loaders]
PREFIX = 'dev/nightbeam/odysseymap/'
REQUIRED = ['world/Tile', 'world/TileCache', 'world/TileStorage', 'world/WorldScanner',
            'world/OdysseyMapClient', 'client/ClientEvents', 'render/MinimapTexture',
            'render/FullscreenMapRenderer']

def require(condition, message):
    if not condition:
        raise ValueError(message)

def hashes(data):
    return {name: hashlib.new(name, data).hexdigest() for name in ('sha1', 'sha256', 'sha512')}

def properties(root, game):
    values = {}
    for line in (root / game / 'gradle.properties').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()
    return values

def class_strings(data):
    """Read class constant pool UTF8 names, including method names, without execution."""
    require(data[:4] == b'\xca\xfe\xba\xbe', 'Bad class magic')
    count = struct.unpack_from('>H', data, 8)[0]
    index, offset, strings = 1, 10, set()
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
              12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, offset)[0]
            offset += 2
            strings.add(data[offset:offset + length].decode('utf-8', errors='replace'))
            offset += length
        else:
            require(tag in widths, f'Unknown class constant-pool tag {tag}')
            offset += widths[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    return strings

def inspect(jar, root, game, loader, java, version):
    require((game, loader, java) in MATRIX, 'Unsupported release target')
    name = f'odysseymap-{loader}-{game}-{version}.jar'
    require(jar.name == name, f'Expected exact packaged filename {name}')
    props = properties(root, game)
    require(props['version'] == version and props['minecraft_version'] == game
            and props['java_version'] == str(java), 'Workspace version/game/Java mismatch')
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate ZIP entries')
        require(all(not n.startswith('/') and '..' not in n.split('/') for n in names), 'Unsafe ZIP path')
        require(archive.testzip() is None, 'Invalid ZIP CRC')
        for cls in REQUIRED:
            require(PREFIX + cls + '.class' in names, f'Missing persistence class {cls}')
        # Ensure the shipped storage implementation has the persistent operations.
        storage = class_strings(archive.read(PREFIX + 'world/TileStorage.class'))
        require({'singleplayerDirectory', 'multiplayerDirectory', 'load', 'save'} <= storage,
                'Packaged TileStorage does not expose the persistent storage operations')
        own_classes = [n for n in names if n.startswith(PREFIX) and n.endswith('.class')]
        require(own_classes, 'No packaged Odyssey Map classes')
        major_versions = set()
        for cls in own_classes:
            data = archive.read(cls)
            require(data[:4] == b'\xca\xfe\xba\xbe', f'Bad class {cls}')
            minor, major = struct.unpack_from('>HH', data, 4)
            require(minor != 65535 and major <= java + 44, f'Wrong/preview Java bytecode: {cls}')
            major_versions.add(major)
        require(max(major_versions) == java + 44, 'Packaged class bytecode does not target the declared Java')
        manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n', '\n')
        manifest = re.sub(r'\n ', '', manifest)
        attributes = dict(line.split(': ', 1) for line in manifest.splitlines() if ': ' in line)
        require(attributes.get('Implementation-Version') == version, 'Manifest version mismatch')
        require(attributes.get('Built-On-Minecraft') == game, 'Manifest Minecraft mismatch')
        metadata_path = {'fabric': 'fabric.mod.json', 'forge': 'META-INF/mods.toml',
                         'neoforge': 'META-INF/neoforge.mods.toml'}[loader]
        metadata_text = archive.read(metadata_path).decode('utf-8')
        require('${' not in metadata_text, 'Unexpanded loader metadata')
        if loader == 'fabric':
            metadata = json.loads(metadata_text)
            require(metadata.get('id') == 'odysseymap' and metadata.get('version') == version,
                    'Fabric identity/version mismatch')
            require(metadata.get('environment') == 'client', 'Fabric environment mismatch')
            depends = metadata.get('depends', {})
            require(depends.get('minecraft') == game and depends.get('java') == f'>={java}'
                    and depends.get('fabricloader') == '>=' + props['fabric_loader_version']
                    and 'fabric-api' in depends, 'Fabric dependencies mismatch')
            entries = metadata.get('entrypoints', {}).get('client', [])
            require(entries == ['dev.nightbeam.odysseymap.OdysseyMapFabric'], 'Fabric entrypoint mismatch')
            require(PREFIX + 'OdysseyMapFabric.class' in names, 'Fabric entrypoint class missing')
            require(all(config in names for config in metadata.get('mixins', [])), 'Fabric mixin config missing')
        else:
            metadata = tomllib.loads(metadata_text)
            require(metadata.get('modLoader') == 'javafml', 'Wrong FML loader')
            mods = metadata.get('mods', [])
            require(len(mods) == 1 and mods[0].get('modId') == 'odysseymap'
                    and mods[0].get('version') == version, 'FML identity/version mismatch')
            deps = {d['modId']: d for d in metadata.get('dependencies', {}).get('odysseymap', [])}
            require(deps.get('minecraft', {}).get('versionRange') == props['minecraft_version_range'],
                    'FML Minecraft dependency range mismatch')
            require(deps.get(loader, {}).get('versionRange') == '[' + props[loader + '_version'] + ',)',
                    'FML loader dependency mismatch')
            require(all(d.get('side') == 'CLIENT' for d in deps.values()), 'FML environment mismatch')
            require(PREFIX + 'OdysseyMap.class' in names, 'FML mod entrypoint class missing')
            require(all(config.get('config') in names for config in metadata.get('mixins', [])), 'FML mixin config missing')
    return {'filename': name, 'game': game, 'loader': loader, 'java': java,
            'version': version, 'size': jar.stat().st_size, 'hashes': hashes(jar.read_bytes()),
            'inspection': {'metadata': metadata_path, 'class_major_versions': sorted(major_versions),
                           'persistence_classes': REQUIRED}}

def assemble(root, artifacts, output, version, source_sha, ci_run_id):
    manifests = sorted(artifacts.rglob('provenance-*.json'))
    require(len(manifests) == len(MATRIX), 'Expected exactly eight CI provenance files')
    expected = {(game, loader, java) for game, loader, java in MATRIX}
    files, seen = [], set()
    for path in manifests:
        proof = json.loads(path.read_text())
        target = (proof.get('game'), proof.get('loader'), proof.get('java'))
        require(target in expected and target not in seen, 'Unexpected/duplicate release target')
        require(proof.get('source_sha') == source_sha and proof.get('ci_run_id') == ci_run_id
                and proof.get('version') == version, 'Artifact source/CI run/version mismatch')
        jar = path.parent / proof['filename']
        actual = inspect(jar, root, *target, version)
        require(all(proof.get(key) == value for key, value in actual.items()), 'CI artifact hash/metadata mismatch')
        seen.add(target)
        files.append(actual)
    all_jars = sorted(artifacts.rglob('*.jar'))
    require(len(all_jars) == len(MATRIX), 'Unexpected extra or missing JAR in CI artifacts')
    output.mkdir(parents=True, exist_ok=False)
    for proof in manifests:
        entry = json.loads(proof.read_text())
        shutil.copy2(proof.parent / entry['filename'], output / entry['filename'])
    manifest = {'schema': 1, 'repository': 'MeherBenSalem/Minimap', 'source_sha': source_sha,
                'ci_run_id': ci_run_id, 'version': version, 'files': sorted(files, key=lambda x: x['filename'])}
    (output / 'release-provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--version', required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--ci-run-id', type=int, required=True)
    parser.add_argument('--game')
    parser.add_argument('--loader')
    parser.add_argument('--java', type=int)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--assemble', type=Path)
    args = parser.parse_args()
    require(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', args.version), 'Bad release version')
    require(re.fullmatch(r'[a-f0-9]{40}', args.source_sha), 'Bad source SHA')
    require(args.ci_run_id > 0, 'Bad CI run ID')
    if args.assemble:
        manifest = assemble(args.root, args.assemble, args.output, args.version, args.source_sha, args.ci_run_id)
        print(f"Verified {len(manifest['files'])} immutable CI JARs for {args.source_sha}")
        return
    jar_name = f'odysseymap-{args.loader}-{args.game}-{args.version}.jar'
    libs = args.root / args.game / args.loader / 'build/libs'
    packaged = [p for p in libs.glob('*.jar') if not re.search(r'-(sources|javadoc|dev|shadow|dev-shadow)\.jar$', p.name)]
    require(len(packaged) == 1 and packaged[0].name == jar_name, 'Expected one exact packaged release JAR')
    info = inspect(packaged[0], args.root, args.game, args.loader, args.java, args.version)
    info.update({'schema': 1, 'source_sha': args.source_sha, 'ci_run_id': args.ci_run_id})
    args.output.mkdir(parents=True, exist_ok=False)
    shutil.copy2(packaged[0], args.output / jar_name)
    (args.output / f'provenance-{args.game}-{args.loader}.json').write_text(json.dumps(info, indent=2) + '\n')
    print(json.dumps(info, indent=2))

if __name__ == '__main__':
    main()
