/** Publish only the eight immutable, inspected artifacts downloaded from exact-SHA CI.
 * Credentials are read from environment variables only. Never reads credential files or logs values.
 * POST uploads are intentionally not retried: an uncertain upload must be reconciled first.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CURSEFORGE_PROJECT, MODRINTH_PROJECT, REPOSITORY, displayName, versionNumber, FABRIC_API_MODRINTH, FABRIC_API_CURSEFORGE } from './release-matrix.mjs';
import { hashes, requireThat, verifyProvenance, verifyModrinthProject, verifyCurseForgeProject, verifyModrinth, verifyCurseForge, findDuplicate, sectionForVersion, isDefiniteUploadRejection, validateUploadReceipt, mergeUploadReceipts, assertPriorReceiptScope, modrinthDependencies, curseforgeRelations } from './release-verification.mjs';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
class ReadError extends Error { constructor(url, status) { super(`Read failed (${status}): ${url}`); this.status = status; } }
export async function readJson(url, headers = {}) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const response = await fetch(url, {headers, signal: AbortSignal.timeout(60000), redirect: 'error'});
    if (response.ok) return response.json();
    if ([429, 502, 503, 504].includes(response.status) && attempt < 3) { await sleep(5000 * (attempt + 1)); continue; }
    throw new ReadError(url, response.status);
  }
}
const json = readJson;
function writeJson(directory, name, value) {
  fs.mkdirSync(directory, {recursive: true});
  const destination = path.join(directory, name);
  fs.writeFileSync(destination + '.tmp', JSON.stringify(value, null, 2) + '\n');
  fs.renameSync(destination + '.tmp', destination);
}
function changelog(version, sourceDirectory = root) {
  return sectionForVersion(fs.readFileSync(path.join(sourceDirectory, 'CHANGELOG.md'), 'utf8'), version);
}

const receiptName = (platform, file) => `${platform}-${file.loader}-${file.game}.json`;
export function recoverUploadReceipts(proof, receipts, recovery) {
  if (recovery) {
    const directories = [recovery, ...fs.readdirSync(recovery, {withFileTypes: true})
      .filter(entry => entry.isDirectory()).map(entry => path.join(recovery, entry.name))];
    for (const recoveredDirectory of directories) {
      const proofPath = path.join(recoveredDirectory, 'release-provenance.json');
      if (fs.existsSync(proofPath)) {
        const recoveredProof = JSON.parse(fs.readFileSync(proofPath, 'utf8'));
        requireThat(JSON.stringify(recoveredProof) === JSON.stringify(proof), 'Recovery provenance does not match original exact-source artifacts');
      }
      for (const file of proof.files) for (const platform of ['modrinth', 'curseforge']) {
        const name = receiptName(platform, file);
        const recoveredPath = path.join(recoveredDirectory, name);
        if (!fs.existsSync(recoveredPath)) continue;
        const recovered = JSON.parse(fs.readFileSync(recoveredPath, 'utf8'));
        validateUploadReceipt(recovered, proof, file, platform);
        const currentPath = path.join(receipts, name);
        let merged = recovered;
        if (fs.existsSync(currentPath)) {
          const current = JSON.parse(fs.readFileSync(currentPath, 'utf8'));
          validateUploadReceipt(current, proof, file, platform);
          merged = mergeUploadReceipts(current, recovered);
        }
        writeJson(receipts, name, merged);
      }
    }
  }
}

export function selectPlatforms(value = 'both') {
  requireThat(['both', 'modrinth', 'curseforge'].includes(value), 'PUBLISH_PLATFORM must be both, modrinth or curseforge');
  return value === 'both' ? ['modrinth', 'curseforge'] : [value];
}
export function requiredSecretNames(platforms) {
  requireThat(Array.isArray(platforms) && platforms.length && new Set(platforms).size === platforms.length
    && platforms.every(platform => ['modrinth', 'curseforge'].includes(platform)), 'Invalid publication platforms');
  return [...(platforms.includes('modrinth') ? ['MODRINTH_TOKEN'] : []),
    ...(platforms.includes('curseforge') ? ['CURSEFORGE_TOKEN', 'CURSEFORGE_API_KEY'] : [])];
}
export function importBrowserReceipts(proof, receipts, input) {
  if (!input) return;
  let bundle;
  try { bundle = typeof input === 'string' ? JSON.parse(input) : input; }
  catch { throw new Error('Browser receipt bundle must be valid JSON; do not include credentials.'); }
  requireThat(bundle && bundle.schema === 1 && bundle.repository === REPOSITORY
    && bundle.source_sha === proof.source_sha && bundle.ci_run_id === proof.ci_run_id && bundle.version === proof.version
    && Array.isArray(bundle.receipts), 'Browser receipt bundle source/repository/version mismatch');
  const identities = new Set();
  // Validate the entire import before retaining any receipt. Existing journals only upgrade.
  const validated = bundle.receipts.map(receipt => {
    const file = proof.files.find(file => file.filename === receipt.filename);
    requireThat(file && ['modrinth', 'curseforge'].includes(receipt.platform), 'Browser receipt artifact/platform mismatch');
    validateUploadReceipt(receipt, proof, file, receipt.platform);
    const id = receipt.remote_id;
    const validId = receipt.platform === 'curseforge' ? Number.isSafeInteger(id) && id > 0
      : typeof id === 'string' && /^[A-Za-z0-9]+$/.test(id);
    requireThat(id == null || validId, 'Browser receipt remote ID has an invalid platform format');
    requireThat(!['accepted', 'verified'].includes(receipt.state) || validId, 'Accepted browser receipt requires an immutable remote ID');
    const name = receiptName(receipt.platform, file);
    requireThat(!identities.has(name), 'Duplicate browser receipt identity');
    identities.add(name);
    // Import only the immutable recovery identity/state, never arbitrary input fields.
    const normalized = Object.fromEntries(['schema', 'repository', 'source_sha', 'ci_run_id', 'version',
      'filename', 'loader', 'game', 'hashes', 'size', 'platform', 'project_id', 'state', 'remote_id']
      .map(key => [key, receipt[key]]));
    normalized.method = 'browser_recovery';
    const currentPath = path.join(receipts, name);
    if (!fs.existsSync(currentPath)) return {name, value: normalized};
    const current = JSON.parse(fs.readFileSync(currentPath, 'utf8'));
    validateUploadReceipt(current, proof, file, receipt.platform);
    return {name, value: mergeUploadReceipts(current, normalized)};
  });
  for (const {name, value} of validated) writeJson(receipts, name, value);
}

export async function main() {
  const controllerSha = process.env.PUBLISHER_CONTROLLER_SHA || null;
  requireThat(!controllerSha || /^[a-f0-9]{40}$/.test(controllerSha), 'Publisher controller SHA must be a full commit SHA');
  const platforms = selectPlatforms(process.env.PUBLISH_PLATFORM || 'both');
  const publishMr = platforms.includes('modrinth');
  const publishCf = platforms.includes('curseforge');
  const version = process.env.VERSION;
  const sourceSha = process.env.SOURCE_SHA;
  const ciRunId = Number(process.env.CI_RUN_ID);
  requireThat(/^\d+\.\d+\.\d+$/.test(version || ''), 'VERSION is required');
  const sourceDirectory = path.resolve(process.env.RELEASE_SOURCE_DIRECTORY || root);
  requireThat(fs.readFileSync(path.join(sourceDirectory, 'VERSION'), 'utf8').trim() === version, 'Checked source release version mismatch');
  const directory = path.resolve(process.env.ARTIFACT_DIRECTORY || 'verified-release');
  const proof = JSON.parse(fs.readFileSync(path.join(directory, 'release-provenance.json'), 'utf8'));
  verifyProvenance(proof, version, sourceSha, ciRunId, name => fs.readFileSync(path.join(directory, name)));
  const files = proof.files;
  const receipts = path.resolve(process.env.RECEIPT_DIRECTORY || 'publication-receipts');
  const recovery = process.env.RECOVERY_RECEIPTS ? path.resolve(process.env.RECOVERY_RECEIPTS) : null;
  const baseReceipt = file => ({schema: 1, repository: REPOSITORY, source_sha: sourceSha, ci_run_id: ciRunId, version,
    filename: file.filename, loader: file.loader, game: file.game, hashes: file.hashes, size: file.size});
  const priorReceipt = (platform, file) => {
    const candidates = [path.join(receipts, receiptName(platform, file)), recovery && path.join(recovery, receiptName(platform, file))].filter(Boolean);
    const previous = candidates.find(candidate => fs.existsSync(candidate));
    if (!previous) return null;
    const value = JSON.parse(fs.readFileSync(previous, 'utf8'));
    const expected = baseReceipt(file);
    requireThat(Object.keys(expected).every(key => JSON.stringify(value[key]) === JSON.stringify(expected[key]))
      && value.platform === platform && value.project_id === (platform === 'modrinth' ? MODRINTH_PROJECT : CURSEFORGE_PROJECT), 'Recovery receipt source/project/artifact mismatch');
    return value;
  };
  const save = (platform, file, fields) => writeJson(receipts, receiptName(platform, file), {...baseReceipt(file), platform,
    project_id: platform === 'modrinth' ? MODRINTH_PROJECT : CURSEFORGE_PROJECT, recorded_at: new Date().toISOString(), ...fields});
  writeJson(receipts, 'release-provenance.json', proof);
  recoverUploadReceipts(proof, receipts, recovery);
  const browserReceiptDirectory = path.join(root, 'publication-recovery');
  if (fs.existsSync(browserReceiptDirectory)) {
    const baselines = fs.readdirSync(browserReceiptDirectory, {withFileTypes: true})
      .filter(entry => entry.isFile() && entry.name.endsWith('.json'))
      .map(entry => ({name: `publication-receipts-${entry.name.slice(0, -5)}`}));
    assertPriorReceiptScope(baselines, version, sourceSha);
  }
  const boundBrowserReceipts = path.join(browserReceiptDirectory, `${version}-${sourceSha}.json`);
  if (fs.existsSync(boundBrowserReceipts)) importBrowserReceipts(proof, receipts, fs.readFileSync(boundBrowserReceipts, 'utf8'));
  requireThat(!(process.env.BROWSER_RECEIPTS_JSON && process.env.BROWSER_RECEIPTS_FILE), 'Choose one browser receipt import source');
  importBrowserReceipts(proof, receipts, process.env.BROWSER_RECEIPTS_JSON
    || (process.env.BROWSER_RECEIPTS_FILE ? fs.readFileSync(process.env.BROWSER_RECEIPTS_FILE, 'utf8') : null));
  const secretNames = requiredSecretNames(platforms);
  const missing = secretNames.filter(name => !process.env[name]);
  requireThat(!missing.length, `Missing GitHub Actions secrets: ${missing.join(', ')}. Check presence/permissions; do not send values.`);
  const mrHeaders = {Authorization: process.env.MODRINTH_TOKEN, 'User-Agent': 'NightBeam-OdysseyMap-release'};
  const cfHeaders = {'x-api-key': process.env.CURSEFORGE_API_KEY};
  const uploadHeaders = {'X-Api-Token': process.env.CURSEFORGE_TOKEN};
  const notes = changelog(version, sourceDirectory);


  // Every project, credential, tag and duplicate is preflighted before the first upload.
  let mrProject, cfResponse, gameTags, loaderTags, cfTagResponse, mrUser, organization;
  if (publishMr) {
    [mrProject, gameTags, loaderTags, mrUser] = await Promise.all([
      json(`https://api.modrinth.com/v2/project/${MODRINTH_PROJECT}`, mrHeaders),
      json('https://api.modrinth.com/v2/tag/game_version', mrHeaders),
      json('https://api.modrinth.com/v2/tag/loader', mrHeaders),
      json('https://api.modrinth.com/v2/user', mrHeaders),
    ]);
    verifyModrinthProject(mrProject);
    const [projectTeam, destinationOrganization] = await Promise.all([
      json(`https://api.modrinth.com/v2/team/${mrProject.team}/members`, mrHeaders),
      json(`https://api.modrinth.com/v3/organization/${mrProject.organization}`, mrHeaders),
    ]);
    organization = destinationOrganization;
    requireThat(organization.id === 'SVDVsyjd' && organization.slug === 'nightbeam', 'Modrinth organization mismatch');
    const organizationTeam = Array.isArray(organization.members) ? organization.members
      : organization.team_id ? await json(`https://api.modrinth.com/v2/team/${organization.team_id}/members`, mrHeaders) : [];
    requireThat([...projectTeam, ...organizationTeam].some(member => member.accepted !== false && member.user?.id === mrUser.id), 'Authenticated Modrinth user is not an accepted destination team member');
  }
  if (publishCf) {
    [cfResponse, cfTagResponse] = await Promise.all([
      json(`https://api.curseforge.com/v1/mods/${CURSEFORGE_PROJECT}`, cfHeaders),
      json('https://minecraft.curseforge.com/api/game/versions', uploadHeaders),
    ]);
    verifyCurseForgeProject(cfResponse.data);
  }
  const cfTags = publishCf ? (Array.isArray(cfTagResponse) ? cfTagResponse : cfTagResponse.data) : [];
  requireThat(!publishCf || Array.isArray(cfTags), 'Invalid CurseForge game-version inventory');
  const uniqueCfTag = name => {
    const hits = cfTags.filter(tag => tag.name === name);
    requireThat(hits.length === 1 && Number.isInteger(hits[0].id), `Missing/ambiguous CurseForge tag: ${name}`);
    return hits[0].id;
  };
  // All packaged entrypoints/dependencies are client-only; a server install is unsupported.
  const environmentIds = publishCf ? ['Client'].map(uniqueCfTag) : [];
  const loaderNames = {fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge'};
  const knownLoaderIds = {fabric: 7499, forge: 7498, neoforge: 10150};
  const tagIds = new Map();
  for (const file of files) {
    if (publishMr) requireThat(gameTags.some(tag => tag.version === file.game && tag.version_type === 'release')
      && loaderTags.some(tag => tag.name === file.loader && tag.supported_project_types.includes('mod')), 'Unsupported Modrinth game/loader tag');
    if (publishCf) {
      const loaderId = uniqueCfTag(loaderNames[file.loader]);
      requireThat(loaderId === knownLoaderIds[file.loader], 'CurseForge loader inventory ID changed; review before upload');
      tagIds.set(file.filename, [...environmentIds, loaderId, uniqueCfTag(file.game)]);
    }
  }
  const fabricFiles = files.filter(file => file.loader === 'fabric');
  if (fabricFiles.length && publishMr) {
    const mrApi = await json(`https://api.modrinth.com/v2/project/${FABRIC_API_MODRINTH}`, mrHeaders);
    requireThat(mrApi.id === FABRIC_API_MODRINTH && mrApi.slug === 'fabric-api' && mrApi.title === 'Fabric API'
      && mrApi.source_url === 'https://github.com/FabricMC/fabric' && mrApi.status === 'approved', 'Fabric API Modrinth dependency identity mismatch');
    for (const file of fabricFiles) {
      const available = await json(`https://api.modrinth.com/v2/project/${FABRIC_API_MODRINTH}/version?loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(JSON.stringify([file.game]))}`, mrHeaders);
      requireThat(available.some(entry => entry.project_id === FABRIC_API_MODRINTH && entry.game_versions?.includes(file.game)
        && entry.loaders?.includes('fabric') && entry.status === 'listed' && entry.version_type === 'release'), `No available Fabric API Modrinth dependency for ${file.game}`);
    }
  }
  if (fabricFiles.length && publishCf) {
    const cfApi = await json(`https://api.curseforge.com/v1/mods/${FABRIC_API_CURSEFORGE}`, cfHeaders);
    requireThat(cfApi.data?.id === FABRIC_API_CURSEFORGE && cfApi.data.slug === 'fabric-api' && cfApi.data.name === 'Fabric API'
      && cfApi.data.gameId === 432 && cfApi.data.links?.sourceUrl === 'https://github.com/FabricMC/fabric'
      && cfApi.data.authors?.some(author => author.name === 'modmuss50'), 'Fabric API CurseForge dependency identity mismatch');
    for (const file of fabricFiles) {
      const available = await json(`https://api.curseforge.com/v1/mods/${FABRIC_API_CURSEFORGE}/files?gameVersion=${encodeURIComponent(file.game)}&modLoaderType=4&pageSize=50`, cfHeaders);
      requireThat(available.data?.some(entry => entry.modId === FABRIC_API_CURSEFORGE && entry.gameVersions?.includes(file.game)
        && entry.gameVersions?.includes('Fabric') && entry.isAvailable && entry.releaseType === 1), `No available Fabric API CurseForge dependency for ${file.game}`);
    }
  }
  const mrVersions = publishMr ? await json(`https://api.modrinth.com/v2/project/${MODRINTH_PROJECT}/version`, mrHeaders) : [];
  const cfFiles = [];
  if (publishCf) for (let index = 0; ; index += 50) {
    const response = await json(`https://api.curseforge.com/v1/mods/${CURSEFORGE_PROJECT}/files?pageSize=50&index=${index}`, cfHeaders);
    requireThat(Array.isArray(response.data) && response.pagination?.index === index, 'Invalid CurseForge file pagination');
    cfFiles.push(...response.data);
    if (index + response.data.length >= response.pagination.totalCount) break;
    requireThat(response.data.length > 0 && index < 100000, 'Incomplete CurseForge file inventory');
  }
  const existing = {modrinth: new Map(), curseforge: new Map()};
  for (const file of files) {
    const mr = findDuplicate(mrVersions, entry => entry.version_number === versionNumber(version, file)
      || entry.name === displayName(version, file) || entry.files?.some(remote => remote.filename === file.filename)
      || (entry.version_number === version || entry.version_number.startsWith(version + '+'))
        && entry.game_versions?.includes(file.game) && entry.loaders?.includes(file.loader), entry => verifyModrinth(entry, version, file));
    let cf = findDuplicate(cfFiles, entry => entry.fileName === file.filename || entry.displayName === displayName(version, file)
      || ((entry.displayName === version || entry.displayName?.startsWith(version + ' · ')) || entry.fileName?.endsWith(`-${version}.jar`))
        && entry.gameVersions?.includes(file.game) && entry.gameVersions?.includes(loaderNames[file.loader]), entry => verifyCurseForge(entry, version, file));
    for (const platform of platforms) {
      let match = platform === 'modrinth' ? mr : cf;
      const prior = priorReceipt(platform, file);
      if (!match && prior?.remote_id) {
        // This accepted ID is immutable even while moderation hides public metadata.
        // Public verification below will wait; it can never cause a second POST.
        match = {id: prior.remote_id, recovery_pending: true};
      }
      if (match && prior?.remote_id) requireThat(String(match.id) === String(prior.remote_id), 'Public listing conflicts with the recorded accepted upload ID');
      requireThat(match || !prior || prior.state === 'rejected', `Unresolved ${platform} upload attempt for ${file.filename}. Reconcile the receipt before any retry.`);
      if (match) existing[platform].set(file.filename, match);
    }
  }
  writeJson(receipts, 'preflight.json', {repository: REPOSITORY, source_sha: sourceSha, ci_run_id: ciRunId, version, controller_sha: controllerSha,
    platforms,
    destinations: {
      ...(publishMr ? {modrinth: {id: mrProject.id, slug: mrProject.slug, organization: organization.slug}} : {}),
      ...(publishCf ? {curseforge: {id: cfResponse.data.id, slug: cfResponse.data.slug, authors: cfResponse.data.authors.map(author => author.name)}} : {})},
    files: files.map(file => ({filename: file.filename, hashes: file.hashes, curseforge_tag_ids: tagIds.get(file.filename),
      existing_modrinth_id: existing.modrinth.get(file.filename)?.id, existing_curseforge_id: existing.curseforge.get(file.filename)?.id}))});
  console.log(`Preflight passed for ${files.length} exact CI artifacts on ${platforms.join(', ')}, verified destination projects, all game/loader tags and duplicate versions.`);
  if (process.env.PREFLIGHT_ONLY === 'true') return;
  const deadline = Date.now() + 45 * 60 * 1000;
  async function verifyPublic(platform, file, id, original) {
    let remote;
    while (true) {
      try {
        remote = platform === 'modrinth'
          ? await json(`https://api.modrinth.com/v2/version/${id}`)
          : (await json(`https://api.curseforge.com/v1/mods/${CURSEFORGE_PROJECT}/files/${id}`, cfHeaders)).data;
        save(platform, file, {state: 'accepted', remote_id: id, upload: original, public: remote});
        if (platform === 'curseforge' && [5, 6, 7, 8, 12, 15].includes(remote.fileStatus)) {
          throw new Error(`CurseForge accepted upload ${id} has terminal/unavailable file status ${remote.fileStatus}; inspect the retained receipt before any further action.`);
        }
        if (platform === 'curseforge' && (!remote.isAvailable || !remote.hashes?.length)) throw new ReadError('CurseForge moderation/public availability', 404);
        (platform === 'modrinth' ? verifyModrinth : verifyCurseForge)(remote, version, file);
        const download = platform === 'modrinth' ? remote.files[0].url : remote.downloadUrl
          || (await json(`https://api.curseforge.com/v1/mods/${CURSEFORGE_PROJECT}/files/${id}/download-url`, cfHeaders)).data;
        const url = new URL(download);
        requireThat(url.protocol === 'https:' && (platform === 'modrinth' ? url.hostname === 'cdn.modrinth.com'
          : url.hostname === 'forgecdn.net' || url.hostname.endsWith('.forgecdn.net')), 'Unexpected public artifact download destination');
        const response = await fetch(url, {signal: AbortSignal.timeout(60000), redirect: 'error'});
        if (!response.ok) throw new ReadError('Public artifact download', response.status);
        const bytes = Buffer.from(await response.arrayBuffer());
        const digest = hashes(bytes);
        requireThat(bytes.length === file.size && digest.sha256 === file.hashes.sha256 && digest.sha512 === file.hashes.sha512, 'Publicly downloaded artifact differs from immutable CI artifact');
        save(platform, file, {state: 'verified', remote_id: id, upload: original, public: remote, public_download_hashes: digest});
        console.log(`${platform} public artifact verified: ${id} ${file.filename} SHA256 ${digest.sha256}`);
        return;
      } catch (error) {
        if (!(error instanceof ReadError) || ![404, 429, 502, 503, 504].includes(error.status) || Date.now() >= deadline) throw error;
        console.log(`${platform} accepted upload ${id} is awaiting public verification; no duplicate upload will be attempted.`);
        await sleep(30000);
      }
    }
  }
  for (const platform of platforms) {
    for (const file of files) {
      const previous = existing[platform].get(file.filename);
      if (previous) { await verifyPublic(platform, file, previous.id, priorReceipt(platform, file)?.upload); continue; }
      requireThat(process.env.VERIFY_ONLY !== 'true', `Missing ${platform} upload; VERIFY_ONLY prevents publication`);
      const form = new FormData();
      if (platform === 'modrinth') {
        form.append('data', JSON.stringify({name: displayName(version, file), version_number: versionNumber(version, file),
          changelog: notes, dependencies: modrinthDependencies(file), environment: 'client_only', game_versions: [file.game], version_type: 'release', loaders: [file.loader],
          featured: false, status: 'listed', project_id: MODRINTH_PROJECT, file_parts: ['file_0'], primary_file: 'file_0'}));
        form.append('file_0', new Blob([fs.readFileSync(path.join(directory, file.filename))]), file.filename);
      } else {
        form.append('metadata', JSON.stringify({changelog: notes, changelogType: 'markdown', displayName: displayName(version, file),
          gameVersions: tagIds.get(file.filename), releaseType: 'release',
          relations: curseforgeRelations(file)}));
        form.append('file', new Blob([fs.readFileSync(path.join(directory, file.filename))]), file.filename);
      }
      save(platform, file, {state: 'attempting', remote_id: null});
      let response;
      try {
        response = await fetch(platform === 'modrinth' ? 'https://api.modrinth.com/v2/version'
          : `https://minecraft.curseforge.com/api/projects/${CURSEFORGE_PROJECT}/upload-file`, {
          method: 'POST', headers: platform === 'modrinth' ? mrHeaders : uploadHeaders, body: form,
          signal: AbortSignal.timeout(120000), redirect: 'error',
        });
      } catch {
        save(platform, file, {state: 'uncertain', remote_id: null, reason: 'Upload request ended without a response'});
        throw new Error(`${platform} upload outcome is uncertain; reconcile the saved receipt before retrying.`);
      }
      if (!response.ok) {
        // A returned rejection is bounded and recorded. Never retry this POST automatically.
        save(platform, file, {state: isDefiniteUploadRejection(response.status) ? 'rejected' : 'uncertain', http_status: response.status, remote_id: null});
        throw new Error(`${platform} upload returned HTTP ${response.status}; inspect saved receipt before retrying.`);
      }
      let upload;
      try { upload = await response.json(); }
      catch {
        save(platform, file, {state: 'uncertain', remote_id: null, http_status: response.status, reason: 'Accepted response was not readable JSON'});
        throw new Error(`${platform} accepted upload without a readable receipt; reconcile before retrying.`);
      }
      const id = upload?.id ?? upload?.data?.id;
      if (!(platform === 'curseforge' ? Number.isSafeInteger(id) && id > 0 : typeof id === 'string' && /^[A-Za-z0-9]+$/.test(id))) {
        save(platform, file, {state: 'uncertain', remote_id: null, http_status: response.status, reason: 'Accepted response lacked a valid immutable ID'});
        throw new Error(`${platform} accepted upload without a valid returned ID; reconcile before retrying.`);
      }
      save(platform, file, {state: 'accepted', remote_id: id, upload});
      console.log(`${platform} upload accepted: ${id} ${file.filename}`);
      await verifyPublic(platform, file, id, upload);
    }
  }
  writeJson(receipts, 'publication-complete.json', {repository: REPOSITORY, source_sha: sourceSha, ci_run_id: ciRunId,
    version, controller_sha: controllerSha, platforms, completed_at: new Date().toISOString(), verified_uploads: files.length * platforms.length});
  console.log(`All ${files.length * platforms.length} selected-platform public uploads verified against the exact CI artifact hashes.`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main().catch(error => {
  console.error(error.message); process.exitCode = 1;
});
