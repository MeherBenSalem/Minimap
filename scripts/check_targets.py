#!/usr/bin/env python3
"""NightBeam convention checks for Remnants Minimap: exactly the 8 supported targets.

  python3 scripts/check_targets.py workspaces
      Every supported workspace exists with exactly its two loaders, no out-of-convention workspace is left,
      every gradle.properties has the same version= (and the root VERSION file matches it), and the Minecraft
      ranges stay inside their own line.

  python3 scripts/check_targets.py jars --version 2.0.1 --dir releases [--list-file jars.txt]
      Exactly one jar per supported loader + Minecraft pair, named <mod_id>-<loader>-<minecraft>-<version>.jar,
      and no other loader jar for that version. Prints the 8 paths (and writes them to --list-file).

  python3 scripts/check_targets.py version
      Prints the shared workspace version (fails if the workspaces differ).
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD_ID = 'remnantminimaps'
# Minecraft folder -> its two loaders (NightBeam convention, section 1).
TARGETS = {
    '1.20.1': ('fabric', 'forge'),
    '1.21.1': ('fabric', 'neoforge'),
    '26.2': ('fabric', 'neoforge'),
    '26.3': ('fabric', 'neoforge'),
}
ALL_LOADERS = ('fabric', 'forge', 'neoforge')
# The exact minecraft_version_range each workspace may declare (stays inside its own line).
RANGES = {
    '1.20.1': '[1.20.1, 1.20.2)',
    '1.21.1': '[1.21.1, 1.21.2)',
    '26.2': '[26.2, 26.3)',
    '26.3': '[26.3, 26.4)',
}
# fabric.mod.json "minecraft" dependency: the exact version or ~version, both inside the folder's line.
FABRIC_MINECRAFT = ('${minecraft_version}', '~${minecraft_version}')
SKIP_SUFFIXES = ('-sources.jar', '-javadoc.jar', '-dev.jar', '-dev-shadow.jar', '-shadow.jar')


def props(ws, root=ROOT):
    out = {}
    for line in (root / ws / 'gradle.properties').read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            out[key.strip()] = value.strip()
    return out


def minecraft_toml_ranges(text):
    """versionRange of every [[dependencies.*]] block whose modId is "minecraft"."""
    ranges = []
    for block in re.split(r'^\s*\[\[', text, flags=re.M)[1:]:
        if re.search(r'^\s*modId\s*=\s*"minecraft"', block, flags=re.M):
            m = re.search(r'^\s*versionRange\s*=\s*"([^"]*)"', block, flags=re.M)
            ranges.append(m.group(1) if m else None)
    return ranges


def check_workspaces(root=ROOT):
    errors = []
    versions = {}
    for ws, loaders in TARGETS.items():
        if not (root / ws / 'settings.gradle').is_file():
            errors.append(f'{ws}: missing workspace')
            continue
        p = props(ws, root)
        versions[ws] = p.get('version')
        if p.get('mod_id') != MOD_ID:
            errors.append(f"{ws}: mod_id is {p.get('mod_id')!r}, expected {MOD_ID!r}")
        if p.get('minecraft_version') != ws:
            errors.append(f"{ws}: minecraft_version is {p.get('minecraft_version')!r}")
        if p.get('minecraft_version_range') != RANGES[ws]:
            errors.append(f"{ws}: minecraft_version_range is {p.get('minecraft_version_range')!r}, expected {RANGES[ws]!r}")
        settings = (root / ws / 'settings.gradle').read_text(encoding='utf-8')
        included = set(re.findall(r"""include\s*\(?\s*['"]:?([\w-]+)['"]""", settings))
        if included != {'common', *loaders}:
            errors.append(f'{ws}: settings.gradle includes {sorted(included)}, expected {sorted({"common", *loaders})}')
        for loader in ALL_LOADERS:
            present = (root / ws / loader).is_dir()
            if loader in loaders and not present:
                errors.append(f'{ws}: missing {loader}/')
            if loader not in loaders and present:
                errors.append(f'{ws}: {loader}/ is out of convention')
        fabric_json = root / ws / 'fabric/src/main/resources/fabric.mod.json'
        if fabric_json.is_file():
            depends = json.loads(fabric_json.read_text(encoding='utf-8')).get('depends', {})
            if depends.get('minecraft') not in FABRIC_MINECRAFT:
                errors.append(f"{ws}: fabric.mod.json minecraft range is {depends.get('minecraft')!r}")
            if 'fabric-api' not in depends:
                errors.append(f'{ws}: fabric.mod.json does not depend on fabric-api (the publish job declares it)')
        for loader in loaders:
            if loader == 'fabric':
                continue
            toml = root / ws / loader / 'src/main/resources/META-INF' / ('mods.toml' if loader == 'forge' else 'neoforge.mods.toml')
            if not toml.is_file():
                errors.append(f'{ws}: missing {toml.relative_to(root)}')
                continue
            ranges = minecraft_toml_ranges(toml.read_text(encoding='utf-8'))
            if ranges != ['${minecraft_version_range}']:
                errors.append(f'{ws}: {toml.name} minecraft versionRange is {ranges!r}, expected the ${{minecraft_version_range}} property')
    for path in sorted(root.iterdir()):
        if path.is_dir() and re.fullmatch(r'\d+\.\d+(\.\d+)?', path.name) and path.name not in TARGETS:
            errors.append(f'{path.name}/: workspace is out of convention')
    if len(set(versions.values())) > 1:
        errors.append(f'workspace versions differ: {versions}')
    version_file = root / 'VERSION'
    if version_file.is_file() and len(set(versions.values())) == 1:
        if version_file.read_text(encoding='utf-8').strip() != next(iter(versions.values())):
            errors.append(f'VERSION is {version_file.read_text(encoding="utf-8").strip()!r}, the workspaces are at {next(iter(versions.values()))!r}')
    return errors, versions


def expected_names(version):
    return {f'{MOD_ID}-{loader}-{game}-{version}.jar': (loader, game)
            for game, loaders in TARGETS.items() for loader in loaders}


def check_jars(jar_dir, version):
    jar_dir = Path(jar_dir)
    expected = expected_names(version)
    found = sorted(p.name for p in jar_dir.glob(f'{MOD_ID}-*-{version}.jar')
                   if p.is_file() and not p.name.endswith(SKIP_SUFFIXES) and '-common-' not in p.name)
    errors = []
    missing = sorted(set(expected) - set(found))
    extra = sorted(set(found) - set(expected))
    for name in missing:
        loader, game = expected[name]
        errors.append(f'missing {loader} {game}: {name}')
    for name in extra:
        errors.append(f'out-of-convention jar: {name}')
    return errors, [jar_dir / name for name in sorted(expected) if name in found]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='cmd', required=True)
    sub.add_parser('workspaces')
    sub.add_parser('version')
    jars = sub.add_parser('jars')
    jars.add_argument('--version', required=True)
    jars.add_argument('--dir', required=True)
    jars.add_argument('--list-file')
    args = parser.parse_args(argv)

    if args.cmd in ('workspaces', 'version'):
        errors, versions = check_workspaces()
        if args.cmd == 'version':
            distinct = set(versions.values())
            if len(distinct) != 1:
                print(f'workspace versions differ: {versions}', file=sys.stderr)
                return 1
            print(distinct.pop())
            return 0
        for e in errors:
            print(f'::error::{e}')
        if errors:
            return 1
        summary = ', '.join(ws + ' ' + '+'.join(loaders) for ws, loaders in TARGETS.items())
        print(f'8 targets OK ({summary}), version {next(iter(versions.values()))}')
        return 0

    errors, paths = check_jars(args.dir, args.version)
    for e in errors:
        print(f'::error::{e}')
    if errors:
        print(f'Expected exactly the 8 convention jars for {args.version} in {args.dir}/', file=sys.stderr)
        return 1
    for p in paths:
        print(p)
    if args.list_file:
        Path(args.list_file).write_text(''.join(f'{p}\n' for p in paths), encoding='utf-8')
    return 0


if __name__ == '__main__':
    sys.exit(main())
