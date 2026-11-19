/**
 * MEYVEM - Google Apps Script sunucu kodu
 * ------------------------------------------------------------------
 * Uygulama (Android ve Windows) bu betikle konuşur. Üç sayfa kullanılır:
 *
 *  1) "yetkili kişiler giriş no"
 *       A: giriş numaraları (2. satırdan itibaren; 1. satır başlık)
 *       B: Gmail adresi (kullanıcı kaydolunca otomatik yazılır)
 *  2) "yoklama"  (uygulama kendisi doldurur)
 *       A: giriş no | B: UUID | C: şifre ÖZETİ | D: uygulamada geçen süre (s:dk:sn)
 *       E ve sonrası: son giriş tarihleri (soldan sağa, en yeni sağda)
 *  3) "web site linkleri"
 *       A: web sitenin kısa tanımı | B: orijinal link (https://...) | C: açıklama
 *
 * KURULUM: README için apps_script/KURULUM.md dosyasına bakın.
 * Ayrıca bir kez `kurulum` fonksiyonunu çalıştırırsanız eksik sayfalar ve
 * başlıklar otomatik oluşturulur (var olan hiçbir şeyin üzerine yazmaz).
 */

const SHEET_AUTH = 'yetkili kişiler giriş no';
const SHEET_ATT = 'yoklama';
const SHEET_LINKS = 'web site linkleri';

const TZ = 'Europe/Istanbul';
const FIRST_LOGIN_COL = 5;          // E sütunu
const MAX_LOGINS = 100;             // Bir kişi için tutulan en son giriş sayısı
const MAX_SECONDS_PER_CALL = 7200;  // Tek istekte eklenebilecek en çok süre (2 saat)
const FAIL_LIMIT = 100;             // Pencere içinde izin verilen hatalı deneme sayısı
const FAIL_WINDOW_SEC = 600;        // 10 dakika
const SPREADSHEET_ID = '';          // E-tabloya BAĞLI betikte boş bırakın

// ------------------------------------------------------------------ giriş noktaları

function doGet() {
  return json_({ ok: true, service: 'meyvem' });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  let locked = false;
  try {
    lock.waitLock(20000);
    locked = true;
    const raw = e && e.postData && e.postData.contents ? e.postData.contents : '{}';
    const body = JSON.parse(raw);
    switch (String(body.action)) {
      case 'check': return json_(check_(body));
      case 'register': return json_(register_(body));
      case 'gmail': return json_(gmail_(body));
      case 'login': return json_(login_(body));
      case 'usage': return json_(usage_(body));
      case 'links': return json_(links_(body));
      default: return json_({ ok: false, error: 'bad_request' });
    }
  } catch (err) {
    return json_({ ok: false, error: 'server' });
  } finally {
    if (locked) lock.releaseLock();
  }
}

// ------------------------------------------------------------------ işlemler

/** Giriş numarası yetkili listede var mı? */
function check_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const no = normNo_(b.no);
  if (!/^[0-9A-Z-]{4,40}$/.test(no) || !findRow_(sheet_(SHEET_AUTH), no)) {
    addFail_();
    return { ok: false, error: 'invalid' };
  }
  return { ok: true };
}

/**
 * Şifre kaydedilince çağrılır: kişiye UUID üretir, "yoklama" sayfasına satır yazar.
 * Aynı numara tekrar kaydolursa (uygulama silinip yeniden kurulduysa) aynı satır
 * yeni UUID ve şifre özetiyle güncellenir; toplam süre ve giriş tarihleri korunur.
 */
function register_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const no = normNo_(b.no);
  const pwdHash = String(b.pwdHash || '');
  if (!/^[0-9a-f]{64}$/.test(pwdHash)) return { ok: false, error: 'bad_request' };
  if (!/^[0-9A-Z-]{4,40}$/.test(no) || !findRow_(sheet_(SHEET_AUTH), no)) {
    addFail_();
    return { ok: false, error: 'invalid' };
  }

  const uuid = Utilities.getUuid();
  const att = sheet_(SHEET_ATT);
  let row = findRow_(att, no);
  if (row) {
    const r = att.getRange(row, 2, 1, 2);
    r.setNumberFormat('@');
    r.setValues([[uuid, pwdHash]]);
  } else {
    row = Math.max(att.getLastRow(), 1) + 1;
    const r = att.getRange(row, 1, 1, 4);
    r.setNumberFormat('@');
    r.setValues([[no, uuid, pwdHash, '0:00:00']]);
  }
  addLogin_(att, row);
  return { ok: true, uuid: uuid };
}

/** Gmail adresini yetkili listedeki numaranın yanına (B sütunu) yazar. */
function gmail_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const gmail = String(b.gmail || '').trim().toLowerCase();
  if (!/^[a-z0-9._%+-]+@(gmail|googlemail)\.com$/.test(gmail)) {
    return { ok: false, error: 'bad_request' };
  }
  const authSheet = sheet_(SHEET_AUTH);
  const row = findRow_(authSheet, a.no);
  if (!row) return { ok: false, error: 'auth' };
  const cell = authSheet.getRange(row, 2);
  cell.setNumberFormat('@');
  cell.setValue(gmail);
  return { ok: true };
}

/** Uygulamaya her girişte çağrılır: tarihi E sütunundan sonra yan yana ekler. */
function login_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  addLogin_(a.att, a.row);
  return { ok: true };
}

/** Uygulamada geçen süreyi (saniye) D sütunundaki toplama ekler. */
function usage_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  let add = Math.floor(Number(b.seconds));
  if (!(add > 0)) return { ok: false, error: 'bad_request' };
  add = Math.min(add, MAX_SECONDS_PER_CALL);

  const cell = a.att.getRange(a.row, 4);
  const total = parseDur_(cell.getDisplayValues()[0][0]) + add;
  cell.setNumberFormat('@');
  cell.setValue(fmtDur_(total));
  return { ok: true, total: total };
}

/** "web site linkleri" sayfasındaki listeyi döndürür (yalnızca https adresler). */
function links_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const sh = sheet_(SHEET_LINKS);
  const last = sh.getLastRow();
  const out = [];
  if (last >= 2) {
    const rows = sh.getRange(2, 1, last - 1, 3).getDisplayValues();
    rows.forEach(function (r) {
      const url = String(r[1]).trim();
      if (/^https:\/\/\S+$/i.test(url)) {
        out.push({ name: String(r[0]).trim() || url, url: url, desc: String(r[2]).trim() });
      }
    });
  }
  return { ok: true, links: out };
}

// ------------------------------------------------------------------ yardımcılar

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function ss_() {
  return SPREADSHEET_ID ? SpreadsheetApp.openById(SPREADSHEET_ID) : SpreadsheetApp.getActiveSpreadsheet();
}

function sheet_(name) {
  const want = name.trim().toLowerCase();
  const all = ss_().getSheets();
  for (let i = 0; i < all.length; i++) {
    if (all[i].getName().trim().toLowerCase() === want) return all[i];
  }
  throw new Error('Sayfa bulunamadı: ' + name);
}

function normNo_(v) {
  return String(v === null || v === undefined ? '' : v).trim().toUpperCase();
}

/** A sütununda (2. satırdan itibaren) numarayı arar; satır numarasını döndürür, yoksa 0. */
function findRow_(sheet, no) {
  const last = sheet.getLastRow();
  if (last < 2) return 0;
  const vals = sheet.getRange(2, 1, last - 1, 1).getDisplayValues();
  for (let i = 0; i < vals.length; i++) {
    if (normNo_(vals[i][0]) === no) return i + 2;
  }
  return 0;
}

/** numara + UUID + şifre özeti üçlüsünü "yoklama" sayfasıyla doğrular. */
function auth_(b) {
  const no = normNo_(b.no);
  const uuid = String(b.uuid || '');
  const token = String(b.token || '');
  if (!no || !uuid || !token) return null;
  const att = sheet_(SHEET_ATT);
  const row = findRow_(att, no);
  if (!row) return null;
  const rec = att.getRange(row, 2, 1, 2).getDisplayValues()[0];
  if (String(rec[0]) !== uuid || String(rec[1]) !== token) return null;
  return { att: att, row: row, no: no };
}

/** Giriş tarihini E sütunundan sonrasına ekler; en fazla MAX_LOGINS tarih tutulur. */
function addLogin_(att, row) {
  const stamp = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm:ss');
  const lastCol = att.getLastColumn();
  let logins = [];
  if (lastCol >= FIRST_LOGIN_COL) {
    logins = att.getRange(row, FIRST_LOGIN_COL, 1, lastCol - FIRST_LOGIN_COL + 1)
      .getDisplayValues()[0]
      .filter(function (x) { return String(x).length > 0; });
  }
  logins.push(stamp);
  if (logins.length > MAX_LOGINS) logins = logins.slice(logins.length - MAX_LOGINS);
  const rng = att.getRange(row, FIRST_LOGIN_COL, 1, logins.length);
  rng.setNumberFormat('@');
  rng.setValues([logins]);
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

// Numara tahmin etme / istek yağdırma denemelerini yavaşlatır (herkes için ortak sayaç)
function tooManyFails_() {
  return Number(CacheService.getScriptCache().get('fails') || 0) >= FAIL_LIMIT;
}

function addFail_() {
  const c = CacheService.getScriptCache();
  c.put('fails', String(Number(c.get('fails') || 0) + 1), FAIL_WINDOW_SEC);
}

// ------------------------------------------------------------------ ilk kurulum

/**
 * Bir kez elle çalıştırın (Çalıştır ▸ kurulum). Eksik sayfaları ve başlıkları
 * oluşturur; var olan sayfa ve verilerin üzerine yazmaz.
 */
function kurulum() {
  const ss = ss_();
  const defs = [
    [SHEET_AUTH, ['giriş no', 'gmail']],
    [SHEET_ATT, ['yetkili kişi giriş no', 'UUID', 'şifre (özet)', 'uygulamada geçen süre (s:dk:sn)', 'son giriş tarihleri →']],
    [SHEET_LINKS, ['web sitenin kısa tanımı', 'web sitenin orijinal linki', 'siteye dair açıklama']],
  ];
  defs.forEach(function (d) {
    let sh = null;
    const all = ss.getSheets();
    for (let i = 0; i < all.length; i++) {
      if (all[i].getName().trim().toLowerCase() === d[0]) sh = all[i];
    }
    if (!sh) sh = ss.insertSheet(d[0]);
    if (String(sh.getRange(1, 1).getDisplayValue() || '').length === 0) {
      sh.getRange(1, 1, 1, d[1].length).setValues([d[1]]);
    }
  });
  // Numaralar sıfırla başladığı için A sütununu düz metin yap
  [SHEET_AUTH, SHEET_ATT].forEach(function (n) {
    const sh = sheet_(n);
    sh.getRange(2, 1, Math.max(sh.getMaxRows() - 1, 1), 1).setNumberFormat('@');
  });
}
