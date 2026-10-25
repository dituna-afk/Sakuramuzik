# Sakura Music — Revize Edilmiş Performans, İndirme Merkezi ve Griffith Teması Uygulama Planı

Bu revize plan, ilettiğin **A–G maddelerindeki tüm mühendislik kurallarını**, mevcut kod tabanının gerçek mimarisiyle birebir eşleştirerek uygular.

---

## A. Gerçek Darboğaz Analizi ve Ölçüm Şeffaflığı

### 1. Ortam Ölçüm Sınırları (Açık Beyan)
- Bulut derleme ortamında fiziksel bir Android cihaz veya ADB/Emülatör bağlantısı bulunmadığından **Android Studio Profiler, Perfetto, FrameMetrics veya cihaz üstü Macrobenchmark** çalıştırılamamaktadır. Bu nedenle **tahmini FPS veya cihaz kare süresi (ms) değerleri gerçek ölçüm gibi sunulmayacaktır**.
- **Yapılacak Gerçek Ölçümler:**
  - **JVM / Robolectric Performans ve Bellek Ölçümleri (`ExampleRobolectricTest.kt` & `ExampleUnitTest.kt`):**
    1. Büyük oynatma listesi (500–2000 öğe) JSON ayrıştırma süresi (ms) ve bellek tahsisi.
    2. `ViewportWindowOptimizer` öncesi ve sonrası kaydırma simülasyonunda oluşan durum değişimi (state mutation) ve öğe yeniden bağlama sayısı.
    3. Çoklu indirme kuyruğu (100+ öğe) ilerleme güncellemelerinde durum emisyonu (emission) sıklığı ve CPU süresi.
    4. Soğuk başlangıç (`MainActivity` + `YtMusicMainScreen` ilk kurulum) ve 5 sekme arasında ardışık geçiş süresi (Robolectric milisaniye ölçümü).
  - **Hedef:** 60 Hz ekranlarda **16,67 ms/kare**, 120 Hz ekranlarda **8,33 ms/kare** bütçesini hedef olarak değerlendirmek ve ana iş parçacığındaki (Main Thread) kare başı işi ile gereksiz recomposition tetikleyicilerini azaltmak (bunları garanti olarak sunmadan).
  - **Ölçüm Dürüstlüğü ve Gerçek Cihaz Senaryoları:** Robolectric veya JVM testleri gerçek FPS ve kaydırma akıcılığının kesin kanıtı olarak kabul edilmeyecek; fiziksel cihaz veya ADB bağlantısı olmadan gerçek cihaz performansı ölçüldüğü iddia edilmeyecektir. Ölçüm öncesi ve sonrası JVM sonuçları karşılaştırılacak, gerçek cihazda doğrulanabilecek senaryolar (soğuk açılış, sekme geçişi, hızlı kaydırma, indirme sırasında kullanım) açıkça listelenecek ve bulut ortamında ölçülemeyen metrikler teslim raporunda açıkça belirtilecektir.

### 2. Kod İncelemesinde Tespit Edilen Kanıtlanmış Darboğazlar
1. **`ViewportWindowOptimizer.kt`:** `rememberProgressiveWindowState` fonksiyonu `snapshotFlow { listState.layoutInfo.visibleItemsInfo }` dinleyerek kaydırma sırasında `loadedCount` değerini `40`'ar öğe artırıyor ve `activeRange` (`firstVisible - 8 .. lastVisible + 14`) dışındaki her satır için `isTrackInActiveMemoryWindow(index) = false` döndürüyor. Bu da `HomeScreen.kt` (satır 598) ve `LibraryScreen.kt` içinde görünür pencereden çıkan her satırda `AsyncImage` bileşeninin ağaçtan sökülüp (`else -> Box`) geri kaydırıldığında sıfırdan oluşturulmasına yol açıyor.
2. **`YtMusicMainScreen.kt`:**
   - Kök `Box` üzerinde `TouchSakuraBlossomOverlay` her dokunuşta ve kaydırma hareketinde parçacık listesini güncelliyor.
   - `val youtubeDLState by viewModel.youtubeDLState.collectAsState()` üst bileşende dinlenip `HomeScreen` ve diğer alt ekranlara doğrudan geçiriliyor; indirme sırasında gelen her ilerleme yüzdesi ana ekranı yeniden çiziyor.
   - Sekme geçişlerinde (`currentTab = 0..4`) aktif olmayan ekranlar ağaçtan tamamen kaldırıldığı için bazı yerel `remember` durumları sıfırlanabiliyor; aynı zamanda arka plan sahne animasyonları (`SakuraTreeWallpaperStage`, `AnimeThemeStageVisuals`) liste kaydırılırken de tam hızda çizim yapıyor.
3. **`HomeScreen.kt`:** Ana ekranın kendi `LazyColumn` listesinin en üstünde `PendingDownloadQueueCard` (içinde 220.dp yüksekliğinde ikinci bir `LazyColumn`) ve `ResolvedPlaylistSelectionCard` (içinde 280.dp yüksekliğinde üçüncü bir `LazyColumn`) yer alıyor.

---

## B. Liste Sanallaştırması ve Görsel Önbelleği

### 1. `ViewportWindowOptimizer.kt` Bağımlılık Haritası
Kod tabanında yapılan taramada `ViewportWindowOptimizer.kt` şu **6 dosyada** kullanılmaktadır:
- `app/src/main/java/com/example/ui/screens/HomeScreen.kt` (`windowState`)
- `app/src/main/java/com/example/ui/screens/LibraryScreen.kt` (`windowState`)
- `app/src/main/java/com/example/ui/screens/SearchScreen.kt` (`windowState`)
- `app/src/main/java/com/example/ui/screens/PlaylistScreens.kt` (`gridWindow`, `listWindow`, `detailWindow`, `addDialogWindow`)
- `app/src/main/java/com/example/ui/screens/VaultAndSyncScreens.kt` (`windowState`, `subWindowState`)
- `app/src/main/java/com/example/ui/components/CookieComponents.kt` (`cookieWindow`, `logWindow`)

### 2. Güvenli Sanallaştırma Stratejisi
- **Tüm Şarkıların Aynı Anda Oluşturulmasını Önleme:** `LazyColumn` ve `LazyVerticalGrid` zaten yalnızca ekranda görünen (+ prefetch) öğeleri besteler (compose eder). `ViewportWindowOptimizer.kt` API imzasını (`ProgressiveWindowState`, `GridProgressiveWindowState`) koruyarak 6 dosyanın hiçbirinde kırılma yaratmayacağız; ancak:
  - Kaydırma sırasında `.take(loadedCount)` ile veri kümesini sürekli yeniden boyutlandırma (dataset mutation) yerine `LazyColumn`'un kendi sanallaştırmasını kullanacağız.
  - `isTrackInActiveMemoryWindow(index)` fonksiyonunun kaydırma esnasında kapak görsellerini sürekli söküp takmasını (`AsyncImage` <-> `Box` geçişi) kaldıracağız; böylece `LazyColumn` öğe geri dönüşümü (item reuse) ve Coil bellek önbelleği doğal şekilde çalışacak.
  - Tüm `itemsIndexed` ve `items` çağrılarında kararlı kimlikler (`key`) ve uygun `contentType` değerleri kullanarak yanlış satır durumlarını ve gereksiz yeniden oluşturmayı azaltacağız.
  - Kapak görsellerinde sabit yer tutucu boyutları (`Modifier.size(...)`) kullanarak kaydırma sırasında yerleşim sıçramalarını (layout jumps) önleyeceğiz.

### 3. Mevcut Coil `ImageLoader` Mimarisi (`SakuraMusicApplication.kt`)
- İnceleme sonucunda `SakuraMusicApplication.kt` (satır 85–128) içinde zaten tekil `ImageLoaderFactory`, `onTrimMemory` ve `onLowMemory` yönetiminin bulunduğu doğrulandı.
- **Körlemesine Değişiklik Yapmama:** Mevcut `SakuraMusicApplication.kt` içindeki çalışan Coil önbelleğini körlemesine değiştirmeyeceğiz; yalnızca kod incelemesiyle gerekçelendirilen iyileştirmeleri yapacağız:
  - Küçük liste kapaklarında (`48.dp` / `144px`) bellek dostu çözünürlük (`Size(144, 144)`) ve `allowRgb565(true)` kullanırken,
  - Tam ekran oynatıcı (`FullPlayerOverlay`) ve büyük albüm/playlist kapaklarında renk geçişlerinde bantlanma (banding) olmaması için `Bitmap.Config.ARGB_8888` (`allowRgb565(false)`) ve yüksek görüntü kalitesini koruyacağız.
  - Bellek baskısı (`onTrimMemory` / `onLowMemory`) altında görsel önbelleğinin mevcut mekanizma ile güvenli biçimde küçülmesini koruyacağız.

---

## C. Ana Ekran, Sekmeler ve Yeniden Çizim (Recomposition) İzolasyonu

1. **Sekme Geçişleri ve Durum Koruma:**
   - `HomeScreen`, `LibraryScreen`, `SearchScreen`, `PlaylistsTabScreen` ve `DownloadsVaultScreen` için `LazyListState` ve arama/filtre durumlarını `YtMusicMainScreen` seviyesinde (`rememberSaveable`) tutarak sekmeler arasında geçildiğinde kaydırma konumunun sıfırlanmasını ve verilerin tekrar yüklenmesini önleyeceğiz.
2. **İndirme, Oynatma ve Animasyon İlerlemesi İzolasyonu (`YtMusicMainScreen`, `HomeScreen`, `LibraryScreen`, `SearchScreen`, `MiniPlayerBar`, `FullPlayerOverlay`):**
   - Bu 6 bileşendeki gereksiz recomposition kaynakları tek tek incelenerek oynatma konumu (`currentPositionMs`), indirme yüzdesi (`progress`) ve animasyon durumlarının tüm ana ekranı veya şarkı listesini tekrar çizmesi engellenecek; sık değişen durumlar yalnızca ilgili küçük bileşenlerde (`MiniPlayerBar`, `FullPlayerOverlay` ilerleme çubuğu, `CompactDownloadStatusBanner` ve `DownloadQueueCenterScreen` satırı) gözlemlenecek.
   - `remember`, `derivedStateOf`, kararlı parametreler ve doğru liste anahtarları yalnızca gerçek arayüz takılmalarını azaltacak ihtiyaç noktalarında kullanılacak; fayda sağlamayan teorik/gereksiz optimizasyonlar eklenmeyecek.
   - Şarkı satırlarına tüm `currentTrack` nesnesi yerine yalnızca `isPlaying: Boolean` (`currentTrack?.id == track.id`) aktarılacak.
3. **Yaşam Döngüsü, Bellek ve Arka Plan İşlerinin Yönetimi:**
   - `SakuraTreeWallpaperStage`, `AnimeThemeStageVisuals` ve uçan şahin animasyonu yalnızca ilgili ekran görünürken ve uygulama ön plandayken (`Lifecycle.State.STARTED`) çalışacak; ekran görünmez olduğunda veya ayar kapatıldığında (`sakuraVisualizerEnabled == false` vb.) animasyonlar ve gereksiz periyodik işler yalnızca görsel olarak gizlenmeyecek, döngüleri gerçekten durdurulacak veya askıya alınacak.
   - Ekran yeniden görünür olduğunda gerekli görsel işler güvenli biçimde kaldığı yerden devam edecek.
   - Ekranlar/sekmeler arasında gezinirken `Flow` koleksiyonları, coroutine görevleri ve görsel kaynakların birikmemesi (sızıntı olmaması) sağlanacak.
   - Görsel animasyonları duraklatmak kesinlikle arka plandaki **şarkı oynatmayı (`ExoPlayer`) veya devam eden indirmeleri (`DownloadEngine` / `YTDlpResolver`) durdurmayacak**.
   - Kullanıcı listeyi aktif olarak kaydırırken (`isScrollInProgress`) ağır arka plan parçacık adımları duraklatılarak kare bütçesi (`16,67 ms` / `8,33 ms`) kaydırma işlemine ayrılacak.

---

## D. İndirme ve Kuyruk Performansı (`DownloadQueueCenterScreen`)

1. **Mevcut Motorla Tam Entegrasyon:**
   - Yeni bir indirme motoru yazılmayacak; `DownloadQueueCenterScreen` doğrudan mevcut `MusicPlayerViewModel`, `YTDlpResolver`, `DownloadEngine`, `PendingDownloadQueueStorage` ve `LocalTrackDatabase` ile çalışacak.
2. **Ana Ekrandan Kuyruk Kartlarının Taşınması:**
   - `HomeScreen.kt` üzerindeki ağır `PendingDownloadQueueCard` ve `ResolvedPlaylistSelectionCard` kaldırılıp yerine tek satırlık hafif bir **"İndirme & Kuyruk Merkezi"** durum/erişim çubuğu eklenecek.
   - Üst bardaki İndirme butonuna veya bu çubuğa basıldığında tam ekran `DownloadQueueCenterScreen` açılacak.
3. **İlerleme Güncellemelerini Sınırlandırma (Throttling):**
   - `YTDlpResolver` içindeki ilerleme geri çağrıları (progress callbacks) öğe başına en fazla `250 ms` aralıkla veya `>= %1` değişim olduğunda UI durumunu güncelleyecek; böylece çoklu indirmede saniyede yüzlerce liste yeniden çizimi oluşmayacak.
4. **Özel Aralık Seçimi (Custom Range Validation):**
   - Hızlı aralık çiplerinin yanında **Başlangıç** ve **Bitiş** numarası giriş alanları eklenecek (`1..totalTracks`).
   - Negatif, sıfır, sınır dışı (`> totalTracks`), ters (`start > end`) veya boş girdiler güvenli biçimde doğrulanacak; geçersiz aralıkta kullanıcıya açık uyarı gösterilecek ve çökme (`IndexOutOfBoundsException` / `IllegalArgumentException`) engellenecek.
5. **Aralıklı İndirme (Staggered Download Delay) ve İptal/Duraklatma Güvenliği:**
   - Kuyrukta şarkılar arası bekleme süresi seçimi (`0 sn`, `3 sn`, `5 sn`, `10 sn`, `15 sn`, `30 sn`) eklenecek ve kullanıcı tercihi kalıcı olarak saklanacak.
   - Bekleme süresi yalnızca ardışık kuyruk indirmeleri arasında (`YTDlpResolver` / `MusicPlayerViewModel` kuyruk işçisinde) uygulanacak; tekli indirmeyi geciktirmeyecek.
   - Bekleme (`delay`) sırasında iptal, duraklatma (`pauseQueue`), devam ettirme (`resumeQueue`) veya yeniden deneme yapıldığında coroutine anında uyanacak ve yarış durumu (race condition) oluşmasını önlemek için kuyruk durumu atomik (`Mutex` / tekil kuyruk işçisi) olarak korunacak.

---

## E. Veri Doğruluğu ve Hata Güvenliği (Oynatma Listesi Ayrıştırma)

1. **Liste Dışı / Önerilen Şarkıların Kesin Olarak Dışlanması:**
   - `DownloadEngine.kt` (`parseWebRemixTracks`, `findContinuationToken`) ve `YouTubeStreamResolver.kt` (`collectPlaylistVideoRenderers`, `extractContinuationToken`) içinde:
     - `secondaryContents`, `musicCarouselShelfRenderer`, `automixPreviewVideoRenderer` ve öneri rafları (`related`, `suggestions`) kesin olarak hariç tutulacak.
     - Ancak YouTube yanıt yapısının farklılaşabileceği (`twoColumnBrowseResultsRenderer`, `singleColumnBrowseResultsRenderer`, `musicPlaylistShelfRenderer`, `musicShelfRenderer` (yalnızca ana `contents` altında ve playlist öğesi taşıyanlar), `playlistVideoListRenderer`, `onResponseReceivedActions` / `appendContinuationItemsAction` / `musicPlaylistShelfContinuation`) tüm geçerli resmi şemalar desteklenecek.
   - Her şarkı kaydının geçerli bir `videoId` (11 karakter) taşıdığı ve öneri rafından gelmediği doğrulanacak; belirsiz kayıtlar başka şarkılarla tahminen eşleştirilmeyecek.
2. **Tek Tuşla Kütüphane Oynatma Listeleri:**
   - Kullanıcının YouTube Music kütüphanesindeki oynatma listeleri (`FEmusic_liked_playlists` / `FEmusic_library_landing`) çekilirken her listenin **Adı**, **Şarkı Sayısı** ve **Playlist ID**'si güvenli biçimde ayrıştırılacak.
3. **Hata Güvenliği:**
   - Ağ hatası, boş/eksik JSON yanıtı, geçersiz çerez veya bozuk continuation token durumunda uygulama çökmeyecek; kısmi yüklenen geçerli şarkılar korunacak ve kullanıcıya anlaşılır hata mesajı + yeniden deneme seçeneği sunulacak.
   - Mevcut **Beğenilen Şarkılar (`VLLM`)**, tek şarkı indirme, arama ve yerel oynatma akışları koruma testleriyle doğrulanacak.

---

## F. Griffith ve Diğer Tema Animasyonları

1. **4 Bağımsız ve Kalıcı Griffith Ayarı (`FeatureTogglesConfig` & `FeatureTogglesStorageHelper`):**
   - `griffithQuotesEnabled`: Açıkken ve yalnızca `BERSERK_GRIFFITH` temasında üst başlıkta ve oynatıcıda kısa, estetik Griffith sözleri gösterir. Kapalıyken hiçbir yerde görünmez.
   - `griffithFalconEmblemEnabled`: Açıkken `BERSERK_GRIFFITH` temasında sabit Işığın Şahini (Falcon of Light) vektör amblemini/silüetini gösterir.
   - `griffithFlyingFalconEnabled`: Sabit amblemden tamamen bağımsızdır. Açıkken ve yalnızca `BERSERK_GRIFFITH` temasında, Ana Ekran görünürken hafif bir vektör/Canvas şahin silüeti belirli aralıklarla şarkı kartlarının üzerinden zarifçe süzülür. Dokunmaları, kaydırmayı ve oynatma kontrollerini kesinlikle engellemez (`pointerInput` içermez). Başka sekmeye geçildiğinde, ekran kapandığında veya uygulama arka plana alındığında (`Lifecycle.Event.ON_STOP`) animasyon döngüsü tamamen durur.
   - `griffithRandomLinesEnabled`: Açıkken şarkı geçişlerinde (`currentTrack?.id` değişiminde) veya boş liste ekranlarında kısa Griffith replikleri gösterir; sürekli metin değiştirerek gereksiz yeniden çizim yapmaz.
2. **Mevcut Tasarımın ve Diğer Temaların Korunması:**
   - Griffith temasının ana ekran ve mini oynatıcı arka plan görselleri **kesinlikle geri getirilmeyecek**.
   - Sakura ağacı (`SakuraTreeCanvas.kt`) ve Cowboy Bebop yıldızları (`AnimeThemeStageVisuals.kt`) tamamen silinmeyecek; Cowboy Bebop'un 96 sabit yıldızı `drawWithCache` ile önbelleğe alınarak kare başına düşen çizim maliyeti azaltılacak ve liste kaydırılırken veya ilgili ekran görünmezken animasyon döngüleri duraklatılacak.

---

## G. Gerçekçi Test ve Teslim Ölçütleri

Uygulama tamamlandığında aşağıdaki **8 senaryo** birim ve Robolectric testleriyle (`gradle :app:testDebugUnitTest`) ve derleme kontrolüyle (`compile_applet`) bizzat çalıştırılarak doğrulanacaktır:
1. Soğuk açılış ve art arda sekme değiştirme (kaydırma ve durum korunumu).
2. Uzun şarkı listelerinde (500+ öğe) hızlı kaydırma ve kapak yükleme penceresi davranışı.
3. Çok sayıda öğe içeren indirme kuyruğu ve sınırlandırılmış (throttled) ilerleme güncellemeleri.
4. Özel aralıkla indirme (geçerli aralık örn. 15–60 ve sınır dışı/ters aralık doğrulaması) ve farklı bekleme süreleri (`0s..30s`).
5. Normal çalma listeleri (`VLPL...` öneri rafları dışlanmış) ve Beğenilen Şarkılar (`VLLM`) listesinde yalnızca gerçek liste şarkılarının ayrıştırılması.
6. Ağ hatası, eksik yanıt, iptal, bekleme sırasında duraklatma ve yeniden deneme.
7. 4 Griffith ayarının ve tema/animasyon tercihlerinin birbirinden bağımsızlığı ve kalıcılığı.
8. Uygulamanın arka plana alınıp (`ON_STOP`) yeniden açılması (`ON_START`) sırasında uçan şahin ve arka plan animasyonlarının durup başlaması (bu sırada şarkı oynatma ve indirme işlemlerinin kesintisiz devam etmesi).

### Nihai Doğrulama ve Raporlama Kuralları
- Her değişiklikten sonra mevcut API'lerin, `ViewportWindowOptimizer.kt`'nin 6 kullanım noktasının, `DownloadEngine` yapısının, yerel müzik kütüphanesinin (`LocalTrackDatabase`) ve bağımlılıkların bozulmadığı doğrulanacaktır.
- Gerçek cihazda test edilebilecek senaryolar (soğuk açılış, hızlı sekme geçişleri, uzun listelerde hızlı kaydırma, yoğun indirme kuyruğu sırasında kullanım, tema değiştirme ve arka plana alıp geri dönme) hem otomatik testlerle kontrol edilecek hem de gerçek cihaz doğrulama adımları olarak sunulacaktır.
- Testler başarısızsa hiçbir hata gizlenmeyecek; derleme veya test hataları tamamen çözülmeden iş tamamlandı olarak raporlanmayacaktır.
- Nihai teslim raporunda **değiştirilen dosyalar**, **çalıştırılan testler**, **gerçek ölçüm sonuçları (öncesi/sonrası JVM ölçümleri ve bulut ortamında ölçülemeyen cihaz metriklerinin açık beyanı)** ve **varsa çözülemeyen/kalan sorunlar** ayrı başlıklar halinde açıkça bildirilecektir.

---

## H. COWBOY BEBOP — BAĞIMSIZ TEMA ÖZELLEŞTİRME SİSTEMİ

### 1. Görsel Kimlik ve Gece Dostu Renk Paleti
- Cowboy Bebop temasının (`AppTheme.COWBOY_BEBOP`) temel kimliği **Faye Valentine'in mor-sarı renk estetiği** olacak; ancak mevcut parlak ve göz yoran renklerden kaçınılacaktır:
  - **Sarı:** Mat kehribar (`#D4A338`), koyu altın (`#B88728`) ve yumuşak sarı tonları.
  - **Mor:** Koyu erik (`#3A1F47`), dumanlı mor (`#58356E`) ve ölçülü mor vurgular.
  - **Arka Plan:** Gece dostu koyu lacivert (`#0A0D16`), kömür siyahı (`#07080D`) ve gerektiğinde çok koyu mor (`#110B1A`).
  - Neon renkler, yüksek doygunluk, yoğun parıltılar ve göz alan kontrastlar varsayılan olarak düşük tutulacaktır.
  - Metin okunabilirliği, erişilebilir kontrast ve mevcut tema kimliği korunacaktır.
  - Cowboy Bebop'un görünümü Sakura, Griffith veya diğer temalara zorunlu olarak uygulanmayacaktır.
- **Varsayılan Değerler:**
  - Efekt yoğunluğu: **Düşük (`LOW`)**
  - Görsel parlaklık seviyesi: **%50 (`50`)**
  - Faye Valentine mor-sarı renk kimliği: **Açık (`true`)**
  - Gece dostu renk paleti: **Açık (`true`)**

### 2. Her Özelliğe Bağımsız ve Kalıcı Aç/Kapa veya Seviye Ayarı (31 Bağımsız Ayar)
Aşağıdaki **31 özelliğin her biri** kendi bağımsız ayar anahtarına sahip olacaktır. Birini kapatmak diğer özellikleri kendiliğinden kapatmayacak ve kullanıcı seçimleri uygulama yeniden başlatıldığında korunacaktır:

#### A. Renk ve Görünüm
1. **Gece dostu renk paleti** (`cbNightFriendlyPalette: Boolean = true`)
2. **Genel görsel parlaklık** (`cbVisualBrightness: Int = 50` — `0..100%` aralığı)
3. **Mor renk yoğunluğu** (`cbPurpleIntensity: Int = 60` — `0..100%` aralığı)
4. **Sarı/altın vurgu yoğunluğu** (`cbAmberGoldIntensity: Int = 55` — `0..100%` aralığı)
5. **Arka plan karanlığı** (`cbBackgroundDarkness: Int = 75` — `0..100%` aralığı)
6. **Parlama efektleri** (`cbGlowEffectsEnabled: Boolean = false`)
7. **Dinamik renk geçişleri** (`cbDynamicColorTransitions: Boolean = true`)
8. **Faye mor-sarı vurgu stili** (`cbFayePurpleGoldStyle: Boolean = true`)

#### B. Uzay ve Arka Plan Efektleri
9. **Yıldızlı arka plan** (`cbStarfieldEnabled: Boolean = true`)
10. **Kayan yıldızlar** (`cbShootingStarsEnabled: Boolean = false`)
11. **Nebula bulutları** (`cbNebulaCloudsEnabled: Boolean = true`)
12. **Uzay tozu parçacıkları** (`cbSpaceDustEnabled: Boolean = false`)
13. **Arka plan parıltısı** (`cbBackgroundShimmerEnabled: Boolean = false`)
14. **Derinlik/paralaks efekti** (`cbDepthParallaxEnabled: Boolean = false`)

#### C. Animasyonlar
15. **Genel animasyonlar** (`cbMasterAnimationsEnabled: Boolean = true`)
16. **Animasyon yoğunluğu** (`cbAnimationIntensity: BebopAnimationIntensity = LOW` — `Düşük / Orta / Yüksek`)
17. **Animasyon hızı** (`cbAnimationSpeed: BebopAnimationSpeed = NORMAL` — `Yavaş / Normal / Hızlı`)
18. **Şarkı değişiminde animasyon** (`cbTrackChangeAnimationEnabled: Boolean = true`)
19. **Ekran kaydırılırken animasyon** (`cbScrollAnimationEnabled: Boolean = false`)
20. **Mini oynatıcı animasyonları** (`cbMiniPlayerAnimationsEnabled: Boolean = true`)
21. **Albüm kapağı geçiş animasyonları** (`cbArtworkTransitionAnimationsEnabled: Boolean = true`)

#### D. Retro-Fütüristik Arayüz ve Oynatıcı
22. **Retro HUD çizgileri** (`cbRetroHudLinesEnabled: Boolean = true`)
23. **Teknik göstergeler** (`cbTechnicalReadoutsEnabled: Boolean = true`)
24. **Ses dalgası görselleştirmesi** (`cbWaveformVisualizerEnabled: Boolean = true`)
25. **Müzik ritmine göre görsel tepki** (`cbBeatReactiveVisualsEnabled: Boolean = false`)
26. **Oynatıcıya özel efektler** (`cbFullPlayerSpecialEffectsEnabled: Boolean = true`)
27. **Tema efektlerini mini oynatıcıda göster** (`cbMiniPlayerThemeEffectsEnabled: Boolean = true`)

#### E. Performans ve Yaşam Döngüsü
28. **Düşük efekt modu** (`cbLowEffectModeEnabled: Boolean = true`)
29. **Pil tasarrufunda animasyonları azalt** (`cbReduceOnBatterySaverEnabled: Boolean = true`)
30. **Uygulama arka plandayken efektleri durdur** (`cbPauseEffectsInBackgroundEnabled: Boolean = true`)
31. **Kaydırma sırasında ağır efektleri durdur** (`cbPauseHeavyEffectsOnScrollEnabled: Boolean = true`)

### 3. Ayarlar Ekranının Tasarımı (`Ayarlar > Temalar > Cowboy Bebop Özelleştirme`)
- Tüm seçenekler **"Ayarlar > Temalar > Cowboy Bebop Özelleştirme"** altında 5 düzenli kategori kartı (`Renk ve Görünüm`, `Uzay ve Arka Plan Efektleri`, `Animasyonlar`, `Retro-Fütüristik Arayüz ve Oynatıcı`, `Performans ve Yaşam Döngüsü`) halinde sunulacaktır.
- Aç/kapa özelliklerinde anlaşılır `Switch` kontrolleri kullanılacaktır.
- Mor yoğunluğu, sarı/altın yoğunluğu, arka plan karanlığı ve görsel parlaklık (`0–100%`, başlangıç `%50`) için yüzdelik göstergeli `Slider` kontrolleri sunulacaktır.
- Animasyon yoğunluğu için **"Düşük / Orta / Yüksek"**, animasyon hızı için **"Yavaş / Normal / Hızlı"** seçicileri eklenecektir.
- Ayar değişiklikleri anında önizlenecektir.
- **Cowboy Bebop Varsayılanlarına Dön:** Yalnızca Cowboy Bebop'a ait 31 ayarı varsayılan değerlerine sıfırlayan buton eklenecek; diğer temaların (Sakura, Griffith vb.) tercihlerini kesinlikle etkilemeyecektir.
- **Hazır Profiller (`Gece`, `Dengeli`, `Sinematik`):**
  - Kullanıcı yalnızca bir profil butonuna açıkça bastığında ilgili Cowboy Bebop ayarları o profile göre güncellenecek; kullanıcı sonrasında her ayarı bağımsız olarak değiştirebilecektir.
  - Bir profilin seçilmesi veya tek bir efektin kapatılması, kullanıcıya açıklanmayan gizli/yan ayar değişiklikleri yapmayacaktır.

### 4. Görsel Parlaklık ve Renk Yoğunluğu Ayrımı
- **Yalnızca Tema İçi Etki:** Genel görsel parlaklık (`cbVisualBrightness`) yalnızca uygulamanın Cowboy Bebop tema görünümünü (renk/yüzey parlaklık çarpanını) etkileyecek; telefonun sistem ekran parlaklığını (`WindowManager.LayoutParams.screenBrightness` veya `Settings.System`), Android parlaklık ayarlarını veya diğer uygulamaları kesinlikle değiştirmeyecektir.
- **Ayrı Katman Kontrolü:** Görsel parlaklık, mor yoğunluğu, sarı/altın vurgu yoğunluğu ve arka plan karanlığı birbirinden ayrı kontroller olarak yüzey, vurgu ve arka plan katmanlarına ayrı ayrı uygulanacak; birbirlerini ezmeden metin kontrastını, oynatma kontrollerini ve durum göstergelerini daima okunabilir tutacaktır.
- Renk ayarı değiştiğinde bütün uygulamayı sıfırdan oluşturmak yerine yalnızca tema renk durumu (`StateFlow<CowboyBebopThemeConfig>`) güncellenecektir.

### 5. Animasyon ve Performans Kuralları
- **96 Yıldız `drawWithCache` Korunumu:** Mevcut Cowboy Bebop yıldız sistemi silinmeyecek; Bölüm B ve F'de belirtilen 96 sabit yıldızın `Modifier.drawWithCache` ile önbelleğe alınması korunacaktır.
- Yeni parçacık, nebula, kayan yıldız ve paralaks efektleri yalnızca ilgili seçenek açık olduğunda (`true`) çalıştırılacaktır.
- Bir efekt kapatıldığında yalnızca görünmez (`alpha = 0f`) yapılmayacak; ilgili animasyon döngüleri (`LaunchedEffect`), periyodik işler ve kare başı hesaplamalar gerçekten durdurulacaktır.
- Uygulama arka plana geçtiğinde (`ON_STOP`) veya ilgili ekran görünmez olduğunda animasyonlar durdurulacaktır.
- **"Kaydırma sırasında ağır efektleri durdur"** açıkken liste kaydırılırken (`isScrollInProgress`) ağır parçacık, nebula ve paralaks güncellemeleri duraklatılacaktır.
- **"Genel animasyonlar"** kapalı olduğunda hareketli efektler devre dışı kalacak; statik renkler ve statik arayüz öğeleri (örn. statik yıldız önbelleği ve sabit HUD çizgileri) çalışmaya devam edecektir.
- **"Düşük efekt modu"** ve pil tasarrufu ayarları yalnızca tanımlı görsel efektleri azaltacak; müzik çalmayı, indirme işlemlerini veya kuyruk yönetimini etkilemeyecektir.
- Ses dalgası (`cbWaveformVisualizerEnabled`) ve müziğe tepki (`cbBeatReactiveVisualsEnabled`) efektleri kapalıyken gereksiz ses analizi veya kare başına hesaplama yapılmayacaktır.
- Mini oynatıcı efektleri (`cbMiniPlayerAnimationsEnabled`, `cbMiniPlayerThemeEffectsEnabled`), tam ekran oynatıcı efektlerinden (`cbFullPlayerSpecialEffectsEnabled`) bağımsız kontrol edilecektir.
- Mevcut tema motoru (`Theme.kt`, `FeatureTogglesStorageHelper.kt`, `AnimeThemeStageVisuals.kt`) genişletilerek uygulanacak; baştan yeni bir tema motoru yazılmayacaktır.

### 6. Kalıcılık ve Hata Güvenliği
- Her özelliğin `SharedPreferences` anahtarı açık ve anlaşılır (`cb_night_friendly_palette`, `cb_visual_brightness`, vb.) olacak ve mevcut depolama mekanizması (`FeatureTogglesStorageHelper`) üzerinde genişletilecektir.
- Eski kullanıcıların kayıtlı tema tercihleri korunacak; eksik, sınır dışı (`<0` veya `>100`) veya bozuk ayar verilerinde güvenli varsayılanlara (`coerceIn(0, 100)`, `try/catch` fallback) dönülerek uygulamanın çökmesi engellenecektir.
- Cowboy Bebop ayarları diğer temaların ayarlarını değiştirmeyecektir.

### 7. Test Planına Eklenen Cowboy Bebop Senaryoları (Bölüm G'ye Ek 12 Senaryo)
Bölüm G'deki 8 temel senaryoya ek olarak aşağıdaki **12 Cowboy Bebop senaryosu** da birim ve Robolectric testleriyle doğrulanacaktır:
1. 31 aç/kapa ve seviye seçeneğinin her birinin diğerlerini bozmadan bağımsız çalışması.
2. Görsel parlaklığın `%0`, `%50` (varsayılan) ve `%100` değerlerinde sistem parlaklığına dokunmadan tema yüzeylerine doğru uygulanması.
3. Mor yoğunluğu (`cbPurpleIntensity`) ve sarı/altın yoğunluğunun (`cbAmberGoldIntensity`) birbirinden bağımsız değiştirilmesi.
4. Tüm uzay efektleri kapalıyken gereksiz animasyon hesaplamalarının durması.
5. Animasyon yoğunluğu (`Düşük / Orta / Yüksek`) ve hızının (`Yavaş / Normal / Hızlı`) doğru çalışması.
6. Mini oynatıcı efektleri ile ana ekran / tam ekran oynatıcı efektlerinin bağımsızlığı.
7. `Gece`, `Dengeli` ve `Sinematik` hazır profillerinin davranışı ve profil seçimi sonrasında her ayarın manuel olarak bağımsız düzenlenebilmesi.
8. Uygulama arka plana geçtiğinde (`ON_STOP`) ve liste kaydırılırken (`isScrollInProgress`) efektlerin uygun biçimde durması.
9. Ayarların uygulama yeniden başlatıldıktan, ayar ekranından çıkıp geri dönüldükten ve başka temaya geçip tekrar Cowboy Bebop'a dönüldükten sonra korunması.
10. Diğer temaların (Sakura, Griffith vb.) renklerinin ve tercih kayıtlarının Cowboy Bebop değişikliklerinden veya sıfırlamasından etkilenmemesi.
11. Tema ayarları değiştirilirken müzik oynatmanın ve indirme kuyruğunun kesintisiz devam etmesi.
12. Önceki tüm performans testlerinin, oynatma listesi ayrıştırma testlerinin ve derleme kontrollerinin başarıyla sürdürülmesi.

