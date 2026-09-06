# FaMiLa Print Android

FaMiLa POS için Android Bluetooth etiket yazdırma yardımcı uygulaması.

## İlk hedef
- Xprinter XP-P328B ile Bluetooth üzerinden doğrudan baskı
- Manuel etiket genişliği / yüksekliği / adet
- Marka, ürün adı ve barkod baskısı
- 20 mm altındaki etiketlerde kompakt düzen
- Son kullanılan yazıcı ve ölçüleri hatırlama
- `famila-print://print?...` deep link desteği

## Telefonda kullanım
1. XP-P328B'yi Android Bluetooth ayarlarından eşleştir.
2. GitHub Actions tarafından üretilen `app-debug.apk` dosyasını telefona kur.
3. FaMiLa Print'i aç, yazıcıyı seç.
4. Marka, ürün adı ve barkodu gir.
5. Genişlik, yükseklik ve adet değerlerini gir.
6. `Etiketi Yazdır` düğmesine bas.

## APK üretimi
Her `main` güncellemesinde GitHub Actions otomatik debug APK üretir. GitHub > Actions > Build FaMiLa Print APK > Artifacts bölümünden `FaMiLa-Print-APK` indirilebilir.

## POS entegrasyonu
Uygulama şu örnekteki gibi çağrılabilir:

`famila-print://print?brand=Ravenni&name=RCG%20120&barcode=2907511170094&width=40&height=30&count=1`

Bu sayede ileride FaMiLa POS'taki `Etiketi Yazdır` düğmesi doğrudan FaMiLa Print'i açabilir.
