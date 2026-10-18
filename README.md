# 🛰️ Mission Control - Telemetry & Event Recorder

**Mission Control**, otonom / uzaktan kumandalı araçlar, insansız deniz/kara araçları (İDA/İKA) ve dronlar için geliştirilmiş yüksek performanslı, modern bir **Yer Kontrol İstasyonu (Ground Control Station - GCS)** ve telemetri analiz platformudur.

Processing (Java / P2D) tabanlı grafik motoru ve **Unfolding Maps** kütüphanesi üzerine inşa edilen bu sistem; gerçek zamanlı UDP veri akışını izleme, kural tabanlı anlık olay/alarm algılama, en az 5 dakikalık sola kayan çoklu telemetri grafikleri, etkileşimli uydu haritası ve geçmiş görevleri tekrar oynatma (**Replay**) yeteneklerine sahiptir.

---

## 📌 Temel Özellikler

### 1. 📡 Gerçek Zamanlı UDP Telemetri Alıcısı (`Live Mode`)
* **Esnek UDP Portu:** `config.txt` üzerinden ayarlanabilen port dinleme (Varsayılan: `5555`).
* **Akıllı Veri Ayrıştırıcı:** Standart 9 alanlı CSV formatının yanı sıra Türkçe Windows bölgesel virgül-ondalık formatlarını da otomatik tanıyan toleranslı ayrıştırıcı mimarisi.
* **Telemetri Parametreleri:** Zaman Damgası (`timestamp`), Enlem (`lat`), Boylam (`lon`), Yönelim Açısı (`heading`), Hız (`speed`), Batarya Seviyesi (`battery`), Motor Sıcaklığı (`temp`), Çalışma Modu (`mode`), Ağ Kalitesi (`network`).

### 2. 🗺️ Etkileşimli Uydu Haritası (`MapView`)
* **Canlı Uydu Katmanı:** Microsoft Aerial uydu haritası üzerinde anlık konum takibi.
* **Dinamik Yönelim & Araç İmleci:** Gerçek zamanlı rotasyon yapan yönelim oku ve nabız efektli radar mesafe halkaları (*Range Rings*).
* **Rota İzi (Trail History):** Son 600 konum noktasını içeren görsel rota çizgisi ve ara nokta (*waypoint*) göstergeleri.
* **Harita Kontrolleri:** Yakınlaştırma / uzaklaştırma (`+` / `-`), gerçek zamanlı GPS koordinat kutusu (`° N/S`, `° E/W`), 500 m mesafe ölçeği ve 3 saniye üzeri kesintilerde devreye giren **GPS SIGNAL LOST** uyarı katmanı.

### 3. ⏱️ Sürekli Sola Kayan Çoklu Metrik Grafikleri (`Graph`)
* **Gerçek Zamanlı Sola Kayan Akış:** Veri akışından bağımsız olarak sistem saatiyle senkronize, 60 FPS hızında sola doğru kesintisiz kayan zaman ekseni.
* **Kayan Pencere Seçenekleri:** Varsayılan **5 Dakika** (`5 MIN`), **10 Dakika** (`10 MIN`) ve **15 Dakika** (`15 MIN`) geçmiş aralığı.
* **Metrik Sekmeleri & Eşik Çizgileri:**
  * **`SPEED` (kn):** Hız geçmişi, 15 kn OVERSPEED kırmızı kesikli uyarı eşiği.
  * **`TEMPERATURE` (°C):** Motor sıcaklığı geçmişi, 80 °C ENGINE OVERHEAT uyarı çizgisi.
  * **`BATTERY` (%):** Batarya voltaj/doluluk geçmişi, 20 % LOW BATTERY kritik eşiği.
  * **`NETWORK` (%):** Ağ sinyal gücü geçmişi, 30 % WEAK SIGNAL uyarı eşiği.
  * **`MULTI`:** Tüm telemetri serilerinin aynı anda karşılaştırmalı çizildiği renk lejantlı çoklu görünüm modu.
* **Canlı İstatistik Rozetleri:** Seçili metrik için son penceredeki **MIN**, **MAX**, **AVG** ve **CUR** değerleri.
* **İnteraktif İnceleme (Hover Tooltip):** Fare grafik üzerindeyken dikey imleç ve o ana ait net telemetri değeri ile geçen zaman (`-Xm Ys`).
* **Hızlı Odaklanma:** Üstteki gösterge kartlarına tıklandığında alt grafiğin otomatik ilgili metriğe geçiş yapması.

### 4. 🎛️ Gelişmiş Gösterge Kartları
* **Pusula / Heading Kartı (`HeadingCard`):** Çift katmanlı denizcilik pusula kadranı, 30° bölmeleri, ana yönler (N, S, E, W), dönen mavi ibre ve dijital derece.
* **Hız / Speed Kartı (`SpeedCard`):** 0 - 30 kn dairesel yay kadranı, dinamik doluluk yayı ve dijital hız göstergesi.
* **Batarya Kartı (`BatteryCard`):** 4 kademeli dikey pil simgesi, renk değişimleri (Yeşil ➔ Kırmızı) ve alt durum çubuğu.
* **Motor Sıcaklığı Kartı (`EngineTempCard`):** Sıvı seviyesi animasyonlu termometre ikonu, 5 segmentli termal çubuk ve dijital °C göstergesi.

### 5. 🛡️ Kural Tabanlı Olay & Alarm Motoru (`EventEngine`)
Canlı telemetri akışında kritik güvenlik kurallarını anlık olarak denetler:
* 🔴 **LOW BATTERY (CRITICAL):** Batarya < %20
* 🔴 **ENGINE OVERHEAT (CRITICAL):** Motor Sıcaklığı > 80 °C
* 🔴 **GPS LOST (CRITICAL):** 3 saniyeden uzun süre telemetri paketi alınamaması
* 🟡 **OVERSPEED (WARNING):** Hız > 15 kn
* 🟡 **CONNECTION WEAK (WARNING):** Ağ kalitesi < %30
* 🟡 **RAPID TURN (WARNING):** Ani rota değişimi (> 45° yön sapması)
* 🔵 **MODE CHANGE (INFO):** Çalışma modunda değişiklik (`MANUAL`, `AUTO`, `RTL`)
* **Görsel Uyarı Nabzı:** Alarm durumunda ilgili telemetri kartının etrafında nabız atan (*pulse*) kırmızı uyarı çerçevesi.
* **Event Timeline:** Son 50 olayı hafızada tutar, arayüzde son 5 olayı önem derecesine göre renk kodlu listeler.

### 6. 📼 Görev Tekrar Oynatma (`Replay Mode`)
* **Log Yükleme:** `logs/` dizinindeki geçmiş CSV kayıtlarını veya `LOAD LOG FILE` butonuyla seçilen harici telemetri dosyalarını yükleme.
* **Oynatma Kontrolleri:** Başa Sarma (`Restart`), Oynat/Duraklat (`Play/Pause`), Hız Çarpanları (`1x`, `2x`, `4x`).
* **Timeline Slider:** Görevin istenen saniyesine fare ile anında atlama (*scrubbing*).
* **Tam Durum Senkronizasyonu:** İleri veya geri sarıldığında harita rota izi, grafik geçmişi ve olay motoru o ana göre anında yeniden hesaplanır.

### 7. 💾 Veri Kayıt & Dışa Aktarma (`DataLogger`)
* **Otomatik Kayıt:** Her canlı oturum başlatıldığında `logs/telemetry_YYYYMMDD_HHMMSS.csv` ve `logs/events_YYYYMMDD_HHMMSS.csv` dosyaları otomatik oluşturulur.
* **Export Log (Canlı & Replay):** Alt bardaki `EXPORT LOG` butonu ile canlı oturum verisi veya replay oturumu anında `logs/exports/` dizinine yeni bir CSV olarak dışa aktarılır.
* **Ekranda Bildirim (Toast):** Başarılı dışa aktarma sonrası ekranda dosya adını belirten yeşil bildirim rozeti gösterilir.

---

## 🏗️ Proje Dizin Yapısı

```text
MissionControl/
├── MissionControl.pde      # Ana uygulama döngüsü (setup, draw, mouse olayları)
├── MapView.pde             # Harita motoru, rota izi, araç imleci, radar ve pusula
├── Graph.pde               # 5+ dk sola kayan çoklu grafik sistemi ve hover tooltip
├── HeadingCard.pde         # Pusula ve yönelim gösterge kartı
├── SpeedCard.pde           # Hız kadranı gösterge kartı
├── BatteryCard.pde         # Batarya doluluk gösterge kartı
├── EngineTempCard.pde      # Motor sıcaklığı ve termometre kartı
├── SystemHealthPanel.pde   # Sistem sağlığı ve bağlantı/durum paneli
├── EventEngine.pde         # Kural tabanlı olay ve alarm algılama motoru
├── Event.pde               # Olay veri modeli ve zaman formatlayıcı
├── TelemetryData.pde       # Telemetri veri modeli ve mod tanımları
├── UDPReceiver.pde         # UDP soket dinleyicisi ve akıllı paket ayrıştırıcı
├── DataLogger.pde          # CSV loglama ve dışa aktarma (export) yöneticisi
├── ReplayManager.pde       # Geçmiş görev logu oynatma ve zaman senkronizasyonu
├── config.txt              # Ağ portu ve IP konfigürasyonu
└── logs/                   # Otomatik telemetri, event ve export kayıtları
    └── exports/            # Dışa aktarılan CSV log dosyaları
```

---

## ⚙️ Kurulum ve Çalıştırma

### Gereksinimler
1. [Processing IDE](https://processing.org/download) (Processing 3 veya 4)
2. Gerekli Processing Kütüphaneleri (*Sketch > Import Library > Add Library...* menüsünden yüklenebilir):
   * **Unfolding Maps** (Harita görselleştirme için)
   * **UDP** (`hypermedia.net` - UDP soket haberleşmesi için)

### Çalıştırma Adımları
1. Bu projeyi bilgisayarınıza klonlayın veya indirin:
   ```bash
   git clone <repo-url>
   ```
2. `MissionControl/MissionControl.pde` dosyasını Processing IDE ile açın.
3. Çalıştırmak için sol üstteki **Run (`Ctrl + R` / `Cmd + R`)** butonuna basın.

### Konfigürasyon (`config.txt`)
Proje kök dizinindeki `config.txt` dosyası üzerinden UDP dinleme portu ayarlanabilir:
```properties
# UDP Telemetry Dinleme Portu
port=5555
ip=127.0.0.1
```

---

## 📡 Telemetri Paket Formatı

UDP üzerinden gönderilen telemetri paketleri virgülle ayrılmış (CSV) dizgi formatında olmalıdır:

```text
timestamp,latitude,longitude,heading,speed,battery,temp,mode,network
```

### Örnek Paket
```text
1725201245000,36.812345,34.641234,142.5,12.4,85,64.2,0,92
```

| Alan | Tip | Açıklama | Örnek |
| :--- | :--- | :--- | :--- |
| `timestamp` | `long` | Unix zaman damgası (milisaniye) | `1725201245000` |
| `latitude` | `double` | Enlem koordinatı | `36.812345` |
| `longitude` | `double` | Boylam koordinatı | `34.641234` |
| `heading` | `float` | Yönelim açısı (0 - 360 derece) | `142.5` |
| `speed` | `float` | Araç hızı (Knot - kn) | `12.4` |
| `battery` | `int` | Batarya yüzdesi (% 0 - 100) | `85` |
| `temp` | `float` | Motor sıcaklığı (°C) | `64.2` |
| `mode` | `int` | 0: MANUAL, 1: AUTO, 2: RTL | `0` |
| `network` | `int` | Ağ / Sinyal kalitesi (% 0 - 100) | `92` |

---

## 🖥️ Kullanıcı Arayüzü Kılavuzu

* **LIVE / REPLAY Butonları:** Üst barda yer alır. Canlı UDP dinleme ile geçmiş log inceleme modları arasında geçiş yapar.
* **Ayarlar (Dişli İkonu):** Üst barın sağındaki dişli simgesine tıklayarak aktif port, IP ve log dosya konumlarını görüntüleyebilirsiniz.
* **Grafik Sekmeleri (`SPEED`, `TEMP`, `BATTERY`, `NETWORK`, `MULTI`):** Grafiğin üstündeki butonlara tıklayarak veya doğrudan ilgili gösterge kartına tıklayarak grafik görünümü değiştirilebilir.
* **Zaman Ölçeği (`5 MIN`, `10 MIN`, `15 MIN`):** Grafiğin sağ üstündeki butonlarla kayan pencere genişliği seçilebilir.
* **LOAD LOG FILE:** Replay modunda geçmiş `.csv` telemetri dosyalarını seçmek için dosya penceresini açar.
* **EXPORT LOG:** Hem canlı oturumda hem replay modunda o anki verileri `logs/exports/` altına dışa aktarır.

---

## 📄 Lisans
Bu proje staj/ar-ge çalışması kapsamında geliştirilmiştir. Tüm hakları saklıdır.
