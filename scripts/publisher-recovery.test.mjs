/** Safe recovery regressions. Every credential, artifact and HTTP response is synthetic. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import * as publisher from './publish_verified_release.mjs';
import { MATRIX, REPOSITORY, MODRINTH_PROJECT, CURSEFORGE_PROJECT,
  FABRIC_API_MODRINTH, FABRIC_API_CURSEFORGE, displayName, filename, versionNumber } from './release-matrix.mjs';
import { hashes } from './release-verification.mjs';

const VERSION = '1.3.1';
const SOURCE_SHA = '682314d63e452e092ee33749fb6d49a7531c7031';
const CI_RUN_ID = 123;
const loaderNames = {fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge'};
const loaderIds = {fabric: 7499, forge: 7498, neoforge: 10150};
const clone = value => structuredClone(value);
const receiptName = (platform, file) => `${platform}-${file.loader}-${file.game}.json`;

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'odyssey-publisher-recovery-'));
  const artifacts = path.join(root, 'artifacts');
  const receipts = path.join(root, 'receipts');
  const recovery = path.join(root, 'recovery');
  for (const directory of [artifacts, receipts, recovery]) fs.mkdirSync(directory);
  // main's trusted root is isolated too: never read the actual publication baseline.
  fs.mkdirSync(path.join(root, 'scripts'));
  for (const name of ['publish_verified_release.mjs', 'release-verification.mjs', 'release-matrix.mjs']) {
    fs.copyFileSync(new URL(name, import.meta.url), path.join(root, 'scripts', name));
  }
  fs.writeFileSync(path.join(root, 'VERSION'), VERSION + '\n');
  fs.writeFileSync(path.join(root, 'CHANGELOG.md'), `# Odyssey Map v${VERSION}\n\nSynthetic verified release notes.\n`);
  const bytes = new Map();
  const files = MATRIX.map(target => {
    const name = filename(VERSION, target);
    const content = Buffer.from(`synthetic immutable CI artifact: ${SOURCE_SHA} ${name}\n`);
    bytes.set(name, content);
    fs.writeFileSync(path.join(artifacts, name), content);
    return {...target, version: VERSION, filename: name, size: content.length, hashes: hashes(content),
      inspection: {environment: 'client', fabric_api_required: target.loader === 'fabric'}};
  });
  const proof = {schema: 1, repository: REPOSITORY, source_sha: SOURCE_SHA,
    ci_run_id: CI_RUN_ID, version: VERSION, files};
  fs.writeFileSync(path.join(artifacts, 'release-provenance.json'), JSON.stringify(proof));
  const env = {VERSION, SOURCE_SHA, CI_RUN_ID: String(CI_RUN_ID), ARTIFACT_DIRECTORY: artifacts,
    RECEIPT_DIRECTORY: receipts, RECOVERY_RECEIPTS: recovery,
    MODRINTH_TOKEN: 'fixture-only-modrinth', CURSEFORGE_TOKEN: 'fixture-only-curseforge',
    CURSEFORGE_API_KEY: 'fixture-only-curseforge-read'};
  return {root, artifacts, receipts, recovery, bytes, files, proof, env,
    cleanup: () => fs.rmSync(root, {recursive: true, force: true})};
}

function receipt(f, platform, file = f.files[0], fields = {}) {
  return {schema: 1, repository: REPOSITORY, source_sha: SOURCE_SHA, ci_run_id: CI_RUN_ID, version: VERSION,
    filename: file.filename, loader: file.loader, game: file.game, hashes: file.hashes, size: file.size,
    platform, project_id: platform === 'modrinth' ? MODRINTH_PROJECT : CURSEFORGE_PROJECT,
    state: 'accepted', remote_id: platform === 'modrinth' ? 'FixtureMR0' : 8000, ...fields};
}

function browserEnvelope(f, receipts) {
  return {schema: 1, repository: REPOSITORY, source_sha: SOURCE_SHA,
    ci_run_id: CI_RUN_ID, version: VERSION, receipts};
}

function writeReceipt(directory, value) {
  fs.writeFileSync(path.join(directory, receiptName(value.platform, value)), JSON.stringify(value));
}

function readReceipt(f, platform, file = f.files[0]) {
  return JSON.parse(fs.readFileSync(path.join(f.receipts, receiptName(platform, file)), 'utf8'));
}

function modrinthFile(file, id) {
  return {id, project_id: MODRINTH_PROJECT, version_number: versionNumber(VERSION, file),
    name: displayName(VERSION, file), version_type: 'release', status: 'listed', environment: 'client_only',
    game_versions: [file.game], loaders: [file.loader],
    dependencies: file.loader === 'fabric' ? [{project_id: FABRIC_API_MODRINTH, dependency_type: 'required'}] : [],
    files: [{primary: true, filename: file.filename, size: file.size, hashes: file.hashes,
      url: `https://cdn.modrinth.com/fixture/${file.filename}`} ]};
}

function curseforgeFile(file, id) {
  return {id, modId: CURSEFORGE_PROJECT, fileName: file.filename, displayName: displayName(VERSION, file),
    releaseType: 1, fileLength: file.size, gameVersions: [file.game, loaderNames[file.loader], 'Client'],
    dependencies: file.loader === 'fabric' ? [{modId: FABRIC_API_CURSEFORGE, relationType: 3}] : [],
    hashes: [{algo: 1, value: file.hashes.sha1}], isAvailable: true, fileStatus: 4,
    downloadUrl: `https://edge.forgecdn.net/fixture/${file.filename}`};
}

function jsonResponse(value, status = 200) {
  return {ok: status >= 200 && status < 300, status, json: async () => clone(value)};
}

/** This is the only HTTP implementation used by main() in this test file. */
function network(f, {existing = true, hidden = [], forbiddenPlatforms = []} = {}) {
  const calls = [];
  const inventory = {
    modrinth: new Map(f.files.map((file, index) => [file.filename, modrinthFile(file, `FixtureMR${index}`)])),
    curseforge: new Map(f.files.map((file, index) => [file.filename, curseforgeFile(file, 8000 + index)])),
  };
  const hiddenKeys = new Set(hidden.map(([platform, file]) => `${platform}:${file.filename}`));
  const listed = platform => existing ? [...inventory[platform].values()].filter(remote => {
    const name = platform === 'modrinth' ? remote.files[0].filename : remote.fileName;
    return !hiddenKeys.has(`${platform}:${name}`);
  }) : [];
  const fetch = async (rawUrl, options = {}) => {
    const url = new URL(rawUrl);
    const method = options.method || 'GET';
    const platform = url.hostname.includes('modrinth') ? 'modrinth'
      : url.hostname.includes('curseforge') || url.hostname.endsWith('forgecdn.net') ? 'curseforge' : null;
    assert(platform, `Unexpected fixture network destination: ${url}`);
    assert(!forbiddenPlatforms.includes(platform), `Unselected ${platform} received a network request`);
    calls.push({url: url.href, method});
    assert.equal(options.redirect, 'error', 'Authenticated and public reads must reject redirects');

    if (method === 'POST') {
      assert.equal(url.href, platform === 'modrinth' ? 'https://api.modrinth.com/v2/version'
        : `https://minecraft.curseforge.com/api/projects/${CURSEFORGE_PROJECT}/upload-file`);
      const blob = options.body.get(platform === 'modrinth' ? 'file_0' : 'file');
      const file = f.files.find(file => file.filename === blob?.name);
      assert(file, 'POST must contain an exact synthetic CI filename');
      assert.deepEqual(Buffer.from(await blob.arrayBuffer()), f.bytes.get(file.filename));
      const id = platform === 'modrinth' ? `FixtureUpload${f.files.indexOf(file)}` : 9000 + f.files.indexOf(file);
      inventory[platform].set(file.filename, platform === 'modrinth' ? modrinthFile(file, id) : curseforgeFile(file, id));
      return jsonResponse({id});
    }
    assert.equal(method, 'GET');
    if (url.hostname === 'cdn.modrinth.com' || url.hostname.endsWith('forgecdn.net')) {
      const bytes = f.bytes.get(path.basename(url.pathname));
      assert(bytes, 'Download must correspond to a synthetic artifact');
      return {ok: true, status: 200, arrayBuffer: async () => Uint8Array.from(bytes).buffer};
    }
    if (url.hostname === 'api.modrinth.com') {
      if (url.pathname === `/v2/project/${MODRINTH_PROJECT}`) return jsonResponse({id: MODRINTH_PROJECT,
        slug: 'odyssey-map', title: 'Odyssey Map', project_type: 'mod', source_url: `https://github.com/${REPOSITORY}`,
        organization: 'SVDVsyjd', status: 'approved', team: 'fixture-project-team'});
      if (url.pathname === '/v2/tag/game_version') return jsonResponse([...new Set(f.files.map(file => file.game))]
        .map(version => ({version, version_type: 'release'})));
      if (url.pathname === '/v2/tag/loader') return jsonResponse(Object.keys(loaderNames)
        .map(name => ({name, supported_project_types: ['mod']})));
      if (url.pathname === '/v2/user') return jsonResponse({id: 'fixture-user'});
      if (url.pathname === '/v2/team/fixture-project-team/members') return jsonResponse([{accepted: true, user: {id: 'fixture-user'}}]);
      if (url.pathname === '/v3/organization/SVDVsyjd') return jsonResponse({id: 'SVDVsyjd', slug: 'nightbeam', members: []});
      if (url.pathname === `/v2/project/${FABRIC_API_MODRINTH}`) return jsonResponse({id: FABRIC_API_MODRINTH,
        slug: 'fabric-api', title: 'Fabric API', source_url: 'https://github.com/FabricMC/fabric', status: 'approved'});
      if (url.pathname === `/v2/project/${FABRIC_API_MODRINTH}/version`) return jsonResponse([{
        project_id: FABRIC_API_MODRINTH, game_versions: JSON.parse(url.searchParams.get('game_versions')),
        loaders: ['fabric'], status: 'listed', version_type: 'release'}]);
      if (url.pathname === `/v2/project/${MODRINTH_PROJECT}/version`) return jsonResponse(listed('modrinth'));
      if (url.pathname.startsWith('/v2/version/')) {
        const remote = [...inventory.modrinth.values()].find(item => item.id === url.pathname.substring('/v2/version/'.length));
        if (!remote || hiddenKeys.has(`modrinth:${remote.files[0].filename}`)) return jsonResponse({}, 404);
        return jsonResponse(remote);
      }
    }
    if (url.hostname === 'minecraft.curseforge.com' && url.pathname === '/api/game/versions') {
      return jsonResponse([{id: 1001, name: 'Client'}, ...Object.entries(loaderNames).map(([loader, name]) => ({id: loaderIds[loader], name})),
        ...[...new Set(f.files.map(file => file.game))].map((name, index) => ({id: 1100 + index, name}))]);
    }
    if (url.hostname === 'api.curseforge.com') {
      if (url.pathname === `/v1/mods/${CURSEFORGE_PROJECT}`) return jsonResponse({data: {
        id: CURSEFORGE_PROJECT, slug: 'odyssey-map', name: 'Odyssey Map', gameId: 432,
        authors: [{name: 'NightBeamStudio'}], links: {sourceUrl: `https://github.com/${REPOSITORY}`}}});
      if (url.pathname === `/v1/mods/${FABRIC_API_CURSEFORGE}`) return jsonResponse({data: {
        id: FABRIC_API_CURSEFORGE, slug: 'fabric-api', name: 'Fabric API', gameId: 432,
        authors: [{name: 'modmuss50'}], links: {sourceUrl: 'https://github.com/FabricMC/fabric'}}});
      if (url.pathname === `/v1/mods/${FABRIC_API_CURSEFORGE}/files`) return jsonResponse({data: [{
        modId: FABRIC_API_CURSEFORGE, gameVersions: [url.searchParams.get('gameVersion'), 'Fabric'],
        isAvailable: true, releaseType: 1}]});
      if (url.pathname === `/v1/mods/${CURSEFORGE_PROJECT}/files`) {
        const data = listed('curseforge');
        return jsonResponse({data, pagination: {index: Number(url.searchParams.get('index')), totalCount: data.length}});
      }
      const prefix = `/v1/mods/${CURSEFORGE_PROJECT}/files/`;
      if (url.pathname.startsWith(prefix)) {
        const remote = [...inventory.curseforge.values()].find(item => String(item.id) === url.pathname.substring(prefix.length));
        if (!remote || hiddenKeys.has(`curseforge:${remote.fileName}`)) return jsonResponse({}, 404);
        return jsonResponse({data: remote});
      }
    }
    throw new Error(`Unhandled mock HTTP route: ${method} ${url}`);
  };
  return {fetch, calls, inventory, posts: () => calls.filter(call => call.method === 'POST')};
}

async function runMain(f, net, overrides = {}, {expireVerification = false} = {}) {
  // Replace, rather than spread, the inherited environment: no real token is read.
  const originalEnv = process.env;
  const originalFetch = globalThis.fetch;
  const originalNow = Date.now;
  let elapsed = 0;
  process.env = {...f.env, ...overrides};
  globalThis.fetch = net.fetch;
  if (expireVerification) Date.now = () => (elapsed += 46 * 60 * 1000);
  try {
    const runtime = await import(pathToFileURL(path.join(f.root, 'scripts', 'publish_verified_release.mjs')).href);
    return await runtime.main();
  }
  finally { process.env = originalEnv; globalThis.fetch = originalFetch; Date.now = originalNow; }
}

test('platform selection defaults to both and accepts either isolated destination', () => {
  assert.deepEqual(publisher.selectPlatforms(), ['modrinth', 'curseforge']);
  assert.deepEqual(publisher.selectPlatforms('both'), ['modrinth', 'curseforge']);
  assert.deepEqual(publisher.selectPlatforms('modrinth'), ['modrinth']);
  assert.deepEqual(publisher.selectPlatforms('curseforge'), ['curseforge']);
  for (const value of ['unknown', 'modrinth,curseforge', 'all', 'MODRINTH']) assert.throws(() => publisher.selectPlatforms(value));
});

test('required credentials are bounded to the selected platform', () => {
  assert.deepEqual(publisher.requiredSecretNames(['modrinth']), ['MODRINTH_TOKEN']);
  assert.deepEqual(publisher.requiredSecretNames(['curseforge']), ['CURSEFORGE_TOKEN', 'CURSEFORGE_API_KEY']);
  assert.deepEqual(publisher.requiredSecretNames(['modrinth', 'curseforge']), ['MODRINTH_TOKEN', 'CURSEFORGE_TOKEN', 'CURSEFORGE_API_KEY']);
});

for (const platform of ['modrinth', 'curseforge']) {
  test(`${platform}-only preflight needs no unselected-platform credentials or network`, async () => {
    const f = fixture();
    const net = network(f, {forbiddenPlatforms: [platform === 'modrinth' ? 'curseforge' : 'modrinth']});
    const credentials = platform === 'modrinth' ? {CURSEFORGE_TOKEN: '', CURSEFORGE_API_KEY: ''} : {MODRINTH_TOKEN: ''};
    try {
      await runMain(f, net, {PUBLISH_PLATFORM: platform, PREFLIGHT_ONLY: 'true', ...credentials});
      assert.equal(net.posts().length, 0);
      assert(net.calls.length > 0);
    } finally { f.cleanup(); }
  });
}

for (const [platform, name] of [['modrinth', 'MODRINTH_TOKEN'], ['curseforge', 'CURSEFORGE_TOKEN'], ['curseforge', 'CURSEFORGE_API_KEY']]) {
  test(`${platform} refuses missing selected ${name} before network or uploads`, async () => {
    const f = fixture();
    const net = network(f);
    try {
      await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform, [name]: ''}), error => {
        assert(error.message.includes(name));
        assert(!error.message.includes('fixture-only-'));
        return true;
      });
      assert.equal(net.calls.length, 0);
    } finally { f.cleanup(); }
  });
}

test('browser receipt import preserves the exact canonical accepted identities and remote IDs', () => {
  const f = fixture();
  const records = [receipt(f, 'modrinth', f.files[0], {recorded_at: '2026-10-08T12:00:00.000Z'}),
    receipt(f, 'curseforge', f.files[1], {remote_id: 8001})];
  try {
    publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, records));
    for (const record of records) {
      const {recorded_at, ...expected} = record;
      assert.deepEqual(readReceipt(f, record.platform, record), {...expected, method: 'browser_recovery'});
    }
    publisher.importBrowserReceipts(f.proof, f.receipts, JSON.stringify(browserEnvelope(f, records)));
    for (const record of records) {
      const {recorded_at, ...expected} = record;
      assert.deepEqual(readReceipt(f, record.platform, record), {...expected, method: 'browser_recovery'});
    }
  } finally { f.cleanup(); }
});

for (const [key, value] of [['schema', 2], ['repository', 'attacker/Minimap'], ['source_sha', 'b'.repeat(40)], ['ci_run_id', CI_RUN_ID + 1], ['version', '1.3.0']]) {
  test(`browser envelope import rejects mismatched ${key} without writing receipts`, () => {
    const f = fixture();
    const input = {...browserEnvelope(f, [receipt(f, 'modrinth')]), [key]: value};
    try {
      assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts, input));
      assert.deepEqual(fs.readdirSync(f.receipts), []);
    } finally { f.cleanup(); }
  });
}

const invalidReceiptFields = [
  ['schema', 2], ['repository', 'attacker/Minimap'], ['source_sha', 'b'.repeat(40)], ['ci_run_id', CI_RUN_ID + 1],
  ['version', '1.3.0'], ['filename', 'other.jar'], ['loader', 'forge'], ['game', '26.2'],
  ['project_id', 'wrong-project'], ['size', 1], ['platform', 'unknown'], ['state', 'draft'],
];
for (const [key, value] of invalidReceiptFields) {
  test(`browser receipt import rejects wrong ${key} without normalizing its identity`, () => {
    const f = fixture();
    try {
      const record = receipt(f, 'modrinth', f.files[0], {[key]: value});
      assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, [record])));
      assert.deepEqual(fs.readdirSync(f.receipts), []);
    } finally { f.cleanup(); }
  });
}
for (const digest of ['sha1', 'sha256', 'sha512']) {
  test(`browser receipt import rejects substituted ${digest}`, () => {
    const f = fixture();
    try {
      const record = receipt(f, 'curseforge', f.files[0], {hashes: {...f.files[0].hashes, [digest]: 'f'.repeat(f.files[0].hashes[digest].length)}});
      assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, [record])));
      assert.deepEqual(fs.readdirSync(f.receipts), []);
    } finally { f.cleanup(); }
  });
}

test('browser receipt batch validates every entry before writing any entry', () => {
  const f = fixture();
  try {
    const records = [receipt(f, 'modrinth'), receipt(f, 'curseforge', f.files[1], {source_sha: 'b'.repeat(40)})];
    assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, records)));
    assert.deepEqual(fs.readdirSync(f.receipts), []);
  } finally { f.cleanup(); }
});

test('browser import never overwrites a retained accepted ID with a conflicting browser ID', () => {
  const f = fixture();
  const original = receipt(f, 'curseforge');
  writeReceipt(f.receipts, original);
  try {
    assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts,
      browserEnvelope(f, [{...original, remote_id: original.remote_id + 1}])));
    assert.deepEqual(readReceipt(f, 'curseforge'), original);
  } finally { f.cleanup(); }
});

test('browser import merges an exact accepted ID into an uncertain recovery marker', () => {
  const f = fixture();
  const file = f.files.find(file => file.loader === 'fabric' && file.game === '26.2');
  writeReceipt(f.receipts, receipt(f, 'curseforge', file, {state: 'uncertain', remote_id: null}));
  try {
    const accepted = receipt(f, 'curseforge', file, {remote_id: 8006});
    publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, [accepted]));
    assert.equal(readReceipt(f, 'curseforge', file).state, 'accepted');
    assert.equal(readReceipt(f, 'curseforge', file).remote_id, 8006);
    assert.deepEqual(readReceipt(f, 'curseforge', file).hashes, file.hashes);
  } finally { f.cleanup(); }
});

for (const platform of ['modrinth', 'curseforge']) {
  test(`${platform} hidden accepted ID blocks duplicate POST and is retained after verification failure`, async () => {
    const f = fixture();
    const file = f.files[0];
    const accepted = receipt(f, platform, file);
    writeReceipt(f.recovery, accepted);
    const net = network(f, {hidden: [[platform, file]], forbiddenPlatforms: [platform === 'modrinth' ? 'curseforge' : 'modrinth']});
    try {
      await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform}, {expireVerification: true}), /404/);
      assert.equal(net.posts().length, 0);
      assert.equal(readReceipt(f, platform, file).remote_id, accepted.remote_id);
      assert.equal(readReceipt(f, platform, file).state, 'accepted');
      assert(net.calls.some(call => call.url.endsWith(`/files/${accepted.remote_id}`) || call.url.endsWith(`/version/${accepted.remote_id}`)));
    } finally { f.cleanup(); }
  });
}

for (const platform of ['both', 'curseforge']) {
  test(`unresolved CurseForge Fabric 26.2 blocks every POST in ${platform} execution`, async () => {
    const f = fixture();
    const file = f.files.find(file => file.loader === 'fabric' && file.game === '26.2');
    writeReceipt(f.recovery, receipt(f, 'curseforge', file, {state: 'uncertain', remote_id: null}));
    const net = network(f, {existing: false});
    try {
      await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform}), /Unresolved curseforge upload attempt/);
      assert.equal(net.posts().length, 0);
      assert.equal(readReceipt(f, 'curseforge', file).state, 'uncertain');
      assert.equal(readReceipt(f, 'curseforge', file).remote_id, null);
    } finally { f.cleanup(); }
  });
}

test('Modrinth-only execution completes despite an unresolved CurseForge Fabric 26.2 marker', async () => {
  const f = fixture();
  const file = f.files.find(file => file.loader === 'fabric' && file.game === '26.2');
  const uncertain = receipt(f, 'curseforge', file, {state: 'uncertain', remote_id: null});
  writeReceipt(f.recovery, uncertain);
  const net = network(f, {existing: false, forbiddenPlatforms: ['curseforge']});
  try {
    await runMain(f, net, {PUBLISH_PLATFORM: 'modrinth', CURSEFORGE_TOKEN: '', CURSEFORGE_API_KEY: ''});
    assert.equal(net.posts().length, 8);
    assert(net.posts().every(call => call.url === 'https://api.modrinth.com/v2/version'));
    for (const target of f.files) {
      assert.equal(readReceipt(f, 'modrinth', target).state, 'verified');
      assert.deepEqual(readReceipt(f, 'modrinth', target).public_download_hashes, target.hashes);
    }
    assert.deepEqual(readReceipt(f, 'curseforge', file), uncertain);
    const complete = JSON.parse(fs.readFileSync(path.join(f.receipts, 'publication-complete.json')));
    assert.equal(complete.verified_uploads, 8);
    assert.equal(complete.source_sha, SOURCE_SHA);
    assert.equal(complete.ci_run_id, CI_RUN_ID);
  } finally { f.cleanup(); }
});

test('default both-platform dry-run validates all destinations and never uploads missing files', async () => {
  const f = fixture();
  const net = network(f, {existing: false});
  try {
    await runMain(f, net, {PREFLIGHT_ONLY: 'true'});
    assert.equal(net.posts().length, 0);
    assert(net.calls.some(call => call.url.includes('api.modrinth.com')));
    assert(net.calls.some(call => call.url.includes('api.curseforge.com')));
    assert(fs.existsSync(path.join(f.receipts, 'preflight.json')));
    assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));
    for (const target of f.files) for (const platform of ['modrinth', 'curseforge']) {
      assert(!fs.existsSync(path.join(f.receipts, receiptName(platform, target))));
    }
  } finally { f.cleanup(); }
});

for (const platform of ['both', 'modrinth', 'curseforge']) {
  test(`${platform} verify-only never uploads a missing file`, async () => {
    const f = fixture();
    const net = network(f, {existing: false});
    try {
      await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform, VERIFY_ONLY: 'true'}), /VERIFY_ONLY prevents publication/);
      assert.equal(net.posts().length, 0);
      assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));
    } finally { f.cleanup(); }
  });
}

test('strict browser JSON import is applied by main before selected-platform network access', async () => {
  const f = fixture();
  const file = f.files[0];
  const accepted = receipt(f, 'modrinth', file);
  const net = network(f, {hidden: [['modrinth', file]], forbiddenPlatforms: ['curseforge']});
  try {
    await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: 'modrinth',
      BROWSER_RECEIPTS_JSON: JSON.stringify(browserEnvelope(f, [accepted]))}, {expireVerification: true}), /404/);
    assert.equal(net.posts().length, 0);
    assert.equal(readReceipt(f, 'modrinth', file).remote_id, accepted.remote_id);
    assert.equal(readReceipt(f, 'modrinth', file).state, 'accepted');
  } finally { f.cleanup(); }
});

test('main rejects a browser receipt with another source SHA before any network read', async () => {
  const f = fixture();
  const net = network(f);
  try {
    const input = browserEnvelope(f, [receipt(f, 'modrinth', f.files[0], {source_sha: 'b'.repeat(40)})]);
    await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: 'modrinth', BROWSER_RECEIPTS_JSON: JSON.stringify(input)}));
    assert.equal(net.calls.length, 0);
  } finally { f.cleanup(); }
});

test('browser import copies only canonical immutable identity/state fields', () => {
  const f = fixture();
  try {
    const record = receipt(f, 'curseforge');
    publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, [{...record,
      upload: {id: 9999}, public: {unverified: true}, recorded_at: '2026-10-08T12:00:00.000Z',
      arbitrary_metadata: 'must not be copied', method: 'untrusted-input-method'}]));
    assert.deepEqual(readReceipt(f, 'curseforge'), {...record, method: 'browser_recovery'});
  } finally { f.cleanup(); }
});

for (const [platform, invalidIds] of [
  ['curseforge', [null, 0, -1, 1.5, '8000', Number.MAX_SAFE_INTEGER + 1]],
  ['modrinth', [null, 8000, '', 'with-hyphen', '../unsafe', 'with space']],
]) {
  for (const remote_id of invalidIds) {
    test(`${platform} browser import rejects accepted immutable ID ${JSON.stringify(remote_id)}`, () => {
      const f = fixture();
      try {
        assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts,
          browserEnvelope(f, [receipt(f, platform, f.files[0], {remote_id})])),
          remote_id == null ? /immutable remote ID/ : /invalid platform format/);
        assert.deepEqual(fs.readdirSync(f.receipts), []);
      } finally { f.cleanup(); }
    });
  }
}

test('browser import rejects duplicate entries atomically', () => {
  const f = fixture();
  const record = receipt(f, 'modrinth');
  try {
    assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts,
      browserEnvelope(f, [record, record])), /Duplicate browser receipt identity/);
    assert.deepEqual(fs.readdirSync(f.receipts), []);
  } finally { f.cleanup(); }
});

test('conflicting later browser entry leaves earlier new receipts unwritten', () => {
  const f = fixture();
  const retained = receipt(f, 'curseforge', f.files[1], {remote_id: 8001});
  writeReceipt(f.receipts, retained);
  try {
    assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts,
      browserEnvelope(f, [receipt(f, 'modrinth'), {...retained, remote_id: 9001}])), /Conflicting recovery remote/);
    assert.deepEqual(fs.readdirSync(f.receipts), [receiptName('curseforge', retained)]);
    assert.deepEqual(readReceipt(f, 'curseforge', retained), retained);
  } finally { f.cleanup(); }
});

test('browser import cannot downgrade an existing verified accepted ID', () => {
  const f = fixture();
  const retained = receipt(f, 'curseforge', f.files[0], {state: 'verified',
    public_download_hashes: f.files[0].hashes});
  writeReceipt(f.receipts, retained);
  try {
    publisher.importBrowserReceipts(f.proof, f.receipts,
      browserEnvelope(f, [receipt(f, 'curseforge', f.files[0], {state: 'rejected', remote_id: null})]));
    assert.deepEqual(readReceipt(f, 'curseforge'), retained);
  } finally { f.cleanup(); }
});

test('synthetic bound browser baseline with seven accepted IDs and uncertain Fabric 26.2 blocks all CurseForge POSTs', async () => {
  const f = fixture();
  const uncertainFile = f.files.find(file => file.loader === 'fabric' && file.game === '26.2');
  const records = f.files.map((file, index) => receipt(f, 'curseforge', file,
    file === uncertainFile ? {state: 'uncertain', remote_id: null} : {remote_id: 8000 + index}));
  fs.mkdirSync(path.join(f.root, 'publication-recovery'));
  fs.writeFileSync(path.join(f.root, 'publication-recovery', `${VERSION}-${SOURCE_SHA}.json`),
    JSON.stringify(browserEnvelope(f, records)));
  const net = network(f, {existing: false});
  try {
    await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: 'curseforge'}), /Unresolved curseforge upload attempt/);
    assert.equal(net.posts().length, 0);
    for (const record of records) {
      assert.equal(readReceipt(f, 'curseforge', record).state, record.state);
      assert.equal(readReceipt(f, 'curseforge', record).remote_id, record.remote_id);
    }
  } finally { f.cleanup(); }
});

test('main imports an exact synthetic browser receipt file without re-uploading its hidden accepted ID', async () => {
  const f = fixture();
  const accepted = receipt(f, 'curseforge');
  const inputPath = path.join(f.root, 'browser-import.json');
  fs.writeFileSync(inputPath, JSON.stringify(browserEnvelope(f, [accepted])));
  const net = network(f, {hidden: [['curseforge', f.files[0]]], forbiddenPlatforms: ['modrinth']});
  try {
    await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: 'curseforge', BROWSER_RECEIPTS_FILE: inputPath},
      {expireVerification: true}), /404/);
    assert.equal(net.posts().length, 0);
    assert.equal(readReceipt(f, 'curseforge').remote_id, accepted.remote_id);
  } finally { f.cleanup(); }
});

test('main rejects simultaneous inline and file browser imports before network', async () => {
  const f = fixture();
  const net = network(f);
  try {
    await assert.rejects(runMain(f, net, {BROWSER_RECEIPTS_JSON: JSON.stringify(browserEnvelope(f, [])),
      BROWSER_RECEIPTS_FILE: path.join(f.root, 'must-not-be-read.json')}), /Choose one browser receipt import source/);
    assert.equal(net.calls.length, 0);
  } finally { f.cleanup(); }
});

test('main rejects altered synthetic artifact bytes before credentials or network', async () => {
  const f = fixture();
  const net = network(f);
  fs.writeFileSync(path.join(f.artifacts, f.files[0].filename), 'substituted bytes');
  try {
    await assert.rejects(runMain(f, net), /immutable CI hashes/);
    assert.equal(net.calls.length, 0);
  } finally { f.cleanup(); }
});

for (const platform of ['modrinth', 'curseforge']) {
  test(`${platform} verify-only validates existing earlier files and never uploads a later missing file`, async () => {
    const f = fixture();
    const missing = f.files.at(-1);
    const net = network(f, {hidden: [[platform, missing]]});
    try {
      await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform, VERIFY_ONLY: 'true'}), /VERIFY_ONLY prevents publication/);
      assert.equal(net.posts().length, 0);
      for (const file of f.files.slice(0, -1)) assert.equal(readReceipt(f, platform, file).state, 'verified');
      assert(!fs.existsSync(path.join(f.receipts, receiptName(platform, missing))));
      assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));
    } finally { f.cleanup(); }
  });
}

test('default both-platform verify-only completes all sixteen existing public hash checks without POSTs', async () => {
  const f = fixture();
  const net = network(f);
  try {
    await runMain(f, net, {VERIFY_ONLY: 'true'});
    assert.equal(net.posts().length, 0);
    for (const file of f.files) for (const platform of ['modrinth', 'curseforge']) {
      const value = readReceipt(f, platform, file);
      assert.equal(value.state, 'verified');
      assert.deepEqual(value.public_download_hashes, file.hashes);
      assert.equal(value.source_sha, SOURCE_SHA);
      assert.equal(value.ci_run_id, CI_RUN_ID);
    }
    const complete = JSON.parse(fs.readFileSync(path.join(f.receipts, 'publication-complete.json')));
    assert.equal(complete.verified_uploads, 16);
  } finally { f.cleanup(); }
});

for (const platform of ['modrinth', 'curseforge']) {
  const faults = [
    {name: 'thrown upload transport', response: () => { throw new Error('fixture-private-diagnostic-must-not-leak'); },
      message: /upload outcome is uncertain/},
    {name: 'unreadable successful JSON', response: () => ({ok: true, status: 200,
      json: async () => { throw new Error('fixture-private-diagnostic-must-not-leak'); }}),
      message: /without a readable receipt/},
    {name: 'successful response missing ID', response: () => jsonResponse({}), message: /without a valid returned ID/},
    {name: 'null successful JSON body', response: () => jsonResponse(null), message: /without a valid returned ID/},
    {name: 'invalid empty or negative ID', response: () => jsonResponse({id: platform === 'modrinth' ? '' : -1}),
      message: /without a valid returned ID/},
    {name: 'incorrect immutable ID type', response: () => jsonResponse({id: platform === 'modrinth' ? 8000 : '8000'}),
      message: /without a valid returned ID/},
    {name: 'unsafe immutable ID', response: () => jsonResponse({id: platform === 'modrinth' ? 'unsafe-id' : Number.MAX_SAFE_INTEGER + 1}),
      message: /without a valid returned ID/},
    {name: 'ambiguous HTTP 500 upload response', response: () => jsonResponse({}, 500), message: /upload returned HTTP 500/},
  ];
  for (const fault of faults) {
    test(`${platform} ${fault.name} retains uncertainty and prevents POST on a second invocation`, async () => {
      const f = fixture();
      const net = network(f, {existing: false});
      const normalFetch = net.fetch;
      net.fetch = async (url, options = {}) => {
        if (options.method === 'POST') {
          net.calls.push({url: String(url), method: 'POST'});
          assert.equal(options.redirect, 'error');
          return fault.response();
        }
        return normalFetch(url, options);
      };
      try {
        await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform}), error => {
          assert.match(error.message, fault.message);
          assert(!error.message.includes('fixture-private-diagnostic'));
          assert(!error.message.includes('fixture-only-'));
          return true;
        });
        assert.equal(net.posts().length, 1, 'An uncertain POST must not be automatically retried');
        const saved = readReceipt(f, platform);
        assert.equal(saved.state, 'uncertain');
        assert.equal(saved.remote_id, null);
        assert.equal(saved.source_sha, SOURCE_SHA);
        assert.deepEqual(saved.hashes, f.files[0].hashes);
        assert(!JSON.stringify(saved).includes('fixture-private-diagnostic'));
        assert(!JSON.stringify(saved).includes('fixture-only-'));
        assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));

        net.fetch = normalFetch;
        await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: platform}),
          new RegExp(`Unresolved ${platform} upload attempt`));
        assert.equal(net.posts().length, 1, 'Retained uncertainty must block the next invocation before any POST');
        assert.deepEqual(readReceipt(f, platform), saved);
      } finally { f.cleanup(); }
    });
  }
}


test('malformed receipt JSON reports a bounded error without reflecting input', () => {
  const f = fixture();
  try {
    const malformed = 'fixture-only-sensitive-string is not receipt JSON';
    assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts, malformed), error => {
      assert.equal(error.message, 'Browser receipt bundle must be valid JSON; do not include credentials.');
      assert(!error.message.includes(malformed));
      return true;
    });
    assert.deepEqual(fs.readdirSync(f.receipts), []);
  } finally { f.cleanup(); }
});

for (const platform of ['curseforge', 'both', 'modrinth']) {
  test(`${platform} rejects a same-version different-source browser baseline before network and preserves journals`, async () => {
    const f = fixture();
    const blocked = f.files.find(file => file.game === '26.2' && file.loader === 'fabric');
    const baselineDirectory = path.join(f.root, 'publication-recovery');
    fs.mkdirSync(baselineDirectory);
    const oldBundle = browserEnvelope(f, f.files.map((file, index) => receipt(f, 'curseforge', file,
      file === blocked ? {state: 'uncertain', remote_id: null} : {remote_id: 8000 + index})));
    const baselinePath = path.join(baselineDirectory, `${VERSION}-${SOURCE_SHA}.json`);
    const baselineBytes = JSON.stringify(oldBundle);
    fs.writeFileSync(baselinePath, baselineBytes);

    // A valid proof for another SHA must still respect same-version prior upload uncertainty.
    const newerSource = 'b'.repeat(40);
    f.proof.source_sha = newerSource;
    fs.writeFileSync(path.join(f.artifacts, 'release-provenance.json'), JSON.stringify(f.proof));
    const retained = receipt(f, 'curseforge', f.files[0], {source_sha: newerSource});
    writeReceipt(f.receipts, retained);
    const net = network(f, {hidden: [['curseforge', blocked]]});
    try {
      await assert.rejects(runMain(f, net, {SOURCE_SHA: newerSource, PUBLISH_PLATFORM: platform},
        {expireVerification: true}), /different source\/artifact bytes/);
      assert.equal(net.calls.length, 0, 'Same-version source-scope conflicts must fail before every network request');
      assert.equal(net.posts().length, 0, 'A source change cannot enable another attempt at an uncertain upload');
      assert.deepEqual(readReceipt(f, 'curseforge'), retained);
      assert.equal(fs.readFileSync(baselinePath, 'utf8'), baselineBytes);
      assert(!fs.existsSync(path.join(f.receipts, receiptName('curseforge', blocked))));
      assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));
    } finally { f.cleanup(); }
  });
}

for (const platform of ['curseforge', 'both', 'modrinth']) {
  test(`${platform} allows another-version sidecar and imports the exact source/run baseline`, async () => {
    const f = fixture();
    const baselineDirectory = path.join(f.root, 'publication-recovery');
    fs.mkdirSync(baselineDirectory);
    const current = receipt(f, 'curseforge');
    const currentPath = path.join(baselineDirectory, `${VERSION}-${SOURCE_SHA}.json`);
    const currentBytes = JSON.stringify(browserEnvelope(f, [current]));
    fs.writeFileSync(currentPath, currentBytes);
    const previousVersionPath = path.join(baselineDirectory, `1.3.0-${'b'.repeat(40)}.json`);
    // Only the current version's exact-source baseline should be read or imported.
    const previousBytes = 'unrelated previous-version synthetic sidecar must not be parsed';
    fs.writeFileSync(previousVersionPath, previousBytes);
    const net = network(f);
    try {
      await runMain(f, net, {PUBLISH_PLATFORM: platform, PREFLIGHT_ONLY: 'true'});
      assert(net.calls.length > 0);
      assert.equal(net.posts().length, 0);
      assert.deepEqual(readReceipt(f, 'curseforge'), {...current, method: 'browser_recovery'});
      assert.equal(fs.readFileSync(currentPath, 'utf8'), currentBytes);
      assert.equal(fs.readFileSync(previousVersionPath, 'utf8'), previousBytes);
      assert(fs.existsSync(path.join(f.receipts, 'preflight.json')));
      assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));
    } finally { f.cleanup(); }
  });
}

for (const platform of ['modrinth', 'curseforge']) {
  for (const state of ['attempting', 'uncertain', 'rejected']) {
    test(`${platform} ${state} browser receipts reject every invalid non-null remote ID atomically`, () => {
      const f = fixture();
      const invalidIds = platform === 'modrinth' ? [8000, '', 'bad-id', '../unsafe', 'with space']
        : [0, -1, 1.5, '8000', Number.MAX_SAFE_INTEGER + 1];
      try {
        for (const remote_id of invalidIds) {
          assert.throws(() => publisher.importBrowserReceipts(f.proof, f.receipts,
            browserEnvelope(f, [receipt(f, platform, f.files[0], {state, remote_id})])), /invalid platform format/);
          assert.deepEqual(fs.readdirSync(f.receipts), [], 'Invalid IDs must not create an upload receipt');
        }
      } finally { f.cleanup(); }
    });

    test(`${platform} ${state} browser receipts allow null or a valid immutable remote ID`, () => {
      for (const remote_id of [null, platform === 'modrinth' ? 'FixtureMR0' : 8000]) {
        const f = fixture();
        try {
          const record = receipt(f, platform, f.files[0], {state, remote_id});
          publisher.importBrowserReceipts(f.proof, f.receipts, browserEnvelope(f, [record]));
          assert.deepEqual(readReceipt(f, platform), {...record, method: 'browser_recovery'});
        } finally { f.cleanup(); }
      }
    });
  }
}

test('main rejects an uncertain browser receipt with an invalid non-null ID before network and creates no upload receipt', async () => {
  const f = fixture();
  const net = network(f, {existing: false});
  const invalid = receipt(f, 'curseforge', f.files[0], {state: 'uncertain', remote_id: '8000'});
  try {
    await assert.rejects(runMain(f, net, {PUBLISH_PLATFORM: 'curseforge',
      BROWSER_RECEIPTS_JSON: JSON.stringify(browserEnvelope(f, [invalid]))}), /invalid platform format/);
    assert.equal(net.calls.length, 0);
    assert.equal(net.posts().length, 0);
    for (const file of f.files) for (const platform of ['modrinth', 'curseforge']) {
      assert(!fs.existsSync(path.join(f.receipts, receiptName(platform, file))));
    }
    assert(!fs.existsSync(path.join(f.receipts, 'preflight.json')));
    assert(!fs.existsSync(path.join(f.receipts, 'publication-complete.json')));
  } finally { f.cleanup(); }
});
