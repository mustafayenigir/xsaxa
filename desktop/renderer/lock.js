'use strict';
const $ = (id) => document.getElementById(id);
let hasPassword = false;
let busy = false;

function render() {
  $('title').textContent = hasPassword ? 'Şifreyi Gir' : 'Şifre Oluştur';
  $('confirmRow').hidden = hasPassword;
  $('go').textContent = hasPassword ? 'Giriş Yap' : 'Oluştur ve Devam Et';
  $('hint').textContent = hasPassword
    ? 'Şifrenizi unuttuysanız: Windows tuşu + R, ardından %APPDATA%\\Meyvem yazıp açılan klasörü silin ve uygulamayı yeniden başlatın.'
    : 'En az 4 karakterli bir şifre belirleyin. Unutursanız uygulama verisini silerek sıfırlayabilirsiniz.';
}

async function init() {
  const s = await window.api.lockState();
  hasPassword = !!(s && s.hasPassword);
  render();
  $('pwd').focus();
}

$('form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (busy) return;
  busy = true;
  $('go').disabled = true;
  $('msg').textContent = '';
  try {
    const r = hasPassword
      ? await window.api.unlock($('pwd').value)
      : await window.api.createPassword($('pwd').value, $('pwd2').value);
    if (!r.ok) {
      $('msg').textContent = r.error || 'Bir hata oluştu.';
      $('pwd').value = '';
      $('pwd2').value = '';
      $('pwd').focus();
    }
  } finally {
    busy = false;
    $('go').disabled = false;
  }
});

$('settings').addEventListener('click', () => window.api.openSettings());

init();
