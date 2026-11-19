'use strict';
const $ = (id) => document.getElementById(id);
let step = 'enter';
let busy = false;

const INFO_NO = 'Yetkili kişiler giriş numaranızı (örnek: 00xxxxxx-xxxxx) yalnızca bir kere gireceksiniz. '
  + 'Sonraki açılışlarda yalnızca şifreniz sorulur.\n\nGiriş tarihleriniz ve uygulamada geçirdiğiniz süre kaydedilir.';
const INFO_PASSWORD = 'En az 4 karakterli bir şifre belirleyin.\n\n'
  + 'Şifrenizi unutursanız uygulamayı silip yeniden yüklemeniz gerekir '
  + '(Windows: %APPDATA%\\Meyvem klasörünü silip uygulamayı yeniden başlatın).';
const INFO_GMAIL = 'Gmail adresiniz yalnızca bu uygulamaya özel olarak kullanılmaktadır ve web sitesine giriş için şart ve zorunludur.';

const VIEW = {
  no: { title: 'Giriş Numarası', info: INFO_NO, button: 'Devam Et' },
  password: { title: 'Şifre Oluştur', info: INFO_PASSWORD, button: 'Kaydet' },
  gmail: { title: 'Gmail Adresi', info: INFO_GMAIL, button: 'Kaydet ve Devam Et' },
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
  $('gmailRow').hidden = step !== 'gmail';
  $('hint').textContent = '';
  $('msg').textContent = '';
  const first = { no: 'no', password: 'pwd', gmail: 'gmail', enter: 'pwd' }[step];
  $(first).focus();
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
    else if (step === 'gmail') r = await window.api.saveGmail($('gmail').value);
    else r = await window.api.unlock($('pwd').value);

    if (r && r.step && r.step !== step) {
      const err = r.ok ? '' : r.error;
      $('pwd').value = '';
      $('pwd2').value = '';
      render(r.step);
      $('msg').textContent = err || '';
    } else if (r && r.ok) {
      // Site açılıyor (gmail / enter adımı): sayfa birazdan değişecek
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
