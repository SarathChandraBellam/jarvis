import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const PORT = Number(process.env.PORT_PREVIEW || 3000);
const HOST = '0.0.0.0';
const ROOT = process.cwd();
const APK_PATH = path.join(ROOT, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const SHOTS_DIR = path.join(ROOT, 'app', 'build', 'screenshots');

const SCREENS = [
  { id: 'home', label: 'Today', group: 'Home', description: 'Daily briefing, wellness rings, focus, and schedule overview', wide: false },
  { id: 'home-food', label: 'Food & Macros', group: 'Home', description: 'Daily calorie and macro tracker with quick meal logging', wide: false },
  { id: 'home-cycle', label: 'Cycle', group: 'Home', description: 'Phase insights and cycle wellness tracking', wide: false },
  { id: 'home-sleep', label: 'Sleep', group: 'Home', description: 'Sleep duration, quality score, and nightly trend', wide: false },
  { id: 'home-plan', label: 'Planner', group: 'Home', description: 'Tasks, reminders, and proactive nudges', wide: false },
  { id: 'subs-overview', label: 'Money Overview', group: 'Money', description: 'Monthly spend vs. last month, budget pace, and recent payments', wide: false },
  { id: 'subs-renewals', label: 'Subscriptions', group: 'Money', description: 'Recurring services and upcoming renewal dates', wide: false },
  { id: 'subs-bills', label: 'Bills', group: 'Money', description: 'Upcoming bills due soon and recurring utilities', wide: false },
  { id: 'inbox', label: 'Inbox', group: 'Inbox', description: 'Categorized SMS, notifications, deliveries, and finance items', wide: false },
  { id: 'sheet', label: 'Item Detail', group: 'Inbox', description: 'Detail bottom sheet with mark-done, dismiss, and share actions', wide: false },
  { id: 'ask', label: 'Ask ASTRA', group: 'Assistant', description: 'Conversational Q&A over your local inbox and telemetry', wide: false },
  { id: 'settings', label: 'Settings', group: 'System', description: 'Auto-saving preferences, privacy redaction, and OpenRouter key', wide: false },
  { id: 'onboarding', label: 'Onboarding', group: 'System', description: 'First-launch welcome flow for callsign and permissions', wide: false },
  { id: 'home-empty', label: 'Empty State', group: 'System', description: 'First-run Today view before permissions are granted', wide: false },
  { id: 'home-male', label: 'Today (Alt)', group: 'Home', description: 'Today layout with cycle tracking disabled', wide: false },
  { id: 'tablet', label: 'Tablet Layout', group: 'Responsive', description: 'Landscape tablet layout with side navigation rail', wide: true },
];

const ALLOWED_SHOTS = new Set(SCREENS.map((s) => `${s.id}.png`));

function getStatus() {
  let apk = { exists: false, sizeBytes: 0, updatedAt: null };
  try {
    const st = fs.statSync(APK_PATH);
    apk = { exists: true, sizeBytes: st.size, updatedAt: st.mtime.toISOString() };
  } catch {
    // APK not yet present
  }

  let availableShots = [];
  try {
    availableShots = fs
      .readdirSync(SHOTS_DIR)
      .filter((f) => ALLOWED_SHOTS.has(f))
      .map((f) => f.replace(/\.png$/, ''));
  } catch {
    // Screenshots not yet rendered
  }

  return {
    appName: 'ASTRA HUD',
    applicationId: 'com.astra.hud',
    versionName: '2.0.0',
    minSdk: 26,
    targetSdk: 36,
    apk,
    availableShots,
  };
}

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

function renderHtml(status) {
  const availableSet = new Set(status.availableShots);
  const screens = SCREENS.map((s) => ({ ...s, ready: availableSet.has(s.id) }));
  const initialScreen = screens.find((s) => s.ready) || screens[0];

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>ASTRA HUD — Android Preview</title>
  <style>
    :root {
      --bg: #FAF7F2;
      --surface: #FFFFFF;
      --surface-high: #F3EFE8;
      --outline: #E3DBD0;
      --outline-soft: #EEE8DE;
      --ink: #23201C;
      --ink-dim: #6B645B;
      --ink-muted: #9B9389;
      --blush: #D66D8A;
      --blush-soft: #F9E9EE;
      --sage: #3E936B;
      --sage-soft: #E3F2EA;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
      background: var(--bg);
      color: var(--ink);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    header {
      background: var(--surface);
      border-bottom: 1px solid var(--outline);
      padding: 16px 24px;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      position: sticky;
      top: 0;
      z-index: 10;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .monogram {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: var(--blush-soft);
      border: 1px solid rgba(214, 109, 138, 0.3);
      color: var(--blush);
      display: grid;
      place-items: center;
      font-family: Georgia, "Times New Roman", serif;
      font-weight: 700;
      font-size: 18px;
      flex-shrink: 0;
    }
    h1 {
      margin: 0;
      font-family: Georgia, "Times New Roman", serif;
      font-size: 22px;
      font-weight: 600;
      letter-spacing: -0.01em;
    }
    .subtitle {
      font-size: 13px;
      color: var(--ink-dim);
      margin-top: 2px;
    }
    .actions {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      background: var(--ink);
      color: var(--bg);
      border: 1px solid var(--ink);
      border-radius: 999px;
      padding: 10px 18px;
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
      cursor: pointer;
      transition: opacity 0.15s ease;
    }
    .btn-primary:hover { opacity: 0.88; }
    .btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--surface);
      color: var(--ink);
      border: 1px solid var(--outline);
      border-radius: 999px;
      padding: 9px 14px;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
    }
    .btn-secondary:hover { background: var(--surface-high); }
    main {
      flex: 1;
      max-width: 1280px;
      width: 100%;
      margin: 0 auto;
      padding: 24px;
      display: grid;
      grid-template-columns: 300px minmax(0, 1fr);
      gap: 28px;
      align-items: start;
    }
    @media (max-width: 860px) {
      main {
        grid-template-columns: 1fr;
        padding: 16px;
      }
    }
    aside {
      background: var(--surface);
      border: 1px solid var(--outline);
      border-radius: 20px;
      padding: 18px;
    }
    .meta-card {
      background: var(--surface-high);
      border-radius: 14px;
      padding: 12px 14px;
      margin-bottom: 16px;
      font-size: 12px;
      color: var(--ink-dim);
      line-height: 1.55;
    }
    .meta-row {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .meta-row strong { color: var(--ink); font-weight: 600; }
    .group-title {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--ink-muted);
      font-weight: 600;
      margin: 14px 0 8px 4px;
    }
    .screen-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .screen-btn {
      width: 100%;
      text-align: left;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 12px;
      padding: 9px 12px;
      font-size: 13.5px;
      font-weight: 500;
      color: var(--ink-dim);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: space-between;
      transition: background 0.12s ease, color 0.12s ease;
    }
    .screen-btn:hover {
      background: var(--surface-high);
      color: var(--ink);
    }
    .screen-btn[aria-pressed="true"] {
      background: var(--ink);
      color: var(--bg);
      font-weight: 600;
    }
    .screen-btn:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .stage {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
    }
    .stage-bar {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
      background: var(--surface);
      border: 1px solid var(--outline);
      border-radius: 16px;
      padding: 12px 18px;
    }
    .stage-title {
      font-family: Georgia, "Times New Roman", serif;
      font-size: 18px;
      font-weight: 600;
      margin: 0;
    }
    .stage-desc {
      font-size: 13px;
      color: var(--ink-dim);
      margin: 2px 0 0;
    }
    .device-shell {
      background: #1C1A17;
      border-radius: 38px;
      padding: 12px;
      box-shadow: 0 24px 60px rgba(35, 32, 28, 0.14), 0 4px 14px rgba(35, 32, 28, 0.08);
      max-width: 100%;
      transition: width 0.2s ease;
    }
    .device-shell.phone { width: 404px; }
    .device-shell.tablet { width: min(860px, 100%); border-radius: 26px; }
    .device-viewport {
      background: var(--bg);
      border-radius: 28px;
      overflow-y: auto;
      overflow-x: hidden;
      max-height: 760px;
      border: 1px solid rgba(255,255,255,0.08);
    }
    .device-shell.tablet .device-viewport {
      border-radius: 16px;
      max-height: 640px;
    }
    .device-viewport img {
      display: block;
      width: 100%;
      height: auto;
    }
    .empty-shot {
      padding: 48px 28px;
      text-align: center;
      color: var(--ink-dim);
      font-size: 14px;
      line-height: 1.6;
    }
    .nav-arrows {
      display: flex;
      gap: 8px;
    }
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <div class="monogram" aria-hidden="true">J</div>
      <div>
        <h1>ASTRA HUD</h1>
        <div class="subtitle">Native Android (Kotlin + Jetpack Compose) · com.astra.hud v${status.versionName}</div>
      </div>
    </div>
    <div class="actions">
      ${
        status.apk.exists
          ? `<a class="btn-primary" href="/download/app-debug.apk" download="astra-hud-debug.apk">Download Debug APK (${formatBytes(status.apk.sizeBytes)})</a>`
          : `<span class="btn-secondary">APK not built yet</span>`
      }
    </div>
  </header>

  <main>
    <aside aria-label="Android screens">
      <div class="meta-card">
        <div class="meta-row"><span>Package</span><strong>${status.applicationId}</strong></div>
        <div class="meta-row"><span>Android SDK</span><strong>min ${status.minSdk} · target ${status.targetSdk}</strong></div>
        <div class="meta-row"><span>APK Build</span><strong>${status.apk.exists ? formatBytes(status.apk.sizeBytes) + ' (signed)' : 'Pending'}</strong></div>
        <div class="meta-row"><span>Rendered Screens</span><strong>${status.availableShots.length} / ${SCREENS.length}</strong></div>
      </div>
      <nav id="screen-nav"></nav>
    </aside>

    <section class="stage" aria-live="polite">
      <div class="stage-bar">
        <div>
          <h2 class="stage-title" id="active-title">${initialScreen.label}</h2>
          <p class="stage-desc" id="active-desc">${initialScreen.description}</p>
        </div>
        <div class="nav-arrows">
          <button type="button" class="btn-secondary" id="prev-btn" aria-label="Previous screen">← Prev</button>
          <button type="button" class="btn-secondary" id="next-btn" aria-label="Next screen">Next →</button>
        </div>
      </div>

      <div class="device-shell ${initialScreen.wide ? 'tablet' : 'phone'}" id="device-shell">
        <div class="device-viewport" id="device-viewport">
          ${
            initialScreen.ready
              ? `<img id="preview-img" src="/screenshots/${initialScreen.id}.png" alt="${initialScreen.label} Android screen" />`
              : `<div class="empty-shot">Rendered Jetpack Compose preview screenshots are generated via Robolectric. Click reload once screenshots finish rendering.</div>`
          }
        </div>
      </div>
    </section>
  </main>

  <script>
    const screens = ${JSON.stringify(screens)};
    const readyScreens = screens.filter(s => s.ready);
    let activeId = ${JSON.stringify(initialScreen.id)};

    const nav = document.getElementById('screen-nav');
    const titleEl = document.getElementById('active-title');
    const descEl = document.getElementById('active-desc');
    const shellEl = document.getElementById('device-shell');
    const viewportEl = document.getElementById('device-viewport');

    function renderNav() {
      const groups = [...new Set(screens.map(s => s.group))];
      nav.innerHTML = '';
      for (const group of groups) {
        const heading = document.createElement('div');
        heading.className = 'group-title';
        heading.textContent = group;
        nav.appendChild(heading);

        const list = document.createElement('div');
        list.className = 'screen-list';
        for (const s of screens.filter(item => item.group === group)) {
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'screen-btn';
          btn.textContent = s.label;
          btn.disabled = !s.ready;
          btn.setAttribute('aria-pressed', String(s.id === activeId));
          btn.addEventListener('click', () => selectScreen(s.id));
          list.appendChild(btn);
        }
        nav.appendChild(list);
      }
    }

    function selectScreen(id) {
      const s = screens.find(item => item.id === id);
      if (!s) return;
      activeId = s.id;
      titleEl.textContent = s.label;
      descEl.textContent = s.description;
      shellEl.className = 'device-shell ' + (s.wide ? 'tablet' : 'phone');
      viewportEl.scrollTop = 0;
      if (s.ready) {
        viewportEl.innerHTML = '';
        const img = document.createElement('img');
        img.id = 'preview-img';
        img.src = '/screenshots/' + encodeURIComponent(s.id) + '.png';
        img.alt = s.label + ' Android screen';
        viewportEl.appendChild(img);
      }
      renderNav();
    }

    function step(delta) {
      if (!readyScreens.length) return;
      const idx = readyScreens.findIndex(s => s.id === activeId);
      const next = readyScreens[(idx + delta + readyScreens.length) % readyScreens.length];
      selectScreen(next.id);
    }

    document.getElementById('prev-btn').addEventListener('click', () => step(-1));
    document.getElementById('next-btn').addEventListener('click', () => step(1));
    window.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') step(1);
      if (e.key === 'ArrowLeft') step(-1);
    });

    renderNav();
  </script>
</body>
</html>`;
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

  if (url.pathname === '/api/status') {
    const body = JSON.stringify(getStatus());
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(body);
    return;
  }

  if (url.pathname === '/download/app-debug.apk') {
    if (!fs.existsSync(APK_PATH)) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Debug APK not found. Build the project first.');
      return;
    }
    const stat = fs.statSync(APK_PATH);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': stat.size,
      'Content-Disposition': 'attachment; filename="astra-hud-debug.apk"',
      'Cache-Control': 'no-store',
    });
    fs.createReadStream(APK_PATH).pipe(res);
    return;
  }

  if (url.pathname.startsWith('/screenshots/')) {
    const file = path.basename(url.pathname);
    if (!ALLOWED_SHOTS.has(file)) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Screenshot not found');
      return;
    }
    const full = path.join(SHOTS_DIR, file);
    if (!fs.existsSync(full)) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Screenshot not generated yet');
      return;
    }
    const stat = fs.statSync(full);
    res.writeHead(200, {
      'Content-Type': 'image/png',
      'Content-Length': stat.size,
      'Cache-Control': 'no-cache',
    });
    fs.createReadStream(full).pipe(res);
    return;
  }

  if (url.pathname === '/' || url.pathname === '/index.html') {
    const html = renderHtml(getStatus());
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(html);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('Not found');
});

server.listen(PORT, HOST, () => {
  console.log(`ASTRA HUD Android preview server listening on http://${HOST}:${PORT}`);
});
