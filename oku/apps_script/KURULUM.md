# Oku / Okut — E-Tablo ve sunucu kurulumu

## 1) Kodu yapıştırın
1. Yeni E-Tabloyu açın → **Uzantılar ▸ Apps Script**.
2. Açılan `Code.gs` içindekileri silin, bu klasördeki `Code.gs` dosyasının tamamını yapıştırın, **Kaydet**.
3. **Dağıt ▸ Dağıtımları yönet** → mevcut dağıtımın yanındaki ✏️ → **Sürüm: Yeni sürüm** → **Dağıt**.
   (Adres değişmez; uygulamalar zaten bu adresi kullanıyor.)
4. Adresi tarayıcıda açın: `{"ok":true,"service":"oku",...}` görmelisiniz. Bu sırada sayfalar
   kendiliğinden oluşur.

## 2) Sayfaları doldurun
| Sayfa | Ne yazılır |
|---|---|
| **Öğrenciler** | A: Öğrenci No · B: Ad Soyad · C: Sınıf / Grup (isteğe bağlı, ör. 5A) |
| **Veliler** | A: Veli No · B: Ad Soyad · C: Öğrenci No'ları, virgülle (ör. `101, 102`) |
| **Web Siteleri** | A: Site adı · B: Link (https://…) · C: Açıklama — yalnızca öğrenci uygulamasında görünür |
| **Takvim** | A: Tarih (27.11.2026) · B: Başlangıç (14:00) · C: Bitiş · D: Ders · E: Zoom linki · F: Açıklama · G: Kimler (boş = herkes; öğrenci no veya sınıf, virgülle) |
| **Bildirimler** | A: Tarih · B: Başlık · C: Mesaj · D: Kime (boş = herkes, `öğrenci`, `veli`, ya da no / sınıf) · E: Son gün (isteğe bağlı) |

**Kayıtlar**, **Giriş Kayıtları** ve **Günlük Süre** sayfalarını uygulama doldurur; elle değiştirmeyin.

- Bir öğrencinin numarasını **Öğrenciler**'den silerseniz o öğrenci artık giriş yapamaz.
- Takvimde bugüne ait dersler, uygulamada bildirimlerin en üstünde **sabit ve vurgulu** görünür.
- İleri tarihli bildirim o gün gelir; "Son gün" geçince kaybolur.

## 3) Zoom'un uygulama içinde açılması (isteğe bağlı)
Uygulama Zoom'a kendi içinden katılabilsin diye sunucu bir imza üretir. Gizli anahtar yalnızca
burada durur, uygulamaya konmaz.

1. Apps Script'te sol menüden **Proje Ayarları** (⚙️) → en altta **Betik Özellikleri** → **Betik özelliği ekle**.
2. Özellik: `ZOOM_CLIENT_ID` · Değer: Zoom Marketplace'teki **Client ID**.
3. Bir özellik daha: `ZOOM_CLIENT_SECRET` · Değer: Zoom Marketplace'teki **Client Secret**
   (Basic Information ▸ App Credentials ▸ Client secret ▸ Copy).
4. **Kaydet**. Yeniden dağıtmak gerekmez.

Not: Bu yöntemle yalnızca **aynı Zoom hesabında açılan** toplantılara uygulama içinden katılınır.
Zoom uygulama içi katılım henüz eklenmemişse ya da çalışmazsa, uygulama telefondaki Zoom'u
doğrudan toplantıya açar.
