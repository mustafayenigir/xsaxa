'use strict';
const {
  app, BrowserWindow, Menu, ipcMain, nativeTheme, powerSaveBlocker, screen, session,
} = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

const SITE_URL = 'https://scjegitim.blogspot.com';
const ICON = path.join(__dirname, 'build', 'icon.png');
const PRELOAD = path.join(__dirname, 'preload.js');

const ZOOM_MIN = 50;
const ZOOM_MAX = 200;
const THEMES = ['system', 'light', 'dark'];

// Ekran kapanıp pencere gizlense bile sesin ve sayfanın uyutulmaması için
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');

// ---------------------------------------------------------------- ayarlar
const DEFAULTS = { keepAudio: true, theme: 'system', zoom: 100, pwdHash: null, pwdSalt: null };
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
    theme: settings.theme,
    zoom: settings.zoom,
    zoomMin: ZOOM_MIN,
    zoomMax: ZOOM_MAX,
  };
}

// ---------------------------------------------------------------- şifre
function hashPwd(pwd, saltHex) {
  return crypto.scryptSync(pwd, Buffer.from(saltHex, 'hex'), 32).toString('hex');
}

function safeEqualHex(a, b) {
  const ba = Buffer.from(a, 'hex');
  const bb = Buffer.from(b, 'hex');
  return ba.length === bb.length && crypto.timingSafeEqual(ba, bb);
}

let failedAttempts = 0;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------- pencereler
let win = null;
let settingsWin = null;
let blackWin = null;
let unlocked = false;
let blocker = null;
let themeCssKey = null;

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
  if (!win || !unlocked) return;
  const wc = win.webContents;
  wc.setZoomFactor(settings.zoom / 100);

  if (themeCssKey) {
    wc.removeInsertedCSS(themeCssKey).catch(() => {});
    themeCssKey = null;
  }
  if (nativeTheme.shouldUseDarkColors) {
    wc.insertCSS(DARK_CSS).then((key) => { themeCssKey = key; }).catch(() => {});
  }
}

function updateBlocker() {
  const need = unlocked && settings.keepAudio;
  if (need && blocker === null) {
    blocker = powerSaveBlocker.start('prevent-app-suspension');
  } else if (!need && blocker !== null) {
    powerSaveBlocker.stop(blocker);
    blocker = null;
  }
}

function applyAll() {
  nativeTheme.themeSource = settings.theme;
  updateBlocker();
  applySiteSettings();
  if (settingsWin && !settingsWin.isDestroyed()) {
    settingsWin.webContents.send('settings:updated', publicSettings());
  }
}

function createMainWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 420,
    minHeight: 500,
    show: false,
    icon: ICON,
    backgroundColor: '#0F1522',
    title: 'Meyvem',
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
    if (unlocked && /^https?:/i.test(url)) wc.loadURL(url);
    return { action: 'deny' };
  });
  wc.on('will-navigate', (e, url) => {
    if (!unlocked && !url.startsWith('file://')) e.preventDefault();
  });
  wc.on('did-finish-load', () => { if (unlocked) applySiteSettings(); });

  showLock();
}

function showLock() {
  exitBlack();
  unlocked = false;
  themeCssKey = null;
  updateBlocker();
  if (win) win.loadFile(path.join(__dirname, 'renderer', 'lock.html'));
  buildMenu();
}

function openSite() {
  unlocked = true;
  updateBlocker();
  if (win) win.loadURL(SITE_URL);
  buildMenu();
}

function lockApp() {
  if (settingsWin && !settingsWin.isDestroyed()) settingsWin.close();
  showLock(); // site boşaltılır, ses durur
}

function openSettings() {
  if (settingsWin && !settingsWin.isDestroyed()) {
    settingsWin.focus();
    return;
  }
  settingsWin = new BrowserWindow({
    width: 440,
    height: 660,
    parent: win || undefined,
    modal: !!win,
    resizable: false,
    minimizable: false,
    maximizable: false,
    title: 'Ayarlar',
    icon: ICON,
    backgroundColor: '#0F1522',
    autoHideMenuBar: true,
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  settingsWin.setMenu(null);
  secure(settingsWin);
  settingsWin.loadFile(path.join(__dirname, 'renderer', 'settings.html'));
  settingsWin.on('closed', () => { settingsWin = null; });
}

// Siyah ekran: tüm ekranı kaplayan siyah pencere; altta ses çalmaya devam eder.
function enterBlack() {
  if (!unlocked || blackWin || !win) return;
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
  const template = [
    {
      label: 'Meyvem',
      submenu: [
        { label: 'Ayarlar', accelerator: 'CmdOrCtrl+,', click: openSettings },
        { label: 'Siyah ekran', accelerator: 'CmdOrCtrl+B', enabled: unlocked, click: enterBlack },
        { label: 'Uygulamayı kilitle', accelerator: 'CmdOrCtrl+L', enabled: unlocked, click: lockApp },
        { type: 'separator' },
        { label: 'Çıkış', accelerator: 'CmdOrCtrl+Q', click: () => app.quit() },
      ],
    },
    {
      label: 'Görünüm',
      submenu: [
        { label: 'Yazıyı büyüt', accelerator: 'CmdOrCtrl+Plus', enabled: unlocked, click: () => bumpZoom(10) },
        { label: 'Yazıyı büyüt (gizli)', accelerator: 'CmdOrCtrl+=', visible: false, click: () => bumpZoom(10) },
        { label: 'Yazıyı küçült', accelerator: 'CmdOrCtrl+-', enabled: unlocked, click: () => bumpZoom(-10) },
        { label: 'Varsayılan boyut', accelerator: 'CmdOrCtrl+0', enabled: unlocked, click: resetZoom },
        { type: 'separator' },
        { label: 'Tam ekran', accelerator: 'F11', role: 'togglefullscreen' },
      ],
    },
    {
      label: 'Git',
      submenu: [
        {
          label: 'Geri', accelerator: 'Alt+Left', enabled: unlocked,
          click: () => { if (win && win.webContents.canGoBack()) win.webContents.goBack(); },
        },
        {
          label: 'İleri', accelerator: 'Alt+Right', enabled: unlocked,
          click: () => { if (win && win.webContents.canGoForward()) win.webContents.goForward(); },
        },
        { label: 'Yenile', accelerator: 'F5', enabled: unlocked, click: () => { if (win) win.webContents.reload(); } },
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

ipcMain.handle('lock:state', (e) => {
  if (!fromLocal(e)) return null;
  return { hasPassword: !!settings.pwdHash };
});

ipcMain.handle('lock:create', (e, pwd, confirm) => {
  if (!fromLocal(e)) return { ok: false, error: 'İzin yok.' };
  if (settings.pwdHash) return { ok: false, error: 'Şifre zaten oluşturulmuş.' };
  pwd = String(pwd || '');
  if (!pwd) return { ok: false, error: 'Lütfen şifre girin.' };
  if (pwd.length < 4) return { ok: false, error: 'Şifre en az 4 karakter olmalı.' };
  if (pwd !== String(confirm || '')) return { ok: false, error: 'Şifreler aynı değil.' };
  const salt = crypto.randomBytes(16).toString('hex');
  settings.pwdSalt = salt;
  settings.pwdHash = hashPwd(pwd, salt);
  saveSettings();
  openSite();
  return { ok: true };
});

ipcMain.handle('lock:unlock', async (e, pwd) => {
  if (!fromLocal(e)) return { ok: false, error: 'İzin yok.' };
  pwd = String(pwd || '');
  if (!pwd) return { ok: false, error: 'Lütfen şifre girin.' };
  if (!settings.pwdHash) return { ok: false, error: 'Önce şifre oluşturun.' };
  if (safeEqualHex(hashPwd(pwd, settings.pwdSalt), settings.pwdHash)) {
    failedAttempts = 0;
    openSite();
    return { ok: true };
  }
  failedAttempts += 1;
  await sleep(Math.min(failedAttempts * 500, 5000)); // yanlış denemelerde bekletme
  return { ok: false, error: 'Hatalı şifre.' };
});

ipcMain.handle('settings:get', (e) => (fromLocal(e) ? publicSettings() : null));

ipcMain.handle('settings:set', (e, patch) => {
  if (!fromLocal(e) || !patch || typeof patch !== 'object') return null;
  if (typeof patch.keepAudio === 'boolean') settings.keepAudio = patch.keepAudio;
  if (THEMES.includes(patch.theme)) settings.theme = patch.theme;
  if (Number.isFinite(patch.zoom)) {
    settings.zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(patch.zoom / 10) * 10));
  }
  saveSettings();
  applyAll();
  return publicSettings();
});

ipcMain.on('settings:open', (e) => { if (fromLocal(e)) openSettings(); });
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
    settingsFile = path.join(app.getPath('userData'), 'settings.json');
    loadSettings();
    nativeTheme.themeSource = settings.theme;
    nativeTheme.on('updated', applySiteSettings);

    // Kamera, mikrofon, konum gibi izinler reddedilir (tam ekran video serbest)
    session.defaultSession.setPermissionRequestHandler((_wc, permission, cb) => {
      cb(permission === 'fullscreen');
    });

    createMainWindow();
  });

  app.on('window-all-closed', () => app.quit());
}
