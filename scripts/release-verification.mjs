import crypto from 'node:crypto';
import { CURSEFORGE_PROJECT, MODRINTH_PROJECT, MATRIX, REPOSITORY, displayName, filename, requiredBuildSteps, versionNumber } from './release-matrix.mjs';

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
    && receipt.dependencies?.length === 0 && receipt.files?.length === 1
    && receipt.files[0].primary === true && receipt.files[0].filename === file.filename
    && receipt.files[0].size === file.size && receipt.files[0].hashes?.sha512 === file.hashes.sha512
    && receipt.files[0].hashes?.sha1 === file.hashes.sha1, 'Modrinth metadata/hash mismatch');
}
export function verifyCurseForge(receipt, version, file) {
  const loaderName = {fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge'}[file.loader];
  const otherLoaders = ['Fabric', 'Forge', 'NeoForge'].filter(name => name !== loaderName);
  requireThat(receipt.modId === CURSEFORGE_PROJECT && receipt.fileName === file.filename
    && receipt.displayName === displayName(version, file) && receipt.releaseType === 1
    && receipt.fileLength === file.size && receipt.gameVersions?.includes(file.game)
    && receipt.gameVersions?.includes(loaderName) && !otherLoaders.some(name => receipt.gameVersions.includes(name))
    && receipt.hashes?.some(hash => hash.algo === 1 && hash.value.toLowerCase() === file.hashes.sha1), 'CurseForge metadata/hash mismatch');
}
export function findDuplicate(entries, candidates, verify) {
  const matches = entries.filter(candidates);
  requireThat(matches.length <= 1, 'Ambiguous duplicate release versions/files');
  if (matches.length) verify(matches[0]);
  return matches[0];
}
