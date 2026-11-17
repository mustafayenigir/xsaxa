# SCJ Eğitim — Android Uygulaması

`https://scjegitim.blogspot.com` sitesini açan, şifre kilitli, ekran görüntüsü engelli
ve ekran kapanınca sesi devam ettiren tek görevli bir Android uygulaması.

## Özellikler
- 🔒 **Şifre kilidi:** İlk açılışta şifre oluşturulur, sonraki açılışlarda sorulur. Şifre
  cihazda düz metin olarak değil, tuzlu SHA-256 özeti olarak saklanır.
- 🗑️ **Şifre sıfırlama:** Şifre unutulursa, uygulama silinip yeniden kurulduğunda şifre sıfırlanır
  (yedekleme kapalı olduğu için buluttan geri gelmez).
- 📵 **Ekran görüntüsü / ekran kaydı engeli:** Banka uygulamalarındaki `FLAG_SECURE` koruması.
  Tüm uygulamada ve son uygulamalar ekranında geçerlidir.
- 🔊 **Arka planda çalma:** Ekran kapandığında site kapanmaz; ön plan servisi sayesinde
  HTML5 ses/video çalmaya devam eder.

> **Önemli not:** Site içindeki video **YouTube/Vimeo gibi gömülü oynatıcı** ise, bu platformlar
> arka plan oynatmayı kendileri engeller; bu bir uygulama kısıtı değildir ve aşılamaz.
> Ayrıca ekran görüntüsü engeli, telefon **root'lu** ise veya karşıdan ikinci bir cihazla
> fotoğraf çekilirse %100 koruma sağlamaz (bu her uygulama için geçerlidir).

---

## APK nasıl alınır? (2 yol)

### Yol A — GitHub ile, bilgisayara hiçbir şey kurmadan (önerilen)
1. [github.com](https://github.com) adresinde ücretsiz hesap açın.
2. Yeni bir depo (repository) oluşturun (örn. `scj-egitim`). **Public** olması yeterli.
3. Bu klasördeki tüm dosyaları depoya yükleyin
   (GitHub sitesinde **Add file → Upload files** ile sürükleyip bırakabilirsiniz;
   `.github` klasörünü de yüklemeyi unutmayın).
4. Yükleme biter bitmez **Actions** sekmesine gidin. "APK Derle" işi otomatik başlar
   (başlamazsa soldan seçip **Run workflow** deyin).
5. Yeşil tik çıkınca işe tıklayın → en altta **Artifacts** bölümünden `scj-egitim-apk`
   dosyasını indirin. İçinden `app-debug.apk` çıkar.

### Yol B — Kendi bilgisayarınızda (Android Studio veya terminal)
Android Studio ile `SCJEgitim` klasörünü açıp **Build → Build APK** diyebilir,
ya da terminalde şunu çalıştırabilirsiniz:
```bash
./gradlew assembleDebug
```
APK şurada oluşur: `app/build/outputs/apk/debug/app-debug.apk`

---

## APK telefona nasıl kurulur?
1. `app-debug.apk` dosyasını telefona aktarın (kablo, e-posta, Drive vb.).
2. Dosyaya dokunun. "Bilinmeyen kaynaklara izin ver" uyarısı çıkarsa izin verin
   (Ayarlar → Uygulama kur izni).
3. Kurulumdan sonra ilk açılışta şifrenizi oluşturun.

## Ayarları değiştirmek isterseniz
- **Açılacak site:** `app/src/main/java/com/scjegitim/app/MainActivity.kt` içindeki `siteUrl`.
- **Uygulama adı:** `app/src/main/res/values/strings.xml` içindeki `app_name`.
- **Renkler:** `app/src/main/res/values/colors.xml`.
