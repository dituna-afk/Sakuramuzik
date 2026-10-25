# Ayarlar Mimarisinin Yeniden Gruplandırılması, Tema Sadeleştirmesi ve Sakura Ağacı Performans Optimizasyonu

Ayarlar ekranındaki karmaşayı ortadan kaldırmak için ekranı **4 ana üst kategori sekmesine (Görünüm, Ses, Oynatıcı, Sistem)** ayırıyor, tema listesinden **Faye Valentine** ve **Neon Genesis Evangelion** temalarını kaldırıyor ve **Sakura Ağacı özelleştirme seçeneklerini** sıfır takılma (60 FPS) ile anında açılan **Alt Pencere (BottomSheet)** yapısına taşıyoruz.

## User Review & Critical Decisions

> [!IMPORTANT]
> Aşağıdaki kararlar tercihleriniz doğrultusunda kesinleştirilmiştir:

- **Onaylanan Karar 1 (Tema Sadeleştirmesi)**: `Cowboy Bebop - Faye Valentine` ve `Evangelion - Unit-01 & Rei` temaları tema listesinden ve aktif tema seçeneklerinden tamamen kaldırılacak; ancak **Uygulama Simgesi (App Icon)** seçenekleri korunacaktır.
- **Onaylanan Karar 2 (Üst Kategori Sekmeli Ayarlar Düzeni)**: Tek listede 8 uzun açılır-kapanır kart yerine, ekranın en üstünde **Görünüm**, **Ses**, **Oynatıcı** ve **Sistem** olmak üzere 4 net kategori sekmesi yer alacak; her sekme yalnızca kendiyle ilgili ayarları temiz ve düzenli kartlar halinde gösterecektir.
- **Onaylanan Karar 3 (Sakura Ağacı Alt Penceresi & Kasma Çözümü)**: Sakura temalarının yanına **"Ağacı Özelleştir"** butonu eklenecek ve tıklandığında gövde formları ile çiçek renk stilleri bağımsız bir **Alt Pencere (ModalBottomSheet)** içinde anında açılacaktır.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Tema Listesi Temizliği**: Anime temaları yalnızca **Berserk - Guts**, **Berserk - Griffith** ve **Cowboy Bebop - Spike Spiegel** olarak sadeleştirilir.
  2. **4 Sekmeli Modern Ayarlar Mimarisi**: Kullanıcı aradığı ayarı karmaşık uzun listeler içinde kaybolmadan üstteki 4 sekmeden (`Görünüm`, `Ses`, `Oynatıcı`, `Sistem`) tek dokunuşla bulur.
  3. **Sıfır Kasma & Anında Açılan Sakura Ağacı Menüsü**: Tema değiştirirken tüm ekranın her karede yeniden çizilmesine yol açan `staticCompositionLocalOf` + 16'lı renk animasyonu darboğazı giderilir ve Sakura ağacı ayarları (4 gövde formu + 5 çiçek rengi) özel bir BottomSheet penceresine taşınır.
- **Target Audience / Persona**: Uygulamanın görsel atmosferini, ses DSP modüllerini ve oynatıcı araçlarını takılma olmadan, düzenli ve anlaşılır bir kontrol panelinden yönetmek isteyen dinleyiciler.
- **Key Value**: Ayarlar ekranında %100 okunabilirlik, sıfır çerçeve düşüşü (0ms gecikme) ve anında tepki veren Sakura Ağacı özelleştirme deneyimi.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Üst Sekme Geçişi**: Ayarlar ekranına girildiğinde üstte sabit duran 4 kategori sekmesinden (`🎨 Görünüm`, `🎧 Ses`, `💿 Oynatıcı`, `⚙️ Sistem`) biri seçilir; yalnızca o kategoriye ait temiz gruplanmış kartlar listelenir.
  2. **Sakura Teması Seçimi ve Ağaç Özelleştirme**:
     - `Görünüm` sekmesinde bir Sakura temasına dokunulduğunda tema anında, takılmadan aktifleşir.
     - Sakura temaları kartının başlığında ve seçili Sakura temasının hemen yanında bulunan **"🌸 Ağacı Özelleştir"** butonuna dokunulduğunda, alttan kayarak açılan pencerede **1) Gövde & Dal Mimarisi (4 Form)** ve **2) Çiçek Renk Stili (5 Stil)** net önizlemeleriyle seçilir.
  3. **Anime Teması Seçimi**: Yalnızca `Berserk - Guts`, `Berserk - Griffith` ve `Cowboy Bebop - Spike Spiegel` listelenir; seçili anime temasının sahne animasyon modları temiz bir alt seçiciyle sunulur.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Zarif, düzenli, yüksek kontrastlı ve kart hiyerarşisi netleştirilmiş modern kontrol paneli.
  - *Color Palette & Mood*: Aktif temanın `background`, `surfaceContainer`, `primaryAccent` ve `onSurface` renk jetonları kullanılarak sekme çubukları, aktif seçim rozetleri ve BottomSheet yüzeyi bütünlüklü görünür.
  - *Typography & Hierarchy*: Kategori başlıkları `16sp Bold`, alt grup başlıkları `13sp SemiBold`, açıklama metinleri `11sp` soluk ton (`onSurfaceMuted`) ile görsel kalabalık yaratmadan hiyerarşi kurar.
  - *Component Styling & Layout*: Üstte `8.dp` aralıklı seçilebilir kategori sekmeleri, içerikte `16.dp` kenar boşluklu ve `16.dp` yuvarlatılmış köşeli modüler ayar kartları.
- **Interactive Feedback & Motion**:
  - Tema değişiminde tüm ekran ağacını yeniden oluşturan ağır animasyonlar kaldırılarak anında tepki veren akıcı renk geçişi sağlanır.
  - Sakura Ağacı özelleştirme penceresi (`ModalBottomSheet`) 60 FPS akıcılıkta açılır ve yapılan gövde/renk seçimleri arka plandaki ağaca anında yansır.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Tema Geçiş Kasma Sorununun Kökten Çözümü (`compositionLocalOf` + Doğrudan Palet Sağlama)**
  - *Chosen Approach*: `LocalAppThemePalette` tanımını `staticCompositionLocalOf` yerine izlenebilir `compositionLocalOf` yapısına geçiriyoruz ve her tema değişiminde 16 ayrı rengi aynı anda `animateColorAsState` ile saniyede 60 kez tüm uygulama ağacına pompalamak yerine doğrudan hedef paleti sağlıyoruz. Ayrıca Sakura Ağacı seçeneklerini `LazyColumn` içindeki ağır iç içe `AnimatedVisibility` bloğundan çıkarıp bağımsız `ModalBottomSheet` bileşenine ayırıyoruz.
  - *Why*: `staticCompositionLocalOf` değeri her değiştiğinde Jetpack Compose tüm ekranı baştan aşağı yeniden oluşturur (full recomposition). 16 rengin 420ms boyunca her karede değişmesi ve aynı anda dev bir `LazyColumn` öğesinin `expandVertically` çalıştırması ciddi takılmaya yol açıyordu. Bu değişiklikle tema seçimi ve ağaç menüsü açılışı 0ms gecikmeyle çalışır.
  - *Alternatives Considered*: Animasyon süresini kısaltmak (yine de tam ekran recomposition ürettiği için elendi).

- **Decision 2: Ayarların 4 Ana Sekmede Gruplandırılması**
  - *Chosen Approach*:
    1. **🎨 Görünüm**: Temalar (Sade Tema Stüdyosu, Sakura Temaları + Ağaç Özelleştir BottomSheet, Anime Temaları: Guts, Griffith, Spike) ve Sakura & Dokunuş Animasyonları.
    2. **🎧 Ses & DSP**: Stüdyo DSP Efekt Modülleri (5-Bant EQ, Slowed+Reverb, 8D Ses, Karaoke, Ambiyans) ve +20 dB Ses Yükseltici.
    3. **💿 Oynatıcı & Sözler**: Şarkı Kapağı Sahneleri, Oynatıcı Kontrolleri (Waveform, Çift Dokunma, A-B Döngüsü), 5 Kaynaklı Şarkı Sözü Motoru & Türkçe Çeviri, Akıllı Kütüphane Araçları.
    4. **⚙️ Sistem & İndirme**: Varsayılan İndirme Formatı, Çerez Destekli Hesap Kütüphanesi (YT Müzik LM / YT Videolar LL), Uygulama Simgesi Seçici (tüm ikonlar korunarak) ve `yt-dlp / FFmpeg / Aria2c` Motor Güncellemesi.
  - *Why*: Kullanıcı 8 farklı uzun akordiyon kart arasında kaybolmak yerine aradığı ayarın hangi grupta olduğunu ilk bakışta görür ve yalnızca aktif sekmenin bileşenleri bellekte çizilir.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     SettingsScreen (Modüler Mimari)                      │
├──────────────────────────────────────────────────────────────────────────┤
│  [Üst Kategori Sekmeleri: 🎨 Görünüm | 🎧 Ses | 💿 Oynatıcı | ⚙️ Sistem]  │
└───────┬──────────────────────┬───────────────────────┬──────────────┬────┘
        │                      │                       │              │
        ▼                      ▼                       ▼              ▼
┌───────────────┐      ┌───────────────┐       ┌──────────────┐ ┌──────────────┐
│ Görünüm Sekmesi│      │  Ses Sekmesi  │       │Oynatıcı Sekm.│ │Sistem Sekmesi│
├───────────────┤      ├───────────────┤       ├──────────────┤ ├──────────────┤
│• Sade Tema    │      │• Stüdyo DSP   │       │• Kapak Sahne │ │• İndirme Fmt │
│• Sakura Tema  │      │  Modülleri    │       │  & Waveform  │ │• Hesap (LM)  │
│  [Özelleştir]─┼─┐    │• Detaylı FX   │       │• 5 Kaynaklı  │ │• Uygulama    │
│• Anime Tema   │ │    │  Paneli Buton │       │  LRC & Çeviri│ │  Simgesi     │
│  (Guts/Grif/  │ │    │• +20 dB Ses   │       │• Akıllı Kütüp│ │• yt-dlp &    │
│   Spike)      │ │    │  Yükseltici   │       │  Araçları    │ │  FFmpeg Motor│
│• Sakura Anim. │ │    └───────────────┘       └──────────────┘ └──────────────┘
└───────────────┘ │
                  ▼
┌──────────────────────────────────────────────────────────┐
│   SakuraTreeCustomizationBottomSheet (ModalBottomSheet)  │
├──────────────────────────────────────────────────────────┤
│ • Canlı Renk & Form Özeti                                │
│ • 1) Sakura Ağaç Gövde Formu Seçici (4 Mimari Form)      │
│ • 2) Sakura Ağacı Çiçek Renk Stili Seçici (5 Stil)       │
└──────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `AppTheme`: `EVANGELION_01` ve `COWBOY_BEBOP_FAYE` tema listesinden kaldırılır; eskiden bu iki temadan birini seçmiş olan kullanıcılar için `AppTheme.fromName()` güvenli bir şekilde varsayılan temaya (`SAKURA_DARK`) döner.
  - `AppIconOption`: Kullanıcının tercihi doğrultusunda tüm uygulama simgesi seçenekleri (`DEFAULT`, `NEON`, `DARK`, `GOLDEN`, `BERSERK_GUTS`, `BERSERK_GRIFFITH`, `EVANGELION_01`, `COWBOY_BEBOP`) eksiksiz korunur.
  - `SettingsCategory`: `APPEARANCE`, `AUDIO`, `PLAYER_LYRICS`, `SYSTEM` olmak üzere 4 sekme durumu `rememberSaveable` ile tutulur.
  - `showSakuraTreeSheet`: Sakura Ağacı özelleştirme alt penceresinin (`ModalBottomSheet`) açık/kapalı durumunu yönetir.

- **Interactive Component & State Mapping**:
  - **Sakura Teması Satırı**: Satıra tıklandığında `onSelectTheme(theme)` çağrılır; satırın sağındaki **"Özelleştir"** butonuna (veya seçili Sakura temasına tekrar) tıklandığında `showSakuraTreeSheet = true` yapılarak alt pencere anında açılır.
  - **Sakura Ağacı Alt Penceresi (`SakuraTreeCustomizationBottomSheet`)**: İçindeki 4 `SakuraTreeForm` ve 5 `SakuraTreeStyle` seçeneğinden birine dokunulduğunda `onSelectTreeForm` / `onSelectTreeStyle` anında tetiklenir ve seçim işaretlenir.
  - **Kategori Sekmeleri**: Sekme değiştirildiğinde `LazyColumn` yalnızca seçili kategorinin kartlarını oluşturarak kaydırma performansını maksimuma çıkarır.
