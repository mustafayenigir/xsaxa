# Kitap + Üye Yönetim — Kurulum

1. Excel'i açın → **Uzantılar → Apps Script**.
2. `Code.gs` içindeki her şeyi silin, bu dosyanın tamamını yapıştırın, **Kaydet**.
3. **Dağıt → Dağıtımları yönet → (kalem) Düzenle → Sürüm: Yeni sürüm → Dağıt.**
   (Adres aynı kalır; uygulamalar bu adrese bağlı.)
4. Tarayıcıda web uygulaması adresini bir kez açın. `{"ok":true,"service":"kitap",...}` görmelisiniz.
   Eksik sayfalar kendiliğinden oluşur (var olan "Kuran" sayfanıza dokunulmaz).
5. **Yöneticiler** sayfasında A sütunundaki kod (ör. `Y-123456`) sizin yönetici kodunuzdur.
   Üye Yönetim uygulamasına bu kodla girip kendi şifrenizi belirleyin.
6. **Kişiler** sayfasına kişileri yazın: A yetki kodu, B ad soyad, C grup (isteğe bağlı).
   Şifre (D) boş kalır; kişi ilk girişte kendisi belirler ve buraya yazılır.

Notlar
- Şifreler D sütununda açıkça yazar. Şifreyi değiştirirseniz kişi yeni şifreyle girer.
- Kişiyi kapatmak için E sütununa `Pasif` yazın.
- Kişi telefon değiştirirse kodu ve şifresiyle yeni telefonda girebilir.
- "Kuran" sayfasını değiştirirseniz Apps Script'te bir kez `kuranOnbellekTemizle` çalıştırın.
