/** Explicit release targets; keep synchronized with ci.yml and inspect_release_jar.py. */
export const REPOSITORY = 'MeherBenSalem/Minimap';
export const MODRINTH_PROJECT = 'lLviL6Oq';
export const CURSEFORGE_PROJECT = 1564458;
export const FABRIC_API_MODRINTH = 'P7dR8mSH';
export const FABRIC_API_CURSEFORGE = 306612;
export const MATRIX = Object.freeze([
  { game: '1.20.1', loader: 'fabric', java: 17 },
  { game: '1.20.1', loader: 'forge', java: 17 },
  { game: '1.21.1', loader: 'fabric', java: 21 },
  { game: '1.21.1', loader: 'neoforge', java: 21 },
  { game: '26.1.2', loader: 'fabric', java: 25 },
  { game: '26.1.2', loader: 'neoforge', java: 25 },
  { game: '26.2', loader: 'fabric', java: 25 },
  { game: '26.2', loader: 'neoforge', java: 25 },
]);
export const filename = (version, target) => `odysseymap-${target.loader}-${target.game}-${version}.jar`;
export const versionNumber = (version, target) => `${version}+${target.loader}-${target.game}`;
export const displayName = (version, target) => `${version} · ${target.loader} · ${target.game}`;
export const requiredBuildSteps = Object.freeze([
  'Deterministic map persistence regressions',
  'Minecraft storage smoke test',
  'Build packaged loader JAR',
  'Inspect packaged release JAR',
  'Preserve immutable CI artifact',
]);
