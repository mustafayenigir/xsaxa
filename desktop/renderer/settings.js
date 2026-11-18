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

window.api.onSettingsUpdated(show);
window.api.getSettings().then(show);
