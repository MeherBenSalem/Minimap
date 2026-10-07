import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { readJson, recoverUploadReceipts } from './publish_verified_release.mjs';
import { MATRIX, REPOSITORY, MODRINTH_PROJECT, CURSEFORGE_PROJECT, displayName, filename, requiredBuildSteps, versionNumber } from './release-matrix.mjs';
import { hashes, sameSet, verifyProvenance, verifyCiRun, verifyModrinthProject, verifyCurseForgeProject, verifyModrinth, verifyCurseForge, findDuplicate, sectionForVersion, isDefiniteUploadRejection, validateUploadReceipt, mergeUploadReceipts, assertPriorReceiptScope, curseforgeRelations } from './release-verification.mjs';
const version = '1.3.1', sha = 'a'.repeat(40), bytes = Buffer.from('immutable CI artifact');
const target = MATRIX[0];
const file = {...target, version, inspection: {environment: 'client', fabric_api_required: true}, filename: filename(version, target), size: bytes.length, hashes: hashes(bytes)};
const mr = {project_id: MODRINTH_PROJECT, version_number: versionNumber(version, file), name: displayName(version, file),
  version_type: 'release', status: 'listed', game_versions: [file.game], loaders: [file.loader], environment: 'client_only', dependencies: [{project_id: 'P7dR8mSH', dependency_type: 'required'}],
  files: [{primary: true, filename: file.filename, size: file.size, hashes: file.hashes}]};
const cf = {modId: CURSEFORGE_PROJECT, fileName: file.filename, displayName: displayName(version, file), releaseType: 1,
  fileLength: file.size, gameVersions: [file.game, 'Fabric', 'Client'], dependencies: [{modId: 306612, relationType: 3}], hashes: [{algo: 1, value: file.hashes.sha1}]};
const run = {head_sha: sha, path: '.github/workflows/ci.yml', name: 'CI', status: 'completed', conclusion: 'success',
  head_repository: {full_name: REPOSITORY}, event: 'push', head_branch: 'main'};
const jobs = MATRIX.map((target, index) => ({id: index + 1, name: `build (${target.game}, ${target.loader}, ${target.java})`,
  conclusion: 'success', steps: requiredBuildSteps.map(name => ({name, conclusion: 'success'}))}));
jobs.push({id: 9, name: 'release-validation', conclusion: 'success', steps: ['Check publication credential presence', 'Release helper regression tests', 'Packaged JAR verifier regression tests', 'Verify public supported inventory'].map(name => ({name, conclusion: 'success'}))});
const clone = x => structuredClone(x);

test('eight exact loader/game/Java targets and distinct filenames', () => {
  assert.equal(MATRIX.length, 8); assert.equal(new Set(MATRIX.map(t => filename(version, t))).size, 8);
  assert.deepEqual(MATRIX.filter(t => t.loader === 'forge').map(t => t.game), ['1.20.1']);
});
test('sameSet rejects duplicates and supersets', () => {
  assert(sameSet(['forge', 'fabric'], ['fabric', 'forge']));
  assert(!sameSet(['fabric', 'fabric'], ['fabric', 'forge'])); assert(!sameSet(['fabric', 'forge'], ['fabric']));
});
test('verified full exact-SHA CI', () => verifyCiRun(run, jobs, sha));
for (const [key, value] of [['head_sha', 'b'.repeat(40)], ['conclusion', 'failure'], ['path', '.github/workflows/other.yml'], ['event', 'pull_request'], ['head_branch', 'untrusted'], ['head_repository', {full_name: 'attacker/Minimap'}]]) {
  test(`CI rejects wrong ${key}`, () => assert.throws(() => verifyCiRun({...run, [key]: value}, jobs, sha)));
}
test('CI rejects missing matrix job', () => assert.throws(() => verifyCiRun(run, jobs.slice(1), sha)));
test('CI rejects skipped regression', () => { const copy = clone(jobs); copy[0].steps[0].conclusion = 'skipped'; assert.throws(() => verifyCiRun(run, copy, sha)); });
test('CI checks latest job attempt, not a stale success', () => assert.throws(() => verifyCiRun(run, [...jobs, {...jobs[0], id: 100, conclusion: 'failure'}], sha)));
test('CI accepts successful retry after stale failure', () => verifyCiRun(run, [{...jobs[0], id: 0, conclusion: 'failure'}, ...jobs], sha));
test('CI rejects unexpected job', () => assert.throws(() => verifyCiRun(run, [...jobs, {id: 99, name: 'unverified', conclusion: 'success'}], sha)));
const provenance = {schema: 1, repository: REPOSITORY, version, source_sha: sha, ci_run_id: 123,
  files: MATRIX.map(target => ({...file, ...target, inspection: {environment: 'client', fabric_api_required: target.loader === 'fabric'}, filename: filename(version, target)}))};
test('provenance binds all eight files to SHA/run and hashes', () => verifyProvenance(provenance, version, sha, 123, () => bytes));
for (const [key, value] of [['source_sha', 'b'.repeat(40)], ['ci_run_id', 124], ['version', '1.3.0'], ['repository', 'other/repo']]) {
  test(`provenance rejects wrong ${key}`, () => assert.throws(() => verifyProvenance({...provenance, [key]: value}, version, sha, 123, () => bytes)));
}
test('provenance rejects substituted bytes', () => assert.throws(() => verifyProvenance(provenance, version, sha, 123, () => Buffer.from('changed'))));
test('provenance rejects duplicate target', () => { const copy = clone(provenance); copy.files[1] = copy.files[0]; assert.throws(() => verifyProvenance(copy, version, sha, 123, () => bytes)); });
test('Modrinth project is exact and approved', () => {
  const project = {id: MODRINTH_PROJECT, slug: 'odyssey-map', title: 'Odyssey Map', project_type: 'mod', source_url: `https://github.com/${REPOSITORY}`, organization: 'SVDVsyjd', status: 'approved'};
  verifyModrinthProject(project); assert.throws(() => verifyModrinthProject({...project, id: 'wrong'})); assert.throws(() => verifyModrinthProject({...project, source_url: 'wrong'}));
});
test('CurseForge project identity and owner are verified', () => {
  const project = {id: CURSEFORGE_PROJECT, slug: 'odyssey-map', name: 'Odyssey Map', gameId: 432, authors: [{name: 'NightBeamStudio'}], links: {sourceUrl: `https://github.com/${REPOSITORY}`}};
  verifyCurseForgeProject(project); assert.throws(() => verifyCurseForgeProject({...project, authors: []})); assert.throws(() => verifyCurseForgeProject({...project, id: 1479926}));
});
test('Modrinth exact public hash/loader/game metadata', () => verifyModrinth(mr, version, file));
for (const [key, value] of [['version_number', '1.3.1'], ['game_versions', ['26.2']], ['loaders', ['paper']], ['status', 'draft'], ['dependencies', [{project_id: 'unknown'}]]]) {
  test(`Modrinth rejects wrong ${key}`, () => assert.throws(() => verifyModrinth({...mr, [key]: value}, version, file)));
}
test('Modrinth rejects wrong public hash', () => { const copy = clone(mr); copy.files[0].hashes.sha512 = 'f'.repeat(128); assert.throws(() => verifyModrinth(copy, version, file)); });
test('CurseForge exact public hash/loader/game metadata', () => verifyCurseForge(cf, version, file));
for (const [key, value] of [['fileName', 'wrong.jar'], ['modId', 1479926], ['fileLength', 1], ['gameVersions', ['1.20.1', 'Paper']], ['hashes', [{algo: 1, value: 'wrong'}]]]) {
  test(`CurseForge rejects wrong ${key}`, () => assert.throws(() => verifyCurseForge({...cf, [key]: value}, version, file)));
}
test('CurseForge rejects mixed loader tags', () => assert.throws(() => verifyCurseForge({...cf, gameVersions: [...cf.gameVersions, 'Forge']}, version, file)));
test('idempotency permits only identical existing releases', () => {
  assert.equal(findDuplicate([mr], x => x.version_number === mr.version_number, x => verifyModrinth(x, version, file)), mr);
  assert.throws(() => findDuplicate([mr, mr], () => true, x => verifyModrinth(x, version, file)));
  assert.throws(() => findDuplicate([{...mr, loaders: ['forge']}], () => true, x => verifyModrinth(x, version, file)));
});


test('named Odyssey release changelog preserves all subsections and excludes older releases', () => {
  const markdown = '# Odyssey Map v1.3.1\n\nRelease date\n\n## Fixes\n\n- Persist terrain\n\n## Upgrade Notes\n\n1. Back up worlds\n\n---\n# Odyssey Map v1.3.0\n\nOld release notes';
  assert.equal(sectionForVersion(markdown, version), 'Release date\n\n## Fixes\n\n- Persist terrain\n\n## Upgrade Notes\n\n1. Back up worlds\n\n---');
});
test('changelog supports bracketed level-two releases with deeper subsections', () => {
  const markdown = '## [1.3.1] - 2026-10-07\r\n\r\n### Fixed\r\nFull notes\r\n### Upgrade\r\nKeep settings\r\n## [1.3.0] - 2026-09-03\r\nOld notes';
  assert.equal(sectionForVersion(markdown, version), '### Fixed\nFull notes\n### Upgrade\nKeep settings');
});
test('changelog selects the exact version instead of a prefix or body mention', () => {
  const markdown = '# Odyssey Map v1.3.10\nLatest notes mentioning 1.3.1\n# Odyssey Map v1.3.1\nMatching notes\n# Odyssey Map v1.3.0\nOlder notes';
  assert.equal(sectionForVersion(markdown, version), 'Matching notes');
});
test('changelog rejects missing or empty release sections', () => {
  assert.throws(() => sectionForVersion('# Odyssey Map v1.3.0\nOld notes', version));
  assert.throws(() => sectionForVersion('# Odyssey Map v1.3.1\n# Odyssey Map v1.3.0\nOld notes', version));
});


test('authenticated JSON reads reject redirects and never expose headers in errors', async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({url, options}); return {ok: false, status: 302}; };
  try {
    await assert.rejects(readJson('https://minecraft.curseforge.com/api/game/versions', {'X-Api-Token': 'fixture-only'}), error => {
      assert.equal(error.status, 302); assert(!error.message.includes('fixture-only')); return true;
    });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].options.redirect, 'error');
  } finally { globalThis.fetch = original; }
});
test('JSON GET succeeds without following redirects', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    assert.equal(options.redirect, 'error'); return {ok: true, json: async () => ({id: 1564458})};
  };
  try { assert.deepEqual(await readJson('https://api.curseforge.com/v1/mods/1564458'), {id: 1564458}); }
  finally { globalThis.fetch = original; }
});
test('authenticated read permission failures are explicit and do not retry', async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; return {ok: false, status: 403}; };
  try { await assert.rejects(readJson('https://api.curseforge.com/v1/mods/1564458', {'x-api-key': 'fixture-only'}), /Read failed \(403\)/); assert.equal(calls, 1); }
  finally { globalThis.fetch = original; }
});


test('CurseForge rejects extra Minecraft versions, duplicate tags and missing environments', () => {
  for (const tags of [[...cf.gameVersions, '1.21.1'], [...cf.gameVersions, 'Fabric'],
    ['1.20.1', 'Fabric', 'Server'], ['1.20.1', 'Fabric']]) {
    assert.throws(() => verifyCurseForge({...cf, gameVersions: tags}, version, file));
  }
});
test('ambiguous upload responses retain non-retriable attempt markers', () => {
  for (const status of [408, 409, 429, 500, 502, 503, 504]) assert.equal(isDefiniteUploadRejection(status), false);
  for (const status of [400, 401, 403, 404, 405, 413, 415, 422]) assert.equal(isDefiniteUploadRejection(status), true);
});


test('required Fabric API dependencies are exact and limited to Fabric', () => {
  assert.throws(() => verifyModrinth({...mr, dependencies: []}, version, file));
  assert.throws(() => verifyModrinth({...mr, dependencies: [{project_id: 'wrong', dependency_type: 'required'}]}, version, file));
  assert.throws(() => verifyCurseForge({...cf, dependencies: []}, version, file));
  assert.throws(() => verifyCurseForge({...cf, dependencies: [{modId: 306612, relationType: 2}]}, version, file));
});
test('client-only release metadata rejects server-required or Server-tagged artifacts', () => {
  assert.throws(() => verifyModrinth({...mr, environment: 'client_and_server'}, version, file));
  assert.throws(() => verifyCurseForge({...cf, gameVersions: [...cf.gameVersions, 'Server']}, version, file));
});


const uploadReceipt = (extra = {}) => ({schema: 1, repository: REPOSITORY, source_sha: sha, ci_run_id: 123, version,
  filename: file.filename, loader: file.loader, game: file.game, hashes: file.hashes, size: file.size,
  platform: 'curseforge', project_id: CURSEFORGE_PROJECT, state: 'accepted', remote_id: 777, ...extra});
test('recovery retains accepted markers across failed successive recoveries before network reads', async () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'odyssey-recovery-test-'));
  const first = path.join(temp, 'first'); const second = path.join(temp, 'second'); const third = path.join(temp, 'third');
  const name = 'curseforge-fabric-1.20.1.json';
  fs.mkdirSync(first); fs.writeFileSync(path.join(first, name), JSON.stringify(uploadReceipt()));
  try {
    recoverUploadReceipts(provenance, second, first);
    const original = globalThis.fetch;
    globalThis.fetch = async () => ({ok: false, status: 404});
    try { await assert.rejects(readJson('https://api.curseforge.com/v1/mods/1564458/files/777'), /404/); }
    finally { globalThis.fetch = original; }
    recoverUploadReceipts(provenance, third, second);
    assert.equal(JSON.parse(fs.readFileSync(path.join(third, name))).remote_id, 777);
    assert.equal(JSON.parse(fs.readFileSync(path.join(third, name))).state, 'accepted');
  } finally { fs.rmSync(temp, {recursive: true, force: true}); }
});
test('recovery merges journal upgrades, rejects conflicting IDs/hashes and preserves uncertain attempts', () => {
  const accepted = uploadReceipt();
  assert.equal(mergeUploadReceipts(accepted, uploadReceipt({state: 'verified'})).state, 'verified');
  assert.equal(mergeUploadReceipts(accepted, uploadReceipt({state: 'attempting', remote_id: null})).remote_id, 777);
  assert.equal(mergeUploadReceipts(uploadReceipt({state: 'uncertain', remote_id: null}), uploadReceipt({state: 'rejected', remote_id: null})).state, 'uncertain');
  assert.throws(() => mergeUploadReceipts(accepted, uploadReceipt({remote_id: 778})), /Conflicting recovery remote/);
  assert.throws(() => mergeUploadReceipts(accepted, uploadReceipt({hashes: {...file.hashes, sha256: 'wrong'}})), /Conflicting recovery receipt/);
  assert.throws(() => validateUploadReceipt(uploadReceipt({source_sha: 'b'.repeat(40)}), provenance, file, 'curseforge'));
});
test('multi-attempt recovery merges every exact-source journal and rejects conflicts', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'odyssey-journals-test-'));
  const root = path.join(temp, 'recovery'); const output = path.join(temp, 'output'); const name = 'curseforge-fabric-1.20.1.json';
  for (const id of ['101', '102']) fs.mkdirSync(path.join(root, id), {recursive: true});
  fs.writeFileSync(path.join(root, '101', name), JSON.stringify(uploadReceipt()));
  fs.writeFileSync(path.join(root, '102', name), JSON.stringify(uploadReceipt({state: 'verified'})));
  try {
    recoverUploadReceipts(provenance, output, root);
    assert.equal(JSON.parse(fs.readFileSync(path.join(output, name))).state, 'verified');
    fs.writeFileSync(path.join(root, '102', name), JSON.stringify(uploadReceipt({remote_id: 999})));
    assert.throws(() => recoverUploadReceipts(provenance, output, root), /Conflicting recovery remote/);
  } finally { fs.rmSync(temp, {recursive: true, force: true}); }
});
test('same release version from a different source SHA cannot ignore prior upload journals', () => {
  assertPriorReceiptScope([{name: `publication-receipts-${version}-${sha}-attempt1`}], version, sha);
  assertPriorReceiptScope([{name: `publication-receipts-1.3.0-${'b'.repeat(40)}-attempt1`}], version, sha);
  assert.throws(() => assertPriorReceiptScope([{name: `publication-receipts-${version}-${'b'.repeat(40)}-attempt1`}], version, sha), /different source/);
});


test('CurseForge upload dependency has the documented slug plus exact project ID', () => {
  assert.deepEqual(curseforgeRelations(file), {projects: [{slug: 'fabric-api', projectID: 306612, type: 'requiredDependency'}]});
  assert.deepEqual(curseforgeRelations({...file, loader: 'forge'}), {projects: []});
  assert.deepEqual(curseforgeRelations({...file, loader: 'neoforge'}), {projects: []});
});
