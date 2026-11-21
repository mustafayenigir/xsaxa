'use strict';
const $ = (id) => document.getElementById(id);

function show(s) {
  if (!s) return;
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
    const tile = document.createElement('button');
    tile.className = 'tile' + (l.url === st.selected ? ' sel' : '');
    tile.title = l.desc ? l.name + ' — ' + l.desc : l.name; // üzerine gelince adı görünür
    tile.setAttribute('aria-label', l.name);
    const letter = document.createElement('span');
    letter.textContent = (l.name.trim()[0] || '?').toUpperCase(); // logo gelene kadar baş harf
    tile.appendChild(letter);
    tile.addEventListener('click', async () => {
      const r = await window.api.selectSite(l.url);
      if (r && !r.opened) showSites(r, 'Seçildi. Giriş yapınca bu site açılacak.');
    });
    box.appendChild(tile);
    window.api.siteIcon(l.url).then((src) => {
      if (!src) return;
      const img = document.createElement('img');
      img.alt = '';
      img.src = src;
      tile.textContent = '';
      tile.appendChild(img);
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
