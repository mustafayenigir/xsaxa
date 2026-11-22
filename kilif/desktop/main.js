'use strict';
const {
  app, BrowserWindow, Menu, ipcMain, nativeTheme, powerSaveBlocker, screen, session, net,
} = require('electron');
const path = require('path');
const fs = require('fs');

const adblock = require('./adblock');
const ICON = path.join(__dirname, 'build', 'icon.png');
const PRELOAD = path.join(__dirname, 'preload.js');

const ZOOM_MIN = 50;
const ZOOM_MAX = 200;
const THEMES = ['system', 'light', 'dark'];
const PALETTE_COUNT = 3;

// Ekran kapanıp pencere gizlense bile sesin ve sayfanın uyutulmaması için
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');

// ---------------------------------------------------------------- ayarlar
const DEFAULTS = {
  keepAudio: true, adBlock: true, theme: 'system', palette: 0, zoom: 100,
  sites: [], // [{ name, url, desc }] — kullanıcının kendi eklediği siteler
  selected: null,
};
let settingsFile = null;
let settings = { ...DEFAULTS };

function loadSettings() {
  try {
    const raw = JSON.parse(fs.readFileSync(settingsFile, 'utf8'));
    settings = { ...DEFAULTS, ...raw };
  } catch (_) {
    settings = { ...DEFAULTS };
  }
}

function saveSettings() {
  try {
    fs.mkdirSync(path.dirname(settingsFile), { recursive: true });
    fs.writeFileSync(settingsFile, JSON.stringify(settings));
  } catch (_) { /* yazılamazsa sessizce geç */ }
}

function publicSettings() {
  return {
    keepAudio: settings.keepAudio,
    adBlock: settings.adBlock !== false,
    theme: settings.theme,
    palette: Number.isInteger(settings.palette) ? settings.palette : 0,
    zoom: settings.zoom,
    zoomMin: ZOOM_MIN,
    zoomMax: ZOOM_MAX,
  };
}

// ---------------------------------------------------------------- siteler
const HOST_RE = /^[\p{L}\p{N}.-]+$/u;

/** "ornek.com" gibi yazılan adresi https://ornek.com biçimine getirir; geçersizse null. */
function normalizeUrl(input) {
  let t = String(input || '').trim();
  if (!t || /\s/.test(t)) return null;
  if (/^https:\/\//i.test(t)) t = 'https://' + t.slice(8);
  else if (/^http:\/\//i.test(t)) t = 'https://' + t.slice(7);
  else if (t.includes('://')) return null;
  else t = 'https://' + t;
  let u;
  try { u = new URL(t); } catch (_) { return null; }
  const host = u.hostname;
  if (!host.includes('.') || host.startsWith('.') || host.endsWith('.') || !HOST_RE.test(host)) return null;
  if (u.username || u.password) return null;
  return t;
}

function defaultName(url) {
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch (_) { return url; }
}

const clean = (s, max) => String(s == null ? '' : s).replace(/[\u0000-\u0008\u000B-\u001F\u007F]/g, '').trim().slice(0, max);
const sameSite = (a, b) => a.replace(/\/+$/, '').toLowerCase() === b.replace(/\/+$/, '').toLowerCase();

function sitesList() {
  const arr = Array.isArray(settings.sites) ? settings.sites : [];
  return arr
    .filter((s) => s && typeof s.url === 'string' && normalizeUrl(s.url) === s.url)
    .map((s) => ({ name: clean(s.name, 40) || defaultName(s.url), url: s.url, desc: clean(s.desc, 300) }));
}

/** Açılacak site: seçili olan, yoksa listedeki ilk site; liste boşsa null. */
function siteToOpen() {
  const list = sitesList();
  const sel = list.find((s) => s.url === settings.selected);
  return (sel || list[0] || {}).url || null;
}

function sitesState() {
  return { sites: sitesList(), selected: siteToOpen() };
}

// ---------------------------------------------------------------- pencereler
let win = null;
let homeWin = null;
let blackWin = null;
let blocker = null;
let themeCssKey = null;
let adCssKey = null;
let siteBase = null; // ana pencerede gösterilen site (karşılama sayfasında null)


function secure(w) {
  // Windows 10 (2004+) ve üstünde pencere ekran görüntüsü / ekran kaydında siyah görünür.
  try { w.setContentProtection(true); } catch (_) { /* desteklenmiyorsa geç */ }
}

const DARK_CSS = `
  html { filter: invert(1) hue-rotate(180deg) !important; background: #fff; }
  img, video, picture, canvas, iframe, svg image {
    filter: invert(1) hue-rotate(180deg) !important;
  }
`;

function applySiteSettings() {
  if (!win || win.isDestroyed()) return;
  const wc = win.webContents;
  const site = /^https?:/i.test(wc.getURL());
  wc.setZoomFactor(site ? settings.zoom / 100 : 1);

  if (themeCssKey) {
    wc.removeInsertedCSS(themeCssKey).catch(() => {});
    themeCssKey = null;
  }
  if (site && nativeTheme.shouldUseDarkColors) {
    wc.insertCSS(DARK_CSS).then((key) => { themeCssKey = key; }).catch(() => {});
  }

  // Reklam alanlarını gizle (yalnızca http/https sayfalarda)
  if (adCssKey) {
    wc.removeInsertedCSS(adCssKey).catch(() => {});
    adCssKey = null;
  }
  if (site && settings.adBlock !== false) {
    wc.insertCSS(adblock.css).then((key) => { adCssKey = key; }).catch(() => {});
  }
}

function updateBlocker() {
  const need = siteBase !== null && settings.keepAudio;
  if (need && blocker === null) {
    blocker = powerSaveBlocker.start('prevent-app-suspension');
  } else if (!need && blocker !== null) {
    powerSaveBlocker.stop(blocker);
    blocker = null;
  }
}

function notifyLocal() {
  for (const w of [homeWin, win]) {
    if (w && !w.isDestroyed() && w.webContents.getURL().startsWith('file:')) {
      w.webContents.send('settings:updated', publicSettings());
    }
  }
}

function applyAll() {
  nativeTheme.themeSource = settings.theme;
  updateBlocker();
  applySiteSettings();
  notifyLocal();
}

function createMainWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 420,
    minHeight: 500,
    show: false,
    icon: ICON,
    backgroundColor: '#1A1226',
    title: 'Kılıf',
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false,
    },
  });
  secure(win);
  win.once('ready-to-show', () => win.show());
  win.on('closed', () => { win = null; });

  const wc = win.webContents;
  // Site bağlantıları aynı pencerede açılsın
  wc.setWindowOpenHandler(({ url }) => {
    if (siteBase && /^https?:/i.test(url)) wc.loadURL(url);
    return { action: 'deny' };
  });
  // Karşılama sayfasından siteye kendiliğinden gidilmez; sitede gezinmek serbest
  wc.on('will-navigate', (e, url) => {
    if (!siteBase && !url.startsWith('file://')) e.preventDefault();
  });
  wc.on('did-finish-load', applySiteSettings);
  // Farenin "geri" tuşu
  win.on('app-command', (_e, cmd) => {
    if (cmd === 'browser-backward') goBack();
    if (cmd === 'browser-forward' && wc.canGoForward()) wc.goForward();
  });

  const url = siteToOpen();
  if (url) loadSite(url);
  else showWelcome();
}

function showWelcome() {
  exitBlack();
  siteBase = null;
  themeCssKey = null;
  adCssKey = null;
  updateBlocker();
  if (win) win.loadFile(path.join(__dirname, 'renderer', 'welcome.html'));
  buildMenu();
}

function loadSite(url) {
  if (!url || !win) return;
  siteBase = url;
  updateBlocker();
  win.loadURL(url);
  buildMenu();
}

/** Sitede geri gidecek sayfa yoksa Ana Sayfa açılır (telefondaki gibi). */
function goBack() {
  if (win && siteBase && win.webContents.canGoBack()) win.webContents.goBack();
  else openHome();
}

function openHome() {
  if (homeWin && !homeWin.isDestroyed()) {
    homeWin.focus();
    return;
  }
  homeWin = new BrowserWindow({
    width: 460,
    height: 780,
    parent: win || undefined,
    modal: !!win,
    resizable: false,
    minimizable: false,
    maximizable: false,
    title: 'Ana Sayfa',
    icon: ICON,
    backgroundColor: '#1A1226',
    autoHideMenuBar: true,
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  homeWin.setMenu(null);
  secure(homeWin);
  homeWin.loadFile(path.join(__dirname, 'renderer', 'home.html'));
  homeWin.on('closed', () => { homeWin = null; });
}

// Siyah ekran: tüm ekranı kaplayan siyah pencere; altta ses çalmaya devam eder.
function enterBlack() {
  if (!siteBase || blackWin || !win) return;
  const display = screen.getDisplayMatching(win.getBounds());
  blackWin = new BrowserWindow({
    x: display.bounds.x,
    y: display.bounds.y,
    width: display.bounds.width,
    height: display.bounds.height,
    frame: false,
    fullscreen: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    show: false,
    backgroundColor: '#000000',
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  blackWin.setMenu(null);
  blackWin.setAlwaysOnTop(true, 'screen-saver');
  blackWin.loadFile(path.join(__dirname, 'renderer', 'black.html'));
  blackWin.once('ready-to-show', () => { if (blackWin) blackWin.show(); });
  blackWin.on('closed', () => { blackWin = null; });
}

function exitBlack() {
  if (blackWin && !blackWin.isDestroyed()) blackWin.close();
  blackWin = null;
}

function bumpZoom(delta) {
  settings.zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, settings.zoom + delta));
  saveSettings();
  applyAll();
}

function resetZoom() {
  settings.zoom = 100;
  saveSettings();
  applyAll();
}

// ---------------------------------------------------------------- menü
function buildMenu() {
  const site = siteBase !== null;
  const template = [
    {
      label: 'Kılıf',
      submenu: [
        { label: 'Ana Sayfa', accelerator: 'CmdOrCtrl+,', click: openHome },
        { label: 'Siyah ekran', accelerator: 'CmdOrCtrl+B', enabled: site, click: enterBlack },
        { type: 'separator' },
        { label: 'Çıkış', accelerator: 'CmdOrCtrl+Q', click: () => app.quit() },
      ],
    },
    {
      label: 'Görünüm',
      submenu: [
        { label: 'Yazıyı büyüt', accelerator: 'CmdOrCtrl+Plus', enabled: site, click: () => bumpZoom(10) },
        { label: 'Yazıyı büyüt (gizli)', accelerator: 'CmdOrCtrl+=', visible: false, click: () => bumpZoom(10) },
        { label: 'Yazıyı küçült', accelerator: 'CmdOrCtrl+-', enabled: site, click: () => bumpZoom(-10) },
        { label: 'Varsayılan boyut', accelerator: 'CmdOrCtrl+0', enabled: site, click: resetZoom },
        { type: 'separator' },
        { label: 'Tam ekran', accelerator: 'F11', role: 'togglefullscreen' },
      ],
    },
    {
      label: 'Git',
      submenu: [
        { label: 'Geri', accelerator: 'Alt+Left', click: goBack },
        {
          label: 'İleri', accelerator: 'Alt+Right', enabled: site,
          click: () => { if (win && win.webContents.canGoForward()) win.webContents.goForward(); },
        },
        { label: 'Yenile', accelerator: 'F5', enabled: site, click: () => { if (win) win.webContents.reload(); } },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// ---------------------------------------------------------------- IPC
// Yalnızca uygulamanın yerel sayfalarından gelen istekleri kabul et.
function fromLocal(e) {
  try { return e.senderFrame.url.startsWith('file://'); } catch (_) { return false; }
}

ipcMain.handle('sites:get', (e) => (fromLocal(e) ? sitesState() : null));

// Site ekle (oldUrl yok) / düzenle (oldUrl = sitenin eski adresi)
ipcMain.handle('sites:put', (e, site, oldUrl) => {
  if (!fromLocal(e) || !site || typeof site !== 'object') return { ok: false };
  const url = normalizeUrl(site.url);
  if (!url) return { ok: false, error: 'Geçerli bir web adresi yazın (ör. ornek.com).' };
  const list = sitesList();
  const old = typeof oldUrl === 'string' ? list.findIndex((s) => s.url === oldUrl) : -1;
  if (list.some((s, i) => i !== old && sameSite(s.url, url))) return { ok: false, error: 'Bu site zaten listende.' };
  const entry = { name: clean(site.name, 40) || defaultName(url), url, desc: clean(site.desc, 300) };
  if (old >= 0) list[old] = entry; else list.push(entry);
  settings.sites = list;
  if (old >= 0 && settings.selected === oldUrl) settings.selected = url;
  saveSettings();
  // Şu an açık olan sitenin adresi değiştiyse yenisini aç
  if (old >= 0 && siteBase === oldUrl && url !== oldUrl) loadSite(url);
  return { ok: true, state: sitesState() };
});

ipcMain.handle('sites:remove', (e, url) => {
  if (!fromLocal(e)) return null;
  settings.sites = sitesList().filter((s) => s.url !== url);
  if (settings.selected === url) settings.selected = null;
  saveSettings();
  if (siteBase === url) {
    const next = siteToOpen();
    if (next) loadSite(next); else showWelcome();
  }
  return sitesState();
});

ipcMain.handle('sites:select', (e, url) => {
  if (!fromLocal(e)) return null;
  if (!sitesList().some((s) => s.url === url)) return sitesState();
  settings.selected = url;
  saveSettings();
  if (homeWin && !homeWin.isDestroyed()) homeWin.close();
  loadSite(url);
  return sitesState();
});

// Site logosu (favicon): indirilir, cihazda saklanır, veri adresi olarak verilir
const iconMem = new Map();
ipcMain.handle('sites:icon', async (e, url) => {
  if (!fromLocal(e)) return null;
  let host;
  try { host = new URL(String(url)).hostname.toLowerCase(); } catch (_) { return null; }
  if (!/^[a-z0-9.-]+$/.test(host)) return null;
  if (iconMem.has(host)) return iconMem.get(host);
  const dir = path.join(app.getPath('userData'), 'icons');
  const file = path.join(dir, host + '.png');
  try {
    let buf = null;
    if (fs.existsSync(file)) {
      buf = fs.readFileSync(file);
    } else {
      const res = await net.fetch('https://www.google.com/s2/favicons?domain=' + encodeURIComponent(host) + '&sz=128',
        { signal: AbortSignal.timeout(8000) });
      if (!res.ok) return null;
      buf = Buffer.from(await res.arrayBuffer());
      if (!buf.length) return null;
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(file, buf);
    }
    const dataUrl = 'data:image/png;base64,' + buf.toString('base64');
    iconMem.set(host, dataUrl);
    return dataUrl;
  } catch (_) {
    return null;
  }
});

ipcMain.handle('settings:get', (e) => (fromLocal(e) ? publicSettings() : null));

ipcMain.handle('settings:set', (e, patch) => {
  if (!fromLocal(e) || !patch || typeof patch !== 'object') return null;
  if (typeof patch.keepAudio === 'boolean') settings.keepAudio = patch.keepAudio;
  const adBefore = settings.adBlock !== false;
  if (typeof patch.adBlock === 'boolean') settings.adBlock = patch.adBlock;
  if (THEMES.includes(patch.theme)) settings.theme = patch.theme;
  if (Number.isInteger(patch.palette) && patch.palette >= 0 && patch.palette < PALETTE_COUNT) settings.palette = patch.palette;
  if (Number.isFinite(patch.zoom)) {
    settings.zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(patch.zoom / 10) * 10));
  }
  saveSettings();
  applyAll();
  // Reklam ayarı değiştiyse açık sayfayı yenile ki istekler yeni ayara göre yüklensin
  if (siteBase && win && adBefore !== (settings.adBlock !== false)) win.webContents.reload();
  return publicSettings();
});

ipcMain.on('home:open', (e) => { if (fromLocal(e)) openHome(); });
ipcMain.on('black:exit', (e) => { if (fromLocal(e)) exitBlack(); });

// ---------------------------------------------------------------- başlangıç
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.focus();
    }
  });

  app.whenReady().then(() => {
    // Reklam engelleme: bilinen reklam sunucularına giden istekleri iptal et (Ana Sayfa'dan kapatılabilir)
    const adUrls = adblock.domains.map((d) => `*://*.${d}/*`);
    session.defaultSession.webRequest.onBeforeRequest({ urls: adUrls }, (details, cb) => {
      cb({ cancel: settings.adBlock !== false });
    });
    settingsFile = path.join(app.getPath('userData'), 'settings.json');
    loadSettings();
    nativeTheme.themeSource = settings.theme;
    nativeTheme.on('updated', applySiteSettings);

    // Kamera, mikrofon, konum gibi izinler reddedilir (tam ekran video serbest)
    session.defaultSession.setPermissionRequestHandler((_wc, permission, cb) => {
      cb(permission === 'fullscreen');
    });

    createMainWindow();
    if (!siteToOpen()) openHome(); // ilk açılış: site eklemek için Ana Sayfa
  });

  app.on('window-all-closed', () => app.quit());
}
