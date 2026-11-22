'use strict';
// Hiç site yokken ana pencerede görünen karşılama sayfası.
window.api.getSettings().then((s) => s && applyPalette(s.palette));
window.api.onSettingsUpdated((s) => s && applyPalette(s.palette));
document.getElementById('home').addEventListener('click', () => window.api.openHome());
