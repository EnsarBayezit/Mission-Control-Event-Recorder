class UDPReceiver {
  UDP udp;
  volatile TelemetryData latestData;
  private volatile boolean newPacket = false;

  UDPReceiver(PApplet parent, int port) {
    try {
      udp = new UDP(parent, port);
      udp.setReceiveHandler("receive");
      udp.listen(true);
      println("[UDPReceiver] Port " + port + " dinleniyor (UDP)...");
    } catch (Exception e) {
      println("[UDPReceiver] Port acilamadi: " + e.getMessage());
    }
    latestData = new TelemetryData();
  }

  void update() {
    // hypermedia.net UDP arka planda thread ile calisir
  }

  void receive(byte[] data, String ip, int port) {
    if (data == null || data.length == 0) return;

    String message = new String(data).trim();
    TelemetryData parsed = parsePacket(message);

    if (parsed != null) {
      latestData = parsed;
      newPacket = true;
    } else {
      println("[UDPReceiver] Cozumlenemeyen Paket: " + message);
    }
  }

  // Hem standart 9 alanlı (nokta virgül) hem de Türkçe Windows virgül ondalıklı (14 alanlı) paketleri çözer
  TelemetryData parsePacket(String message) {
    if (message == null || message.length() == 0) return null;

    String[] parts = split(message, ',');

    // 1. Durum: Standart 9 Alanlı Paket
    if (parts.length == 9) {
      try {
        long ts = Long.parseLong(parts[0].trim());
        double lat = Double.parseDouble(parts[1].trim().replace(',', '.'));
        double lon = Double.parseDouble(parts[2].trim().replace(',', '.'));
        float heading = Float.parseFloat(parts[3].trim().replace(',', '.'));
        float speed = Float.parseFloat(parts[4].trim().replace(',', '.'));
        int battery = Integer.parseInt(parts[5].trim());
        float temp = Float.parseFloat(parts[6].trim().replace(',', '.'));
        int mode = Integer.parseInt(parts[7].trim());
        int network = Integer.parseInt(parts[8].trim());

        return sanitizeData(ts, lat, lon, heading, speed, battery, temp, mode, network);
      } catch (Exception e) {
        // Asagidaki fallback yapisina devam et
      }
    }

    // 2. Durum: Türkçe Bölge Ayarlı (nf() virgül ürettiği için 14 alana bölünen) Paket
    // Format: [ts, lat_tam, lat_ond, lon_tam, lon_ond, head_tam, head_ond, spd_tam, spd_ond, bat, tmp_tam, tmp_ond, mode, net]
    if (parts.length == 14) {
      try {
        long ts = Long.parseLong(parts[0].trim());
        double lat = Double.parseDouble(parts[1].trim() + "." + parts[2].trim());
        double lon = Double.parseDouble(parts[3].trim() + "." + parts[4].trim());
        float heading = Float.parseFloat(parts[5].trim() + "." + parts[6].trim());
        float speed = Float.parseFloat(parts[7].trim() + "." + parts[8].trim());
        int battery = Integer.parseInt(parts[9].trim());
        float temp = Float.parseFloat(parts[10].trim() + "." + parts[11].trim());
        int mode = Integer.parseInt(parts[12].trim());
        int network = Integer.parseInt(parts[13].trim());

        return sanitizeData(ts, lat, lon, heading, speed, battery, temp, mode, network);
      } catch (Exception e) {
        return null;
      }
    }

    return null;
  }

  TelemetryData sanitizeData(long ts, double lat, double lon, float heading, float speed, int battery, float temp, int mode, int network) {
    heading = ((heading % 360.0f) + 360.0f) % 360.0f;
    speed = max(0.0f, speed);
    battery = constrain(battery, 0, 100);
    temp = constrain(temp, -50.0f, 200.0f);
    mode = constrain(mode, 0, 2);
    network = constrain(network, 0, 100);

    return new TelemetryData(ts, lat, lon, heading, speed, battery, temp, mode, network);
  }

  boolean hasNewPacket() {
    if (newPacket) {
      newPacket = false;
      return true;
    }
    return false;
  }

  TelemetryData getLatestTelemetry() {
    return latestData;
  }
}
