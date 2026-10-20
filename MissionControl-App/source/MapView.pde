import java.util.ArrayList;

class MapView {
  PApplet parent;
  float x, y, w, h;
  String title = "MAP";

  UnfoldingMap unfoldingMap;

  // Konum geçmişi (rota izi)
  ArrayList<Location> trail = new ArrayList<Location>();
  int maxTrailPoints = 600;

  TelemetryData current = null;
  boolean hasFix = false;
  long lastUpdateMillis = 0;
  long staleThresholdMs = 3000; // 3 saniye GPS Lost eşiği

  // Zoom Buton Konumları
  float btnZoomInX, btnZoomInY, btnZoomOutX, btnZoomOutY, btnZoomSize = 22;

  MapView(PApplet parent, float x, float y, float w, float h) {
    this.parent = parent;
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;

    btnZoomInX = x + 16;
    btnZoomInY = y + 44;
    btnZoomOutX = x + 16;
    btnZoomOutY = y + 70;

    try {
      unfoldingMap = new UnfoldingMap(
        parent, 
        (int)x, 
        (int)y, 
        (int)w, 
        (int)h, 
        new Microsoft.AerialProvider()
      );

      Location startLocation = new Location(36.812345f, 34.641234f);
      unfoldingMap.zoomAndPanTo(14, startLocation);
      MapUtils.createDefaultEventDispatcher(parent, unfoldingMap);
    } catch (Exception e) {
      println("[MapView] Harita motoru başlatılamadı (çevrimdışı mod): " + e.getMessage());
    }
  }

  void updateData(TelemetryData d) {
    if (d == null) return;
    current = d;
    lastUpdateMillis = millis();

    Location currentLocation = new Location((float)d.lat, (float)d.lon);
    trail.add(currentLocation);

    if (unfoldingMap != null) {
      unfoldingMap.panTo(currentLocation);
    }

    if (trail.size() > maxTrailPoints) trail.remove(0);
    hasFix = true;
  }

  void clearTrail() {
    trail.clear();
  }

  void rebuildTrail(ArrayList<TelemetryData> dataList, int upToIndex) {
    trail.clear();
    if (dataList == null || dataList.size() == 0) return;
    int end = min(upToIndex, dataList.size() - 1);
    if (end < 0) return;
    int start = max(0, end - maxTrailPoints);
    for (int i = start; i <= end; i++) {
      TelemetryData d = dataList.get(i);
      trail.add(new Location((float)d.lat, (float)d.lon));
    }
    current = dataList.get(end);
    hasFix = true;
    if (unfoldingMap != null) {
      unfoldingMap.panTo(new Location((float)current.lat, (float)current.lon));
    }
  }

  boolean isStale() {
    return hasFix && (millis() - lastUpdateMillis > staleThresholdMs);
  }

  float plotLeft() { return x + 6; }
  float plotRight() { return x + w - 6; }
  float plotTop() { return y + 36; }
  float plotBottom() { return y + h - 6; }

  void display() {
    // Panel Gövdesi
    fill(14, 28, 40);
    stroke(35, 63, 82);
    strokeWeight(1);
    rect(x, y, w, h, 7);

    // Harita Katmanı
    try {
      if (unfoldingMap != null) unfoldingMap.draw();
    } catch (Exception e) {
      // Çevrimdışı güvenli geçiş
    }

    // Panel Başlığı
    fill(100, 215, 250);
    textSize(13);
    textAlign(LEFT, CENTER);
    text(title, x + 18, y + 20);

    // Çizim sınırlarını kırp
    pushStyle();
    clip(plotLeft(), plotTop(), plotRight() - plotLeft(), plotBottom() - plotTop());

    drawGrid();

    if (hasFix && current != null) {
      drawTrail();
      drawVehicle();
    } else {
      fill(90, 115, 135);
      textSize(12);
      textAlign(CENTER, CENTER);
      text("WAITING FOR GPS FIX...", x + w / 2, y + h / 2);
    }

    noClip();
    popStyle();

    // Harita Üzeri Kontroller & Katmanlar
    drawZoomButtons();
    drawCompass();
    drawCoordinatesAndScale();

    // GPS Sinyal Kesintisi Alarm Katmanı
    if (isStale()) {
      fill(20, 10, 10, 160);
      noStroke();
      rect(plotLeft(), plotTop(), plotRight() - plotLeft(), plotBottom() - plotTop(), 4);

      float blink = (sin(millis() * 0.008) + 1) / 2.0;
      fill(255, 70, 70, 140 + blink * 115);
      textSize(13);
      textAlign(CENTER, CENTER);
      text("\u26A0 GPS SIGNAL LOST", x + w / 2, y + h / 2);
    }

    textAlign(LEFT, BASELINE);
  }

  void drawGrid() {
    stroke(25, 45, 60, 100);
    strokeWeight(1);
    int lines = 6;
    float left = plotLeft(), right = plotRight(), top = plotTop(), bottom = plotBottom();
    for (int i = 0; i <= lines; i++) {
      float gx = left + (right - left) * i / (float) lines;
      line(gx, top, gx, bottom);
      float gy = top + (bottom - top) * i / (float) lines;
      line(left, gy, right, gy);
    }
  }

  void drawTrail() {
    if (trail.size() < 2 || unfoldingMap == null) return;

    pushStyle();
    // Kesikli Yeşil/Cyan Rota Çizgisi
    stroke(40, 220, 130, 220);
    strokeWeight(2.5);
    noFill();

    beginShape();
    for (Location loc : trail) {
      ScreenPosition pos = unfoldingMap.getScreenPosition(loc);
      vertex(pos.x, pos.y);
    }
    endShape();

    // Rota Üzerindeki Noktalar (Waypoints)
    noStroke();
    fill(40, 220, 130, 170);
    int step = max(1, trail.size() / 35);
    for (int i = 0; i < trail.size(); i += step) {
      Location loc = trail.get(i);
      ScreenPosition pos = unfoldingMap.getScreenPosition(loc);
      ellipse(pos.x, pos.y, 4, 4);
    }

    popStyle();
  }

  void drawVehicle() {
    if (current == null || unfoldingMap == null) return;

    Location vehicleLocation = new Location((float)current.lat, (float)current.lon);
    ScreenPosition pos = unfoldingMap.getScreenPosition(vehicleLocation);
    float px = pos.x;
    float py = pos.y;
    float heading = current.heading;

    pushStyle();

    // ---------------------------------------------------------
    // 1. MESAFE ÇEMBERLERİ VE RADAR ALANI (RANGE RINGS)
    // ---------------------------------------------------------
    float pulse = (sin(millis() * 0.005) + 1) / 2.0;

    // En Dış Mesafe Çemberi (Örn: 200m eşdeğeri)
    noFill();
    stroke(40, 200, 110, 45);
    strokeWeight(1);
    ellipse(px, py, 180, 180);

    // Orta Mesafe Çemberi (Örn: 100m eşdeğeri)
    stroke(40, 210, 120, 75);
    strokeWeight(1.2);
    ellipse(px, py, 115, 115);

    // İç Dinamik Radar / Doğruluk Alanı (Yeşil Işıltı)
    fill(35, 185, 95, 75);
    noStroke();
    ellipse(px, py, 60, 60);

    // Canlı Nabız (Pulse) Çemberi
    noFill();
    stroke(50, 230, 130, 80 + pulse * 90);
    strokeWeight(1.5);
    ellipse(px, py, 60 + pulse * 18, 60 + pulse * 18);

    // ---------------------------------------------------------
    // 2. NAVİGASYON OKU (ÖRNEK GÖRSELDEKİ BEYAZ YÖN OKU)
    // ---------------------------------------------------------
    // Alt Gölge Efekti
    pushMatrix();
    translate(px + 2, py + 2);
    rotate(radians(heading));
    noStroke();
    fill(0, 130);
    beginShape();
    vertex(0, -18);
    vertex(12, 14);
    vertex(0, 8);
    vertex(-12, 14);
    endShape(CLOSE);
    popMatrix();

    // Ana Yön Oku (Net Beyaz Gövde & İnce Kontur)
    pushMatrix();
    translate(px, py);
    rotate(radians(heading));
    fill(255);
    stroke(15, 45, 60);
    strokeWeight(1.5);
    beginShape();
    vertex(0, -18);   // Burun (İleri yön)
    vertex(12, 14);   // Sağ kanat
    vertex(0, 8);     // İç oyuk merkez
    vertex(-12, 14);  // Sol kanat
    endShape(CLOSE);
    popMatrix();

    popStyle();
  }

  void drawZoomButtons() {
    pushStyle();
    stroke(40, 75, 100);
    strokeWeight(1);

    // Zoom In [+]
    fill(14, 28, 40, 220);
    rect(btnZoomInX, btnZoomInY, btnZoomSize, btnZoomSize, 4);
    fill(220);
    textSize(14);
    textAlign(CENTER, CENTER);
    text("+", btnZoomInX + btnZoomSize / 2, btnZoomInY + btnZoomSize / 2 - 1);

    // Zoom Out [-]
    fill(14, 28, 40, 220);
    rect(btnZoomOutX, btnZoomOutY, btnZoomSize, btnZoomSize, 4);
    fill(220);
    textSize(14);
    text("-", btnZoomOutX + btnZoomSize / 2, btnZoomOutY + btnZoomSize / 2 - 1);

    popStyle();
  }

  void drawCompass() {
    float cx = plotRight() - 20;
    float cy = plotTop() + 20;

    pushStyle();
    noStroke();
    fill(5, 15, 25, 200);
    ellipse(cx, cy, 32, 32);

    stroke(160, 200, 220);
    strokeWeight(1);
    noFill();
    ellipse(cx, cy, 24, 24);

    // Kuzey İğnesi
    noStroke();
    fill(255, 80, 80);
    triangle(cx, cy - 11, cx - 4, cy, cx + 4, cy);
    fill(220);
    textSize(9);
    textAlign(CENTER, CENTER);
    text("N", cx, cy + 6);
    popStyle();
  }

  void drawCoordinatesAndScale() {
    pushStyle();
    // Sol Alt: Koordinat Kutusu (Örnek: 40.9712° N / 29.1238° E)
    float coordBoxX = x + 14;
    float coordBoxY = y + h - 46;
    fill(10, 20, 30, 200);
    noStroke();
    rect(coordBoxX, coordBoxY, 110, 36, 4);

    if (current != null) {
      fill(220);
      textSize(10);
      textAlign(LEFT, CENTER);
      String latStr = nf(abs((float)current.lat), 2, 4) + "° " + (current.lat >= 0 ? "N" : "S");
      String lonStr = nf(abs((float)current.lon), 2, 4) + "° " + (current.lon >= 0 ? "E" : "W");
      text(latStr, coordBoxX + 8, coordBoxY + 11);
      text(lonStr, coordBoxX + 8, coordBoxY + 25);
    } else {
      fill(140);
      textSize(10);
      textAlign(LEFT, CENTER);
      text("--.----° N", coordBoxX + 8, coordBoxY + 11);
      text("--.----° E", coordBoxX + 8, coordBoxY + 25);
    }

    // Sağ Alt: Mesafe Ölçek Çubuğu (500 m)
    float scaleX = x + w - 85;
    float scaleY = y + h - 22;
    float scaleW = 65;

    fill(10, 20, 30, 200);
    rect(scaleX - 6, scaleY - 14, scaleW + 12, 22, 4);

    stroke(220);
    strokeWeight(1.5);
    line(scaleX, scaleY, scaleX + scaleW, scaleY);
    line(scaleX, scaleY - 4, scaleX, scaleY + 1);
    line(scaleX + scaleW, scaleY - 4, scaleX + scaleW, scaleY + 1);

    fill(220);
    textSize(9);
    textAlign(CENTER, BOTTOM);
    text("500 m", scaleX + scaleW / 2, scaleY - 3);

    popStyle();
  }

  void handleMouseClick(float mx, float my) {
    if (unfoldingMap == null) return;

    // Zoom In [+]
    if (mx >= btnZoomInX && mx <= btnZoomInX + btnZoomSize && my >= btnZoomInY && my <= btnZoomInY + btnZoomSize) {
      unfoldingMap.zoomIn();
    }
    // Zoom Out [-]
    else if (mx >= btnZoomOutX && mx <= btnZoomOutX + btnZoomSize && my >= btnZoomOutY && my <= btnZoomOutY + btnZoomSize) {
      unfoldingMap.zoomOut();
    }
  }
}
