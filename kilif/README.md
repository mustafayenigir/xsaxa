# Kılıf

Kendi eklediğin web sitelerini tek bir yerde, ekran görüntüsü engelli ve ekran kapanınca
sesi devam ettiren bir pencerede açan uygulama. Android ve Windows sürümü var.

**İndir**
- Android: https://github.com/mustafayenigir/xsaxa/releases/download/kilif-android/Kilif.apk
- Windows: https://github.com/mustafayenigir/xsaxa/releases/download/kilif-windows/Kilif.exe

## Özellikler
- **Sitelerim:** İstediğin kadar site ekle, düzenle, sil. Her sitenin logosu ve adı görünür;
  sağ alttaki ⓘ açıklamayı gösterir. Siteler yalnızca cihazda saklanır.
- Şifre, kayıt, Google hesabı ya da E-Tablo bağlantısı yok; uygulama açılınca son site açılır.
- Ekran görüntüsü / ekran kaydı engeli (Android `FLAG_SECURE`, Windows içerik koruması).
- Ekran kapanınca ses ve video çalmaya devam eder; siyah ekran modu.
- Reklam engelleme, gece/gündüz görünümü, yazı boyutu, 3 renk teması (Gün Batımı, Okyanus, Lavanta).
- Android: sürüklenebilir yüzen düğme (tek dokunuş menü, çift dokunuş siyah ekran);
  sitede geri gidecek sayfa kalmayınca Ana Sayfa, bir daha geri = çıkış.

## Klasörler
- `app/` — Android (Kotlin, paket `com.kilif.app`)
- `desktop/` — Windows (Electron, taşınabilir `Kilif.exe`)

Derleme GitHub Actions ile yapılır (`.github/workflows/kilif-android.yml`, `kilif-windows.yml`).
