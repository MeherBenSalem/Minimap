// Renders the listing artwork in marketing/images/ from real in-game screenshots, the mod's own GUI art and the bundled fonts.
// Usage (from the repository root): NODE_PATH="$(npm root -g)" node marketing/source/render.cjs [name...]
// Requires the `playwright` package with a Chromium build available. Everything it reads lives under marketing/.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

// Printed on the banner. Keep in sync with the compatibility table in curseforge/description.md.
const VERSIONS = ['1.20.1', '1.21.1', '26.1.2', '26.2'];
const LOADERS = ['Fabric', 'Forge', 'NeoForge'];

const root = path.resolve(__dirname, '..', '..');
const out = path.join(root, 'marketing', 'images');
const shots = path.join(out, 'screenshots');
const fonts = path.join(__dirname, 'fonts');
const url = (file) => 'file://' + file;
const shot = (name) => url(path.join(shots, name));
const source = (name) => url(path.join(__dirname, name));

// Pixel sizes of the captures in images/screenshots/, used to place crops.
const SIZES = {
  'hud-round.jpg': [1600, 900], 'hud-square.jpg': [1600, 900], 'minimap-closeup.jpg': [820, 940], 'pillar-dusk.jpg': [1600, 900],
  'settings.jpg': [1600, 900], 'worldmap.jpg': [1600, 900], 'worldmap-far.jpg': [1600, 900], 'worldmap-list.jpg': [1600, 900],
};

// Paper fibres and stains for the parchment panels, as inline SVG noise.
const grain = (freq, octaves, alpha, seed) => `url("data:image/svg+xml,${encodeURIComponent(`<svg xmlns='http://www.w3.org/2000/svg' width='400' height='400'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='${freq}' numOctaves='${octaves}' seed='${seed}' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 .32 0 0 0 0 .23 0 0 0 0 .12 0 0 0 ${alpha} 0'/></filter><rect width='100%' height='100%' filter='url(#n)'/></svg>`)}")`;

const css = `
@font-face { font-family: RmSerif; src: url('${url(path.join(fonts, 'eb_garamond.ttf'))}'); }
@font-face { font-family: RmDisplay; src: url('${url(path.join(fonts, 'cinzel.ttf'))}'); }
:root { --text:#dcd4c0; --dim:#a39a86; --faint:#6f6858; --header:#ede3cc; --gold:#c9a961; --gold-hi:#f2dfa0; --gold-deep:#8a6b2e;
        --parch:#e2d4af; --ink:#3a2c1a; --ink-dim:#6b5636; --blood:#c2412f; --bg:#0b0a09; }
* { box-sizing: border-box; }
html, body { margin: 0; background: var(--bg); color: var(--text); font-family: RmSerif, serif; }
.stage { position: relative; overflow: hidden; background: radial-gradient(ellipse at 30% 40%, #1d1a15 0%, #0b0a09 70%); }
.display { font-family: RmDisplay, serif; letter-spacing: .04em; }
.gold { background: linear-gradient(180deg, #fff4cf 0%, #f2dfa0 35%, #c9a961 70%, #8a6b2e 100%);
        -webkit-background-clip: text; background-clip: text; color: transparent; }
.glow { filter: drop-shadow(0 0 18px rgba(201,169,97,.38)); }
.kicker { font-family: RmDisplay, serif; color: var(--gold); letter-spacing: .22em; font-size: 22px; }
.rule { height: 1px; background: linear-gradient(90deg, rgba(201,169,97,0), rgba(201,169,97,.9) 20%, rgba(201,169,97,.9) 70%, rgba(201,169,97,0)); }
.diamond { display: inline-block; width: 9px; height: 9px; background: var(--gold); transform: rotate(45deg); margin: 0 12px 3px 0; }
.frame { position: relative; border: 1px solid rgba(201,169,97,.55); box-shadow: 0 0 0 1px rgba(0,0,0,.6), 0 30px 80px rgba(0,0,0,.65), 0 0 60px rgba(201,169,97,.16); }
.frame > img { display: block; width: 100%; }
.frame .cn { position: absolute; width: 26px; height: 26px; border: 0 solid var(--gold); filter: drop-shadow(0 0 6px rgba(201,169,97,.6)); }
.frame .tl { left: -7px; top: -7px; border-width: 2px 0 0 2px; } .frame .tr { right: -7px; top: -7px; border-width: 2px 2px 0 0; }
.frame .bl { left: -7px; bottom: -7px; border-width: 0 0 2px 2px; } .frame .br { right: -7px; bottom: -7px; border-width: 0 2px 2px 0; }
.crop { position: relative; overflow: hidden; } .crop img { position: absolute; max-width: none; display: block; }
.crop.px img { image-rendering: pixelated; }
canvas.motes { position: absolute; left: 0; top: 0; width: 100%; height: 100%; pointer-events: none; }
.vignette { position: absolute; inset: 0; background: radial-gradient(ellipse at center, rgba(0,0,0,0) 45%, rgba(0,0,0,.75) 100%); }
.backdrop { position: absolute; inset: 0; background-size: cover; background-position: center; }
.clouds { position: absolute; width: 360px; height: 360px; background: url('${source('art/cloud_corner.png')}') center / contain no-repeat;
          filter: sepia(1) saturate(.6) brightness(.9); opacity: .16; }
.pill { display: inline-block; border: 1px solid rgba(201,169,97,.6); padding: 6px 14px 4px; margin-right: 10px;
        font-family: RmDisplay, serif; font-size: 18px; letter-spacing: .12em; color: var(--header); background: rgba(0,0,0,.4); }
.callout { position: absolute; font-family: RmDisplay, serif; font-size: 21px; letter-spacing: .14em; color: var(--header);
           background: rgba(10,9,8,.88); border: 1px solid rgba(201,169,97,.85); padding: 8px 16px 6px; white-space: nowrap;
           box-shadow: 0 8px 26px rgba(0,0,0,.6); }
.callout small { display: block; font-family: RmSerif, serif; letter-spacing: 0; font-size: 19px; color: var(--dim); margin-top: 2px; }
.badge { position: absolute; width: 46px; height: 46px; margin: -23px 0 0 -23px; border-radius: 50%; background: #12100d;
         border: 2px solid var(--gold); box-shadow: 0 0 18px rgba(201,169,97,.7); font-family: RmDisplay, serif; font-size: 26px;
         color: var(--gold-hi); display: flex; align-items: center; justify-content: center; z-index: 3; }
.ring { position: absolute; border: 2px solid var(--gold); border-radius: 4px; box-shadow: 0 0 18px rgba(201,169,97,.55); z-index: 2; }
.ring.round { border-radius: 50%; }
.lens { position: absolute; border-radius: 50%; overflow: hidden; border: 3px solid var(--gold);
        box-shadow: 0 0 0 7px rgba(12,10,8,.9), 0 0 0 8px rgba(201,169,97,.7), 0 24px 70px rgba(0,0,0,.75), 0 0 60px rgba(242,223,160,.28); }
.parchment { background-color: var(--parch); color: var(--ink);
             background-image: radial-gradient(ellipse at 25% 15%, rgba(255,250,232,.55), rgba(255,250,232,0) 55%),
               radial-gradient(ellipse at 85% 95%, rgba(110,80,35,.32), rgba(110,80,35,0) 60%),
               radial-gradient(ellipse at 50% 50%, rgba(0,0,0,0) 55%, rgba(90,62,25,.28) 100%),
               ${grain(0.85, 3, 0.2, 3)}, ${grain(0.012, 4, 0.22, 9)}; }
ul.points { list-style: none; padding: 0; margin: 0; }
ul.points li { position: relative; padding-left: 26px; margin: 0 0 14px; font-size: 27px; line-height: 1.32; color: var(--text); }
ul.points li::before { content: ''; position: absolute; left: 2px; top: 13px; width: 8px; height: 8px; background: var(--gold); transform: rotate(45deg); }
b.n { color: var(--gold-hi); }
b.w { color: var(--header); font-weight: normal; }
.k { display: inline-block; border: 1px solid rgba(201,169,97,.7); padding: 0 8px; margin: 0 2px; background: rgba(0,0,0,.35); font-size: .9em; color: var(--gold-hi); }
`;

// Slow golden motes, matching the in-game ambience of the Sites of Grace. Deterministic so renders are reproducible.
const motes = (count = 90, seed = 7) => `<canvas class="motes"></canvas><script>
(() => { const c = document.currentScript.previousElementSibling; const r = c.getBoundingClientRect();
  c.width = r.width; c.height = r.height; const g = c.getContext('2d'); let s = ${seed};
  const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < ${count}; i++) { const x = rnd() * c.width, y = rnd() * c.height, a = .25 + rnd() * .6, z = 1 + rnd() * 2.2;
    const h = g.createRadialGradient(x, y, 0, x, y, z * 5); h.addColorStop(0, 'rgba(246,222,156,' + a * .55 + ')'); h.addColorStop(1, 'rgba(246,222,156,0)');
    g.fillStyle = h; g.beginPath(); g.arc(x, y, z * 5, 0, Math.PI * 2); g.fill();
    g.fillStyle = 'rgba(255,240,200,' + a + ')'; g.fillRect(x - z / 2, y - z / 2, z, z); } })();
</script>`;

// A four-point star, the shape of the Site of Grace emblem and the compass rose.
const star = (size = 22, color = '#e9cf8a') => `<svg width="${size}" height="${size}" viewBox="-10 -10 20 20" style="flex:none;filter:drop-shadow(0 0 6px rgba(242,223,160,.75))">
  <path d="M0-10 L2.1-2.1 L10 0 L2.1 2.1 L0 10 L-2.1 2.1 L-10 0 L-2.1-2.1Z" fill="${color}"/></svg>`;

const page = (width, height, body, transparent = false) => `<!doctype html><html><head><meta charset="utf-8"><style>${css}
body { width: ${width}px; height: ${height}px; } .stage { width: ${width}px; height: ${height}px; }
${transparent ? 'html, body, .stage { background: transparent; }' : ''}</style></head>
<body><div class="stage">${body}</div></body></html>`;

// The (x, y, w, h) region of a capture, scaled to `width` pixels wide. `px` keeps blocks and pixel text sharp when enlarged.
const crop = (name, [x, y, w, h], width, { px = false, style = '', filter = '' } = {}) => {
  const [sw] = SIZES[name];
  const k = width / w;
  return `<div class="crop${px ? ' px' : ''}" style="width:${width}px;height:${Math.round(h * k)}px;${style}">
    <img src="${shot(name)}" style="width:${sw * k}px;left:${-x * k}px;top:${-y * k}px;${filter ? `filter:${filter};` : ''}"></div>`;
};
const corners = '<i class="cn tl"></i><i class="cn tr"></i><i class="cn bl"></i><i class="cn br"></i>';
const framed = (inner, style = '') => `<div class="frame" style="${style}">${inner}${corners}</div>`;
// A round magnifier over a square region of a capture.
const lens = (name, [x, y, s], diameter, style) => `<div class="lens" style="width:${diameter}px;height:${diameter}px;${style}">${crop(name, [x, y, s, s], diameter - 6, { px: true })}</div>`;

const header = (title) => page(1400, 120, `
  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:26px;background:linear-gradient(90deg,rgba(0,0,0,0),rgba(20,18,15,.9) 30%,rgba(20,18,15,.9) 70%,rgba(0,0,0,0))">
    <div class="rule" style="width:300px;transform:scaleX(-1)"></div>
    ${star(24)}
    <div class="display gold glow" style="font-size:44px;white-space:nowrap">${title}</div>
    ${star(24)}
    <div class="rule" style="width:300px"></div>
  </div>`, true);

// A feature card: copy on the left, artwork on the right.
const card = ({ kicker, title, points, art, width = 1600, height = 900, artWidth = 980 }) => page(width, height, `
  <div class="backdrop" style="background-image:url('${source('backdrop.jpg')}');opacity:.6"></div>
  <div class="clouds" style="left:-40px;top:-40px"></div><div class="clouds" style="left:-40px;bottom:-40px;transform:scaleY(-1)"></div>
  <div class="vignette"></div>${motes()}
  <div style="position:absolute;left:80px;top:0;bottom:0;width:${width - artWidth - 150}px;display:flex;flex-direction:column;justify-content:center">
    <div class="kicker"><span class="diamond"></span>${kicker}</div>
    <div class="display gold glow" style="font-size:58px;line-height:1.08;margin:18px 0 22px">${title}</div>
    <div class="rule" style="width:80%;margin-bottom:30px"></div>
    <ul class="points">${points.map((point) => `<li>${point}</li>`).join('')}</ul>
  </div>
  <div style="position:absolute;right:60px;top:50%;transform:translateY(-50%);width:${artWidth}px">${art}</div>`);

// The 30 emblems the marker editor offers, in its order: atlas index, default tint (MarkerIcon.java), name.
const EMBLEMS = [[0, 0xFFFFFF, 'Site of Grace'], [2, 0xE9DFC6, 'Marker'], [4, 0xD9A86A, 'Bed'], [5, 0xE9DFC6, 'Home'], [6, 0xE9DFC6, 'Village'],
  [7, 0xE9DFC6, 'Castle'], [8, 0xE9DFC6, 'Church'], [9, 0xE9DFC6, 'Ruins'], [10, 0xE9DFC6, 'Tower'], [11, 0xE9DFC6, 'Cave'],
  [12, 0xE9DFC6, 'Catacombs'], [13, 0xC9A7FF, 'Portal'], [14, 0xE0705A, 'Boss'], [15, 0xE0705A, 'Danger'], [16, 0xE8C35E, 'Treasure'],
  [17, 0xE9DFC6, 'Mine'], [18, 0xE9DFC6, 'Forge'], [19, 0xD8D07A, 'Farm'], [20, 0xE8C35E, 'Great Tree'], [21, 0xE9A15E, 'Camp'],
  [22, 0xE9DFC6, 'Ship'], [23, 0xF2DFA0, 'Star'], [24, 0xE0705A, 'Banner'], [25, 0xE8C35E, 'Merchant'], [26, 0x9FD3E0, 'Waygate'],
  [27, 0x9FC9E0, 'Spring'], [28, 0xE9DFC6, 'Summit'], [29, 0xE9DFC6, 'Unknown'], [30, 0xE0705A, 'Battle'], [31, 0xE9DFC6, 'Lookout']];

// One emblem canvas; the Site of Grace glows as it does in the editor.
const emblem = (index, tint, size, glow = index === 0) => `<canvas class="emblem" width="${size}" height="${size}" data-index="${index}" data-tint="${tint}"
  style="width:${size}px;height:${size}px;flex:none;filter:${glow ? 'drop-shadow(0 0 10px rgba(255,215,122,.8))' : 'drop-shadow(0 2px 3px rgba(0,0,0,.6))'}"></canvas>`;

const emblemGrid = (cell, columns, gap) => `<div style="display:grid;grid-template-columns:repeat(${columns},${cell}px);gap:${gap}px">
  ${EMBLEMS.map(([index, tint]) => emblem(index, tint, cell)).join('')}</div>${emblemScript}`;
// Draws every emblem canvas from the markers.png atlas, multiplied by its tint the way the game's vertex color does.
const emblemScript = `<script>(() => { const atlas = new Image(); atlas.src = '${source('art/markers.png')}';
    window.emblemsReady = new Promise((done) => { atlas.onload = () => { for (const c of document.querySelectorAll('canvas.emblem')) {
      const g = c.getContext('2d'); const i = +c.dataset.index, t = +c.dataset.tint; g.imageSmoothingQuality = 'high';
      g.drawImage(atlas, (i % 8) * 64, Math.floor(i / 8) * 64, 64, 64, 0, 0, c.width, c.height);
      g.globalCompositeOperation = 'multiply'; g.fillStyle = '#' + t.toString(16).padStart(6, '0'); g.fillRect(0, 0, c.width, c.height);
      g.globalCompositeOperation = 'destination-in'; g.drawImage(atlas, (i % 8) * 64, Math.floor(i / 8) * 64, 64, 64, 0, 0, c.width, c.height); }
      done(); }; }); })();</script>`;

// A numbered step on a parchment panel.
const step = (n, title, text) => `<div class="frame parchment" style="padding:24px 34px;display:flex;align-items:center;gap:28px">
  <div class="display" style="font-size:52px;width:60px;color:var(--gold-deep)">${n}</div>
  <div><div class="display" style="font-size:31px;color:var(--ink)">${title}</div>
  <div style="font-size:25px;color:var(--ink-dim);margin-top:4px">${text}</div></div>${corners}</div>`;

const assets = {
  // Blurred world map behind every card: parchment tones, no legible detail.
  backdrop: () => page(1600, 900, `<img src="${shot('worldmap.jpg')}" style="position:absolute;left:-60px;top:-60px;width:1720px;height:1020px;filter:blur(26px) brightness(.5) saturate(.9)">`),

  banner: () => page(1800, 600, `
    ${crop('pillar-dusk.jpg', [0, 0, 1600, 533], 1800, { style: 'position:absolute;left:0;top:0' })}
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(8,7,6,.96) 0%,rgba(8,7,6,.88) 30%,rgba(8,7,6,.3) 46%,rgba(8,7,6,0) 56%)"></div>
    <div style="position:absolute;inset:0;background:linear-gradient(180deg,rgba(8,7,6,0) 60%,rgba(8,7,6,.7) 100%)"></div>
    <div class="vignette" style="background:radial-gradient(ellipse at 60% 45%, rgba(0,0,0,0) 50%, rgba(0,0,0,.6) 100%)"></div>${motes(110, 11)}
    <div style="position:absolute;left:90px;top:86px;width:900px">
      <div class="kicker"><span class="diamond"></span>2.0 · Minimap &amp; World Map</div>
      <div class="display gold glow" style="font-size:84px;line-height:1.04;margin:22px 0 26px;white-space:nowrap">Remnants<br>Minimap</div>
      <div class="rule" style="width:620px;margin-bottom:26px"></div>
      <div style="font-size:34px;color:var(--header);line-height:1.3">Chart your world in parchment and ink.<br>Follow the pillars of light home.</div>
      <div style="margin-top:36px">${LOADERS.map((l) => `<span class="pill">${l}</span>`).join('')}
        <span style="font-size:24px;color:var(--dim);margin-left:8px">${VERSIONS.join(' · ')}</span></div>
    </div>`),

  icon: () => page(512, 512, `
    <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 46%,#2b251c 0%,#120f0c 58%,#070605 100%)"></div>${motes(70, 5)}
    <div style="position:absolute;left:56px;top:56px;width:400px;height:400px;border-radius:50%;background:radial-gradient(circle,rgba(242,223,160,.30),rgba(242,223,160,0) 68%)"></div>
    <img src="${source('art/logo.png')}" style="position:absolute;left:36px;top:36px;width:440px;height:440px;filter:drop-shadow(0 14px 26px rgba(0,0,0,.75)) drop-shadow(0 0 22px rgba(201,169,97,.35))">`),

  'card-cartography': () => card({
    kicker: 'Cartography', title: 'Inked on<br>parchment',
    points: ['A muted parchment palette instead of raw block colors', 'Relief shading and contour lines, every fourth one inked darker',
      'Engraved seas with a second line offshore', 'Ink outlines around coasts, buildings and roads', 'Unexplored land stays hidden in fog'],
    art: `<div style="display:flex;flex-direction:column;gap:22px">
      <div style="position:relative">${framed(crop('worldmap.jpg', [650, 60, 600, 240], 980, { px: true }))}
        <div class="callout" style="left:22px;bottom:22px">Engraved water<small>Wave lines, pale shallows, ink coastlines</small></div></div>
      <div style="display:flex;gap:20px">
        <div style="position:relative">${framed(crop('worldmap.jpg', [1188, 490, 210, 175], 480, { px: true }))}
          <div class="callout" style="left:18px;bottom:18px">Relief &amp; contours</div></div>
        <div style="position:relative">${framed(crop('worldmap-far.jpg', [905, 240, 250, 208], 480, { px: true }))}
          <div class="callout" style="left:18px;bottom:18px">Fog of the unexplored</div></div>
      </div></div>` }),

  'card-minimap': () => card({
    kicker: 'The minimap', title: 'A gilded ring<br>that turns with you',
    points: ['<b class="n">1</b> Cardinal letters ride the rim as the map turns', '<b class="n">2</b> Graces, beacons and bloodstains stay pinned to the edge',
      '<b class="n">3</b> Coordinates, time of day and, if you like, the biome', 'Sub-pixel rotation and scrolling, animated zoom'],
    art: `<div style="position:relative;width:716px;margin-left:auto;margin-right:110px">${framed(`<img src="${shot('minimap-closeup.jpg')}">`)}
      <div class="ring round" style="left:72%;top:20.5%;width:9%;height:7.8%"></div><div class="badge" style="left:85%;top:24.5%">1</div>
      <div class="ring round" style="left:31.7%;top:69.1%;width:26.2%;height:8%"></div><div class="badge" style="left:28%;top:73.4%">2</div>
      <div class="ring" style="left:20.7%;top:83.5%;width:55.5%;height:13.5%"></div><div class="badge" style="left:80.5%;top:90.4%">3</div></div>` }),

  'card-hud': () => card({
    kicker: 'Make the HUD yours', title: 'Round or<br>square',
    points: ['Turn with your view or keep north up', 'Any screen corner, 64 to 320 GUI pixels', 'Zoom with <span class="k">=</span> and <span class="k">-</span>, from 1/8 to ×6',
      'Opacity, icon size and edge margin', 'Moves down for your status effect icons'],
    art: `<div style="display:flex;gap:40px;justify-content:center">
      ${[['hud-round.jpg', 'Round', 'turns with your view'], ['hud-square.jpg', 'Square', 'north up']].map(([name, label, note]) => `<div style="width:470px">
        ${framed(crop(name, [1150, 15, 440, 480], 470))}
        <div style="text-align:center;margin-top:26px"><div class="display" style="font-size:30px;letter-spacing:.14em;color:var(--header)">${label}</div>
        <div style="font-size:24px;color:var(--dim);margin-top:2px">${note}</div></div></div>`).join('')}</div>` }),

  'card-worldmap': () => card({
    kicker: 'The world map', title: 'Your whole world<br>in one view',
    points: ['Press <span class="k">M</span>. Drag to pan and the map glides on', 'Scroll to zoom toward the cursor, or pan with WASD',
      'Far zoom levels switch to overview tiles, so it stays fast', 'Compass rose, day-cycle dial and scale bar', '<span class="k">Space</span> returns you to your position'],
    art: `<div style="position:relative">${framed(`<img src="${shot('worldmap-far.jpg')}">`)}
      ${lens('worldmap.jpg', [1140, 395, 270], 330, 'left:40.4%;top:29.4%')}
      <div class="callout" style="left:3%;top:44%">Scroll in<small>from the whole world to single blocks</small></div></div>` }),

  'card-markers': () => card({
    kicker: 'Markers', title: 'Thirty emblems,<br>nine filters',
    points: ['Name and tint each marker, and choose minimap, pillar and label', 'Press <span class="k">B</span> to mark where you stand, or right click the map',
      'Press <span class="k">G</span> for the Grace list: search, filters and distances', 'Click an entry and the map glides to it'],
    art: `<div style="display:flex;flex-direction:column;gap:26px">
      <div class="frame" style="background:rgba(12,11,9,.92);padding:26px 30px 30px">
        <div class="display" style="font-size:26px;color:var(--gold-hi);letter-spacing:.14em;margin-bottom:20px">Emblem</div>
        ${emblemGrid(70, 10, 21)}${corners}</div>
      <div style="display:flex;gap:26px;align-items:stretch">
        ${framed(crop('worldmap-list.jpg', [1078, 96, 522, 376], 480, { px: true }))}
        <div class="frame" style="flex:1;background:rgba(12,11,9,.92);padding:26px 28px">
          <div class="display" style="font-size:26px;color:var(--gold-hi);letter-spacing:.14em;margin-bottom:18px">Automatic</div>
          ${[[3, 0xFFFFFF, 'Bloodstain', 'where you fell; 3 are kept by default'], [4, 0xD9A86A, 'Resting Place', 'the last bed you slept in'], [1, 0xFFFFFF, 'Beacon', 'left click the world map']]
            .map(([index, tint, name, note]) => `<div style="display:flex;align-items:center;gap:18px;margin-bottom:14px">${emblem(index, tint, 54)}
              <div><div style="font-size:26px;color:var(--header)">${name}</div><div style="font-size:21px;color:var(--dim);line-height:1.2">${note}</div></div></div>`).join('')}
          ${corners}</div></div></div>` }),

  'card-pillars': () => card({
    kicker: 'Pillars of light', title: 'Always find<br>your way back',
    points: ['Beacons, graces and bloodstains raise pillars of light', 'Each one carries its name and distance', 'Far pillars are pulled to the horizon, so you still see them',
      'Drawn up to 2,048 m away by default, or up to 16,384 m'],
    art: `<div style="position:relative;height:720px">
      ${framed(crop('pillar-dusk.jpg', [420, 0, 1180, 540], 800), 'position:absolute;left:0;top:0')}
      ${framed(crop('hud-round.jpg', [620, 360, 400, 290], 560, { px: true }), 'position:absolute;right:0;bottom:0')}
      <div class="callout" style="left:18px;top:18px">A Site of Grace at dusk</div>
      <div class="callout" style="right:18px;bottom:18px">Name &amp; distance</div>
      <div class="frame" style="position:absolute;left:0;bottom:0;width:390px;background:rgba(12,11,9,.92);padding:24px 28px 18px">
        <div class="display" style="font-size:24px;color:var(--gold-hi);letter-spacing:.14em;margin-bottom:16px">Pillar colors</div>
        ${[['#f0cc6e', 'Site of Grace'], ['#ffe9a8', 'Beacon'], ['#c2412f', 'Bloodstain'], ['linear-gradient(90deg,#e0705a,#9fd3e0,#c9a7ff)', 'Others: their own hue']]
          .map(([color, name]) => `<div style="display:flex;align-items:center;gap:16px;margin-bottom:10px"><span style="width:12px;height:34px;background:${color};box-shadow:0 0 12px ${color.startsWith('#') ? color : '#e0705a'}"></span>
            <span style="font-size:24px;color:var(--header)">${name}</span></div>`).join('')}${corners}</div></div>` }),

  'card-settings': () => card({
    kicker: 'Settings', title: 'Forty-one options,<br>five tabs',
    points: ['<b class="n">1</b> Minimap, Markers, World Map, Cartography and Performance', '<b class="n">2</b> Every option explains itself at the bottom of the screen', 'Changes apply immediately',
      'Open with <span class="k">O</span> on the world map, or from the NeoForge mod list'],
    art: `<div style="position:relative">${framed(crop('settings.jpg', [0, 0, 1600, 900], 980, { filter: 'url(#lift)' }))}
      <svg width="0" height="0" style="position:absolute"><filter id="lift" color-interpolation-filters="sRGB"><feComponentTransfer>
        <feFuncR type="linear" slope="1.06" intercept="-0.06"/><feFuncG type="linear" slope="1.06" intercept="-0.06"/><feFuncB type="linear" slope="1.06" intercept="-0.06"/></feComponentTransfer></filter></svg>
      <div class="ring" style="left:4%;top:17%;width:21%;height:33%"></div><div class="badge" style="left:4%;top:17%">1</div>
      <div class="ring" style="left:3.5%;top:80.5%;width:70%;height:6%"></div><div class="badge" style="left:76.5%;top:83.5%">2</div></div>` }),

  'card-performance': () => card({
    kicker: 'Performance', title: 'Light on<br>your game',
    points: ['A mapping budget of 2&nbsp;ms a tick by default, from 0.5 to 8&nbsp;ms', 'Style changes redraw your saved map, with no exploring again',
      'Client-side only: nothing to install on the server'],
    art: `<div style="display:flex;flex-direction:column;gap:24px">
      ${step(1, 'Read', 'new chunks first, then your surroundings, then a slow sweep')}
      ${step(2, 'Draw', 'mipmapped textures, smooth at every zoom level')}
      ${step(3, 'Save', 'one compressed file per 256×256 region, on its own thread')}
      ${step(4, 'Zoom out', 'overview tiles of 4×4 regions keep the far map fast')}</div>` }),
};

const headers = { 'header-cartography': 'Cartography', 'header-minimap': 'The Minimap', 'header-worldmap': 'The World Map',
  'header-markers': 'Markers & Pillars of Light', 'header-settings': 'Settings & Performance', 'header-controls': 'Controls',
  'header-compatibility': 'Downloads & Compatibility', 'header-faq': 'Questions', 'header-gallery': 'Gallery' };
for (const [name, title] of Object.entries(headers)) assets[name] = () => header(title.replace('&', '&amp;'));
assets.divider = () => page(1400, 40, `<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:18px">
  <div class="rule" style="width:520px;transform:scaleX(-1)"></div>${star(20)}<div class="rule" style="width:520px"></div></div>`, true);

(async () => {
  const wanted = process.argv.slice(2);
  const browser = await chromium.launch();
  fs.mkdirSync(out, { recursive: true });
  for (const [name, build] of Object.entries(assets)) {
    if (wanted.length && !wanted.includes(name)) continue;
    const html = build();
    const size = html.match(/body \{ width: (\d+)px; height: (\d+)px; \}/);
    const tab = await browser.newPage({ viewport: { width: +size[1], height: +size[2] }, deviceScaleFactor: 1 });
    const file = path.join(__dirname, `.render-${name}.html`);
    fs.writeFileSync(file, html);
    await tab.goto(url(file));
    await tab.evaluate(async () => { await document.fonts.ready; if (window.emblemsReady) await window.emblemsReady;
      await Promise.all([...document.images].map((img) => img.decode().catch(() => {}))); });
    const transparent = name === 'divider' || name.startsWith('header-');
    const target = name === 'backdrop' ? path.join(__dirname, 'backdrop.jpg') : path.join(out, `${name}.png`);
    await tab.screenshot(name === 'backdrop' ? { path: target, type: 'jpeg', quality: 88 } : { path: target, omitBackground: transparent });
    fs.unlinkSync(file);
    await tab.close();
    console.log('rendered', name);
  }
  await browser.close();
})().catch((error) => { console.error(error); process.exit(1); });
