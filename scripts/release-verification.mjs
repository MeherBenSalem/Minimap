import crypto from 'node:crypto';
import { CURSEFORGE_PROJECT, MODRINTH_PROJECT, MATRIX, REPOSITORY, displayName, filename, requiredBuildSteps, versionNumber, FABRIC_API_MODRINTH, FABRIC_API_CURSEFORGE } from './release-matrix.mjs';

export const hashes = bytes => Object.fromEntries(['sha1', 'sha256', 'sha512'].map(name => [name, crypto.createHash(name).update(bytes).digest('hex')]));
export const sameSet = (actual, expected) => Array.isArray(actual) && actual.length === expected.length && new Set(actual).size === expected.length && expected.every(value => actual.includes(value));
export function requireThat(condition, message) { if (!condition) throw new Error(message); }
export function verifyProvenance(proof, version, sourceSha, ciRunId, readBytes) {
  requireThat(proof.schema === 1 && proof.repository === REPOSITORY && proof.version === version
    && proof.source_sha === sourceSha && proof.ci_run_id === ciRunId && /^[a-f0-9]{40}$/.test(sourceSha), 'Release provenance identity mismatch');
  requireThat(Array.isArray(proof.files) && proof.files.length === MATRIX.length, 'Expected eight release artifacts');
  for (const target of MATRIX) {
    const matches = proof.files.filter(file => file.game === target.game && file.loader === target.loader && file.java === target.java);
    requireThat(matches.length === 1, 'Missing/duplicate artifact target');
    const file = matches[0];
    requireThat(file.filename === filename(version, target) && file.version === version, 'Artifact filename/version mismatch');
    requireThat(file.inspection?.environment === 'client' && file.inspection?.fabric_api_required === (target.loader === 'fabric'), 'Packaged environment/dependency inspection mismatch');
    const bytes = readBytes(file.filename);
    const actual = hashes(bytes);
    requireThat(file.size === bytes.length && Object.keys(actual).every(key => actual[key] === file.hashes?.[key]), 'Artifact does not match immutable CI hashes');
  }
}
export function verifyCiRun(run, jobs, sourceSha) {
  requireThat(run.head_sha === sourceSha && run.path === '.github/workflows/ci.yml'
    && run.name === 'CI' && run.status === 'completed' && run.conclusion === 'success'
    && run.head_repository?.full_name === REPOSITORY && run.event === 'push' && run.head_branch === 'main',
    'No successful main-branch CI for the exact release source SHA');
  const required = MATRIX.map(target => ({name: `build (${target.game}, ${target.loader}, ${target.java})`, steps: requiredBuildSteps}));
  required.push({name: 'release-validation', steps: ['Release helper regression tests', 'Packaged JAR verifier regression tests', 'Verify public supported inventory']});
  const latest = new Map();
  for (const job of jobs) if (!latest.has(job.name) || latest.get(job.name).id < job.id) latest.set(job.name, job);
  requireThat(latest.size === required.length, 'Unexpected or incomplete CI job matrix');
  for (const expected of required) {
    const job = latest.get(expected.name);
    requireThat(job?.conclusion === 'success' && expected.steps.every(name => job.steps?.some(step => step.name === name && step.conclusion === 'success')),
      `Required CI job/steps have not passed: ${expected.name}`);
  }
}
export function verifyModrinthProject(project) {
  requireThat(project.id === MODRINTH_PROJECT && project.slug === 'odyssey-map' && project.title === 'Odyssey Map'
    && project.project_type === 'mod' && project.source_url === `https://github.com/${REPOSITORY}`
    && project.organization === 'SVDVsyjd' && project.status === 'approved', 'Modrinth destination identity mismatch');
}
export function verifyCurseForgeProject(project) {
  requireThat(project.id === CURSEFORGE_PROJECT && project.slug === 'odyssey-map' && project.name === 'Odyssey Map'
    && project.gameId === 432 && project.authors?.some(author => author.name === 'NightBeamStudio')
    && project.links?.sourceUrl === `https://github.com/${REPOSITORY}`, 'CurseForge destination identity/owner mismatch');
}
export function verifyModrinth(receipt, version, file) {
  requireThat(receipt.project_id === MODRINTH_PROJECT && receipt.version_number === versionNumber(version, file)
    && receipt.name === displayName(version, file) && receipt.version_type === 'release' && receipt.status === 'listed'
    && sameSet(receipt.game_versions, [file.game]) && sameSet(receipt.loaders, [file.loader])
    && receipt.environment === 'client_only' && verifyModrinthDependencies(receipt.dependencies, file) && receipt.files?.length === 1
    && receipt.files[0].primary === true && receipt.files[0].filename === file.filename
    && receipt.files[0].size === file.size && receipt.files[0].hashes?.sha512 === file.hashes.sha512
    && receipt.files[0].hashes?.sha1 === file.hashes.sha1, 'Modrinth metadata/hash mismatch');
}
export function verifyCurseForge(receipt, version, file) {
  const loaderName = {fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge'}[file.loader];
  requireThat(receipt.modId === CURSEFORGE_PROJECT && receipt.fileName === file.filename
    && receipt.displayName === displayName(version, file) && receipt.releaseType === 1
    && receipt.fileLength === file.size && sameSet(receipt.gameVersions, [file.game, loaderName, 'Client'])
    && verifyCurseForgeDependencies(receipt.dependencies, file)
    && receipt.hashes?.some(hash => hash.algo === 1 && hash.value.toLowerCase() === file.hashes.sha1), 'CurseForge metadata/hash mismatch');
}
export function findDuplicate(entries, candidates, verify) {
  const matches = entries.filter(candidates);
  requireThat(matches.length <= 1, 'Ambiguous duplicate release versions/files');
  if (matches.length) verify(matches[0]);
  return matches[0];
}
export function sectionForVersion(markdown, version) {
  const escaped = version.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const exactVersion = new RegExp(`(?:^|[^A-Za-z0-9.])v?${escaped}(?:$|[^A-Za-z0-9.])`);
  const lines = markdown.split(/\r?\n/);
  const heading = line => /^(#{1,6})\s+(.+)$/.exec(line.trim());
  const start = lines.findIndex(line => {
    const match = heading(line);
    return match && exactVersion.test(match[2]);
  });
  requireThat(start >= 0, `Missing release changelog section for ${version}`);
  const level = heading(lines[start])[1].length;
  let end = lines.length;
  for (let index = start + 1; index < lines.length; index++) {
    const match = heading(lines[index]);
    if (match && match[1].length <= level && /(?:^|[^A-Za-z0-9.])v?\d+\.\d+\.\d+(?:$|[^A-Za-z0-9.])/.test(match[2])) {
      end = index;
      break;
    }
  }
  const notes = lines.slice(start + 1, end).join('\n').trim();
  requireThat(notes.length > 0, 'Empty release changelog');
  return notes;
}

export const isDefiniteUploadRejection = status => [400, 401, 403, 404, 405, 413, 415, 422].includes(status);
export function validateUploadReceipt(receipt, proof, file, platform) {
  const expected = {schema: 1, repository: REPOSITORY, source_sha: proof.source_sha, ci_run_id: proof.ci_run_id,
    version: proof.version, filename: file.filename, loader: file.loader, game: file.game, hashes: file.hashes,
    size: file.size, platform, project_id: platform === 'modrinth' ? MODRINTH_PROJECT : CURSEFORGE_PROJECT};
  requireThat(Object.keys(expected).every(key => JSON.stringify(receipt[key]) === JSON.stringify(expected[key]))
    && ['attempting', 'uncertain', 'rejected', 'accepted', 'verified'].includes(receipt.state), 'Recovery receipt source/project/artifact/state mismatch');
}
export function mergeUploadReceipts(left, right) {
  const identity = ['schema', 'repository', 'source_sha', 'ci_run_id', 'version', 'filename', 'loader', 'game', 'hashes', 'size', 'platform', 'project_id'];
  requireThat(identity.every(key => JSON.stringify(left[key]) === JSON.stringify(right[key])), 'Conflicting recovery receipt identities/hashes');
  requireThat(!left.remote_id || !right.remote_id || String(left.remote_id) === String(right.remote_id), 'Conflicting recovery remote upload IDs');
  const rank = {rejected: 0, attempting: 1, uncertain: 2, accepted: 3, verified: 4};
  const preferred = rank[left.state] >= rank[right.state] ? left : right;
  return {...preferred, remote_id: preferred.remote_id || left.remote_id || right.remote_id || null};
}

export function modrinthDependencies(file) {
  return file.loader === 'fabric' ? [{project_id: FABRIC_API_MODRINTH, dependency_type: 'required'}] : [];
}
export function verifyModrinthDependencies(dependencies, file) {
  return Array.isArray(dependencies) && (file.loader === 'fabric'
    ? dependencies.length === 1 && dependencies[0].project_id === FABRIC_API_MODRINTH
      && dependencies[0].dependency_type === 'required' && !dependencies[0].version_id && !dependencies[0].file_name
    : dependencies.length === 0);
}
export function verifyCurseForgeDependencies(dependencies, file) {
  return Array.isArray(dependencies) && (file.loader === 'fabric'
    ? dependencies.length === 1 && dependencies[0].modId === FABRIC_API_CURSEFORGE && dependencies[0].relationType === 3
    : dependencies.length === 0);
}
export function assertPriorReceiptScope(artifacts, version, sourceSha) {
  const escaped = version.replaceAll('.', '\\.');
  const matcher = new RegExp(`^publication-receipts-${escaped}-([a-f0-9]{40})(?:-attempt[1-9][0-9]*)?$`);
  for (const artifact of artifacts) {
    const hit = matcher.exec(artifact.name);
    requireThat(!hit || hit[1] === sourceSha, 'A previous publication journal for this version uses different source/artifact bytes; reconcile it or bump the release version before uploading');
  }
}
export function curseforgeRelations(file) {
  return {projects: file.loader === 'fabric' ? [{slug: 'fabric-api', projectID: FABRIC_API_CURSEFORGE, type: 'requiredDependency'}] : []};
}
