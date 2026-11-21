'use strict';
const {
  app, BrowserWindow, Menu, ipcMain, nativeTheme, net, powerSaveBlocker, screen, session,
} = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

const API_URL = 'https://script.google.com/macros/s/AKfycbyUwCt2kyUA4npnPIaqJztXCNsq6B0anGvRWaW5UwfHKBhHbgGadagfocj-KH2jeSYt/exec';
const adblock = require('./adblock');
const NO_RE = /^[A-Z0-9-]{6,24}$/;
const GOOGLE_SIGNIN_URL = 'https://accounts.google.com/ServiceLogin?hl=tr&continue=https%3A%2F%2Fmyaccount.google.com%2F';
const GOOGLE_HOSTS = /(^|\.)(google\.com|gstatic\.com|googleusercontent\.com|googleapis.com|youtube\.com)$/i;
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
const DEFAULTS = {
  keepAudio: true, adBlock: true, theme: 'system', palette: 0, zoom: 100, pwdHash: null, pwdSalt: null,
  // kayıt / yoklama
  pendingNo: null, no: null, uuid: null, token: null, googleDone: false, siteDone: false,
  siteUrl: null, links: [], pendingSeconds: 0,
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

// ---------------------------------------------------------------- sunucu (Google Apps Script)
// Yanıt: { ok: true, ... } | { ok: false, error: 'invalid|busy|auth|bad_request|server' } | null (ağ hatası)
async function api(action, fields) {
  try {
    const res = await net.fetch(API_URL, {
      method: 'POST',
      // text/plain: Apps Script gövdeyi okur; ek ön kontrol gerektirmez
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify({ action, ...fields }),
      redirect: 'follow', // Apps Script yanıtı 302 ile verir
      signal: AbortSignal.timeout(15000),
    });
    return await res.json();
  } catch (_) {
    return null;
  }
}

function identity() {
  return settings.no && settings.uuid && settings.token
    ? { no: settings.no, uuid: settings.uuid, token: settings.token }
    : null;
}

function currentStep() {
  if (identity()) {
    if (!settings.googleDone) return 'google';
    if (!settings.siteDone) return 'site';
    return 'enter';
  }
  if (settings.pendingNo) return 'password';
  return 'no';
}

function errText(code) {
  switch (code) {
    case 'invalid': return 'Bu numara yetkili listede bulunamadı.';
    case 'busy': return 'Çok fazla deneme yapıldı. Biraz bekleyip tekrar deneyin.';
    case 'auth': return 'Hesabınız doğrulanamadı. Uygulama verisini silip yeniden başlatın ve numaranızı tekrar girin.';
    default: return 'Sunucuda bir sorun oluştu. Biraz sonra tekrar deneyin.';
  }
}
const NET_ERR = 'Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.';

function httpsLinks(arr) {
  const out = [];
  for (const o of Array.isArray(arr) ? arr : []) {
    const url = o && typeof o.url === 'string' ? o.url : '';
    if (!/^https:\/\/./i.test(url)) continue;
    out.push({ name: String(o.name || url), url, desc: String(o.desc || '') });
  }
  return out;
}

async function refreshLinks() {
  const id = identity();
  if (!id) return null;
  const r = await api('links', id);
  if (!r || !r.ok) return null;
  settings.links = httpsLinks(r.links);
  saveSettings();
  return settings.links;
}

/** Açılacak adres: seçili → listenin ilki. Liste hiç yoksa null (uygulamada hazır site yok). */
function siteToOpen() {
  if (settings.siteUrl && /^https:\/\/./i.test(settings.siteUrl)) return settings.siteUrl;
  const first = (settings.links || [])[0];
  return first ? first.url : null;
}

// ---- Google hesabı oturumu (adres yazılmaz; oturum, uygulamadaki tüm sitelerde geçerli olur)
let googleWin = null;

async function googleSignedIn() {
  try {
    const all = await session.defaultSession.cookies.get({});
    return all.some((c) => (c.name === 'SID' || c.name === '__Secure-1PSID') && /google\.com$/i.test(c.domain || ''));
  } catch (_) {
    return false;
  }
}

function closeGoogleWin() {
  if (googleWin && !googleWin.isDestroyed()) googleWin.close();
  googleWin = null;
}

function openGoogleWin() {
  if (googleWin && !googleWin.isDestroyed()) { googleWin.focus(); return; }
  googleWin = new BrowserWindow({
    width: 520,
    height: 740,
    parent: win || undefined,
    modal: !!win,
    title: 'Google hesabı',
    icon: ICON,
    autoHideMenuBar: true,
    backgroundColor: '#ffffff',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }, // köprü (preload) yok
  });
  googleWin.setMenu(null);
  secure(googleWin);
  const wc = googleWin.webContents;
  // Google, "Electron" işaretli tarayıcılarda girişi engelleyebilir; işareti kaldır.
  wc.setUserAgent(wc.getUserAgent().replace(/\sElectron\/\S+/i, '').replace(/\s?meyvem\/\S+/i, ''));
  wc.setWindowOpenHandler(() => ({ action: 'deny' }));
  wc.on('will-navigate', (e, url) => {
    try {
      const u = new URL(url);
      if (u.protocol !== 'https:' || !GOOGLE_HOSTS.test(u.hostname)) e.preventDefault();
    } catch (_) { e.preventDefault(); }
  });
  googleWin.on('closed', () => { googleWin = null; });
  googleWin.loadURL(GOOGLE_SIGNIN_URL);
}

// Uygulamada geçen süre: dakikada bir yerel sayaca eklenir, 10 dakikada bir ve kilitlenince/çıkışta sunucuya gider.
let usageMark = 0;
let usageTimer = null;
let lastFlush = 0;
let flushing = false;
const TICK_MS = 60 * 1000;
const FLUSH_EVERY_MS = 10 * 60 * 1000;
const MAX_TICK_SEC = 90; // bilgisayar uyursa uyku süresi sayılmaz

function tickUsage() {
  if (!usageMark) return;
  const now = Date.now();
  const sec = Math.min(Math.max(Math.floor((now - usageMark) / 1000), 0), MAX_TICK_SEC);
  usageMark = now;
  settings.pendingSeconds = (settings.pendingSeconds || 0) + sec;
  saveSettings();
  if (now - lastFlush >= FLUSH_EVERY_MS) flushUsage();
}

async function flushUsage() {
  lastFlush = Date.now();
  const id = identity();
  const pending = settings.pendingSeconds || 0;
  if (flushing || !id || pending <= 0) return;
  flushing = true;
  try {
    const chunk = Math.min(pending, 7200);
    const r = await api('usage', { ...id, seconds: chunk });
    if (r && r.ok) {
      settings.pendingSeconds = Math.max(0, (settings.pendingSeconds || 0) - chunk);
      saveSettings();
    }
  } finally {
    flushing = false;
  }
}

function beginUsage() {
  if (!usageMark) usageMark = Date.now();
  if (!lastFlush) lastFlush = Date.now();
  clearInterval(usageTimer);
  usageTimer = setInterval(tickUsage, TICK_MS);
  flushUsage(); // önceki oturumdan kalan süre varsa gönder
}

function endUsage() {
  clearInterval(usageTimer);
  tickUsage();
  usageMark = 0;
  return flushUsage();
}

// ---------------------------------------------------------------- pencereler
let win = null;
let settingsWin = null;
let blackWin = null;
let unlocked = false;
let blocker = null;
let themeCssKey = null;
let adCssKey = null;
let siteBase = null;

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

  // Reklam alanlarını gizle (yalnızca http/https sayfalarda)
  if (adCssKey) {
    wc.removeInsertedCSS(adCssKey).catch(() => {});
    adCssKey = null;
  }
  if (settings.adBlock !== false && /^https?:/i.test(wc.getURL())) {
    wc.insertCSS(adblock.css).then((key) => { adCssKey = key; }).catch(() => {});
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

function loadSite(url) {
  if (!url) return;
  siteBase = url;
  if (win && unlocked) win.loadURL(url);
}

/** Seçili site listeden kaldırıldıysa seçimi sıfırlar; gösterilen site değiştiyse yeniler. */
function reconcileSite() {
  if (!unlocked) return;
  const list = settings.links || [];
  if (list.length === 0) return;
  if (settings.siteUrl && !list.some((l) => l.url === settings.siteUrl)) {
    settings.siteUrl = null;
    saveSettings();
  }
  const want = siteToOpen();
  if (want && want !== siteBase) loadSite(want);
}

async function openSite(logLogin) {
  unlocked = true;
  updateBlocker();
  buildMenu();
  beginUsage();
  const id = identity();
  if (logLogin && id) api('login', id); // yoklama tablosuna giriş tarihi (cevabı beklenmez)

  if (!siteToOpen()) {
    // Liste hiç inmemiş: önce listeyi al, sonra ilk siteyi aç
    if (id) await refreshLinks();
    if (!unlocked) return;
    const url = siteToOpen();
    if (url) loadSite(url);
    else if (win) win.loadURL('about:blank');
  } else {
    loadSite(siteToOpen());
    if (id) refreshLinks().then(reconcileSite);
  }
}

function lockApp() {
  if (settingsWin && !settingsWin.isDestroyed()) settingsWin.close();
  endUsage();
  siteBase = null;
  showLock(); // site boşaltılır, ses durur
}

function openSettings() {
  if (settingsWin && !settingsWin.isDestroyed()) {
    settingsWin.focus();
    return;
  }
  settingsWin = new BrowserWindow({
    width: 440,
    height: 760,
    parent: win || undefined,
    modal: !!win,
    resizable: false,
    minimizable: false,
    maximizable: false,
    title: 'Ana Sayfa',
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
        { label: 'Ana Sayfa', accelerator: 'CmdOrCtrl+,', click: openSettings },
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
  return { step: currentStep() };
});

// 1) Giriş numarası (yalnızca bir kere)
ipcMain.handle('lock:checkNo', async (e, no) => {
  if (!fromLocal(e)) return { ok: false, error: 'İzin yok.' };
  if (currentStep() !== 'no') return { ok: true, step: currentStep() };
  no = String(no || '').trim().replace(/\s+/g, '').toUpperCase();
  if (!NO_RE.test(no)) return { ok: false, error: 'Giriş numarası geçerli biçimde değil. Örnek: 00xxxxxx-xxxxx' };
  const r = await api('check', { no });
  if (!r) return { ok: false, error: NET_ERR };
  if (!r.ok) return { ok: false, error: errText(r.error) };
  settings.pendingNo = no;
  saveSettings();
  return { ok: true, step: 'password' };
});

// 2) Şifre oluştur → sunucuda UUID üretilir ve yoklama satırı yazılır
ipcMain.handle('lock:create', async (e, pwd, confirm) => {
  if (!fromLocal(e)) return { ok: false, error: 'İzin yok.' };
  if (currentStep() !== 'password') return { ok: true, step: currentStep() };
  pwd = String(pwd || '');
  if (!pwd) return { ok: false, error: 'Lütfen şifre girin.' };
  if (pwd.length < 4) return { ok: false, error: 'Şifre en az 4 karakter olmalı.' };
  if (pwd !== String(confirm || '')) return { ok: false, error: 'Şifreler aynı değil.' };
  const no = settings.pendingNo;
  const salt = crypto.randomBytes(16).toString('hex');
  const hash = hashPwd(pwd, salt); // 64 hex karakter: sunucuya giden "token" bu özettir
  const r = await api('register', { no, pwdHash: hash });
  if (!r) return { ok: false, error: NET_ERR };
  if (r.ok && typeof r.uuid === 'string' && r.uuid) {
    // Yerel şifre özeti yalnızca sunucu kaydı başarılı olunca saklanır
    settings.pwdSalt = salt;
    settings.pwdHash = hash;
    settings.no = no;
    settings.uuid = r.uuid;
    settings.token = hash;
    settings.googleDone = false;
    settings.siteDone = false;
    settings.pendingNo = null;
    saveSettings();
    return { ok: true, step: 'google' };
  }
  if (r.error === 'invalid') {
    settings.pendingNo = null; // numara bu arada listeden çıkarılmış
    saveSettings();
    return { ok: false, step: 'no', error: errText('invalid') };
  }
  return { ok: false, error: errText(r.error) };
});

// 3) Google hesabıyla oturum açma
ipcMain.handle('lock:googleOpen', async (e) => {
  if (!fromLocal(e)) return { ok: false };
  if (currentStep() !== 'google') return { ok: true, step: currentStep() };
  if (await googleSignedIn()) return { ok: true, signedIn: true };
  openGoogleWin();
  return { ok: true, signedIn: false };
});

ipcMain.handle('lock:googleStatus', async (e) => {
  if (!fromLocal(e)) return { signedIn: false };
  const signedIn = await googleSignedIn();
  if (signedIn) closeGoogleWin(); // giriş tamamlandı: pencereyi kendiliğinden kapat
  return { signedIn };
});

ipcMain.handle('lock:googleDone', async (e) => {
  if (!fromLocal(e)) return { ok: false, error: 'İzin yok.' };
  if (currentStep() !== 'google') return { ok: true, step: currentStep() };
  if (!(await googleSignedIn())) return { ok: false, error: 'Henüz Google hesabıyla oturum açılmadı.' };
  closeGoogleWin();
  settings.googleDone = true;
  saveSettings();
  setTimeout(openSettings, 300); // sıradaki adım: ilk ayarlar (site seçimi)
  return { ok: true, step: 'site' };
});

// 3b) İlk ayarlar: site seçilip "Devam Et" denir
ipcMain.handle('lock:siteStart', (e) => {
  if (!fromLocal(e)) return { ok: false };
  if (currentStep() === 'site') openSettings();
  return { ok: true };
});

// 4) Sonraki açılışlar: yalnızca şifre (yerelde doğrulanır, ağ gerekmez)
ipcMain.handle('lock:unlock', async (e, pwd) => {
  if (!fromLocal(e)) return { ok: false, error: 'İzin yok.' };
  pwd = String(pwd || '');
  if (!pwd) return { ok: false, error: 'Lütfen şifre girin.' };
  if (currentStep() !== 'enter') return { ok: false, step: currentStep(), error: 'Önce kaydı tamamlayın.' };
  if (safeEqualHex(hashPwd(pwd, settings.pwdSalt), settings.pwdHash)) {
    failedAttempts = 0;
    openSite(true);
    return { ok: true };
  }
  failedAttempts += 1;
  await sleep(Math.min(failedAttempts * 500, 5000)); // yanlış denemelerde bekletme
  return { ok: false, error: 'Hatalı şifre.' };
});

// Web sitesi listesi (Ayarlar)
function sitesState() {
  const firstRun = currentStep() === 'site';
  // İlk kurulumda hiçbiri hazır seçili gelmez; kullanıcı kendisi seçer
  const selected = firstRun ? settings.siteUrl : siteToOpen();
  return { registered: !!identity(), links: settings.links || [], selected, firstRun };
}

ipcMain.handle('sites:get', (e) => (fromLocal(e) ? sitesState() : null));

ipcMain.handle('sites:refresh', async (e) => {
  if (!fromLocal(e)) return null;
  const list = await refreshLinks();
  if (list) reconcileSite();
  return { ...sitesState(), refreshed: !!list };
});

ipcMain.handle('sites:select', (e, url) => {
  if (!fromLocal(e)) return null;
  // Yalnızca sunucudan gelen listedeki adresler seçilebilir
  if (!(settings.links || []).some((l) => l.url === url)) return sitesState();
  settings.siteUrl = url;
  saveSettings();
  const first = currentStep() === 'site';
  if (first) {
    settings.siteDone = true; // ilk kurulumda logoya tıklamak seçer ve siteyi açar
    saveSettings();
  }
  if (first || unlocked) {
    if (settingsWin && !settingsWin.isDestroyed()) settingsWin.close();
    if (first) openSite(false); // ilk giriş tarihi kayıt sırasında zaten yazıldı
    else loadSite(siteToOpen());
  }
  return { ...sitesState(), picked: true, opened: first || unlocked };
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
  const palBefore = settings.palette;
  if (Number.isInteger(patch.palette) && patch.palette >= 0 && patch.palette <= 9) settings.palette = patch.palette;
  if (Number.isFinite(patch.zoom)) {
    settings.zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(patch.zoom / 10) * 10));
  }
  saveSettings();
  applyAll();
  // Renk teması değiştiyse kilit ekranı gibi yerel sayfalar da anında yeni renkleri alsın
  if (palBefore !== settings.palette && win && !win.isDestroyed() && win.webContents.getURL().startsWith('file:')) {
    win.webContents.send('settings:updated', publicSettings());
  }
  // Reklam ayarı değiştiyse açık sayfayı yenile ki istekler yeni ayara göre yüklensin
  if (unlocked && win && adBefore !== (settings.adBlock !== false)) win.webContents.reload();
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
    // Reklam engelleme: bilinen reklam sunucularına giden istekleri iptal et (Ayarlar'dan kapatılabilir)
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
  });

  app.on('window-all-closed', () => app.quit());

  // Çıkarken birikmiş süreyi (en çok 3 sn bekleyerek) sunucuya gönder
  let quitting = false;
  app.on('before-quit', (e) => {
    if (quitting) return;
    quitting = true;
    e.preventDefault();
    tickUsage();
    Promise.race([flushUsage(), sleep(3000)]).finally(() => app.quit());
  });
}
