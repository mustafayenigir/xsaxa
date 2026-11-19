# Meyvem sunucu kurulumu (Google Apps Script)

Uygulamalar `apps_script/Code.gs` dosyasındaki kodla konuşur. Bu kodu **bir kere** yapıştırıp yeniden dağıtmanız yeterli; web uygulaması adresi (`.../exec`) **değişmez**.

## 1. Kodu yapıştır
1. E-Tablonuzu açın → **Uzantılar → Apps Script**.
2. Soldaki `Code.gs` içindeki her şeyi silin, `apps_script/Code.gs` dosyasının tamamını yapıştırın. **Kaydet** (disket simgesi).

## 2. Sayfaları oluştur
En kolayı: kodu yapıştırıp yeni sürüm dağıttıktan sonra (3. adım) **adresi tarayıcıda açın**; eksik sayfalar kendiliğinden oluşur. Elle yapmak isterseniz:
1. Üstteki fonksiyon listesinden **`kurulum`** seçip **Çalıştır**'a basın.
2. İlk seferde izin ister: *İzinleri incele → hesabınız → Gelişmiş → (güvenli değil) git → İzin ver*. (E-Tablonuza erişim izni, kendi yazdığınız kod için normaldir.)
3. Eksik sayfalar başlıklarıyla oluşturulur; var olanlara **dokunulmaz**:
   - `yetkili kişiler giriş no` — A sütunu: giriş numaraları (2. satırdan başlayarak). B sütununa kişinin Gmail adresi yazılır.
   - `yoklama` — A: numara, B: UUID, C: şifre özeti, D: süre (s:dd:ss), E ve sonrası: giriş tarihleri.
   - `web site linkleri` — A: kısa tanım, B: link (**https** ile başlamalı), C: açıklama.
4. `yetkili kişiler giriş no` sayfasında **A sütununu seçip Biçim → Sayı → Düz metin** yapın; böylece `00123456-…` gibi numaralardaki baştaki sıfırlar kaybolmaz. (`kurulum` bunu sizin için de dener.)

## 3. Yeniden dağıt (adres aynı kalır)
1. **Dağıt → Dağıtımları yönet**.
2. Mevcut dağıtımın yanındaki **kalem** simgesi → **Sürüm: Yeni sürüm** → **Dağıt**.
   - Yeni dağıtım **oluşturmayın**; oluşturursanız adres değişir.
3. Dağıtım ayarları: *Şu kişi olarak çalıştır: Ben* — *Erişimi olan: Herkes*.

## 4. Denemek için
Tarayıcıda adresi açın: `{"ok":true,"service":"meyvem"}` görmelisiniz.
(Daha önce "Script function not found: doGet" görüyordunuz; kod yapıştırılıp yeni sürüm dağıtılınca düzelir.)

## Notlar
- Yeni kişi eklemek: `yetkili kişiler giriş no` sayfasının A sütununa numarayı yazmanız yeter.
- Siteyi değiştirmek: `web site linkleri` sayfasına satır ekleyin/silin. Kullanıcılar Ayarlar'dan seçer; ilk satır varsayılandır.
- Şifre **düz metin değil**, tuzlu bir özet (64 karakter) olarak C sütununa yazılır.
- Bir kişi uygulamayı silip yeniden kurarsa aynı numarayla yeniden kayıt olur: aynı satır güncellenir (yeni UUID/şifre özeti), süre ve eski tarihler korunur.
- Google kota: tüketici hesaplarında günlük toplam betik çalışma süresi sınırlıdır. Uygulama süreyi 10 dakikada bir toplu gönderir; yine de çok kullanıcı olursa kota dolabilir (Workspace hesabında sınır daha yüksektir).
