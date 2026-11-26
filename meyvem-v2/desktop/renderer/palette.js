'use strict';
// Renk teması (Enneagram'dan esinlenen eğlenceli temalar; kişilik testi değildir).
// Renk değişkenleri style.css içinde [data-palette="N"] olarak tanımlı.
const PALETTES = [
  {
    "name": "Meyvem",
    "type": "Klasik",
    "desc": "Neşeli, sıcak ve enerjik. Varsayılan tema.",
    "start": "#ff9a3d",
    "end": "#ff3d87"
  },
  {
    "name": "Kusursuz",
    "type": "Tip 1 · İyileştirici",
    "desc": "Düzenli, ilkeli, kaliteye önem veren biri gibi: berrak okyanus mavisi.",
    "start": "#3fa9f5",
    "end": "#1b4f9c"
  },
  {
    "name": "Candan",
    "type": "Tip 2 · Yardımsever",
    "desc": "Sıcak, cömert, insanlara değer veren: gül pembesi.",
    "start": "#ff8fb1",
    "end": "#e8457c"
  },
  {
    "name": "Parlak",
    "type": "Tip 3 · Başarı Odaklı",
    "desc": "Hedef odaklı, enerjik, ilham veren: altın ve turuncu.",
    "start": "#f0a000",
    "end": "#d9480f"
  },
  {
    "name": "Özgün",
    "type": "Tip 4 · Bireyci",
    "desc": "Derin, yaratıcı, kendine has: lavanta ve mor.",
    "start": "#b06af0",
    "end": "#5b2db8"
  },
  {
    "name": "Bilge",
    "type": "Tip 5 · Araştırmacı",
    "desc": "Meraklı, analitik, derinlemesine düşünen: zümrüt ve petrol.",
    "start": "#1fa37a",
    "end": "#0b5d6b"
  },
  {
    "name": "Güvenilir",
    "type": "Tip 6 · Sadık",
    "desc": "Sadık, sorumluluk sahibi, güven veren: lacivert.",
    "start": "#4e6bdb",
    "end": "#1e2a78"
  },
  {
    "name": "Neşeli",
    "type": "Tip 7 · Maceracı",
    "desc": "Hevesli, hareketli, hep yeni fikirli: yeşil ve turkuaz.",
    "start": "#3dae3a",
    "end": "#0e8f85"
  },
  {
    "name": "Güçlü",
    "type": "Tip 8 · Lider",
    "desc": "Kararlı, cesur, koruyucu: kırmızı ve bordo.",
    "start": "#ff4b3e",
    "end": "#8e0f2b"
  },
  {
    "name": "Huzurlu",
    "type": "Tip 9 · Barışçı",
    "desc": "Sakin, uyumlu, denge arayan: adaçayı ve gök mavisi.",
    "start": "#4fb99a",
    "end": "#3a86c8"
  }
];

function applyPalette(n) {
  const i = Number.isInteger(n) && n >= 0 && n < PALETTES.length ? n : 0;
  document.documentElement.setAttribute('data-palette', String(i));
}
