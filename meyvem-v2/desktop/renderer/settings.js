'use strict';
const $ = (id) => document.getElementById(id);

function renderPalettes(cur) {
  const box = $('palettes');
  if (!box.childElementCount) {
    PALETTES.forEach((p, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'pal';
      b.setAttribute('aria-label', p.name + ', ' + p.type);
      const sw = document.createElement('span'); sw.className = 'sw';
      sw.style.background = 'linear-gradient(135deg, ' + p.start + ', ' + p.end + ')';
      const n = document.createElement('span'); n.className = 'pn'; n.textContent = p.name;
      const t = document.createElement('span'); t.className = 'pt'; t.textContent = p.type;
      b.append(sw, n, t);
      b.addEventListener('click', () => change({ palette: i }));
      box.appendChild(b);
    });
  }
  [...box.children].forEach((b, i) => {
    b.classList.toggle('sel', i === cur);
    b.firstChild.textContent = i === cur ? '✓' : '';
  });
  const p = PALETTES[cur] || PALETTES[0];
  $('palDesc').textContent = p.name + ' (' + p.type + '): ' + p.desc;
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

const PALETTE = ['#ff5a36', '#ff3d87', '#8e44ec', '#2d9cdb', '#1db98a', '#f2a100'];
const hash = (t) => { let h = 0; for (const c of t) h = (h * 31 + c.codePointAt(0)) | 0; return Math.abs(h); };

function openInfo(l) {
  $('dlgTitle').textContent = l.name;
  $('dlgText').textContent = l.desc && l.desc.trim() ? l.desc : 'Bu site için açıklama yok.';
  $('overlay').hidden = false;
  $('dlgOk').focus();
}
$('dlgOk').addEventListener('click', () => { $('overlay').hidden = true; });
$('overlay').addEventListener('click', (e) => { if (e.target === $('overlay')) $('overlay').hidden = true; });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') $('overlay').hidden = true; });

// ---- Web siteleri: logo kutucukları (metinler/logolar textContent ve img.src ile yazılır; HTML yorumlanmaz)
function showSites(st, note) {
  const box = $('sites');
  box.textContent = '';
  const msg = $('siteMsg');
  const setMsg = (t) => { msg.textContent = t || ''; msg.hidden = !t; };
  if (!st || !st.registered) {
    $('siteRefresh').hidden = true;
    setMsg('Site listesi, giriş numaranızla kayıt olduktan sonra görünür.');
    return;
  }
  $('siteRefresh').hidden = false;
  if (!st.links.length) { setMsg('Listede site bulunamadı.'); return; }
  setMsg(note || '');
  st.links.forEach((l) => {
    const tile = document.createElement('div');
    tile.className = 'tile' + (l.url === st.selected ? ' sel' : '');
    tile.setAttribute('role', 'button');
    tile.tabIndex = 0;
    tile.setAttribute('aria-label', l.name);
    const logo = document.createElement('span');
    logo.className = 'logo';
    logo.style.background = PALETTE[hash(l.name) % PALETTE.length];
    logo.textContent = (l.name.trim()[0] || '?').toUpperCase(); // logo gelene kadar baş harf
    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = l.name;
    const info = document.createElement('button');
    info.className = 'info';
    info.type = 'button';
    info.textContent = 'i';
    info.setAttribute('aria-label', l.name + ' hakkında');
    info.addEventListener('click', (e) => { e.stopPropagation(); openInfo(l); });
    tile.append(logo, name, info);
    const pick = async () => {
      const r = await window.api.selectSite(l.url);
      if (r && !r.opened) showSites(r, 'Seçildi. Giriş yapınca bu site açılacak.');
    };
    tile.addEventListener('click', pick);
    tile.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });
    box.appendChild(tile);
    window.api.siteIcon(l.url).then((src) => {
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

async function refreshSites() {
  $('siteRefresh').disabled = true;
  try {
    const st = await window.api.refreshSites();
    showSites(st, st && st.refreshed ? '' : 'Liste yenilenemedi. Kayıtlı liste gösteriliyor.');
  } finally {
    $('siteRefresh').disabled = false;
  }
}
$('siteRefresh').addEventListener('click', refreshSites);
window.api.getSites().then((st) => { showSites(st); if (st && st.registered) refreshSites(); });

window.api.onSettingsUpdated(show);
window.api.getSettings().then(show);

document.getElementById('zoomJoin').addEventListener('click', () => window.api.openZoom());
