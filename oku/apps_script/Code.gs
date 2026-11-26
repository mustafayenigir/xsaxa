/**
 * OKU / OKUT - Google Apps Script sunucu kodu
 * ------------------------------------------------------------------
 * Öğrenci uygulaması (Oku) ve veli uygulaması (Okut) bu betikle konuşur.
 * E-Tabloya Uzantılar ▸ Apps Script ile açılan betiğe yapıştırın.
 * Sayfalar (yoksa kendiliğinden oluşturulur):
 *
 *  SİZİN DOLDURDUKLARINIZ
 *  1) "Öğrenciler"     A: Öğrenci No | B: Ad Soyad | C: Sınıf / Grup (isteğe bağlı)
 *  2) "Veliler"        A: Veli No | B: Ad Soyad | C: Öğrenci No'ları (virgülle: 101, 102)
 *  3) "Web Siteleri"   A: Site adı | B: Link (https://...) | C: Açıklama
 *  4) "Takvim"         A: Tarih | B: Başlangıç | C: Bitiş | D: Ders / Başlık | E: Zoom linki |
 *                      F: Açıklama | G: Kimler (boş = herkes; öğrenci no veya sınıf, virgülle)
 *  5) "Bildirimler"    A: Tarih | B: Başlık | C: Mesaj | D: Kime (boş = herkes; "öğrenci", "veli",
 *                      öğrenci no / veli no / sınıf, virgülle) | E: Son gün (isteğe bağlı)
 *
 *  UYGULAMANIN DOLDURDUKLARI (elle değiştirmeyin)
 *  6) "Kayıtlar"       A: No | B: Rol | C: UUID | D: Şifre özeti | E: Kayıt tarihi |
 *                      F: Son giriş | G: Toplam süre (s:dk:sn) | H: Giriş sayısı
 *  7) "Giriş Kayıtları" A: Tarih-saat | B: No | C: Rol | D: Ad | E: Olay
 *  8) "Günlük Süre"    A: Tarih | B: Öğrenci No | C: Süre (s:dk:sn) | D: Saniye
 *
 * ZOOM (uygulama içinde katılma): Proje Ayarları ▸ Betik Özellikleri'ne
 *   ZOOM_CLIENT_ID  ve  ZOOM_CLIENT_SECRET  ekleyin (KURULUM.md).
 */

const S_STUDENTS = 'Öğrenciler';
const S_PARENTS = 'Veliler';
const S_SITES = 'Web Siteleri';
const S_CAL = 'Takvim';
const S_NOTICES = 'Bildirimler';
const S_REG = 'Kayıtlar';
const S_LOG = 'Giriş Kayıtları';
const S_DAILY = 'Günlük Süre';

const TZ = 'Europe/Istanbul';
const NO_RE = /^[0-9A-Z-]{3,40}$/;
const MAX_SECONDS_PER_CALL = 7200;
const FAIL_LIMIT = 100;
const FAIL_WINDOW_SEC = 600;
const LOG_SCAN_ROWS = 4000;    // velinin gördüğü giriş geçmişi için okunan son satır sayısı
const DAILY_SCAN_ROWS = 3000;
const CAL_PAST_DAYS = 62;
const CAL_FUTURE_DAYS = 190;
const SPREADSHEET_ID = '';     // E-tabloya BAĞLI betikte boş bırakın

const ROLE = { s: 'Öğrenci', p: 'Veli' };

// ------------------------------------------------------------------ giriş noktaları

function doGet() {
  try {
    ensureSheets_();
    return json_({ ok: true, service: 'oku', sheets: 'ok', zoom: !!zoomCreds_() });
  } catch (err) {
    return json_({ ok: false, service: 'oku', error: String(err && err.message ? err.message : err) });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  let locked = false;
  try {
    lock.waitLock(20000);
    locked = true;
    const raw = e && e.postData && e.postData.contents ? e.postData.contents : '{}';
    const b = JSON.parse(raw);
    switch (String(b.action)) {
      case 'check': return json_(check_(b));
      case 'register': return json_(register_(b));
      case 'login': return json_(login_(b));
      case 'usage': return json_(usage_(b));
      case 'home': return json_(home_(b));
      case 'zoomSig': return json_(zoomSig_(b));
      default: return json_({ ok: false, error: 'bad_request' });
    }
  } catch (err) {
    return json_({ ok: false, error: 'server' });
  } finally {
    if (locked) lock.releaseLock();
  }
}

// ------------------------------------------------------------------ hesap

function role_(b) { return b.role === 'p' ? 'p' : 's'; }

/** Öğrenciler / Veliler sayfasındaki satır: { row, no, name, extra } */
function person_(role, no) {
  const sh = sheet_(role === 'p' ? S_PARENTS : S_STUDENTS);
  const row = findRow_(sh, no, 1);
  if (!row) return null;
  const v = sh.getRange(row, 1, 1, 3).getDisplayValues()[0];
  return { row: row, no: no, name: String(v[1]).trim(), extra: String(v[2]).trim() };
}

function check_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const role = role_(b);
  const no = normNo_(b.no);
  const p = NO_RE.test(no) ? person_(role, no) : null;
  if (!p) {
    addFail_();
    return { ok: false, error: 'invalid' };
  }
  return { ok: true, name: p.name };
}

/** Şifre oluşturulunca: UUID üretir, "Kayıtlar"a yazar (yeniden kurulumda aynı satır güncellenir). */
function register_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const role = role_(b);
  const no = normNo_(b.no);
  const pwdHash = String(b.pwdHash || '');
  if (!/^[0-9a-f]{64}$/.test(pwdHash)) return { ok: false, error: 'bad_request' };
  const p = NO_RE.test(no) ? person_(role, no) : null;
  if (!p) {
    addFail_();
    return { ok: false, error: 'invalid' };
  }
  const uuid = Utilities.getUuid();
  const reg = sheet_(S_REG);
  const now = stamp_();
  let row = findRegRow_(reg, no, role);
  if (row) {
    const r = reg.getRange(row, 3, 1, 2);
    r.setNumberFormat('@');
    r.setValues([[uuid, pwdHash]]);
  } else {
    row = Math.max(reg.getLastRow(), 1) + 1;
    const r = reg.getRange(row, 1, 1, 8);
    r.setNumberFormat('@');
    r.setValues([[no, ROLE[role], uuid, pwdHash, now, '', '0:00:00', '0']]);
  }
  addLog_(no, role, p.name, 'Kayıt');
  touchLogin_(reg, row, now);
  return { ok: true, uuid: uuid, name: p.name };
}

function login_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  touchLogin_(a.reg, a.row, stamp_());
  addLog_(a.no, a.role, a.person.name, 'Giriş');
  return { ok: true };
}

/** Öğrencinin uygulamada geçirdiği süre: toplam (Kayıtlar G) + günlük (Günlük Süre). */
function usage_(b) {
  const a = auth_(b);
  if (!a || a.role !== 's') return { ok: false, error: 'auth' };
  let add = Math.floor(Number(b.seconds));
  if (!(add > 0)) return { ok: false, error: 'bad_request' };
  add = Math.min(add, MAX_SECONDS_PER_CALL);

  const cell = a.reg.getRange(a.row, 7);
  const total = parseDur_(cell.getDisplayValues()[0][0]) + add;
  cell.setNumberFormat('@');
  cell.setValue(fmtDur_(total));

  const day = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const d = sheet_(S_DAILY);
  const last = d.getLastRow();
  let row = 0;
  if (last >= 2) {
    const start = Math.max(2, last - DAILY_SCAN_ROWS + 1);
    const vals = d.getRange(start, 1, last - start + 1, 2).getDisplayValues();
    for (let i = vals.length - 1; i >= 0; i--) {
      if (vals[i][0] === day && normNo_(vals[i][1]) === a.no) { row = start + i; break; }
    }
  }
  if (row) {
    const sec = Number(d.getRange(row, 4).getDisplayValue()) + add;
    const r = d.getRange(row, 3, 1, 2);
    r.setNumberFormat('@');
    r.setValues([[fmtDur_(sec), String(sec)]]);
  } else {
    const r = d.getRange(Math.max(last, 1) + 1, 1, 1, 4);
    r.setNumberFormat('@');
    r.setValues([[day, a.no, fmtDur_(add), String(add)]]);
  }
  return { ok: true, total: total };
}

// ------------------------------------------------------------------ ana sayfa verisi

/**
 * Uygulamanın tek seferde ihtiyaç duyduğu her şey:
 *  öğrenci → { name, links, events, notices }
 *  veli    → { name, children, events, notices }
 */
function home_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const tz = ss_().getSpreadsheetTimeZone() || TZ;
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const out = { ok: true, name: a.person.name, today: today, zoom: !!zoomCreds_() };

  // Kimin hangi etkinlik / bildirimi göreceği için anahtarlar
  let keys;
  if (a.role === 's') {
    keys = [a.no];
    if (a.person.extra) keys.push(normNo_(a.person.extra));
    out.links = links_();
  } else {
    const kids = splitNos_(a.person.extra);
    out.children = children_(kids);
    keys = [a.no];
    out.children.forEach(function (c) {
      keys.push(c.no);
      if (c.group) keys.push(normNo_(c.group));
    });
  }
  out.events = events_(keys, tz);
  out.notices = notices_(a.role, keys, tz, today);
  return out;
}

function links_() {
  const sh = sheet_(S_SITES);
  const last = sh.getLastRow();
  const out = [];
  if (last < 2) return out;
  sh.getRange(2, 1, last - 1, 3).getDisplayValues().forEach(function (r) {
    const url = String(r[1]).trim();
    if (/^https:\/\/\S+$/i.test(url)) out.push({ name: String(r[0]).trim() || url, url: url, desc: String(r[2]).trim() });
  });
  return out;
}

function events_(keys, tz) {
  const sh = sheet_(S_CAL);
  const last = sh.getLastRow();
  const out = [];
  if (last < 2) return out;
  const now = new Date();
  const from = Utilities.formatDate(new Date(now.getTime() - CAL_PAST_DAYS * 864e5), TZ, 'yyyy-MM-dd');
  const to = Utilities.formatDate(new Date(now.getTime() + CAL_FUTURE_DAYS * 864e5), TZ, 'yyyy-MM-dd');
  const vals = sh.getRange(2, 1, last - 1, 7).getValues();
  const disp = sh.getRange(2, 1, last - 1, 7).getDisplayValues();
  for (let i = 0; i < vals.length; i++) {
    const date = toDate_(vals[i][0], disp[i][0], tz);
    const title = String(disp[i][3]).trim();
    if (!date || !title || date < from || date > to) continue;
    if (!targets_(disp[i][6], keys)) continue;
    const zoom = String(disp[i][4]).trim();
    out.push({
      id: 'e' + (i + 2) + '_' + hash_(date + title),
      date: date,
      start: toTime_(vals[i][1], disp[i][1], tz),
      end: toTime_(vals[i][2], disp[i][2], tz),
      title: title,
      zoom: /^https:\/\/([a-z0-9-]+\.)?zoom\.us\//i.test(zoom) ? zoom : '',
      desc: String(disp[i][5]).trim(),
    });
  }
  out.sort(function (x, y) { return (x.date + x.start) < (y.date + y.start) ? -1 : 1; });
  return out;
}

function notices_(role, keys, tz, today) {
  const sh = sheet_(S_NOTICES);
  const last = sh.getLastRow();
  const out = [];
  if (last < 2) return out;
  const vals = sh.getRange(2, 1, last - 1, 5).getValues();
  const disp = sh.getRange(2, 1, last - 1, 5).getDisplayValues();
  for (let i = 0; i < vals.length; i++) {
    const title = String(disp[i][1]).trim();
    const msg = String(disp[i][2]).trim();
    if (!title && !msg) continue;
    const date = toDate_(vals[i][0], disp[i][0], tz) || '';
    if (date && date > today) continue; // ileri tarihli bildirim o gün gelir
    const until = toDate_(vals[i][4], disp[i][4], tz);
    if (until && until < today) continue;
    const who = normNo_(disp[i][3]);
    let show;
    if (!who || who === 'HERKES') show = true;
    else if (who === 'ÖĞRENCİ' || who === 'ÖĞRENCI' || who === 'OGRENCI') show = role === 's';
    else if (who === 'VELİ' || who === 'VELI') show = role === 'p';
    else show = targets_(disp[i][3], keys);
    if (!show) continue;
    out.push({ id: 'n' + (i + 2) + '_' + hash_(date + title + msg), date: date, title: title || msg.slice(0, 40), msg: msg });
  }
  out.sort(function (x, y) { return x.date < y.date ? 1 : -1; }); // en yeni üstte
  return out.slice(0, 50);
}

/** Velinin çocukları: kayıt durumu, son giriş, süreler, son girişler. */
function children_(nos) {
  const out = [];
  if (!nos.length) return out;
  const st = sheet_(S_STUDENTS);
  const reg = sheet_(S_REG);
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const weekAgo = Utilities.formatDate(new Date(Date.now() - 6 * 864e5), TZ, 'yyyy-MM-dd');

  // Günlük süreler (son satırlar)
  const daily = {};
  const d = sheet_(S_DAILY);
  const dl = d.getLastRow();
  if (dl >= 2) {
    const start = Math.max(2, dl - DAILY_SCAN_ROWS + 1);
    d.getRange(start, 1, dl - start + 1, 4).getDisplayValues().forEach(function (r) {
      if (r[0] < weekAgo) return;
      const k = normNo_(r[1]);
      if (!daily[k]) daily[k] = { today: 0, week: 0 };
      const sec = Number(r[3]) || 0;
      daily[k].week += sec;
      if (r[0] === today) daily[k].today += sec;
    });
  }

  // Giriş geçmişi (son satırlar, en yeni önce)
  const logs = {};
  const lg = sheet_(S_LOG);
  const ll = lg.getLastRow();
  if (ll >= 2) {
    const start = Math.max(2, ll - LOG_SCAN_ROWS + 1);
    const rows = lg.getRange(start, 1, ll - start + 1, 5).getDisplayValues();
    for (let i = rows.length - 1; i >= 0; i--) {
      if (rows[i][2] !== ROLE.s) continue;
      const k = normNo_(rows[i][1]);
      if (!logs[k]) logs[k] = [];
      if (logs[k].length < 15) logs[k].push({ at: rows[i][0], ev: rows[i][4] });
    }
  }

  nos.forEach(function (no) {
    const srow = findRow_(st, no, 1);
    if (!srow) {
      out.push({ no: no, name: '', found: false });
      return;
    }
    const sv = st.getRange(srow, 1, 1, 3).getDisplayValues()[0];
    const c = { no: no, name: String(sv[1]).trim(), group: String(sv[2]).trim(), found: true, registered: false };
    const rrow = findRegRow_(reg, no, 's');
    if (rrow) {
      const rv = reg.getRange(rrow, 5, 1, 4).getDisplayValues()[0];
      c.registered = true;
      c.since = rv[0];
      c.last = rv[1];
      c.total = parseDur_(rv[2]);
      c.count = Number(rv[3]) || 0;
    }
    c.today = (daily[no] || {}).today || 0;
    c.week = (daily[no] || {}).week || 0;
    c.logins = logs[no] || [];
    out.push(c);
  });
  return out;
}

// ------------------------------------------------------------------ Zoom

/**
 * Zoom Meeting SDK imzası (JWT, HS256). Gizli anahtar yalnızca sunucuda durur.
 * Yalnızca bu Zoom hesabının toplantılarına katılmak için kullanılır.
 */
function zoomSig_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const creds = zoomCreds_();
  if (!creds) return { ok: false, error: 'no_zoom' };
  const mn = String(b.meetingNumber || '').replace(/\D/g, '');
  if (!/^\d{9,12}$/.test(mn)) return { ok: false, error: 'bad_request' };
  const iat = Math.floor(Date.now() / 1000) - 30;
  const exp = iat + 2 * 3600;
  const head = b64url_(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64url_(JSON.stringify({
    appKey: creds.id, sdkKey: creds.id, mn: mn, role: 0, iat: iat, exp: exp, tokenExp: exp,
  }));
  const sig = Utilities.computeHmacSha256Signature(head + '.' + body, creds.secret);
  return { ok: true, signature: head + '.' + body + '.' + b64urlBytes_(sig), name: a.person.name };
}

function zoomCreds_() {
  const p = PropertiesService.getScriptProperties();
  const id = String(p.getProperty('ZOOM_CLIENT_ID') || '').trim();
  const secret = String(p.getProperty('ZOOM_CLIENT_SECRET') || '').trim();
  return id && secret ? { id: id, secret: secret } : null;
}

function b64url_(s) { return Utilities.base64EncodeWebSafe(s, Utilities.Charset.UTF_8).replace(/=+$/, ''); }
function b64urlBytes_(bytes) { return Utilities.base64EncodeWebSafe(bytes).replace(/=+$/, ''); }

// ------------------------------------------------------------------ yardımcılar

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function ss_() {
  return SPREADSHEET_ID ? SpreadsheetApp.openById(SPREADSHEET_ID) : SpreadsheetApp.getActiveSpreadsheet();
}

function key_(name) { return String(name).trim().toLocaleLowerCase('tr'); }

function sheet_(name) {
  const want = key_(name);
  let all = ss_().getSheets();
  for (let i = 0; i < all.length; i++) if (key_(all[i].getName()) === want) return all[i];
  ensureSheets_();
  all = ss_().getSheets();
  for (let j = 0; j < all.length; j++) if (key_(all[j].getName()) === want) return all[j];
  throw new Error('Sayfa bulunamadı: ' + name);
}

function normNo_(v) {
  return String(v === null || v === undefined ? '' : v).trim().toLocaleUpperCase('tr').replace(/\s+/g, ' ');
}

function splitNos_(s) {
  return String(s || '').split(/[,;\s]+/).map(normNo_).filter(function (x) { return NO_RE.test(x); })
    .filter(function (x, i, arr) { return arr.indexOf(x) === i; }).slice(0, 20);
}

/** Virgüllü "Kimler" hücresi boşsa herkes; doluysa anahtarlardan biri geçmeli. */
function targets_(cell, keys) {
  const raw = String(cell || '').trim();
  if (!raw || normNo_(raw) === 'HERKES') return true;
  const list = raw.split(/[,;]+/).map(normNo_).filter(String);
  for (let i = 0; i < list.length; i++) if (keys.indexOf(list[i]) >= 0) return true;
  return false;
}

function findRow_(sheet, no, col) {
  const last = sheet.getLastRow();
  if (last < 2) return 0;
  const vals = sheet.getRange(2, col || 1, last - 1, 1).getDisplayValues();
  for (let i = 0; i < vals.length; i++) if (normNo_(vals[i][0]) === no) return i + 2;
  return 0;
}

function findRegRow_(reg, no, role) {
  const last = reg.getLastRow();
  if (last < 2) return 0;
  const vals = reg.getRange(2, 1, last - 1, 2).getDisplayValues();
  for (let i = 0; i < vals.length; i++) {
    if (normNo_(vals[i][0]) === no && String(vals[i][1]).trim() === ROLE[role]) return i + 2;
  }
  return 0;
}

function auth_(b) {
  const role = role_(b);
  const no = normNo_(b.no);
  const uuid = String(b.uuid || '');
  const token = String(b.token || '');
  if (!no || !uuid || !token) return null;
  const reg = sheet_(S_REG);
  const row = findRegRow_(reg, no, role);
  if (!row) return null;
  const rec = reg.getRange(row, 3, 1, 2).getDisplayValues()[0];
  if (String(rec[0]) !== uuid || String(rec[1]) !== token) return null;
  const person = person_(role, no);
  if (!person) return null; // listeden çıkarılmış
  return { reg: reg, row: row, no: no, role: role, person: person };
}

function touchLogin_(reg, row, now) {
  const r = reg.getRange(row, 6, 1, 3);
  const v = r.getDisplayValues()[0];
  r.setNumberFormat('@');
  r.setValues([[now, v[1] || '0:00:00', String((Number(v[2]) || 0) + 1)]]);
}

function addLog_(no, role, name, ev) {
  const lg = sheet_(S_LOG);
  const r = lg.getRange(Math.max(lg.getLastRow(), 1) + 1, 1, 1, 5);
  r.setNumberFormat('@');
  r.setValues([[stamp_(), no, ROLE[role], name, ev]]);
}

function stamp_() { return Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm:ss'); }

/** Hücredeki tarihi "yyyy-MM-dd" yapar (tarih hücresi ya da 27.11.2026 / 2026-11-27 yazısı). */
function toDate_(v, shown, tz) {
  if (v instanceof Date && !isNaN(v)) return Utilities.formatDate(v, tz, 'yyyy-MM-dd');
  const s = String(shown || v || '').trim();
  let m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(s);
  if (m) return m[1] + '-' + pad_(m[2]) + '-' + pad_(m[3]);
  m = /^(\d{1,2})[./](\d{1,2})[./](\d{4})/.exec(s);
  if (m) return m[3] + '-' + pad_(m[2]) + '-' + pad_(m[1]);
  return '';
}

/** Hücredeki saati "HH:mm" yapar; boşsa "". */
function toTime_(v, shown, tz) {
  if (v instanceof Date && !isNaN(v)) return Utilities.formatDate(v, tz, 'HH:mm');
  const s = String(shown || v || '').trim();
  const m = /^(\d{1,2})[:.](\d{2})/.exec(s);
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) return '';
  return pad_(m[1]) + ':' + m[2];
}

function pad_(x) { x = String(x); return x.length < 2 ? '0' + x : x; }

function hash_(s) {
  let h = 0;
  s = String(s);
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return (h >>> 0).toString(36);
}

function parseDur_(s) {
  const m = /^(\d+):(\d{1,2}):(\d{1,2})$/.exec(String(s).trim());
  return m ? Number(m[1]) * 3600 + Number(m[2]) * 60 + Number(m[3]) : 0;
}

function fmtDur_(total) {
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h + ':' + (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
}

function tooManyFails_() {
  return Number(CacheService.getScriptCache().get('fails') || 0) >= FAIL_LIMIT;
}

function addFail_() {
  const c = CacheService.getScriptCache();
  c.put('fails', String(Number(c.get('fails') || 0) + 1), FAIL_WINDOW_SEC);
}

// ------------------------------------------------------------------ ilk kurulum

/** Bir kez elle çalıştırabilirsiniz (Çalıştır ▸ kurulum); adresi tarayıcıda açmak da aynı işi yapar. */
function kurulum() {
  ensureSheets_();
}

function ensureSheets_() {
  const ss = ss_();
  if (!ss) throw new Error('Bu betik bir E-Tabloya bağlı değil. E-Tabloyu açıp Uzantılar > Apps Script ile açılan betiğe yapıştırın.');
  const today = Utilities.formatDate(new Date(), TZ, 'dd.MM.yyyy');
  const defs = [
    [S_STUDENTS, ['Öğrenci No', 'Ad Soyad', 'Sınıf / Grup (isteğe bağlı)'], null, true],
    [S_PARENTS, ['Veli No', 'Ad Soyad', "Öğrenci No'ları (virgülle)"], null, true],
    [S_SITES, ['Site adı', 'Link (https://...)', 'Açıklama'], null, false],
    [S_CAL, ['Tarih', 'Başlangıç', 'Bitiş', 'Ders / Başlık', 'Zoom linki', 'Açıklama', 'Kimler (boş = herkes)'],
      [today, '20:00', '21:00', 'Örnek ders (silebilirsiniz)',
        'https://us05web.zoom.us/j/88170373640?pwd=NGLhQOdez5wVLIw4pgJygAVlDCxy2k.1', 'Deneme için örnek satır', ''],
      false],
    [S_NOTICES, ['Tarih', 'Başlık', 'Mesaj', 'Kime (boş = herkes)', 'Son gün (isteğe bağlı)'],
      [today, 'Hoş geldiniz', 'Oku ve Okut kullanıma açıldı. (Örnek bildirim, silebilirsiniz.)', '', ''], false],
    [S_REG, ['No', 'Rol', 'UUID', 'Şifre (özet)', 'Kayıt tarihi', 'Son giriş', 'Toplam süre (s:dk:sn)', 'Giriş sayısı'], null, true],
    [S_LOG, ['Tarih-saat', 'No', 'Rol', 'Ad', 'Olay'], null, true],
    [S_DAILY, ['Tarih', 'Öğrenci No', 'Süre (s:dk:sn)', 'Saniye'], null, true],
  ];
  defs.forEach(function (d) {
    let sh = null;
    const all = ss.getSheets();
    for (let i = 0; i < all.length; i++) if (key_(all[i].getName()) === key_(d[0])) sh = all[i];
    const created = !sh;
    if (!sh) sh = ss.insertSheet(d[0]);
    if (String(sh.getRange(1, 1).getDisplayValue() || '').length === 0) {
      sh.getRange(1, 1, 1, d[1].length).setValues([d[1]]);
    }
    if (created) {
      // Numaralar sıfırla başlayabilir: A sütunu düz metin
      if (d[3]) sh.getRange(2, 1, Math.max(sh.getMaxRows() - 1, 1), 1).setNumberFormat('@');
      if (d[2] && sh.getLastRow() < 2) sh.getRange(2, 1, 1, d[2].length).setValues([d[2]]);
    }
  });
  // Boş ilk sayfayı ("Sayfa1"/"Sheet1") kaldır
  ss.getSheets().forEach(function (s) {
    if (/^(sayfa|sheet)1$/i.test(s.getName()) && s.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(s);
  });
}
