/**
 * Upload Remnants Minimap jars from all-jars/ to Modrinth and CurseForge.
 *
 *   node scripts/upload_platforms.mjs --version 2.0.0 [--modrinth-only | --curseforge-only]
 *                                     [--dry-run] [--sync-project] [--changelog-file FILE]
 *
 * Jars are named remnantminimaps-<loader>-<minecraft>-<version>.jar; each one is published for exactly
 * that loader and Minecraft version (see SUPPORTED below for the version roots of this repository).
 *
 * Environment: MODRINTH_TOKEN, CURSEFORGE_TOKEN (only the ones for the selected platforms).
 * Optional: MODRINTH_PROJECT_ID, CURSEFORGE_PROJECT_ID to override the project ids below.
 * Locally, secrets can also live in secrets/local.env (KEY=value lines, never committed).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, "..");

const MODRINTH_ID = process.env.MODRINTH_PROJECT_ID || "lLviL6Oq";
const CURSEFORGE_ID = process.env.CURSEFORGE_PROJECT_ID || "1564458";
const MOD_TITLE = "Remnants Minimap";
const JAR_DIR = path.join(ROOT, "all-jars");
const ICON_PATH = path.join(ROOT, "docs", "icon.png");
const DESCRIPTION_PATH = path.join(ROOT, "CURSEFORGE.md");
const RELEASE_STATE_PATH = path.join(ROOT, ".release-upload-state.json");

const MODRINTH_FABRIC_API = "P7dR8mSH";
const CURSEFORGE_LOADER_IDS = { fabric: 7499, forge: 7498, neoforge: 10150 };

/** Minecraft version of each version root and the loaders it builds. */
const SUPPORTED = {
  "26.2": ["fabric", "neoforge"],
  "26.1.2": ["fabric", "neoforge"],
  "1.21.1": ["fabric", "neoforge"],
  "1.20.1": ["fabric", "forge"],
};

function loadLocalEnv() {
  const envPath = path.join(ROOT, "secrets", "local.env");
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, "utf8").split(/\r?\n/)) {
    const m = line.match(/^([^#=]+)=(.*)$/);
    if (m && !process.env[m[1].trim()]) process.env[m[1].trim()] = m[2].trim();
  }
  console.log("Loaded secrets from", envPath);
}

function parseArgs(argv) {
  const out = { curseforgeOnly: false, modrinthOnly: false, dryRun: false, syncProject: false };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--version") out.version = argv[++i];
    else if (a === "--changelog-file") out.changelogFile = argv[++i];
    else if (a === "--curseforge-only") out.curseforgeOnly = true;
    else if (a === "--modrinth-only") out.modrinthOnly = true;
    else if (a === "--dry-run") out.dryRun = true;
    else if (a === "--sync-project") out.syncProject = true;
    else throw new Error(`Unknown arg ${a}`);
  }
  if (!out.version) throw new Error("--version required");
  out.version = out.version.replace(/^v/, "");
  return out;
}

function parseJar(jar) {
  const name = path.basename(jar);
  const m = name.match(/^remnantminimaps-(fabric|forge|neoforge)-(.+)-(\d+\.\d+\.\d+)\.jar$/i);
  if (!m) throw new Error(`Cannot parse jar name: ${name}`);
  const loader = m[1].toLowerCase();
  const game = m[2];
  if (!SUPPORTED[game]?.includes(loader)) {
    throw new Error(`${name}: ${loader} on Minecraft ${game} is not a version root of this repository`);
  }
  return { jar, name, loader, game, version: m[3] };
}

function collectJars(version) {
  if (!fs.existsSync(JAR_DIR)) throw new Error(`Missing ${JAR_DIR}; build first`);
  return fs
    .readdirSync(JAR_DIR)
    .filter((n) => n.startsWith("remnantminimaps-") && n.endsWith(`-${version}.jar`))
    .map((n) => path.join(JAR_DIR, n))
    .sort();
}

/** The CHANGELOG.md section for this version: from its "# ... vX.Y.Z" heading to the next one. */
function readChangelog(version, changelogFile) {
  if (changelogFile) return fs.readFileSync(changelogFile, "utf8");
  const file = path.join(ROOT, "CHANGELOG.md");
  if (fs.existsSync(file)) {
    const lines = fs.readFileSync(file, "utf8").replace(/^﻿/, "").split(/\r?\n/);
    const start = lines.findIndex((l) => /^#\s/.test(l) && l.includes(`v${version}`));
    if (start >= 0) {
      let end = lines.findIndex((l, i) => i > start && /^#\s/.test(l));
      if (end < 0) end = lines.length;
      const section = lines.slice(start + 1, end).join("\n").replace(/\n-{3,}\s*$/m, "").trim();
      if (section) return section;
    }
  }
  return `## ${MOD_TITLE} ${version}`;
}

function loadReleaseState() {
  try {
    const parsed = JSON.parse(fs.readFileSync(RELEASE_STATE_PATH, "utf8"));
    if (Array.isArray(parsed.uploads)) return parsed;
  } catch {}
  return { uploads: [] };
}

function hasRecordedUpload(state, platform, version, jar) {
  return state.uploads.some((e) => e.platform === platform && e.version === version && e.fileName === jar.name);
}

function recordUpload(state, platform, version, jar, remoteId) {
  if (hasRecordedUpload(state, platform, version, jar)) return;
  state.uploads.push({ platform, version, loader: jar.loader, game: jar.game, fileName: jar.name, remoteId, recordedAt: new Date().toISOString() });
  fs.writeFileSync(RELEASE_STATE_PATH, `${JSON.stringify(state, null, 2)}\n`, "utf8");
}

const versionLabel = (version, jar) => `${version} · ${jar.loader} · ${jar.game}`;

// ------------------------------------------------------------------ Modrinth

async function modrinth(pathname, token, init = {}) {
  const res = await fetch(`https://api.modrinth.com/v2${pathname}`, {
    ...init,
    headers: { Authorization: token, "User-Agent": "MeherBenSalem/Minimap publish", ...(init.headers || {}) },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Modrinth ${init.method || "GET"} ${pathname}: ${res.status} ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : null;
}

async function syncModrinthProject(token, dryRun) {
  const body = fs.existsSync(DESCRIPTION_PATH) ? fs.readFileSync(DESCRIPTION_PATH, "utf8") : null;
  if (dryRun) {
    console.log("[dry-run] Modrinth project: title, description and icon");
    return;
  }
  const patch = { title: MOD_TITLE, description: "An Elden Ring inspired minimap and world map: parchment cartography, Sites of Grace, beacons and markers." };
  if (body) patch.body = body;
  await modrinth(`/project/${MODRINTH_ID}`, token, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
  if (fs.existsSync(ICON_PATH)) {
    await modrinth(`/project/${MODRINTH_ID}/icon?ext=png`, token, {
      method: "PATCH",
      headers: { "Content-Type": "image/png" },
      body: fs.readFileSync(ICON_PATH),
    });
  }
  console.log("Modrinth project page updated");
}

async function uploadToModrinth(jars, version, changelog, token, dryRun, state) {
  let existing = new Set();
  if (dryRun && !token) {
    for (const jar of jars) console.log("[dry-run] Modrinth", `${version}+${jar.loader}-${jar.game}`, jar.name);
    return;
  }
  try {
    const versions = await modrinth(`/project/${MODRINTH_ID}/version`, token);
    existing = new Set(versions.map((v) => v.version_number));
  } catch (e) {
    if (!dryRun) throw e;
    console.log("[dry-run] could not list Modrinth versions:", e.message);
  }
  for (const jar of jars) {
    const versionNumber = `${version}+${jar.loader}-${jar.game}`;
    if (existing.has(versionNumber) || hasRecordedUpload(state, "modrinth", version, jar)) {
      console.log("Modrinth skip (already published)", versionNumber);
      continue;
    }
    const data = {
      name: versionLabel(version, jar),
      version_number: versionNumber,
      changelog,
      dependencies: jar.loader === "fabric" ? [{ project_id: MODRINTH_FABRIC_API, dependency_type: "required" }] : [],
      game_versions: [jar.game],
      version_type: "release",
      loaders: [jar.loader],
      featured: true,
      status: "listed",
      project_id: MODRINTH_ID,
      file_parts: ["file_0"],
      primary_file: "file_0",
    };
    if (dryRun) {
      console.log("[dry-run] Modrinth", versionNumber, jar.name);
      continue;
    }
    const form = new FormData();
    form.append("data", JSON.stringify(data));
    form.append("file_0", new Blob([fs.readFileSync(jar.jar)]), jar.name);
    const created = await modrinth("/version", token, { method: "POST", body: form });
    recordUpload(state, "modrinth", version, jar, created?.id ?? null);
    console.log("Modrinth OK", versionNumber);
  }
}

// ------------------------------------------------------------------ CurseForge

async function uploadToCurseForge(jars, version, changelog, token, dryRun, state) {
  if (dryRun && !token) {
    for (const jar of jars) console.log("[dry-run] CurseForge", versionLabel(version, jar), jar.name);
    return;
  }
  const res = await fetch("https://minecraft.curseforge.com/api/game/versions", { headers: { "X-Api-Token": token } });
  if (!res.ok) throw new Error(`CurseForge game versions: ${res.status} ${(await res.text()).slice(0, 300)}`);
  const payload = await res.json();
  const all = Array.isArray(payload) ? payload : payload?.data || [];
  const clientId = all.find((v) => v.name === "Client")?.id;
  if (!clientId) throw new Error("CurseForge 'Client' environment id missing");
  // Prefer entries of the "Minecraft x.y" version types, should another type reuse the same name.
  let minecraftTypes = new Set();
  const typesRes = await fetch("https://minecraft.curseforge.com/api/game/version-types", { headers: { "X-Api-Token": token } });
  if (typesRes.ok) {
    const types = await typesRes.json();
    minecraftTypes = new Set((Array.isArray(types) ? types : []).filter((t) => /^Minecraft\b/i.test(t.name)).map((t) => t.id));
  }
  const gameVersion = (name) => {
    const matches = all.filter((v) => v.name === name);
    return matches.find((v) => minecraftTypes.has(v.gameVersionTypeID)) || matches[0];
  };

  for (const jar of jars) {
    if (hasRecordedUpload(state, "curseforge", version, jar)) {
      console.log("CurseForge skip (already published)", jar.name);
      continue;
    }
    const loaderId = CURSEFORGE_LOADER_IDS[jar.loader];
    const game = gameVersion(jar.game);
    if (!loaderId) throw new Error(`Unknown loader ${jar.loader}`);
    if (!game) throw new Error(`No CurseForge game version named ${jar.game}`);
    const metadata = {
      changelog,
      changelogType: "markdown",
      displayName: versionLabel(version, jar),
      gameVersions: [clientId, loaderId, game.id],
      releaseType: "release",
      relations: jar.loader === "fabric" ? { projects: [{ slug: "fabric-api", type: "requiredDependency" }] } : undefined,
    };
    if (dryRun) {
      console.log("[dry-run] CurseForge", metadata.displayName, jar.name);
      continue;
    }
    for (let attempt = 1; ; attempt++) {
      const form = new FormData();
      form.append("metadata", JSON.stringify(metadata));
      form.append("file", new Blob([fs.readFileSync(jar.jar)]), jar.name);
      const up = await fetch(`https://minecraft.curseforge.com/api/projects/${CURSEFORGE_ID}/upload-file`, {
        method: "POST",
        headers: { "X-Api-Token": token },
        body: form,
      });
      const text = await up.text();
      if (up.ok) {
        let id = null;
        try {
          id = JSON.parse(text).id ?? null;
        } catch {}
        recordUpload(state, "curseforge", version, jar, id);
        console.log("CurseForge OK", metadata.displayName);
        break;
      }
      if ((up.status === 429 || up.status >= 500) && attempt < 4) {
        console.log(`CurseForge ${up.status}, retrying in ${15 * attempt}s`);
        await new Promise((r) => setTimeout(r, 15000 * attempt));
        continue;
      }
      throw new Error(`CurseForge ${up.status} ${jar.name} ${text.slice(0, 500)}`);
    }
  }
}

// ------------------------------------------------------------------ main

async function main() {
  loadLocalEnv();
  const args = parseArgs(process.argv);
  const jars = collectJars(args.version).map(parseJar);
  if (!jars.length) throw new Error(`No remnantminimaps jars for version ${args.version} in all-jars/`);
  const changelog = readChangelog(args.version, args.changelogFile);
  const state = loadReleaseState();
  const doModrinth = !args.curseforgeOnly;
  const doCurseForge = !args.modrinthOnly;
  const { MODRINTH_TOKEN, CURSEFORGE_TOKEN } = process.env;
  if (!args.dryRun) {
    if (doModrinth && !MODRINTH_TOKEN) throw new Error("MODRINTH_TOKEN is not set");
    if (doCurseForge && !CURSEFORGE_TOKEN) throw new Error("CURSEFORGE_TOKEN is not set");
  }

  console.log(`Publishing ${MOD_TITLE} ${args.version}${args.dryRun ? " (dry run)" : ""}`);
  for (const j of jars) console.log(`  ${j.name} -> ${j.loader} ${j.game}`);

  if (doModrinth) {
    if (args.syncProject) await syncModrinthProject(MODRINTH_TOKEN, args.dryRun);
    await uploadToModrinth(jars, args.version, changelog, MODRINTH_TOKEN, args.dryRun, state);
  }
  if (doCurseForge) {
    await uploadToCurseForge(jars, args.version, changelog, CURSEFORGE_TOKEN, args.dryRun, state);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
