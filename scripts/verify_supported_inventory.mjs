/** Read only. Recheck actual Mojang runtime requirements and Modrinth tags. */
import { MATRIX } from './release-matrix.mjs';
import { requireThat, verifyModrinthProject } from './release-verification.mjs';
async function json(url) {
  const res = await fetch(url, {headers: {'User-Agent': 'NightBeam-OdysseyMap-release-inventory'}, signal: AbortSignal.timeout(60000)});
  requireThat(res.ok, `Public inventory read failed: ${res.status} ${url}`);
  return res.json();
}
const [mojang, versions, loaders, project] = await Promise.all([
  json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'),
  json('https://api.modrinth.com/v2/tag/game_version'),
  json('https://api.modrinth.com/v2/tag/loader'),
  json('https://api.modrinth.com/v2/project/lLviL6Oq'),
]);
verifyModrinthProject(project);
for (const game of new Set(MATRIX.map(target => target.game))) {
  const targets = MATRIX.filter(target => target.game === game);
  const version = mojang.versions.find(version => version.id === game && version.type === 'release');
  requireThat(version && versions.some(version => version.version === game && version.version_type === 'release'), `Unsupported Minecraft release: ${game}`);
  requireThat(new URL(version.url).hostname === 'piston-meta.mojang.com', 'Unexpected Mojang inventory destination');
  const detail = await json(version.url);
  requireThat(detail.id === game && targets.every(target => target.java === detail.javaVersion?.majorVersion), `Java runtime requirement mismatch for ${game}`);
  requireThat(targets.every(target => loaders.some(loader => loader.name === target.loader && loader.supported_project_types.includes('mod'))), 'Unsupported mod loader');
  console.log(`${game}: Java ${detail.javaVersion.majorVersion}, ${targets.map(target => target.loader).join(' + ')}`);
}
console.log('Official supported inventory verified. CurseForge authenticated tag inventory is checked before publication.');
