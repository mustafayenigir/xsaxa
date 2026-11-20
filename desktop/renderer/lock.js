'use strict';
const $ = (id) => document.getElementById(id);
let step = 'enter';
let busy = false;
let signedIn = false;
let pollTimer = null;

async function pollGoogle() {
  const r = await window.api.googleStatus();
  if (step !== 'google') return;
  signedIn = !!(r && r.signedIn);
  $('go').textContent = signedIn ? 'Devam Et' : VIEW.google.button;
  $('msg').textContent = signedIn ? 'Oturum açıldı ✓' : '';
}

const INFO_NO = 'Yetkili kişiler giriş numaranızı (örnek: 00xxxxxx-xxxxx) yalnızca bir kere gireceksiniz. '
  + 'Sonraki açılışlarda yalnızca şifreniz sorulur.';
const INFO_PASSWORD = 'En az 4 karakterli bir şifre belirleyin.\n\n'
  + 'Şifrenizi unutursanız uygulamayı silip yeniden yüklemeniz gerekir '
  + '(Windows: %APPDATA%\\Meyvem klasörünü silip uygulamayı yeniden başlatın).';
const INFO_GOOGLE = 'Web sitelerine girebilmek için Google hesabınızla oturum açın. '
  + 'Bu oturum, uygulamadaki tüm sitelerde geçerli olur; her sitede yeniden giriş yapmanız gerekmez.';
const INFO_SITE = 'Şimdi Ayarlar açılacak. Görüntülemek istediğiniz web sitesini seçip Devam Et\'e tıklayın.';

const VIEW = {
  no: { title: 'Giriş Numarası', info: INFO_NO, button: 'Devam Et' },
  password: { title: 'Şifre Oluştur', info: INFO_PASSWORD, button: 'Kaydet' },
  google: { title: 'Google Hesabı', info: INFO_GOOGLE, button: 'Google ile Oturum Aç' },
  site: { title: 'Web Sitesi', info: INFO_SITE, button: 'Ayarları Aç' },
  enter: { title: 'Şifreyi Gir', info: '', button: 'Giriş Yap' },
};

function render(s) {
  step = VIEW[s] ? s : 'no';
  const v = VIEW[step];
  $('title').textContent = v.title;
  $('info').textContent = v.info;
  $('info').hidden = !v.info;
  $('go').textContent = v.button;
  $('noRow').hidden = step !== 'no';
  $('pwd').hidden = !(step === 'password' || step === 'enter');
  $('confirmRow').hidden = step !== 'password';
  $('hint').textContent = '';
  $('msg').textContent = '';
  clearInterval(pollTimer);
  signedIn = false;
  if (step === 'google') {
    pollTimer = setInterval(pollGoogle, 1500);
    pollGoogle();
  }
  const first = { no: 'no', password: 'pwd', enter: 'pwd' }[step];
  if (first) $(first).focus();
}

async function init() {
  const s = await window.api.lockState();
  render(s && s.step);
}

$('form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (busy) return;
  busy = true;
  $('go').disabled = true;
  $('msg').textContent = 'Kontrol ediliyor…';
  let keepDisabled = false;
  try {
    let r;
    if (step === 'no') r = await window.api.checkNo($('no').value);
    else if (step === 'password') r = await window.api.createPassword($('pwd').value, $('pwd2').value);
    else if (step === 'google') {
      if (signedIn) {
        r = await window.api.googleDone();
      } else {
        const o = await window.api.googleOpen();
        r = o && o.signedIn ? await window.api.googleDone() : { ok: true, stay: true };
        if (r.stay) $('msg').textContent = 'Açılan pencerede Google hesabınızla oturum açın.';
      }
      if (r.stay) { busy = false; $('go').disabled = false; return; }
    } else if (step === 'site') {
      r = await window.api.siteStart();
      r = { ...r, stay: true };
      busy = false; $('go').disabled = false;
      return;
    }
    else r = await window.api.unlock($('pwd').value);

    if (r && r.step && r.step !== step) {
      const err = r.ok ? '' : r.error;
      $('pwd').value = '';
      $('pwd2').value = '';
      render(r.step);
      $('msg').textContent = err || '';
    } else if (r && r.ok) {
      // Site açılıyor (enter adımı): sayfa birazdan değişecek
      $('msg').textContent = 'Site açılıyor…';
      keepDisabled = true;
    } else {
      $('msg').textContent = (r && r.error) || 'Bir hata oluştu.';
      if (step === 'enter' || step === 'password') {
        $('pwd').value = '';
        $('pwd2').value = '';
        $('pwd').focus();
      }
    }
  } catch (_) {
    $('msg').textContent = 'Bir hata oluştu.';
  } finally {
    busy = false;
    if (!keepDisabled) $('go').disabled = false;
  }
});

$('settings').addEventListener('click', () => window.api.openSettings());

init();
