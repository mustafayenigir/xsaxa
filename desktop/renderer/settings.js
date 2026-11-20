'use strict';
const $ = (id) => document.getElementById(id);

function show(s) {
  if (!s) return;
  $('keepAudio').checked = s.keepAudio;
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
document.querySelectorAll('input[name="theme"]').forEach((r) => {
  r.addEventListener('change', () => { if (r.checked) change({ theme: r.value }); });
});
$('zoom').addEventListener('input', (e) => change({ zoom: Number(e.target.value) }));
$('minus').addEventListener('click', () => change({ zoom: Number($('zoom').value) - 10 }));
$('plus').addEventListener('click', () => change({ zoom: Number($('zoom').value) + 10 }));
$('reset').addEventListener('click', () => change({ zoom: 100 }));
$('close').addEventListener('click', () => window.close());

// ---- Web sitesi listesi (metinler textContent ile yazılır; sunucudan gelen veri HTML olarak yorumlanmaz)
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
  $('siteContinue').hidden = !st.firstRun;
  $('siteContinue').disabled = !st.selected;
  if (!st.links.length) { setMsg('Listede site bulunamadı.'); return; }
  setMsg(note || '');
  st.links.forEach((l) => {
    const label = document.createElement('label');
    label.className = 'opt';
    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = 'site';
    radio.checked = l.url === st.selected;
    radio.addEventListener('change', async () => {
      if (radio.checked) showSites(await window.api.selectSite(l.url), 'Site değiştirildi.');
    });
    const text = document.createElement('span');
    const strong = document.createElement('strong');
    strong.textContent = l.name;
    text.appendChild(strong);
    if (l.desc) {
      const d = document.createElement('div');
      d.className = 'hint';
      d.style.margin = '2px 0 0';
      d.textContent = l.desc;
      text.appendChild(d);
    }
    label.appendChild(radio);
    label.appendChild(text);
    box.appendChild(label);
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
$('siteContinue').addEventListener('click', async () => {
  const r = await window.api.continueSite();
  if (r && !r.ok) { $('siteMsg').textContent = r.error || 'Devam etmek için bir site seçin.'; $('siteMsg').hidden = false; }
});
window.api.getSites().then((st) => { showSites(st); if (st && st.registered) refreshSites(); });

window.api.onSettingsUpdated(show);
window.api.getSettings().then(show);
