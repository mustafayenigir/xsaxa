'use strict';
const $ = (id) => document.getElementById(id);

// ---- Tema + ayarlar
function renderPalettes(cur) {
  const box = $('palettes');
  if (!box.childElementCount) {
    PALETTES.forEach((p, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'pal';
      b.setAttribute('aria-label', p.name);
      const sw = document.createElement('span'); sw.className = 'sw';
      sw.style.background = 'linear-gradient(135deg, ' + p.start + ', ' + p.end + ')';
      const n = document.createElement('span'); n.className = 'pn'; n.textContent = p.name;
      b.append(sw, n);
      b.addEventListener('click', () => change({ palette: i }));
      box.appendChild(b);
    });
  }
  [...box.children].forEach((b, i) => {
    b.classList.toggle('sel', i === cur);
    b.firstChild.textContent = i === cur ? '✓' : '';
  });
}

function show(s) {
  if (!s) return;
  applyPalette(s.palette);
  renderPalettes(Number.isInteger(s.palette) ? s.palette : 0);
  $('keepAudio').checked = s.keepAudio;
  $('adBlock').checked = s.adBlock;
  document.querySelectorAll('input[name="theme"]').forEach((r) => { r.checked = r.value === s.theme; });
  $('zoom').min = s.zoomMin;
  $('zoom').max = s.zoomMax;
  $('zoom').value = s.zoom;
  $('zoomLabel').textContent = '%' + s.zoom;
}

async function change(patch) {
  show(await window.api.setSettings(patch));
}

$('keepAudio').addEventListener('change', (e) => change({ keepAudio: e.target.checked }));
$('adBlock').addEventListener('change', (e) => change({ adBlock: e.target.checked }));
document.querySelectorAll('input[name="theme"]').forEach((r) => {
  r.addEventListener('change', () => { if (r.checked) change({ theme: r.value }); });
});
$('zoom').addEventListener('input', (e) => change({ zoom: Number(e.target.value) }));
$('minus').addEventListener('click', () => change({ zoom: Number($('zoom').value) - 10 }));
$('plus').addEventListener('click', () => change({ zoom: Number($('zoom').value) + 10 }));
$('reset').addEventListener('click', () => change({ zoom: 100 }));
$('close').addEventListener('click', () => window.close());

// ---- Pencereler (bilgi / form / silme onayı)
const boxes = ['infoBox', 'formBox', 'delBox'];
const hideAll = () => boxes.forEach((b) => { $(b).hidden = true; });
boxes.forEach((b) => $(b).addEventListener('click', (e) => { if (e.target === $(b)) $(b).hidden = true; }));
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') hideAll(); });

let current = null; // bilgi penceresinde gösterilen site
let editing = null; // düzenlenen sitenin eski adresi (yeni sitede null)
const hostOf = (u) => { try { return new URL(u).hostname; } catch (_) { return u; } };

function openInfo(s) {
  current = s;
  $('infoTitle').textContent = s.name;
  $('infoText').textContent = s.desc && s.desc.trim() ? s.desc : 'Bu site için açıklama yok.';
  $('infoHost').textContent = hostOf(s.url);
  hideAll();
  $('infoBox').hidden = false;
  $('infoOk').focus();
}
$('infoOk').addEventListener('click', hideAll);
$('infoEdit').addEventListener('click', () => openForm(current));
$('infoDelete').addEventListener('click', () => {
  hideAll();
  $('delText').textContent = current.name + ' listeden silinsin mi?';
  $('delBox').hidden = false;
  $('delCancel').focus();
});
$('delCancel').addEventListener('click', hideAll);
$('delOk').addEventListener('click', async () => {
  hideAll();
  showSites(await window.api.removeSite(current.url));
});

function openForm(s) {
  editing = s ? s.url : null;
  $('formTitle').textContent = s ? 'Siteyi düzenle' : 'Site ekle';
  $('fName').value = s ? s.name : '';
  $('fUrl').value = s ? s.url.replace(/^https:\/\//, '') : '';
  $('fDesc').value = s ? s.desc : '';
  $('fErr').textContent = '';
  hideAll();
  $('formBox').hidden = false;
  (s ? $('fName') : $('fUrl')).focus();
}
$('addSite').addEventListener('click', () => openForm(null));
$('formCancel').addEventListener('click', hideAll);
$('siteForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const r = await window.api.putSite({ name: $('fName').value, url: $('fUrl').value, desc: $('fDesc').value }, editing);
  if (!r || !r.ok) { $('fErr').textContent = (r && r.error) || 'Kaydedilemedi.'; return; }
  hideAll();
  showSites(r.state);
});

// ---- Sitelerim: logo kutucukları (metinler textContent ile yazılır; HTML yorumlanmaz)
const LETTER = ['#ff5a36', '#ff3d87', '#8e44ec', '#2d9cdb', '#1db98a', '#f2a100'];
const hash = (t) => { let h = 0; for (const c of t) h = (h * 31 + c.codePointAt(0)) | 0; return Math.abs(h); };

function showSites(st) {
  if (!st) return;
  const box = $('sites');
  box.textContent = '';
  $('empty').hidden = st.sites.length > 0;
  st.sites.forEach((s) => {
    const tile = document.createElement('div');
    tile.className = 'tile' + (s.url === st.selected ? ' sel' : '');
    tile.setAttribute('role', 'button');
    tile.tabIndex = 0;
    tile.setAttribute('aria-label', s.name);
    const logo = document.createElement('span');
    logo.className = 'logo';
    logo.style.background = LETTER[hash(s.name) % LETTER.length];
    logo.textContent = (s.name.trim()[0] || '?').toUpperCase(); // logo gelene kadar baş harf
    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = s.name;
    const info = document.createElement('button');
    info.className = 'info';
    info.type = 'button';
    info.textContent = 'i';
    info.setAttribute('aria-label', s.name + ' hakkında');
    info.addEventListener('click', (e) => { e.stopPropagation(); openInfo(s); });
    tile.append(logo, name, info);
    const pick = () => window.api.selectSite(s.url);
    tile.addEventListener('click', pick);
    tile.addEventListener('contextmenu', (e) => { e.preventDefault(); openInfo(s); });
    tile.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });
    box.appendChild(tile);
    window.api.siteIcon(s.url).then((src) => {
      if (!src) return;
      const img = document.createElement('img');
      img.alt = '';
      img.src = src;
      logo.textContent = '';
      logo.style.background = 'none';
      logo.appendChild(img);
    });
  });
}

window.api.getSites().then(showSites);
window.api.onSettingsUpdated(show);
window.api.getSettings().then(show);
