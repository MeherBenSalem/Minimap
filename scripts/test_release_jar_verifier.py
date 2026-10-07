#!/usr/bin/env python3
"""Synthetic ZIP fixtures validate packaging/provenance checks, not gameplay."""
import copy
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from inspect_release_jar import MATRIX, PREFIX, REQUIRED, assemble, inspect
from download_verified_ci_artifacts import extract_verified

VERSION = '1.3.1'
SHA = 'a' * 40

def class_bytes(java, strings=()):
    pool = b''.join(b'\x01' + struct.pack('>H', len(value.encode())) + value.encode() for value in strings)
    return b'\xca\xfe\xba\xbe' + struct.pack('>HHH', 0, java + 44, len(strings) + 1) + pool

class VerifierTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for game, loader, java in MATRIX:
            directory = self.root / game
            directory.mkdir(exist_ok=True)
            (directory / 'gradle.properties').write_text(f'version={VERSION}\nminecraft_version={game}\njava_version={java}\n'
                'fabric_loader_version=0.16.9\nforge_version=47.2.30\nneoforge_version=21.1.80\n'
                f'minecraft_version_range=[{game}, 99)\n')
    def tearDown(self):
        self.temp.cleanup()
    def contents(self, game, loader, java):
        entries = {PREFIX + name + '.class': class_bytes(java) for name in REQUIRED}
        entries[PREFIX + 'world/TileStorage.class'] = class_bytes(java, ['singleplayerDirectory', 'multiplayerDirectory', 'load', 'save'])
        entries['META-INF/MANIFEST.MF'] = f'Implementation-Version: {VERSION}\nBuilt-On-Minecraft: {game}\n'.encode()
        entries['odysseymap.mixins.json'] = b'{}'
        if loader == 'fabric':
            entries[PREFIX + 'OdysseyMapFabric.class'] = class_bytes(java)
            entries['fabric.mod.json'] = json.dumps({'id': 'odysseymap', 'version': VERSION, 'environment': 'client',
                'entrypoints': {'client': ['dev.nightbeam.odysseymap.OdysseyMapFabric']}, 'mixins': ['odysseymap.mixins.json'],
                'depends': {'minecraft': game, 'java': f'>={java}', 'fabricloader': '>=0.16.9', 'fabric-api': '*'}}).encode()
        else:
            entries[PREFIX + 'OdysseyMap.class'] = class_bytes(java)
            dependency = '47.2.30' if loader == 'forge' else '21.1.80'
            name = 'META-INF/mods.toml' if loader == 'forge' else 'META-INF/neoforge.mods.toml'
            entries[name] = (f'modLoader="javafml"\n[[mods]]\nmodId="odysseymap"\nversion="{VERSION}"\n'
                f'[[dependencies.odysseymap]]\nmodId="{loader}"\nversionRange="[{dependency},)"\nside="CLIENT"\n'
                f'[[dependencies.odysseymap]]\nmodId="minecraft"\nversionRange="[{game}, 99)"\nside="CLIENT"\n').encode()
        return entries
    def jar(self, target=MATRIX[0], changes=None, parent=None):
        game, loader, java = target
        entries = self.contents(*target)
        if changes:
            changes(entries)
        directory = parent or self.root
        directory.mkdir(exist_ok=True, parents=True)
        jar = directory / f'odysseymap-{loader}-{game}-{VERSION}.jar'
        with zipfile.ZipFile(jar, 'w') as archive:
            for name, value in entries.items():
                archive.writestr(name, value)
        return jar
    def verify(self, target=MATRIX[0], changes=None):
        return inspect(self.jar(target, changes), self.root, *target, VERSION)
    def test_all_eight_metadata_and_java_variants(self):
        for target in MATRIX:
            with self.subTest(target=target):
                result = self.verify(target)
                self.assertEqual(result['inspection']['class_major_versions'], [target[2] + 44])
                self.assertEqual(len(result['hashes']['sha512']), 128)
    def test_missing_storage_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Missing persistence class'):
            self.verify(changes=lambda entries: entries.pop(PREFIX + 'world/TileStorage.class'))
    def test_missing_storage_operations_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'persistent storage operations'):
            self.verify(changes=lambda entries: entries.update({PREFIX + 'world/TileStorage.class': class_bytes(17)}))
    def test_wrong_version_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Manifest version mismatch'):
            self.verify(changes=lambda entries: entries.update({'META-INF/MANIFEST.MF': b'Implementation-Version: 1.3.0\nBuilt-On-Minecraft: 1.20.1\n'}))
    def test_wrong_game_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Manifest Minecraft mismatch'):
            self.verify(changes=lambda entries: entries.update({'META-INF/MANIFEST.MF': b'Implementation-Version: 1.3.1\nBuilt-On-Minecraft: 26.2\n'}))
    def test_newer_bytecode_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Java bytecode'):
            self.verify(changes=lambda entries: entries.update({PREFIX + 'world/Tile.class': class_bytes(25)}))
    def test_unexpanded_metadata_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Unexpanded loader metadata'):
            self.verify(changes=lambda entries: entries.update({'fabric.mod.json': b'{"version":"${version}"}'}))
    def test_wrong_fabric_loader_metadata_is_rejected(self):
        def edit(entries):
            metadata = json.loads(entries['fabric.mod.json']); metadata['depends']['java'] = '>=25'
            entries['fabric.mod.json'] = json.dumps(metadata).encode()
        with self.assertRaisesRegex(ValueError, 'Fabric dependencies mismatch'):
            self.verify(changes=edit)
    def test_wrong_fml_loader_metadata_is_rejected(self):
        def edit(entries):
            entries['META-INF/mods.toml'] = entries['META-INF/mods.toml'].replace(b'modId="forge"', b'modId="paper"')
        with self.assertRaisesRegex(ValueError, 'FML loader dependency mismatch'):
            self.verify(MATRIX[1], edit)
    def test_unsafe_path_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Unsafe ZIP path'):
            self.verify(changes=lambda entries: entries.update({'../escape': b'bad'}))
    def artifacts(self):
        directory = self.root / 'artifacts'
        for target in MATRIX:
            game, loader, java = target
            item = directory / f'{game}-{loader}'
            jar = self.jar(target, parent=item)
            info = inspect(jar, self.root, *target, VERSION)
            info.update({'schema': 1, 'source_sha': SHA, 'ci_run_id': 123})
            (item / f'provenance-{game}-{loader}.json').write_text(json.dumps(info))
        return directory
    def test_assembly_requires_complete_exact_sha_matrix(self):
        proof = assemble(self.root, self.artifacts(), self.root / 'verified', VERSION, SHA, 123)
        self.assertEqual(len(proof['files']), 8)
    def test_assembly_rejects_wrong_sha(self):
        with self.assertRaisesRegex(ValueError, 'source/CI run/version mismatch'):
            assemble(self.root, self.artifacts(), self.root / 'verified', VERSION, 'b' * 40, 123)
    def test_assembly_rejects_wrong_ci_run(self):
        with self.assertRaisesRegex(ValueError, 'source/CI run/version mismatch'):
            assemble(self.root, self.artifacts(), self.root / 'verified', VERSION, SHA, 124)
    def test_assembly_rejects_tampered_jar(self):
        artifacts = self.artifacts()
        jar = next(artifacts.rglob('*.jar'))
        with zipfile.ZipFile(jar, 'a') as archive:
            archive.writestr('tampered.txt', b'tampered')
        with self.assertRaisesRegex(ValueError, 'hash/metadata mismatch'):
            assemble(self.root, artifacts, self.root / 'verified', VERSION, SHA, 123)
    def test_assembly_rejects_incomplete_matrix(self):
        artifacts = self.artifacts()
        next(artifacts.rglob('provenance-*.json')).unlink()
        with self.assertRaisesRegex(ValueError, 'exactly eight'):
            assemble(self.root, artifacts, self.root / 'verified', VERSION, SHA, 123)
    def test_assembly_rejects_extra_jar(self):
        artifacts = self.artifacts()
        (artifacts / 'extra.jar').write_bytes(b'extra')
        with self.assertRaisesRegex(ValueError, 'extra or missing JAR'):
            assemble(self.root, artifacts, self.root / 'verified', VERSION, SHA, 123)

class ArchiveVerifierTests(unittest.TestCase):
    def archive(self, entries, symlink=False):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            for name, value in entries.items():
                if symlink:
                    item = zipfile.ZipInfo(name)
                    item.create_system = 3
                    item.external_attr = 0o120777 << 16
                    archive.writestr(item, value)
                else:
                    archive.writestr(name, value)
        data = buffer.getvalue()
        return data, {'id': 101, 'name': 'fixture', 'digest': 'sha256:' + hashlib.sha256(data).hexdigest()}
    def test_archive_requires_exact_server_digest(self):
        data, proof = self.archive({'receipt.json': b'{}'})
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'output'
            extract_verified(data, proof, output)
            self.assertEqual((output / 'receipt.json').read_bytes(), b'{}')
        with tempfile.TemporaryDirectory() as temp:
            for invalid in [{**proof, 'digest': 'sha256:' + 'a' * 64}, {**proof, 'digest': ''}]:
                with self.assertRaises(ValueError):
                    extract_verified(data, invalid, Path(temp) / 'output')
    def test_archive_rejects_unsafe_paths(self):
        for name in ['../escape.json', '/escape.json', 'sub/receipt.json', 'C:escape.json', 'sub\\receipt.json']:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temp:
                data, proof = self.archive({name: b'{}'})
                with self.assertRaisesRegex(ValueError, 'Unsafe artifact ZIP path'):
                    extract_verified(data, proof, Path(temp) / 'output')
    def test_archive_rejects_symlinks(self):
        data, proof = self.archive({'receipt.json': b'/some/target'}, symlink=True)
        with tempfile.TemporaryDirectory() as temp, self.assertRaisesRegex(ValueError, 'symlink'):
            extract_verified(data, proof, Path(temp) / 'output')
    def test_archive_rejects_unexpected_file_sets(self):
        data, proof = self.archive({'expected.jar': b'jar', 'unexpected.json': b'{}'})
        with tempfile.TemporaryDirectory() as temp, self.assertRaisesRegex(ValueError, 'Unexpected immutable'):
            extract_verified(data, proof, Path(temp) / 'output', ['expected.jar', 'provenance.json'])
    def test_receipt_archive_rejects_non_json(self):
        data, proof = self.archive({'plugin.jar': b'jar'})
        with tempfile.TemporaryDirectory() as temp, self.assertRaisesRegex(ValueError, 'Unexpected recovery'):
            extract_verified(data, proof, Path(temp) / 'output')

if __name__ == '__main__':
    unittest.main()
