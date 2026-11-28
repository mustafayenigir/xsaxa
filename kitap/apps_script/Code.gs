/**
 * KİTAP / ÜYE YÖNETİM - Google Apps Script sunucu kodu
 * ------------------------------------------------------------------
 * "Kitap" uygulaması ve "Üye Yönetim" uygulaması bu betikle konuşur.
 * E-Tabloda Uzantılar ▸ Apps Script ile açılan betiğe yapıştırın, sonra
 * Dağıt ▸ Dağıtımları yönet ▸ Düzenle ▸ Sürüm: Yeni sürüm ▸ Dağıt.
 *
 * Sayfalar (yoksa kendiliğinden oluşturulur; var olanlara dokunulmaz):
 *
 *  SİZİN DOLDURDUKLARINIZ
 *  "Kişiler"       A: Yetki kodu | B: Ad Soyad | C: Grup (isteğe bağlı) | D: Şifre | E: Durum (Aktif / Pasif)
 *                  F: Cihaz | G: Kayıt | H: Son giriş | I: Toplam süre | J: Giriş sayısı   (F–J uygulama yazar)
 *                  K: Erişim (Görebilir / Göremez) — Göremez: Kur'an meali, içerik ve Zoom linkleri gizlenir
 *  "Yöneticiler"   Kişiler ile aynı sütunlar; Üye Yönetim uygulamasına girebilecek kişiler.
 *  "İçerikler"     A: Ad | B: Link (https://...) | C: Açıklama
 *  "Eğitimler"     A: Tarih | B: Başlangıç | C: Bitiş | D: Eğitim adı | E: Zoom linki |
 *                  F: Eğitim metni (emoji olabilir) | G: Kimler (boş = herkes; kod veya grup, virgülle)
 *  "Bildirimler"   A: Tarih | B: Başlık | C: Mesaj | D: Kime (boş = herkes) | E: Son gün (isteğe bağlı)
 *  "Kuran"         A: Sure no | B: Ayet no | C: Meal (Diyanet İşleri - yeni)
 *
 *  UYGULAMANIN DOLDURDUKLARI (elle değiştirmeyin)
 *  "Kitap Notları"  A: Kod | B: Ad | C: Sure | D: Ayet | E: Vurgu | F: Not | G: Tarih
 *  "Kitap Okuma"    A: Kod | B: Ad | C: Sure | D: Sure adı | E: Süre | F: Saniye | G: Son okuma
 *  "Eğitim Notları" A: Tarih-saat | B: Kod | C: Ad | D: Eğitim tarihi | E: Eğitim | F: Not
 *  "Giriş Kayıtları" A: Tarih-saat | B: Kod | C: Ad | D: Uygulama | E: Olay
 *  "Günlük Süre"    A: Tarih | B: Kod | C: Ad | D: Süre | E: Saniye
 *  "Kişisel Notlar" A: Kod | B: Ad | C: Tarih | D: Saat | E: Not | F: Yazıldı   (kişinin kendi planları/hatırlatıcıları)
 *  "Hata Raporları" A: Tarih-saat | B: Uygulama | C: Kod | D: Ad | E: Sürüm | F: Cihaz | G: Rapor
 *
 * Şifreler "Kişiler" sayfasının D sütununda AÇIK yazar (şifremi unuttum yok; siz söylersiniz).
 * Şifreyi değiştirirseniz kişi bir sonraki açılışta yeni şifreyle girer.
 * "Cihaz" hücresini silerseniz kişi kodunu ve şifresini yeni telefonda yeniden girebilir
 * (şifresini bilen kişi zaten kendisi geçebilir).
 */

const S_PEOPLE = 'Kişiler';
const S_ADMINS = 'Yöneticiler';
const S_CONTENT = 'İçerikler';
const S_EDU = 'Eğitimler';
const S_NOTICES = 'Bildirimler';
const S_QURAN = 'Kuran';
const S_QNOTES = 'Kitap Notları';
const S_QREAD = 'Kitap Okuma';
const S_ENOTES = 'Eğitim Notları';
const S_LOG = 'Giriş Kayıtları';
const S_DAILY = 'Günlük Süre';
const S_PLANS = 'Kişisel Notlar';
const S_CRASH = 'Hata Raporları';

// Kişiler / Yöneticiler sütunları
const C_CODE = 1, C_NAME = 2, C_GROUP = 3, C_PWD = 4, C_STATUS = 5, C_DEVICE = 6, C_SINCE = 7, C_LAST = 8, C_TOTAL = 9, C_COUNT = 10, C_ACCESS = 11;
const P_COLS = 11;

const TZ = 'Europe/Istanbul';
const MAX_SECONDS_PER_CALL = 7200;
const FAIL_LIMIT = 100;
const FAIL_WINDOW_SEC = 600;
const DAILY_SCAN_ROWS = 6000;
const LOG_SCAN_ROWS = 6000;
const EDU_PAST_DAYS = 62;
const EDU_FUTURE_DAYS = 365;
const NOTE_MAX = 1000;
const SPREADSHEET_ID = '';     // E-tabloya BAĞLI betikte boş bırakın

const COLORS = ['', 'Sarı', 'Yeşil', 'Mavi'];
const SURELER = ["Fâtiha", "Bakara", "Âl-i İmrân", "Nisâ", "Mâide", "En'âm", "A'râf", "Enfâl", "Tevbe", "Yûnus", "Hûd", "Yûsuf", "Ra'd", "İbrâhîm", "Hicr", "Nahl", "İsrâ", "Kehf", "Meryem", "Tâhâ", "Enbiyâ", "Hac", "Mü'minûn", "Nûr", "Furkân", "Şuarâ", "Neml", "Kasas", "Ankebût", "Rûm", "Lokmân", "Secde", "Ahzâb", "Sebe'", "Fâtır", "Yâsîn", "Sâffât", "Sâd", "Zümer", "Mü'min", "Fussilet", "Şûrâ", "Zuhruf", "Duhân", "Câsiye", "Ahkâf", "Muhammed", "Fetih", "Hucurât", "Kâf", "Zâriyât", "Tûr", "Necm", "Kamer", "Rahmân", "Vâkıa", "Hadîd", "Mücâdele", "Haşr", "Mümtehine", "Saff", "Cuma", "Münâfikûn", "Teğâbün", "Talâk", "Tahrîm", "Mülk", "Kalem", "Hâkka", "Meâric", "Nûh", "Cin", "Müzzemmil", "Müddessir", "Kıyâme", "İnsan", "Mürselât", "Nebe'", "Nâziât", "Abese", "Tekvîr", "İnfitâr", "Mutaffifîn", "İnşikâk", "Bürûc", "Târık", "A'lâ", "Gâşiye", "Fecr", "Beled", "Şems", "Leyl", "Duhâ", "İnşirâh", "Tîn", "Alak", "Kadir", "Beyyine", "Zilzâl", "Âdiyât", "Kâria", "Tekâsür", "Asr", "Hümeze", "Fîl", "Kureyş", "Mâûn", "Kevser", "Kâfirûn", "Nasr", "Tebbet", "İhlâs", "Felak", "Nâs"];

// ------------------------------------------------------------------ giriş noktaları

function doGet() {
  try {
    ensureSheets_();
    const q = sheet_(S_QURAN);
    return json_({ ok: true, service: 'kitap', sheets: 'ok', kuranSatir: Math.max(q.getLastRow() - 1, 0) });
  } catch (err) {
    return json_({ ok: false, service: 'kitap', error: String(err && err.message ? err.message : err) });
  }
}

const READ_ONLY = { home: 1, kuran: 1, eduNotes: 1, adminData: 1, person: 1, eventNotes: 1, check: 1, crashes: 1 };

function doPost(e) {
  let b;
  try {
    const raw = e && e.postData && e.postData.contents ? e.postData.contents : '{}';
    b = JSON.parse(raw);
  } catch (err) {
    return json_({ ok: false, error: 'bad_request' });
  }
  const action = String(b.action || '');
  // Okuma işlemleri kilitsiz (hızlı); yazma işlemleri sırayla
  const lock = READ_ONLY[action] ? null : LockService.getScriptLock();
  let locked = false;
  try {
    if (lock) { lock.waitLock(25000); locked = true; }
    return json_(route_(action, b));
  } catch (err) {
    return json_({ ok: false, error: 'server', detail: String(err && err.message ? err.message : err).slice(0, 200) });
  } finally {
    if (locked) lock.releaseLock();
  }
}

function route_(action, b) {
  switch (action) {
    // hesap (iki uygulama)
    case 'check': return check_(b);
    case 'register': return register_(b);
    case 'relogin': return relogin_(b);
    case 'login': return login_(b);
    // Kitap uygulaması
    case 'usage': return usage_(b);
    case 'home': return home_(b);
    case 'kuran': return kuran_(b);
    case 'mark': return mark_(b);
    case 'note': return note_(b);
    case 'readTime': return readTime_(b);
    case 'eduNote': return eduNote_(b);
    case 'eduNoteDelete': return eduNoteDelete_(b);
    case 'eduNotes': return eduNotes_(b);
    case 'planSave': return planSave_(b);
    case 'planDelete': return planDelete_(b);
    case 'crash': return crash_(b);
    // Üye Yönetim uygulaması
    case 'adminData': return adminData_(b);
    case 'person': return personDetail_(b);
    case 'personSave': return personSave_(b);
    case 'eventSave': return eventSave_(b);
    case 'eventDelete': return eventDelete_(b);
    case 'eventNotes': return eventNotes_(b);
    case 'noticeSave': return noticeSave_(b);
    case 'noticeDelete': return noticeDelete_(b);
    case 'access': return access_(b);
    case 'crashes': return crashes_(b);
    default: return { ok: false, error: 'bad_request' };
  }
}

// ------------------------------------------------------------------ hesap

function isAdminApp_(b) { return b.app === 'y'; }
function peopleSheet_(b) { return sheet_(isAdminApp_(b) ? S_ADMINS : S_PEOPLE); }
function appLabel_(b) { return isAdminApp_(b) ? 'Üye Yönetim' : 'Kitap'; }

function readPerson_(sh, row) {
  const v = sh.getRange(row, 1, 1, P_COLS).getDisplayValues()[0];
  return {
    row: row, code: normCode_(v[0]), name: String(v[1]).trim(), group: String(v[2]).trim(), pwd: String(v[3]),
    passive: isPassive_(v[4]), device: String(v[5]).trim(), since: v[6], last: v[7], total: parseDur_(v[8]), count: Number(v[9]) || 0,
    restricted: isRestricted_(v[10]),
  };
}

/** "Göremez" (ya da Hayır/Kapalı) yazıyorsa Kur'an meali ve linkler gizlenir. */
function isRestricted_(s) {
  const t = normCode_(s).replace(/Ö/g, 'O');
  return t === 'GOREMEZ' || t === 'HAYIR' || t === 'KAPALI' || t === 'YOK';
}

function isPassive_(s) {
  const t = normCode_(s);
  return t === 'PASIF' || t === 'HAYIR' || t === 'KAPALI';
}

/** 1) Kod Excel'de var mı? */
function check_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const code = normCode_(b.code);
  const sh = peopleSheet_(b);
  const row = code.length >= 2 ? findRow_(sh, code, C_CODE) : 0;
  if (!row) { addFail_(); return { ok: false, error: 'invalid' }; }
  const p = readPerson_(sh, row);
  if (p.passive) return { ok: false, error: 'passive' };
  return { ok: true, name: p.name, hasPassword: p.pwd.length > 0 };
}

/** 2a) İlk kez: şifre belirler (Excel'e açık yazılır), cihaz bağlanır. */
function register_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const code = normCode_(b.code);
  const pwd = String(b.pwd || '');
  if (pwd.length < 4 || pwd.length > 40) return { ok: false, error: 'bad_password' };
  const sh = peopleSheet_(b);
  const row = code.length >= 2 ? findRow_(sh, code, C_CODE) : 0;
  if (!row) { addFail_(); return { ok: false, error: 'invalid' }; }
  const p = readPerson_(sh, row);
  if (p.passive) return { ok: false, error: 'passive' };
  if (p.pwd.length > 0) return { ok: false, error: 'has_password' };
  const uuid = Utilities.getUuid();
  const now = stamp_();
  setText_(sh.getRange(row, C_PWD), pwd);
  setText_(sh.getRange(row, C_DEVICE), uuid);
  if (!String(p.since).trim()) setText_(sh.getRange(row, C_SINCE), now);
  touchLogin_(sh, row, now);
  addLog_(code, p.name, appLabel_(b), 'Kayıt (şifre oluşturdu)');
  return { ok: true, uuid: uuid, name: p.name, group: p.group };
}

/** 2b) Şifresi olan kişi yeni cihazda / yeniden kurulumda: şifreyle cihazını bağlar. */
function relogin_(b) {
  if (tooManyFails_()) return { ok: false, error: 'busy' };
  const code = normCode_(b.code);
  const sh = peopleSheet_(b);
  const row = code.length >= 2 ? findRow_(sh, code, C_CODE) : 0;
  if (!row) { addFail_(); return { ok: false, error: 'invalid' }; }
  const p = readPerson_(sh, row);
  if (p.passive) return { ok: false, error: 'passive' };
  if (!p.pwd.length) return { ok: false, error: 'no_password' };
  if (String(b.pwd || '') !== p.pwd) { addFail_(); return { ok: false, error: 'wrong' }; }
  let uuid = p.device;
  if (!uuid || String(b.uuid || '') !== uuid) {
    uuid = Utilities.getUuid();
    setText_(sh.getRange(row, C_DEVICE), uuid);
    addLog_(code, p.name, appLabel_(b), 'Cihaz bağlandı');
  }
  touchLogin_(sh, row, stamp_());
  return { ok: true, uuid: uuid, name: p.name, group: p.group };
}

function login_(b) {
  const a = auth_(b);
  if (!a) return { ok: false, error: 'auth' };
  touchLogin_(a.sh, a.row, stamp_());
  addLog_(a.code, a.name, appLabel_(b), 'Giriş');
  return { ok: true, name: a.name };
}

function auth_(b) {
  const code = normCode_(b.code);
  const uuid = String(b.uuid || '');
  const pwd = String(b.pwd || '');
  if (code.length < 2 || !uuid || !pwd) return null;
  const sh = peopleSheet_(b);
  const row = findRow_(sh, code, C_CODE);
  if (!row) return null;
  const p = readPerson_(sh, row);
  if (p.passive || p.device !== uuid || p.pwd !== pwd) return null;
  return { sh: sh, row: row, code: code, name: p.name, group: p.group, admin: isAdminApp_(b), restricted: !isAdminApp_(b) && p.restricted };
}

function adminAuth_(b) {
  if (!isAdminApp_(b)) return null;
  return auth_(b);
}

/** Uygulamada geçirilen süre: toplam (Kişiler I) + günlük (Günlük Süre). */
function usage_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  let add = Math.floor(Number(b.seconds));
  if (!(add > 0)) return { ok: false, error: 'bad_request' };
  add = Math.min(add, MAX_SECONDS_PER_CALL);

  const cell = a.sh.getRange(a.row, C_TOTAL);
  const total = parseDur_(cell.getDisplayValue()) + add;
  setText_(cell, fmtDur_(total));

  const day = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const d = sheet_(S_DAILY);
  const last = d.getLastRow();
  let row = 0;
  if (last >= 2) {
    const start = Math.max(2, last - DAILY_SCAN_ROWS + 1);
    const vals = d.getRange(start, 1, last - start + 1, 2).getDisplayValues();
    for (let i = vals.length - 1; i >= 0; i--) {
      if (vals[i][0] === day && normCode_(vals[i][1]) === a.code) { row = start + i; break; }
    }
  }
  if (row) {
    const sec = Number(d.getRange(row, 5).getDisplayValue()) + add;
    setTexts_(d.getRange(row, 4, 1, 2), [[fmtDur_(sec), String(sec)]]);
  } else {
    setTexts_(d.getRange(Math.max(last, 1) + 1, 1, 1, 5), [[day, a.code, a.name, fmtDur_(add), String(add)]]);
  }
  return { ok: true, total: total };
}

// ------------------------------------------------------------------ Kitap: ana sayfa verisi

function home_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  const tz = ss_().getSpreadsheetTimeZone() || TZ;
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const keys = [a.code];
  if (a.group) a.group.split(/[,;]+/).map(normCode_).filter(String).forEach(function (g) { keys.push(g); });
  const events = events_(keys, tz, EDU_PAST_DAYS, EDU_FUTURE_DAYS, false);
  if (a.restricted) events.forEach(function (e) { e.zoom = ''; e.desc = stripLinks_(e.desc); });
  const notices = notices_(keys, tz, today, false);
  if (a.restricted) notices.forEach(function (n) { n.msg = stripLinks_(n.msg); n.title = stripLinks_(n.title); });
  return {
    ok: true, name: a.name, today: today, access: !a.restricted,
    links: a.restricted ? [] : links_(),
    events: events,
    notices: notices,
    plans: plansFor_(a.code).plans,
  };
}

/** Göremez durumundaki kişiden metin içindeki bağlantıları gizler. */
function stripLinks_(t) {
  return String(t || '').replace(/(https?:\/\/|www\.)\S+/gi, '').replace(/[ \t]+\n/g, '\n').trim();
}

function links_() {
  const sh = sheet_(S_CONTENT);
  const last = sh.getLastRow();
  const out = [];
  if (last < 2) return out;
  sh.getRange(2, 1, last - 1, 3).getDisplayValues().forEach(function (r) {
    const url = String(r[1]).trim();
    if (/^https:\/\/\S+$/i.test(url)) out.push({ name: String(r[0]).trim() || url, url: url, desc: String(r[2]).trim() });
  });
  return out;
}

/** Eğitimler. keys null ise (yönetim) hepsi; row/who da döner. */
function events_(keys, tz, pastDays, futureDays, admin) {
  const sh = sheet_(S_EDU);
  const last = sh.getLastRow();
  const out = [];
  if (last < 2) return out;
  const now = new Date();
  const from = Utilities.formatDate(new Date(now.getTime() - pastDays * 864e5), TZ, 'yyyy-MM-dd');
  const to = Utilities.formatDate(new Date(now.getTime() + futureDays * 864e5), TZ, 'yyyy-MM-dd');
  const rng = sh.getRange(2, 1, last - 1, 7);
  const vals = rng.getValues();
  const disp = rng.getDisplayValues();
  for (let i = 0; i < vals.length; i++) {
    const date = toDate_(vals[i][0], disp[i][0], tz);
    const title = String(disp[i][3]).trim();
    if (!date || !title || date < from || date > to) continue;
    if (!admin && !targets_(disp[i][6], keys)) continue;
    const zoom = String(disp[i][4]).trim();
    const ev = {
      id: 'e' + hash_(date + '|' + title),
      key: date + '|' + title,
      date: date,
      start: toTime_(vals[i][1], disp[i][1], tz),
      end: toTime_(vals[i][2], disp[i][2], tz),
      title: title,
      zoom: /^https:\/\/([a-z0-9-]+\.)?zoom\.us\//i.test(zoom) ? zoom : '',
      desc: String(disp[i][5]),
    };
    if (admin) { ev.row = i + 2; ev.who = String(disp[i][6]).trim(); ev.zoomRaw = zoom; }
    out.push(ev);
  }
  out.sort(function (x, y) { return (x.date + x.start) < (y.date + y.start) ? -1 : 1; });
  return out;
}

function notices_(keys, tz, today, admin) {
  const sh = sheet_(S_NOTICES);
  const last = sh.getLastRow();
  const out = [];
  if (last < 2) return out;
  const rng = sh.getRange(2, 1, last - 1, 5);
  const vals = rng.getValues();
  const disp = rng.getDisplayValues();
  for (let i = 0; i < vals.length; i++) {
    const title = String(disp[i][1]).trim();
    const msg = String(disp[i][2]).trim();
    if (!title && !msg) continue;
    const date = toDate_(vals[i][0], disp[i][0], tz) || '';
    const until = toDate_(vals[i][4], disp[i][4], tz);
    if (!admin) {
      if (date && date > today) continue; // ileri tarihli bildirim o gün gelir
      if (until && until < today) continue;
      if (!targets_(disp[i][3], keys)) continue;
    }
    const n = { id: 'n' + hash_(date + title + msg), date: date, title: title || msg.slice(0, 40), msg: msg };
    if (admin) { n.row = i + 2; n.who = String(disp[i][3]).trim(); n.until = until || ''; n.key = hash_(date + title + msg); }
    out.push(n);
  }
  out.sort(function (x, y) { return x.date < y.date ? 1 : -1; }); // en yeni üstte
  return admin ? out : out.slice(0, 50);
}

// ------------------------------------------------------------------ Kitap: Kur'an

/** Sure → [ilk satır, ayet sayısı] dizini; önbellekte tutulur. */
function quranIndex_(force) {
  const cache = CacheService.getScriptCache();
  if (!force) {
    const c = cache.get('kidx');
    if (c) return JSON.parse(c);
  }
  const sh = sheet_(S_QURAN);
  const last = sh.getLastRow();
  const idx = {};
  if (last >= 1) {
    const vals = sh.getRange(1, 1, last, 1).getValues();
    for (let i = 0; i < vals.length; i++) {
      const s = Number(vals[i][0]);
      if (!(s >= 1 && s <= 114) || Math.floor(s) !== s) continue;
      if (!idx[s]) idx[s] = [i + 1, 0];
      idx[s][1] = i + 1 - idx[s][0] + 1;
    }
  }
  cache.put('kidx', JSON.stringify(idx), 21600);
  return idx;
}

function quranAyets_(sure) {
  const cache = CacheService.getScriptCache();
  const ck = 'q2s' + sure;
  const parts = Number(cache.get(ck + 'n') || 0);
  if (parts > 0) {
    const got = cache.getAll(Array.from({ length: parts }, function (_, i) { return ck + '_' + i; }));
    let txt = '';
    let okAll = true;
    for (let i = 0; i < parts; i++) { const v = got[ck + '_' + i]; if (v == null) { okAll = false; break; } txt += v; }
    if (okAll) return JSON.parse(txt);
  }
  let idx = quranIndex_(false);
  let ent = idx[sure];
  const sh = sheet_(S_QURAN);
  let rows = ent ? sh.getRange(ent[0], 1, ent[1], 3).getValues() : [];
  if (!ent || !rows.length || Number(rows[0][0]) !== sure || Number(rows[rows.length - 1][0]) !== sure) {
    idx = quranIndex_(true);
    ent = idx[sure];
    rows = ent ? sh.getRange(ent[0], 1, ent[1], 3).getValues() : [];
  }
  const out = [];
  rows.forEach(function (r) {
    if (Number(r[0]) !== sure) return;
    const no = Number(r[1]);
    const text = cleanMeal_(r[2]);
    if (no > 0 && text) out.push([no, text]);
  });
  out.sort(function (x, y) { return x[0] - y[0]; });
  // Önbelleğe parça parça (her anahtar en çok ~90 KB)
  try {
    const s = JSON.stringify(out);
    const size = 30000;
    const map = {};
    let n = 0;
    for (let i = 0; i < s.length; i += size) { map[ck + '_' + n] = s.substring(i, i + size); n++; }
    map[ck + 'n'] = String(n);
    cache.putAll(map, 21600);
  } catch (e) { }
  return out;
}

/**
 * Meal metnindeki dipnot ve ayet-aralığı işaretlerini kaldırır (Excel'e dokunmadan):
 *   "Hamd[4], Âlemlerin Rabbi[5]"  → "Hamd, Âlemlerin Rabbi"
 *   "2,3,4. Hamd..." / "6,7. Bizi..." → "Hamd..." / "Bizi..."
 */
function cleanMeal_(v) {
  return String(v == null ? '' : v)
    .replace(/\[\s*\d+(?:\s*[,\-–]\s*\d+)*\s*\]/g, '')
    .replace(/^\s*\d+(?:\s*[,\-–]\s*\d+)*\s*[.)]\s*/, '')
    .replace(/\s+([.,;:!?’”)])/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}

function kuran_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  if (a.restricted) return { ok: false, error: 'restricted' };
  const sure = Math.floor(Number(b.sure));
  if (!(sure >= 1 && sure <= 114)) return { ok: false, error: 'bad_request' };
  const ayetler = quranAyets_(sure);
  return { ok: true, sure: sure, ayetler: ayetler, kayitlar: marksFor_(a.code, sure).list };
}

/** Kişinin bu suredeki vurgu/notları. */
function marksFor_(code, sure) {
  const sh = sheet_(S_QNOTES);
  const last = sh.getLastRow();
  const list = [];
  const rows = {};
  if (last >= 2) {
    const v = sh.getRange(2, 1, last - 1, 7).getDisplayValues();
    for (let i = 0; i < v.length; i++) {
      if (normCode_(v[i][0]) !== code || Number(v[i][2]) !== sure) continue;
      const ayet = Number(v[i][3]);
      rows[ayet] = i + 2;
      list.push({ ayet: ayet, vurgu: Math.max(0, COLORS.indexOf(String(v[i][4]).trim())), not: v[i][5], tarih: v[i][6] });
    }
  }
  return { list: list, rows: rows, sh: sh };
}

function ayetList_(s) {
  return String(s || '').split(/[,\s]+/).map(Number).filter(function (n) { return n >= 1 && n <= 300 && Math.floor(n) === n; })
    .filter(function (n, i, arr) { return arr.indexOf(n) === i; }).slice(0, 300);
}

/** Vurgu ya da not değişikliği; boş kalan satır silinir. */
function updateMarks_(a, sure, ayetler, fn) {
  const m = marksFor_(a.code, sure);
  const sh = m.sh;
  const now = stamp_();
  const toDelete = [];
  const toAdd = [];
  const byAyet = {};
  m.list.forEach(function (k) { byAyet[k.ayet] = k; });
  ayetler.forEach(function (ayet) {
    const cur = byAyet[ayet] || { ayet: ayet, vurgu: 0, not: '', tarih: '' };
    fn(cur);
    const row = m.rows[ayet];
    const empty = !cur.vurgu && !String(cur.not || '').trim();
    if (row) {
      if (empty) toDelete.push(row);
      else setTexts_(sh.getRange(row, 5, 1, 3), [[COLORS[cur.vurgu] || '', cur.not || '', now]]);
    } else if (!empty) {
      toAdd.push([a.code, a.name, String(sure), String(ayet), COLORS[cur.vurgu] || '', cur.not || '', now]);
    }
  });
  toDelete.sort(function (x, y) { return y - x; }).forEach(function (r) { sh.deleteRow(r); });
  if (toAdd.length) setTexts_(sh.getRange(Math.max(sh.getLastRow(), 1) + 1, 1, toAdd.length, 7), toAdd);
  return { ok: true, kayitlar: marksFor_(a.code, sure).list };
}

function mark_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  if (a.restricted) return { ok: false, error: 'restricted' };
  const sure = Math.floor(Number(b.sure));
  const renk = Math.floor(Number(b.renk)) || 0;
  const ayetler = ayetList_(b.ayetler);
  if (!(sure >= 1 && sure <= 114) || renk < 0 || renk > 3 || !ayetler.length) return { ok: false, error: 'bad_request' };
  return updateMarks_(a, sure, ayetler, function (k) { k.vurgu = renk; });
}

function note_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  if (a.restricted) return { ok: false, error: 'restricted' };
  const sure = Math.floor(Number(b.sure));
  const ayetler = ayetList_(b.ayetler);
  const text = String(b.not == null ? '' : b.not).trim().slice(0, NOTE_MAX);
  if (!(sure >= 1 && sure <= 114) || !ayetler.length) return { ok: false, error: 'bad_request' };
  return updateMarks_(a, sure, ayetler, function (k) { k.not = text; });
}

/** Bir surede okuma süresi (yalnızca ekran açık ve kişi hareket ederken sayılır). */
function readTime_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  if (a.restricted) return { ok: false, error: 'restricted' };
  const sure = Math.floor(Number(b.sure));
  let add = Math.floor(Number(b.saniye));
  if (!(sure >= 1 && sure <= 114) || !(add > 0)) return { ok: false, error: 'bad_request' };
  add = Math.min(add, 3600);
  const sh = sheet_(S_QREAD);
  const last = sh.getLastRow();
  let row = 0;
  if (last >= 2) {
    const v = sh.getRange(2, 1, last - 1, 3).getDisplayValues();
    for (let i = 0; i < v.length; i++) if (normCode_(v[i][0]) === a.code && Number(v[i][2]) === sure) { row = i + 2; break; }
  }
  const now = stamp_();
  if (row) {
    const sec = (Number(sh.getRange(row, 6).getDisplayValue()) || 0) + add;
    setTexts_(sh.getRange(row, 5, 1, 3), [[fmtDur_(sec), String(sec), now]]);
  } else {
    setTexts_(sh.getRange(Math.max(last, 1) + 1, 1, 1, 7), [[a.code, a.name, String(sure), SURELER[sure - 1], fmtDur_(add), String(add), now]]);
  }
  return { ok: true };
}

// ------------------------------------------------------------------ Kitap: eğitim notları

function eduKey_(b) {
  const date = String(b.date || '').slice(0, 10);
  const title = String(b.title || '').trim().slice(0, 200);
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && title ? { date: date, title: title } : null;
}

function eduNote_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  const k = eduKey_(b);
  const text = String(b.not || '').trim().slice(0, NOTE_MAX);
  if (!k || !text) return { ok: false, error: 'bad_request' };
  const sh = sheet_(S_ENOTES);
  setTexts_(sh.getRange(Math.max(sh.getLastRow(), 1) + 1, 1, 1, 6), [[stamp_(), a.code, a.name, k.date, k.title, text]]);
  return eduNotesFor_(a.code, k);
}

function eduNoteDelete_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  const k = eduKey_(b);
  const row = Math.floor(Number(b.row));
  if (!k || !(row >= 2)) return { ok: false, error: 'bad_request' };
  const sh = sheet_(S_ENOTES);
  if (row <= sh.getLastRow()) {
    const v = sh.getRange(row, 1, 1, 6).getDisplayValues()[0];
    if (normCode_(v[1]) === a.code && v[3] === k.date && String(v[4]).trim() === k.title && v[0] === String(b.at || '')) sh.deleteRow(row);
  }
  return eduNotesFor_(a.code, k);
}

function eduNotes_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  const k = eduKey_(b);
  if (!k) return { ok: false, error: 'bad_request' };
  return eduNotesFor_(a.code, k);
}

function eduNotesFor_(code, k) {
  const sh = sheet_(S_ENOTES);
  const last = sh.getLastRow();
  const out = [];
  if (last >= 2) {
    sh.getRange(2, 1, last - 1, 6).getDisplayValues().forEach(function (r, i) {
      if (code && normCode_(r[1]) !== code) return;
      if (k && (r[3] !== k.date || String(r[4]).trim() !== k.title)) return;
      out.push({ row: i + 2, at: r[0], code: normCode_(r[1]), name: r[2], date: r[3], title: r[4], not: r[5] });
    });
  }
  out.reverse(); // en yeni üstte
  return { ok: true, notes: out };
}

// ------------------------------------------------------------------ Kitap: kişisel notlar (planlar)

function plansFor_(code) {
  const sh = sheet_(S_PLANS);
  const last = sh.getLastRow();
  const out = [];
  const from = Utilities.formatDate(new Date(Date.now() - 60 * 864e5), TZ, 'yyyy-MM-dd');
  if (last >= 2) {
    sh.getRange(2, 1, last - 1, 6).getDisplayValues().forEach(function (r, i) {
      if (normCode_(r[0]) !== code || r[2] < from) return;
      out.push({ row: i + 2, date: r[2], time: r[3], not: r[4], at: r[5] });
    });
  }
  out.sort(function (x, y) { return (x.date + x.time) < (y.date + y.time) ? -1 : 1; });
  return { ok: true, plans: out };
}

function planCheck_(sh, row, code, at) {
  if (!(row >= 2) || row > sh.getLastRow()) return false;
  const v = sh.getRange(row, 1, 1, 6).getDisplayValues()[0];
  return normCode_(v[0]) === code && v[5] === String(at || '');
}

function planSave_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  const date = String(b.date || '');
  const time = String(b.time || '');
  const text = String(b.not || '').trim().slice(0, NOTE_MAX);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || (time && !/^\d{2}:\d{2}$/.test(time)) || !text) return { ok: false, error: 'bad_request' };
  const sh = sheet_(S_PLANS);
  const row = Math.floor(Number(b.row)) || 0;
  const vals = [[a.code, a.name, date, time, text, stamp_()]];
  if (row) {
    if (!planCheck_(sh, row, a.code, b.at)) return { ok: false, error: 'changed' };
    setTexts_(sh.getRange(row, 1, 1, 6), vals);
  } else {
    setTexts_(sh.getRange(Math.max(sh.getLastRow(), 1) + 1, 1, 1, 6), vals);
  }
  return plansFor_(a.code);
}

function planDelete_(b) {
  const a = auth_(b);
  if (!a || a.admin) return { ok: false, error: 'auth' };
  const sh = sheet_(S_PLANS);
  const row = Math.floor(Number(b.row)) || 0;
  if (planCheck_(sh, row, a.code, b.at)) sh.deleteRow(row);
  return plansFor_(a.code);
}

// ------------------------------------------------------------------ hata raporları (iki uygulama)

/** Uygulama çöktüğünde sessizce gönderilir; yöneticiler Üye Yönetim'de görür. */
function crash_(b) {
  const c = CacheService.getScriptCache();
  const n = Number(c.get('crashes') || 0);
  if (n >= 60) return { ok: false, error: 'busy' };
  c.put('crashes', String(n + 1), 600);
  const report = String(b.report || '').trim().slice(0, 4000);
  if (!report) return { ok: false, error: 'bad_request' };
  let code = normCode_(b.code).slice(0, 60);
  let name = '';
  const a = auth_(b);
  if (a) name = a.name; else if (code) code += ' (doğrulanmadı)';
  const sh = sheet_(S_CRASH);
  setTexts_(sh.getRange(Math.max(sh.getLastRow(), 1) + 1, 1, 1, 7),
    [[stamp_(), appLabel_(b), code, name, String(b.version || '').slice(0, 40), String(b.device || '').slice(0, 120), report]]);
  return { ok: true };
}

function crashes_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const sh = sheet_(S_CRASH);
  const last = sh.getLastRow();
  const out = [];
  if (last >= 2) {
    const start = Math.max(2, last - 99);
    const rows = sh.getRange(start, 1, last - start + 1, 7).getDisplayValues();
    for (let i = rows.length - 1; i >= 0; i--) {
      const r = rows[i];
      out.push({ at: r[0], app: r[1], code: r[2], name: r[3], version: r[4], device: r[5], report: r[6] });
    }
  }
  return { ok: true, crashes: out };
}

// ------------------------------------------------------------------ Üye Yönetim

function dailyMap_() {
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const weekAgo = Utilities.formatDate(new Date(Date.now() - 6 * 864e5), TZ, 'yyyy-MM-dd');
  const out = {};
  const d = sheet_(S_DAILY);
  const dl = d.getLastRow();
  if (dl >= 2) {
    const start = Math.max(2, dl - DAILY_SCAN_ROWS + 1);
    d.getRange(start, 1, dl - start + 1, 5).getDisplayValues().forEach(function (r) {
      if (r[0] < weekAgo) return;
      const k = normCode_(r[1]);
      if (!out[k]) out[k] = { today: 0, week: 0 };
      const sec = Number(r[4]) || 0;
      out[k].week += sec;
      if (r[0] === today) out[k].today += sec;
    });
  }
  return out;
}

function adminData_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const tz = ss_().getSpreadsheetTimeZone() || TZ;
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const daily = dailyMap_();

  // Kitap okuma toplamları ve not sayıları kişi başına
  const readSum = {};
  const rs = sheet_(S_QREAD);
  if (rs.getLastRow() >= 2) rs.getRange(2, 1, rs.getLastRow() - 1, 6).getDisplayValues().forEach(function (r) {
    const k = normCode_(r[0]); readSum[k] = (readSum[k] || 0) + (Number(r[5]) || 0);
  });
  const noteCount = {};
  const qn = sheet_(S_QNOTES);
  if (qn.getLastRow() >= 2) qn.getRange(2, 1, qn.getLastRow() - 1, 6).getDisplayValues().forEach(function (r) {
    if (!String(r[5]).trim()) return;
    const k = normCode_(r[0]); noteCount[k] = (noteCount[k] || 0) + 1;
  });
  const eduCount = {};
  const eduByPerson = {};
  const en = sheet_(S_ENOTES);
  if (en.getLastRow() >= 2) en.getRange(2, 1, en.getLastRow() - 1, 5).getDisplayValues().forEach(function (r) {
    const key = r[3] + '|' + String(r[4]).trim();
    eduCount[key] = (eduCount[key] || 0) + 1;
    const k = normCode_(r[1]); eduByPerson[k] = (eduByPerson[k] || 0) + 1;
  });

  const people = [];
  const sh = sheet_(S_PEOPLE);
  const last = sh.getLastRow();
  if (last >= 2) {
    sh.getRange(2, 1, last - 1, P_COLS).getDisplayValues().forEach(function (v, i) {
      const code = normCode_(v[0]);
      if (!code) return;
      const d = daily[code] || {};
      people.push({
        row: i + 2, code: code, name: String(v[1]).trim(), group: String(v[2]).trim(), pwd: String(v[3]),
        passive: isPassive_(v[4]), registered: String(v[5]).trim().length > 0, since: v[6], last: v[7],
        total: parseDur_(v[8]), count: Number(v[9]) || 0, today: d.today || 0, week: d.week || 0,
        read: readSum[code] || 0, notes: noteCount[code] || 0, eduNotes: eduByPerson[code] || 0,
        restricted: isRestricted_(v[10]),
      });
    });
  }
  const events = events_(null, tz, 120, EDU_FUTURE_DAYS, true);
  events.forEach(function (e) { e.notes = eduCount[e.key] || 0; });
  const cs = sheet_(S_CRASH);
  const weekAgo = Utilities.formatDate(new Date(Date.now() - 7 * 864e5), TZ, 'yyyy-MM-dd');
  let crashCount = 0;
  if (cs.getLastRow() >= 2) {
    const st = Math.max(2, cs.getLastRow() - 199);
    cs.getRange(st, 1, cs.getLastRow() - st + 1, 1).getDisplayValues().forEach(function (r) { if (r[0] >= weekAgo) crashCount++; });
  }
  return {
    ok: true, name: a.name, today: today, people: people, events: events,
    notices: notices_(null, tz, today, true), crashCount: crashCount,
  };
}

function personDetail_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const code = normCode_(b.target);
  const sh = sheet_(S_PEOPLE);
  const row = findRow_(sh, code, C_CODE);
  if (!row) return { ok: false, error: 'not_found' };
  const p = readPerson_(sh, row);
  const d = dailyMap_()[code] || {};
  const out = {
    ok: true,
    person: {
      row: row, code: code, name: p.name, group: p.group, pwd: p.pwd, passive: p.passive, registered: p.device.length > 0,
      since: p.since, last: p.last, total: p.total, count: p.count, today: d.today || 0, week: d.week || 0,
      restricted: p.restricted,
    },
    days: [], reading: [], marks: [], eduNotes: [], logins: [], plans: plansFor_(code).plans,
  };
  // Son 14 gün
  const from = Utilities.formatDate(new Date(Date.now() - 13 * 864e5), TZ, 'yyyy-MM-dd');
  const ds = sheet_(S_DAILY);
  if (ds.getLastRow() >= 2) {
    const start = Math.max(2, ds.getLastRow() - DAILY_SCAN_ROWS + 1);
    ds.getRange(start, 1, ds.getLastRow() - start + 1, 5).getDisplayValues().forEach(function (r) {
      if (r[0] >= from && normCode_(r[1]) === code) out.days.push({ date: r[0], sec: Number(r[4]) || 0 });
    });
  }
  const rs = sheet_(S_QREAD);
  if (rs.getLastRow() >= 2) rs.getRange(2, 1, rs.getLastRow() - 1, 7).getDisplayValues().forEach(function (r) {
    if (normCode_(r[0]) === code) out.reading.push({ sure: Number(r[2]), name: r[3], sec: Number(r[5]) || 0, last: r[6] });
  });
  out.reading.sort(function (x, y) { return x.last < y.last ? 1 : -1; });
  const qn = sheet_(S_QNOTES);
  if (qn.getLastRow() >= 2) qn.getRange(2, 1, qn.getLastRow() - 1, 7).getDisplayValues().forEach(function (r) {
    if (normCode_(r[0]) !== code) return;
    const s = Number(r[2]);
    out.marks.push({ sure: s, name: SURELER[s - 1] || '', ayet: Number(r[3]), vurgu: Math.max(0, COLORS.indexOf(String(r[4]).trim())), not: r[5], tarih: r[6] });
  });
  out.marks.sort(function (x, y) { return x.tarih < y.tarih ? 1 : -1; });
  out.eduNotes = eduNotesFor_(code, null).notes;
  const lg = sheet_(S_LOG);
  const ll = lg.getLastRow();
  if (ll >= 2) {
    const start = Math.max(2, ll - LOG_SCAN_ROWS + 1);
    const rows = lg.getRange(start, 1, ll - start + 1, 5).getDisplayValues();
    for (let i = rows.length - 1; i >= 0 && out.logins.length < 30; i--) {
      if (normCode_(rows[i][1]) === code && rows[i][3] === 'Kitap') out.logins.push({ at: rows[i][0], ev: rows[i][4] });
    }
  }
  return out;
}

/** Kişi ekle (orig boş) ya da düzenle. */
function personSave_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const sh = sheet_(S_PEOPLE);
  const orig = normCode_(b.orig);
  const code = normCode_(b.pcode);
  const name = String(b.name || '').trim().slice(0, 100);
  const group = String(b.group || '').trim().slice(0, 100);
  const pwd = String(b.ppwd == null ? '' : b.ppwd).trim();
  if (code.length < 2 || !name) return { ok: false, error: 'bad_request' };
  if (pwd && (pwd.length < 4 || pwd.length > 40)) return { ok: false, error: 'bad_password' };
  const other = findRow_(sh, code, C_CODE);
  let row;
  if (!orig) {
    if (other) return { ok: false, error: 'exists' };
    row = Math.max(sh.getLastRow(), 1) + 1;
    setTexts_(sh.getRange(row, 1, 1, 5), [[code, name, group, pwd, b.passive ? 'Pasif' : 'Aktif']]);
    setText_(sh.getRange(row, C_ACCESS), b.restricted ? 'Göremez' : 'Görebilir');
    addLog_(a.code, a.name, 'Üye Yönetim', 'Kişi ekledi: ' + code);
  } else {
    row = findRow_(sh, orig, C_CODE);
    if (!row) return { ok: false, error: 'not_found' };
    if (other && other !== row) return { ok: false, error: 'exists' };
    setTexts_(sh.getRange(row, 1, 1, 5), [[code, name, group, pwd, b.passive ? 'Pasif' : 'Aktif']]);
    setText_(sh.getRange(row, C_ACCESS), b.restricted ? 'Göremez' : 'Görebilir');
    if (b.resetDevice) setText_(sh.getRange(row, C_DEVICE), '');
    addLog_(a.code, a.name, 'Üye Yönetim', 'Kişiyi düzenledi: ' + code);
  }
  return { ok: true, code: code };
}

/** Görebilir / Göremez tiki (yalnızca yöneticiler). */
function access_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const sh = sheet_(S_PEOPLE);
  const code = normCode_(b.target);
  const row = findRow_(sh, code, C_CODE);
  if (!row) return { ok: false, error: 'not_found' };
  setText_(sh.getRange(row, C_ACCESS), b.restricted ? 'Göremez' : 'Görebilir');
  addLog_(a.code, a.name, 'Üye Yönetim', (b.restricted ? 'Göremez yaptı: ' : 'Görebilir yaptı: ') + code);
  return { ok: true };
}

function eventSave_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const date = String(b.date || '');
  const title = String(b.title || '').trim().slice(0, 200);
  const start = String(b.start || '');
  const end = String(b.end || '');
  const zoom = String(b.zoom || '').trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !title) return { ok: false, error: 'bad_request' };
  if (start && !/^\d{2}:\d{2}$/.test(start) || end && !/^\d{2}:\d{2}$/.test(end)) return { ok: false, error: 'bad_request' };
  if (zoom && !/^https:\/\/\S+$/i.test(zoom)) return { ok: false, error: 'bad_link' };
  const sh = sheet_(S_EDU);
  const p = date.split('-');
  const vals = [[p[2] + '.' + p[1] + '.' + p[0], start, end, title, zoom, String(b.desc || '').slice(0, 5000), String(b.who || '').trim()]];
  let row = Math.floor(Number(b.row)) || 0;
  if (row) {
    if (!rowMatchesEvent_(sh, row, b.key)) return { ok: false, error: 'changed' };
  } else row = Math.max(sh.getLastRow(), 1) + 1;
  setTexts_(sh.getRange(row, 1, 1, 7), vals);
  return { ok: true };
}

function rowMatchesEvent_(sh, row, key) {
  if (row < 2 || row > sh.getLastRow()) return false;
  const tz = ss_().getSpreadsheetTimeZone() || TZ;
  const v = sh.getRange(row, 1, 1, 4).getValues()[0];
  const d = sh.getRange(row, 1, 1, 4).getDisplayValues()[0];
  return toDate_(v[0], d[0], tz) + '|' + String(d[3]).trim() === String(key || '');
}

function eventDelete_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const sh = sheet_(S_EDU);
  const row = Math.floor(Number(b.row)) || 0;
  if (!rowMatchesEvent_(sh, row, b.key)) return { ok: false, error: 'changed' };
  sh.deleteRow(row);
  return { ok: true };
}

function eventNotes_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const k = eduKey_(b);
  if (!k) return { ok: false, error: 'bad_request' };
  return eduNotesFor_(null, k);
}

function noticeSave_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const title = String(b.title || '').trim().slice(0, 200);
  const msg = String(b.msg || '').trim().slice(0, 3000);
  const until = String(b.until || '');
  if (!title && !msg) return { ok: false, error: 'bad_request' };
  if (until && !/^\d{4}-\d{2}-\d{2}$/.test(until)) return { ok: false, error: 'bad_request' };
  const sh = sheet_(S_NOTICES);
  const today = Utilities.formatDate(new Date(), TZ, 'dd.MM.yyyy');
  const u = until ? until.split('-') : null;
  setTexts_(sh.getRange(Math.max(sh.getLastRow(), 1) + 1, 1, 1, 5),
    [[today, title, msg, String(b.who || '').trim(), u ? u[2] + '.' + u[1] + '.' + u[0] : '']]);
  return { ok: true };
}

function noticeDelete_(b) {
  const a = adminAuth_(b);
  if (!a) return { ok: false, error: 'auth' };
  const sh = sheet_(S_NOTICES);
  const row = Math.floor(Number(b.row)) || 0;
  if (row < 2 || row > sh.getLastRow()) return { ok: false, error: 'changed' };
  const tz = ss_().getSpreadsheetTimeZone() || TZ;
  const v = sh.getRange(row, 1, 1, 3).getValues()[0];
  const d = sh.getRange(row, 1, 1, 3).getDisplayValues()[0];
  const key = hash_((toDate_(v[0], d[0], tz) || '') + String(d[1]).trim() + String(d[2]).trim());
  if (key !== String(b.key || '')) return { ok: false, error: 'changed' };
  sh.deleteRow(row);
  return { ok: true };
}

// ------------------------------------------------------------------ yardımcılar

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function ss_() {
  return SPREADSHEET_ID ? SpreadsheetApp.openById(SPREADSHEET_ID) : SpreadsheetApp.getActiveSpreadsheet();
}

function key_(name) { return String(name).trim().toLocaleLowerCase('tr'); }

const sheetCache_ = {};
function sheet_(name) {
  if (sheetCache_[name]) return sheetCache_[name];
  const want = key_(name);
  let all = ss_().getSheets();
  for (let i = 0; i < all.length; i++) if (key_(all[i].getName()) === want) return (sheetCache_[name] = all[i]);
  ensureSheets_();
  all = ss_().getSheets();
  for (let j = 0; j < all.length; j++) if (key_(all[j].getName()) === want) return (sheetCache_[name] = all[j]);
  throw new Error('Sayfa bulunamadı: ' + name);
}

function normCode_(v) {
  // Büyük harf; i/ı/İ/I aynı sayılır (telefon klavyesi farkı kodu bozmasın)
  return String(v === null || v === undefined ? '' : v).trim().toLocaleUpperCase('tr').replace(/İ/g, 'I').replace(/\s+/g, ' ');
}

/** "Kimler" hücresi boşsa herkes; doluysa anahtarlardan biri geçmeli. */
function targets_(cell, keys) {
  const raw = String(cell || '').trim();
  if (!raw || normCode_(raw) === 'HERKES') return true;
  const list = raw.split(/[,;]+/).map(normCode_).filter(String);
  for (let i = 0; i < list.length; i++) if (keys.indexOf(list[i]) >= 0) return true;
  return false;
}

function findRow_(sheet, code, col) {
  const last = sheet.getLastRow();
  if (last < 2) return 0;
  const vals = sheet.getRange(2, col || 1, last - 1, 1).getDisplayValues();
  for (let i = 0; i < vals.length; i++) if (normCode_(vals[i][0]) === code) return i + 2;
  return 0;
}

function setText_(range, value) { range.setNumberFormat('@'); range.setValue(value); }
function setTexts_(range, values) { range.setNumberFormat('@'); range.setValues(values); }

function touchLogin_(sh, row, now) {
  const v = sh.getRange(row, C_LAST, 1, 3).getDisplayValues()[0];
  setTexts_(sh.getRange(row, C_LAST, 1, 3), [[now, v[1] || '0:00:00', String((Number(v[2]) || 0) + 1)]]);
}

function addLog_(code, name, app, ev) {
  const lg = sheet_(S_LOG);
  setTexts_(lg.getRange(Math.max(lg.getLastRow(), 1) + 1, 1, 1, 5), [[stamp_(), code, name, app, ev]]);
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
  if (!m || Number(m[1]) > 24 || Number(m[2]) > 59) return '';
  if (Number(m[1]) === 24) return '23:59';
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

/** Kuran sayfasını değiştirdiyseniz önbelleği temizlemek için bir kez çalıştırın. */
function kuranOnbellekTemizle() {
  const c = CacheService.getScriptCache();
  c.remove('kidx');
  for (let s = 1; s <= 114; s++) { c.remove('ks' + s + 'n'); c.remove('q2s' + s + 'n'); }
}

function ensureSheets_() {
  const ss = ss_();
  if (!ss) throw new Error('Bu betik bir E-Tabloya bağlı değil. E-Tabloyu açıp Uzantılar > Apps Script ile açılan betiğe yapıştırın.');
  const today = Utilities.formatDate(new Date(), TZ, 'dd.MM.yyyy');
  const adminCode = 'Y-' + Math.floor(100000 + Math.random() * 900000);
  const pHead = ['Yetki kodu', 'Ad Soyad', 'Grup (isteğe bağlı)', 'Şifre', 'Durum (Aktif / Pasif)', 'Cihaz (uygulama yazar)', 'Kayıt', 'Son giriş', 'Toplam süre (s:dk:sn)', 'Giriş sayısı', 'Erişim (Görebilir / Göremez)'];
  const defs = [
    [S_PEOPLE, pHead, ['DENEME1', 'Deneme Kişi', '', '', 'Aktif'], true],
    [S_ADMINS, pHead.map(function (h, i) { return i === 2 ? 'Görev (isteğe bağlı)' : h; }), [adminCode, 'Yönetici', '', '', 'Aktif'], true],
    [S_CONTENT, ['Ad', 'Link (https://...)', 'Açıklama'], ['Diyanet Kur\'an', 'https://kuran.diyanet.gov.tr/', 'Örnek içerik (silebilirsiniz)'], false],
    [S_EDU, ['Tarih', 'Başlangıç', 'Bitiş', 'Eğitim adı', 'Zoom linki', 'Eğitim metni (emoji olabilir)', 'Kimler (boş = herkes)'],
      [today, '20:00', '21:00', 'Örnek eğitim (silebilirsiniz)',
        'https://us05web.zoom.us/j/88170373640?pwd=NGLhQOdez5wVLIw4pgJygAVlDCxy2k.1',
        '📖 Bu akşamki eğitimimize hoş geldiniz!\n\n✅ Konu: Örnek konu\n⏰ Lütfen 5 dakika önce hazır olun.\n\n🙏 Görüşmek üzere.', ''], false],
    [S_NOTICES, ['Tarih', 'Başlık', 'Mesaj', 'Kime (boş = herkes)', 'Son gün (isteğe bağlı)'],
      [today, 'Hoş geldiniz', 'Kitap uygulaması kullanıma açıldı. (Örnek bildirim, silebilirsiniz.)', '', ''], false],
    [S_QURAN, ['Sure', 'Ayet', 'Meal (Diyanet İşleri - yeni)'], null, false],
    [S_QNOTES, ['Kod', 'Ad', 'Sure', 'Ayet', 'Vurgu', 'Not', 'Tarih'], null, true],
    [S_QREAD, ['Kod', 'Ad', 'Sure', 'Sure adı', 'Süre (s:dk:sn)', 'Saniye', 'Son okuma'], null, true],
    [S_ENOTES, ['Tarih-saat', 'Kod', 'Ad', 'Eğitim tarihi', 'Eğitim', 'Not'], null, false],
    [S_LOG, ['Tarih-saat', 'Kod', 'Ad', 'Uygulama', 'Olay'], null, false],
    [S_DAILY, ['Tarih', 'Kod', 'Ad', 'Süre (s:dk:sn)', 'Saniye'], null, false],
    [S_PLANS, ['Kod', 'Ad', 'Tarih', 'Saat', 'Not', 'Yazıldı'], null, true],
    [S_CRASH, ['Tarih-saat', 'Uygulama', 'Kod', 'Ad', 'Sürüm', 'Cihaz', 'Rapor'], null, false],
  ];
  defs.forEach(function (d) {
    let sh = null;
    const all = ss.getSheets();
    for (let i = 0; i < all.length; i++) if (key_(all[i].getName()) === key_(d[0])) sh = all[i];
    if (sh) {
      // Sonradan eklenen sütun başlığı (ör. "Erişim") eksikse yalnızca başlığı yaz
      if (d[3] && d[1].length === P_COLS && !String(sh.getRange(1, C_ACCESS).getDisplayValue()).trim()) {
        sh.getRange(1, C_ACCESS).setValue(d[1][C_ACCESS - 1]).setFontWeight('bold');
      }
      return; // var olan sayfanın verisine dokunma
    }
    sh = ss.insertSheet(d[0]);
    sh.getRange(1, 1, 1, d[1].length).setValues([d[1]]).setFontWeight('bold');
    sh.setFrozenRows(1);
    if (d[3]) {
      sh.getRange(2, 1, Math.max(sh.getMaxRows() - 1, 1), 1).setNumberFormat('@');
      if (d[1].length === P_COLS) sh.getRange(2, C_PWD, Math.max(sh.getMaxRows() - 1, 1), 1).setNumberFormat('@');
    }
    if (d[2]) setTexts_(sh.getRange(2, 1, 1, d[2].length), [d[2]]);
  });
  // Boş ilk sayfayı ("Sayfa1"/"Sheet1") kaldır
  ss.getSheets().forEach(function (s) {
    if (/^(sayfa|sheet)1$/i.test(s.getName()) && s.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(s);
  });
}
