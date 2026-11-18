'use strict';
// Siyah ekran: çift tıklama veya Esc ile çık. Ses arka planda çalmaya devam eder.
document.addEventListener('dblclick', () => window.api.exitBlack());
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') window.api.exitBlack();
});
