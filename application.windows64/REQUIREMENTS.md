# Telemetry Simulator - Sistem Gereksinimleri ve Çalıştırma Rehberi

Bu simülatör, **Mission Control Yer Kontrol İstasyonu** için gerçek zamanlı UDP telemetri paketleri üreten ve yayınlayan simülasyon aracıdır.

---

## 📋 Gereksinimler (Requirements)

Dosya boyutunu GitHub limitleri (25 MB) altında tutmak ve repo boyutunu şişirmemek adına, 186 MB yer kaplayan yerel Java Runtime (JRE) klasörü paketten çıkarılmıştır. 

Simülatörü çalıştırmak için sisteminizde aşağıdaki seçeneklerden **biri** bulunmalıdır:

### 1. Seçenek: Java (JRE / JDK) ile Çalıştırma (Tavsiye Edilen)
* **Gereksinim:** Java 8 (1.8) veya üzeri (64-bit Java Runtime Environment / JDK).
  * Sisteminizde Java kurulu değilse ücretsiz olarak indirebilirsiniz:
    * [Adoptium Temurin (OpenJDK)](https://adoptium.net/)
    * [Oracle Java](https://www.java.com/download/)
* **Çalıştırma:**
  * `run.bat` dosyasına çift tıklayın veya
  * `MissionControl_TelemetrySimulator.exe` dosyasını çalıştırın.
  * Konsoldan çalıştırmak isterseniz:
    ```bash
    java -Djava.library.path=lib -cp "lib/*" MissionControl_TelemetrySimulator
    ```

---

### 2. Seçenek: Processing IDE ile Kaynak Koddan Çalıştırma
Eğer kodu düzenlemek veya doğrudan Processing içerisinden çalıştırmak isterseniz:
* **Gereksinimler:**
  * [Processing 3.5.4+](https://processing.org/download)
  * **UDP Kütüphanesi:** Processing menüsünden `Sketch` -> `Import Library...` -> `Add Library...` yolunu izleyip **"UDP"** (hypermedia.net) kütüphanesini aratarak yükleyin.
* **Çalıştırma:**
  * `source/MissionControl_TelemetrySimulator.pde` (veya ana dizindeki `.pde`) dosyasını Processing ile açın ve sol üstteki **Çalıştır (Run)** butonuna tıklayın.

---

## 📡 Varsayılan Ağ ve Port Ayarları
* **Hedef IP:** `127.0.0.1` (Localhost)
* **Hedef Port:** `5555` (UDP)
* **Paket Formatı:** `timestamp,lat,lon,heading,speed,battery,temp,mode,network`
