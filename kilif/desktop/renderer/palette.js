'use strict';
// Renk teması. Renk değişkenleri style.css içinde [data-palette="N"] olarak tanımlı.
const PALETTES = [
  { name: 'Gün Batımı', start: '#ff9a3d', end: '#ff3d87' },
  { name: 'Okyanus', start: '#3fa9f5', end: '#1b4f9c' },
  { name: 'Lavanta', start: '#b06af0', end: '#5b2db8' },
];

function applyPalette(n) {
  const i = Number.isInteger(n) && n >= 0 && n < PALETTES.length ? n : 0;
  document.documentElement.setAttribute('data-palette', String(i));
}
