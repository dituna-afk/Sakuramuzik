# Uygulama Planı: Morphe Yama İndirme Motoru, Behelit & Uzay Sahnesi İyileştirmeleri ve Sade Tema Özelleştirme Sistemi

## 1. Morphe Yama Paketi Entegrasyonu: Çerez Köprüsü (`CookieBridge`), Beğenilenler & Çalma Listesi İndirme ve Hayalet Şarkı Filtresi

Paylaştığın 9 adet Java yama dosyasındaki (`CookieBridge`, `CookieLoginDialog`, `LikedSongsDownloader`, `RemotePlaylistFetcher`, `LocalDownloadManager`, `LocalDownloadsFragment`, `OfflineStorage`, `OfflineTrack`, `OfflinePlaybackService`) çalışan mimari birebir uygulamamıza entegre edilecek:

* **Çift Yönlü Çerez Köprüsü (`CookieBridge` Mimarisi) ve "Çerezler Alınmadı" Hatasının Kesin Çözümü**:
  * "Çerezler alınmadı" uyarısının temel sebebi, WebView üzerinden veya metin yapıştırarak alınan çerezlerin Android'in sistem `CookieManager.getInstance().getCookie("https://music.youtube.com")` havuzu ile yerel `CookieStorageHelper` arasında tam senkronize olmaması ve `SAPISID` / `__Secure-3PAPISID` kontrolünün farklı alan adlarında (`youtube.com` vs `music.youtube.com`) ayrışmasıydı.
  * `CookieBridge.java` içerisindeki gibi doğrudan `https://music.youtube.com` üzerinde `CookieManager` okuma/yazma (`injectCookies` + `cm.flush()`) ve `CookieStorageHelper` ile çift yönlü otomatik senkronizasyon kurulacak.
  * `SAPISIDHASH <timestamp>_<sha1(timestamp + " " + sapisid + " https://music.youtube.com")>` imza üretimi (`getAuthorizationHeader`) ve `/youtubei/v1/account/account_menu?prettyPrint=false` uç noktası üzerinden gerçek oturum doğrulaması (`validateCookies`) birebir eklenecek.
* **Çok Sayfalı Beğenilenler ve Çalma Listesi Çekici (`LikedSongsDownloader` & `RemotePlaylistFetcher`)**:
  * `WEB_REMIX` (`clientVersion: 1.20260101.01.00`, `X-YouTube-Client-Name: 67`, `X-Goog-AuthUser: 0`, `Origin: https://music.youtube.com`) başlıkları ve `continuation` token döngüsü (500 sayfaya kadar) ile Beğenilen Şarkılar (`VLLM`) ve uzaktaki çalma listeleri eksiksiz çekilecek.
  * **Aralık Seçimi (`LocalDownloadsFragment` Tarzı)**: İndirme başlatılırken kullanıcıya **Tüm Şarkılar, İlk 25, 26–50, 51–75, 76–100, Son 25, Son 50, Son 100** aralık seçim diyaloğu sunulacak.
* **Hayalet Şarkı (Ghost Track) Filtresi ve 4 Parçalı (`4-Segment Range`) Hızlı İndirme (`LocalDownloadManager`)**:
  * 11 karakterlik geçerli `videoId` (`^[A-Za-z0-9_-]{11}$`) taşımayan, ses akışı çözümlenemeyen, başlığı/süresi geçersiz olan veya müzik dışı olan "hayalet" kayıtlar kuyruğa girmeden elenecek.
  * İndirilen ses akışları `LocalDownloadManager.java` içindeki gibi `Range: bytes=start-end` başlığıyla **4 paralel segment** (`SEGMENT_COUNT = 4`) halinde `.part` dosyasına yazılıp doğrulanarak kaydedilecek; kapak resmi (`.jpg`) ve meta veri (`.json`) yan dosyaları oluşturulup otomatik olarak **"Beğenilenler / Favorites"** listesine bağlanacak.

---

## 2. Berserk (Guts) ve Cowboy Bebop (Spike) Tema Güncellemeleri

* **Berserk — Sadece Ağlayan Behelit (`BEHELIT_ECLIPSE`)**:
  * Berserk temasında ağlayan Behelit dışındaki diğer tüm animasyon modları (`DRAGONSLAYER_BRAND`, `BEAST_OF_DARKNESS`, `ULTIMATE_ECLIPSE_ALL`) kaldırılacak; yalnızca **Kralın Yumurtası (Ağlayan Behelit)** kalacak.
  * **7 Saniyelik Yüz Toparlama**: Tam ekran oynatıcıya geçildiğinde Behelit'in dağınık göz, burun ve ağız uzuvlarının simetrik yerine oturup uyanması tam **7 saniye (`7000ms`)** sürecek.
  * **Masmavi Gözler**: Behelit'in göz irisleri ve gözbebekleri referans görsele uygun **canlı derin masmavi (`#0066FF` / `#00B4FF`)** renkte parlayacak ve göz pınarlarından kanlı gözyaşları süzülecek.
  * **Uzun ve Sallanan İp**: Behelit'in deri kordonu daha uzun tasarlanacak, biri albüm kapağına diğeri ekranın sağ üst köşesine uzanarak gerçekçi bir sarkaç fiziğiyle salınacak.
* **Cowboy Bebop (Spike) — Saf Siyah Derin Uzay ve İnce Yıldızlar**:
  * Yıldızlı gece sahnesinde arka plan **saf zifiri siyah (`#000000`)** yapılacak.
  * Büyük yıldızlar kaldırılarak gerçek derin uzay hissi veren **çok küçük, iğne ucu boyutunda (`0.4dp – 1.4dp`)** yüzlerce sabit yıldız noktası kullanılacak; son sahnedeki gibi yer değiştirmeden yavaşça sönüp yanacak.

---

## 3. Yeni "Sade Tema" (Minimal Özel Tema): HEX Renkler, Ekran Bazlı Duvar Kağıdı & Opaklık ve Özel Font Yükleme

Tema seçiciye 5. ana tema olarak **"Sade Tema" (`CUSTOM_MINIMAL`)** eklenecek:

* **HEX Renk Kodu Özelleştirme**:
  * Kullanıcı `#RRGGBB` veya `#AARRGGBB` formatında HEX kodu girerek (veya hızlı renk seçiciyle) şu 4 alanı dilediği gibi değiştirebilecek:
    1. **Arka Plan Rengi** (Örn: `#000000`)
    2. **Kart ve Yüzey Rengi** (Örn: `#121212`)
    3. **Ana Vurgu (Accent) Rengi** (Örn: `#E53935`)
    4. **Genel Yazı / Font Rengi** (Örn: `#FFFFFF`)
* **Her Ekran İçin Ayrı Duvar Kağıdı ve Opaklık Ayarı**:
  * Kullanıcı galeriden her ekran için ayrı görsel seçebilecek ve her birinin **Opaklık (`0% – 100%`)** değerini bağımsız kaydırıcılarla ayarlayabilecek:
    1. **Ana Sayfa & Kitaplık / İndirmeler Ekranı Duvar Kağıdı + Opaklık**
    2. **Tam Ekran Müzik Çalar Duvar Kağıdı + Opaklık**
    3. **Mini Oynatıcı (Mini Player) Duvar Kağıdı + Opaklık**
* **Özel Font (`.ttf` / `.otf`) Yükleme**:
  * Kullanıcı cihazından herhangi bir `.ttf` veya `.otf` font dosyası seçip yükleyebilecek. Yüklenen font uygulama içi kalıcı depolamaya kopyalanıp tüm uygulama başlıklarında, şarkı sözlerinde ve listelerde anında aktif olacak.
