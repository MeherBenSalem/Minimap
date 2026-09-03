/**
 * Upload Odyssey Map jars from all-jars/ to Modrinth + CurseForge.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, "..");

const MODRINTH_ID = "lLviL6Oq";
const CURSEFORGE_ID = "1564458";
const MOD_TITLE = "Odyssey Map";
const JAR_DIR = path.join(ROOT, "all-jars");
const RELEASE_STATE_PATH = path.join(ROOT, ".release-upload-state.json");

async function loadEnv() {
  const candidates = [
    path.join(ROOT, "secrets", "local.env"),
    path.join(process.env.USERPROFILE || "", "NightBeam-Knowledge-Base", "secrets", "local.env"),
    "C:\\Users\\mahou\\NightBeam-Knowledge-Base\\secrets\\local.env",
  ];
  for (const envPath of candidates) {
    if (!fs.existsSync(envPath)) continue;
    const text = fs.readFileSync(envPath, "utf8");
    for (const line of text.split(/\r?\n/)) {
      const m = line.match(/^([^#=]+)=(.*)$/);
      if (!m) continue;
      const k = m[1].trim();
      const v = m[2].trim();
      if (!process.env[k]) process.env[k] = v;
    }
    console.log("Loaded secrets from", envPath);
    return;
  }
}

function parseArgs(argv) {
  const out = { curseforgeOnly: false, modrinthOnly: false, dryRun: false };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--version") out.version = argv[++i];
    else if (a === "--changelog-file") out.changelogFile = argv[++i];
    else if (a === "--curseforge-only") out.curseforgeOnly = true;
    else if (a === "--modrinth-only") out.modrinthOnly = true;
    else if (a === "--dry-run") out.dryRun = true;
    else throw new Error(`Unknown arg ${a}`);
  }
  if (!out.version) throw new Error("--version required");
  return out;
}

function parseJar(jar) {
  const name = path.basename(jar);
  const m = name.match(/^odysseymap-(fabric|forge|neoforge)-(.+)-(\d+\.\d+\.\d+)\.jar$/i);
  if (!m) throw new Error(`Cannot parse jar name: ${name}`);
  return { jar, name, loader: m[1].toLowerCase(), game: m[2], version: m[3] };
}

function collectJars(version) {
  if (!fs.existsSync(JAR_DIR)) throw new Error(`Missing ${JAR_DIR}; run buildAll first`);
  return fs
    .readdirSync(JAR_DIR)
    .filter((n) => n.startsWith("odysseymap-") && n.endsWith(`-${version}.jar`))
    .map((n) => path.join(JAR_DIR, n))
    .sort();
}

function readChangelog(version, changelogFile) {
  for (const f of [changelogFile, path.join(ROOT, "CHANGELOG.md")].filter(Boolean)) {
    if (fs.existsSync(f)) return fs.readFileSync(f, "utf8");
  }
  return `## ${MOD_TITLE} ${version}\n\nMultiLoader release.`;
}

function loadReleaseState() {
  if (!fs.existsSync(RELEASE_STATE_PATH)) return { uploads: [] };
  try {
    const parsed = JSON.parse(fs.readFileSync(RELEASE_STATE_PATH, "utf8"));
    if (Array.isArray(parsed.uploads)) return parsed;
  } catch {}
  return { uploads: [] };
}

function saveReleaseState(state) {
  fs.writeFileSync(RELEASE_STATE_PATH, `${JSON.stringify(state, null, 2)}\n`, "utf8");
}

function hasRecordedUpload(state, platform, version, parsedJar) {
  return state.uploads.some(
    (e) => e.platform === platform && e.version === version && e.fileName === parsedJar.name,
  );
}

function recordUpload(state, platform, version, parsedJar, remoteId) {
  if (hasRecordedUpload(state, platform, version, parsedJar)) return;
  state.uploads.push({
    platform,
    version,
    loader: parsedJar.loader,
    game: parsedJar.game,
    fileName: parsedJar.name,
    remoteId,
    recordedAt: new Date().toISOString(),
  });
  saveReleaseState(state);
}

async function uploadToModrinth(parsed, version, changelog, token, dryRun, state) {
  for (const parsedJar of parsed) {
    const body = {
      name: `${version} · ${parsedJar.loader} · ${parsedJar.game}`,
      version_number: `${version}+${parsedJar.loader}-${parsedJar.game}`,
      changelog,
      dependencies: [],
      game_versions: [parsedJar.game],
      version_type: "release",
      loaders: [parsedJar.loader],
      featured: false,
      status: "listed",
      project_id: MODRINTH_ID,
      file_parts: ["file_0"],
      primary_file: "file_0",
    };
    if (hasRecordedUpload(state, "modrinth", version, parsedJar)) {
      console.log("Modrinth skip", parsedJar.name);
      continue;
    }
    if (dryRun) {
      console.log("[dry-run] Modrinth", body.version_number, parsedJar.name);
      continue;
    }
    const form = new FormData();
    form.append("data", JSON.stringify(body));
    form.append("file_0", new Blob([fs.readFileSync(parsedJar.jar)]), parsedJar.name);
    const res = await fetch("https://api.modrinth.com/v2/version", {
      method: "POST",
      headers: { Authorization: token },
      body: form,
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`Modrinth ${res.status} ${parsedJar.name} ${text.slice(0, 500)}`);
    let remoteId = null;
    try {
      remoteId = JSON.parse(text).id ?? null;
    } catch {}
    recordUpload(state, "modrinth", version, parsedJar, remoteId);
    console.log("Modrinth OK", body.version_number, parsedJar.name);
  }
}

async function uploadToCurseForge(parsed, version, changelog, token, apiKey, dryRun, state) {
  const LOADER_IDS = { fabric: 7499, forge: 7498, neoforge: 10150 };
  const legacyPayload = await fetch("https://minecraft.curseforge.com/api/game/versions", {
    headers: { "X-Api-Token": token },
  }).then((r) => r.json());
  const legacyFlat = Array.isArray(legacyPayload) ? legacyPayload : legacyPayload?.data || [];
  const clientId = legacyFlat.find((v) => v.name === "Client")?.id;
  const serverId = legacyFlat.find((v) => v.name === "Server")?.id;
  if (!clientId || !serverId) throw new Error("CurseForge Client/Server version ids missing");

  for (const parsedJar of parsed) {
    const loaderId = LOADER_IDS[parsedJar.loader];
    if (!loaderId) throw new Error(`Unknown loader ${parsedJar.loader}`);
    const gameHit = legacyFlat.find((v) => v.name === parsedJar.game);
    if (!gameHit) throw new Error(`No CurseForge game version for ${parsedJar.game}`);
    const meta = {
      changelog,
      changelogType: "markdown",
      displayName: `${version} · ${parsedJar.loader} · ${parsedJar.game}`,
      gameVersions: [clientId, serverId, loaderId, gameHit.id],
      releaseType: "release",
    };
    if (hasRecordedUpload(state, "curseforge", version, parsedJar)) {
      console.log("CurseForge skip", parsedJar.name);
      continue;
    }
    if (dryRun) {
      console.log("[dry-run] CurseForge", meta.displayName, parsedJar.name);
      continue;
    }
    const cfForm = new FormData();
    cfForm.append("metadata", JSON.stringify(meta));
    cfForm.append("file", new Blob([fs.readFileSync(parsedJar.jar)]), parsedJar.name);
    for (let attempt = 1; attempt <= 4; attempt++) {
      const res = await fetch(
        `https://minecraft.curseforge.com/api/projects/${CURSEFORGE_ID}/upload-file`,
        { method: "POST", headers: { "X-Api-Token": token }, body: cfForm },
      );
      const text = await res.text();
      if (res.ok) {
        let remoteId = null;
        try {
          remoteId = JSON.parse(text).id ?? null;
        } catch {}
        recordUpload(state, "curseforge", version, parsedJar, remoteId);
        console.log("CurseForge OK", meta.displayName, parsedJar.name);
        break;
      }
      if ((res.status === 429 || res.status === 503) && attempt < 4) {
        await new Promise((r) => setTimeout(r, 15000 * attempt));
        continue;
      }
      throw new Error(`CurseForge ${res.status} ${parsedJar.name} ${text.slice(0, 500)}`);
    }
  }
}

async function main() {
  await loadEnv();
  const args = parseArgs(process.argv);
  const version = args.version.replace(/^v/, "");
  const changelog = readChangelog(version, args.changelogFile);
  const state = loadReleaseState();
  const jars = collectJars(version);
  if (!jars.length) throw new Error(`No odysseymap jars for version ${version} in all-jars/`);
  const parsed = jars.map(parseJar);

  const { MODRINTH_TOKEN, CURSEFORGE_TOKEN, CURSEFORGE_API_KEY } = process.env;
  if (!MODRINTH_TOKEN || !CURSEFORGE_TOKEN || !CURSEFORGE_API_KEY) {
    throw new Error("Set MODRINTH_TOKEN, CURSEFORGE_TOKEN, CURSEFORGE_API_KEY");
  }

  console.log(`Uploading ${MOD_TITLE} ${version} (${parsed.length} jars)`);
  for (const p of parsed) console.log(" ", p.name, "->", p.loader, p.game);

  if (!args.curseforgeOnly) {
    await uploadToModrinth(parsed, version, changelog, MODRINTH_TOKEN, args.dryRun, state);
  }
  if (!args.modrinthOnly) {
    await uploadToCurseForge(parsed, version, changelog, CURSEFORGE_TOKEN, CURSEFORGE_API_KEY, args.dryRun, state);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
