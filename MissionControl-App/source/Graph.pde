import java.util.ArrayList;

class Graph {
  float x, y, w, h;
  String title;

  // Telemetri Geçmiş Veri Tamponları
  ArrayList<Long> timestamps = new ArrayList<Long>();
  ArrayList<Float> speedPoints = new ArrayList<Float>();
  ArrayList<Float> tempPoints = new ArrayList<Float>();
  ArrayList<Float> batteryPoints = new ArrayList<Float>();
  ArrayList<Float> networkPoints = new ArrayList<Float>();

  // Zaman Penceresi Ayarları (En az 5 dakika = 300.000 ms)
  long windowMs = 5 * 60 * 1000L; // 5m varsayılan
  long[] availableWindows = { 5 * 60 * 1000L, 10 * 60 * 1000L, 15 * 60 * 1000L };
  String[] windowLabels = { "5 MIN", "10 MIN", "15 MIN" };
  int selectedWindowIdx = 0;

  // Metrik Modları: 0: SPEED, 1: TEMP, 2: BATTERY, 3: NETWORK, 4: MULTI
  final int METRIC_SPEED = 0;
  final int METRIC_TEMP = 1;
  final int METRIC_BATTERY = 2;
  final int METRIC_NETWORK = 3;
  final int METRIC_MULTI = 4;
  int currentMetric = METRIC_SPEED;
  String[] metricNames = { "SPEED", "TEMPERATURE", "BATTERY", "NETWORK", "MULTI" };

  // Renk Paleti
  final int COLOR_SPEED = 0xFF5ADCFF; 
  final int COLOR_TEMP = 0xFFFF7B54;  
  final int COLOR_BATTERY = 0xFF50E678;
  final int COLOR_NETWORK = 0xFFBD6BFF;   
  final int COLOR_LIMIT = 0xA0FF4B4B;    

  // Sekme Buton Alanları (Hitbox)
  float[][] metricTabBounds = new float[5][4]; // x, y, w, h
  float[][] windowTabBounds = new float[3][4];

  Graph(float x, float y, float w, float h, String title) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    this.title = title;
  }

  void addData(float speedVal, long timestamp) {
    addData(timestamp, speedVal, 62.0f, 100.0f, 90.0f);
  }

  void addTelemetry(TelemetryData d) {
    if (d == null) return;
    addData(d.timestamp, (float)d.speed, (float)d.temp, (float)d.battery, (float)d.network);
  }

  void addData(long timestamp, float speedVal, float tempVal, float battVal, float netVal) {
    timestamps.add(timestamp);
    speedPoints.add(speedVal);
    tempPoints.add(tempVal);
    batteryPoints.add(battVal);
    networkPoints.add(netVal);
    pruneOld(timestamp);
  }

  void clear() {
    timestamps.clear();
    speedPoints.clear();
    tempPoints.clear();
    batteryPoints.clear();
    networkPoints.clear();
  }

  void rebuildHistory(ArrayList<TelemetryData> dataList, int upToIndex) {
    clear();
    if (dataList == null || dataList.size() == 0) return;
    int end = min(upToIndex, dataList.size() - 1);
    if (end < 0) return;

    long targetTs = dataList.get(end).timestamp;
    long minTs = targetTs - windowMs;

    for (int i = 0; i <= end; i++) {
      TelemetryData d = dataList.get(i);
      if (d.timestamp >= minTs) {
        timestamps.add(d.timestamp);
        speedPoints.add((float)d.speed);
        tempPoints.add((float)d.temp);
        batteryPoints.add((float)d.battery);
        networkPoints.add((float)d.network);
      }
    }
  }

  void pruneOld(long nowTs) {
    long maxRetention = 20 * 60 * 1000L; // En fazla 20 dakikalık tampon tut
    while (timestamps.size() > 0 && (nowTs - timestamps.get(0)) > maxRetention) {
      timestamps.remove(0);
      speedPoints.remove(0);
      tempPoints.remove(0);
      batteryPoints.remove(0);
      networkPoints.remove(0);
    }
    while (timestamps.size() > 5000) {
      timestamps.remove(0);
      speedPoints.remove(0);
      tempPoints.remove(0);
      batteryPoints.remove(0);
      networkPoints.remove(0);
    }
  }

  void setMetric(int metricIdx) {
    if (metricIdx >= 0 && metricIdx <= 4) {
      currentMetric = metricIdx;
    }
  }

  void setWindow(int windowIdx) {
    if (windowIdx >= 0 && windowIdx < availableWindows.length) {
      selectedWindowIdx = windowIdx;
      windowMs = availableWindows[windowIdx];
    }
  }

  // Standart display() çağrısı (Live varsayılanı)
  void display() {
    display(true, System.currentTimeMillis());
  }

  // Replay duyarlı display çağrısı
  void display(boolean isLive, long replayCurrentTs) {
    // 1. Ana Panel Arka Planı
    fill(14, 28, 40);
    stroke(35, 63, 82);
    strokeWeight(1);
    rect(x, y, w, h, 7);

    // Başlık
    fill(100, 215, 250);
    textSize(13);
    textAlign(LEFT, CENTER);
    String headerTitle = (currentMetric == METRIC_MULTI) ? "TELEMETRY HISTORY (MULTI-METRIC)" : (metricNames[currentMetric] + " HISTORY");
    text(headerTitle, x + 18, y + 20);

    // 2. Sekmeler (Metrik & Zaman Aralığı Butonları)
    drawControlTabs();

    // 3. Grafik Çizim Alanı Koordinatları
    float graphTop = y + 48;
    float graphBottom = y + h - 35;
    float graphLeft = x + 55;
    float graphRight = x + w - 25;
    float graphH = graphBottom - graphTop;
    float graphW = graphRight - graphLeft;

    // Gerçek Zamanlı Akış Ekseni (Canlı modda kesintisiz sistem saatine, Replay'de oynatma saatine bağlı)
    long windowEndTs;
    if (isLive) {
      windowEndTs = (timestamps.size() > 0 && Math.abs(System.currentTimeMillis() - timestamps.get(timestamps.size() - 1)) < 60000L)
                    ? System.currentTimeMillis()
                    : (timestamps.size() > 0 ? timestamps.get(timestamps.size() - 1) : System.currentTimeMillis());
    } else {
      windowEndTs = (replayCurrentTs > 0) ? replayCurrentTs : (timestamps.size() > 0 ? timestamps.get(timestamps.size() - 1) : System.currentTimeMillis());
    }
    long windowStartTs = windowEndTs - windowMs;

    // Y Eksen Ölçeği ve Limit Eşiği
    float minY = 0;
    float maxY = 30;
    String unit = "kn";
    float limitVal = -1;
    String limitLabel = "";
    int activeColor = COLOR_SPEED;

    if (currentMetric == METRIC_SPEED) {
      minY = 0; maxY = 30; unit = "kn";
      limitVal = 15.0f; limitLabel = "15 kn LIMIT";
      activeColor = COLOR_SPEED;
    } else if (currentMetric == METRIC_TEMP) {
      minY = 20; maxY = 120; unit = "°C";
      limitVal = 80.0f; limitLabel = "80°C OVERHEAT";
      activeColor = COLOR_TEMP;
    } else if (currentMetric == METRIC_BATTERY) {
      minY = 0; maxY = 100; unit = "%";
      limitVal = 20.0f; limitLabel = "20% LOW BATT";
      activeColor = COLOR_BATTERY;
    } else if (currentMetric == METRIC_NETWORK) {
      minY = 0; maxY = 100; unit = "%";
      limitVal = 30.0f; limitLabel = "30% WEAK NET";
      activeColor = COLOR_NETWORK;
    } else if (currentMetric == METRIC_MULTI) {
      minY = 0; maxY = 100; unit = "%";
      limitVal = -1;
    }

    // 4. Izgara (Grid) ve Y Ekseni Etiketleri
    stroke(25, 45, 60);
    strokeWeight(1);
    int gridDivisions = 4;
    for (int i = 0; i <= gridDivisions; i++) {
      float gy = graphTop + (graphH / (float)gridDivisions) * i;
      line(graphLeft, gy, graphRight, gy);

      fill(120, 150, 175);
      textSize(10);
      textAlign(RIGHT, CENTER);
      float yVal = maxY - (i * ((maxY - minY) / (float)gridDivisions));
      text(int(yVal) + (i == 0 ? " " + unit : ""), graphLeft - 8, gy);
    }

    // 5. Uyarı / Eşik Çizgisi (Varsa kesikli kırmızı çizgi)
    if (limitVal >= minY && limitVal <= maxY && currentMetric != METRIC_MULTI) {
      float limitY = map(limitVal, minY, maxY, graphBottom, graphTop);
      stroke(COLOR_LIMIT);
      strokeWeight(1.2);
      for (float dx = graphLeft; dx < graphRight; dx += 8) {
        line(dx, limitY, min(dx + 4, graphRight), limitY);
      }
      fill(255, 90, 90, 190);
      textSize(9);
      textAlign(RIGHT, BOTTOM);
      text(limitLabel, graphRight, limitY - 2);
    }

    // 6. X Ekseni Zaman Etiketleri (Sola Kayan Zaman Ölçeği)
    textAlign(CENTER, TOP);
    fill(120, 150, 175);
    textSize(10);
    int timeDivs = 5;
    long totalMins = windowMs / (60 * 1000L);
    for (int i = 0; i <= timeDivs; i++) {
      float fx = graphLeft + (graphW) * (i / (float)timeDivs);
      float fraction = (timeDivs - i) / (float)timeDivs;
      float minsAgo = totalMins * fraction;

      String label;
      if (minsAgo == 0) {
        label = "now";
      } else if (minsAgo == (int)minsAgo) {
        label = "-" + int(minsAgo) + "m";
      } else {
        label = "-" + nf(minsAgo, 0, 1) + "m";
      }
      text(label, fx, graphBottom + 8);
    }

    // 7. Telemetri Eğrilerinin Çizimi
    if (timestamps.size() > 0) {
      if (currentMetric == METRIC_MULTI) {
        // Multi-Metrik Görünüm: 4 metrik aynı anda
        drawSeries(speedPoints, 0, 30, windowStartTs, windowEndTs, graphLeft, graphRight, graphBottom, graphTop, COLOR_SPEED, false);
        drawSeries(tempPoints, 20, 120, windowStartTs, windowEndTs, graphLeft, graphRight, graphBottom, graphTop, COLOR_TEMP, false);
        drawSeries(batteryPoints, 0, 100, windowStartTs, windowEndTs, graphLeft, graphRight, graphBottom, graphTop, COLOR_BATTERY, false);
        drawSeries(networkPoints, 0, 100, windowStartTs, windowEndTs, graphLeft, graphRight, graphBottom, graphTop, COLOR_NETWORK, false);
        drawMultiLegend(graphRight - 280, graphTop + 8);
      } else {
        // Tek Metrik Görünümü: Alan dolgusu + Kalın neon çizgi + En son canlı nokta
        ArrayList<Float> activePoints = getActivePointsList();
        drawSeries(activePoints, minY, maxY, windowStartTs, windowEndTs, graphLeft, graphRight, graphBottom, graphTop, activeColor, true);
        drawLivePulseDot(activePoints, minY, maxY, windowStartTs, windowEndTs, graphLeft, graphRight, graphBottom, graphTop, activeColor);
      }

      // 8. İstatistik Rozetleri (MIN, MAX, AVG, CURRENT)
      drawStatsBadge(graphLeft + 10, graphTop + 8, minY, maxY, unit, activeColor);

      // 9. İnteraktif Hover Cursor / Tooltip (Mouse üstündeyken)
      if (mouseX >= graphLeft && mouseX <= graphRight && mouseY >= graphTop && mouseY <= graphBottom) {
        drawHoverTooltip(mouseX, mouseY, graphLeft, graphRight, graphBottom, graphTop, windowStartTs, windowEndTs, minY, maxY, unit, activeColor);
      }
    } else {
      fill(100, 130, 155);
      textSize(12);
      textAlign(CENTER, CENTER);
      text("Waiting for real-time telemetry data...", graphLeft + graphW / 2, graphTop + graphH / 2);
    }

    textAlign(LEFT, BASELINE);
  }

  // Belirli bir veri serisini çizer (Yüksek hassasiyetli 64-bit zaman projeksiyonu)
  void drawSeries(ArrayList<Float> pts, float minY, float maxY, long startTs, long endTs,
                  float gLeft, float gRight, float gBottom, float gTop, int c, boolean withAreaFill) {
    if (pts == null || pts.size() == 0 || timestamps.size() == 0) return;
    long windowDuration = endTs - startTs;
    if (windowDuration <= 0) return;

    float firstPx = -1;
    float lastPx = -1;

    // Alan Dolgusu
    if (withAreaFill) {
      noStroke();
      fill(red(c), green(c), blue(c), 35);
      beginShape();
      boolean started = false;
      for (int i = 0; i < pts.size(); i++) {
        long ts = timestamps.get(i);
        if (ts >= startTs - 5000 && ts <= endTs + 5000) {
          float px = gLeft + ((float)(ts - startTs) / (float)windowDuration) * (gRight - gLeft);
          if (px >= gLeft && px <= gRight) {
            float val = constrain(pts.get(i), minY, maxY);
            float py = map(val, minY, maxY, gBottom, gTop);
            if (!started) {
              vertex(px, gBottom);
              firstPx = px;
              started = true;
            }
            vertex(px, py);
            lastPx = px;
          }
        }
      }
      if (started && lastPx >= firstPx) {
        vertex(lastPx, gBottom);
        endShape(CLOSE);
      }
    }

    // Ana Çizgi
    noFill();
    stroke(c);
    strokeWeight(2);
    beginShape();
    for (int i = 0; i < pts.size(); i++) {
      long ts = timestamps.get(i);
      if (ts >= startTs - 5000 && ts <= endTs + 5000) {
        float px = gLeft + ((float)(ts - startTs) / (float)windowDuration) * (gRight - gLeft);
        if (px >= gLeft && px <= gRight) {
          float val = constrain(pts.get(i), minY, maxY);
          float py = map(val, minY, maxY, gBottom, gTop);
          vertex(px, py);
        }
      }
    }
    endShape();
  }

  // Canlı akışın en son noktasına nabız gibi parlayan neon nokta çizer
  void drawLivePulseDot(ArrayList<Float> pts, float minY, float maxY, long startTs, long endTs,
                        float gLeft, float gRight, float gBottom, float gTop, int c) {
    if (pts.size() == 0) return;
    int lastIdx = pts.size() - 1;
    long lastTs = timestamps.get(lastIdx);
    float lastVal = pts.get(lastIdx);
    long windowDuration = endTs - startTs;
    if (windowDuration <= 0) return;

    float px = gLeft + ((float)(lastTs - startTs) / (float)windowDuration) * (gRight - gLeft);
    if (px >= gLeft && px <= gRight + 5) {
      px = min(px, gRight);
      float py = map(constrain(lastVal, minY, maxY), minY, maxY, gBottom, gTop);

      float pulse = (sin(millis() * 0.009f) + 1.0f) / 2.0f; // 0..1
      noStroke();
      fill(red(c), green(c), blue(c), 70 + pulse * 90);
      ellipse(px, py, 11 + pulse * 6, 11 + pulse * 6);

      fill(c);
      ellipse(px, py, 6, 6);
      fill(255);
      ellipse(px, py, 2.5f, 2.5f);
    }
  }

  // Üst Kontrol Sekmeleri (Metrikler ve Zaman Aralıkları)
  void drawControlTabs() {
    float startX = x + 245;
    float tabY = y + 10;
    float tabH = 20;

    // Metrik Butonları
    String[] shortNames = { "SPEED", "TEMP", "BATTERY", "NETWORK", "MULTI" };
    color[] tabColors = { COLOR_SPEED, COLOR_TEMP, COLOR_BATTERY, COLOR_NETWORK, color(230, 230, 255) };

    for (int i = 0; i < shortNames.length; i++) {
      float tabW = (i == 4) ? 48 : ((i == 2 || i == 3) ? 62 : 48);
      boolean isSelected = (currentMetric == i);

      stroke(isSelected ? tabColors[i] : color(38, 68, 88));
      strokeWeight(1);
      fill(isSelected ? color(25, 55, 75) : color(15, 30, 42));
      rect(startX, tabY, tabW, tabH, 4);

      fill(isSelected ? tabColors[i] : color(140, 170, 190));
      textSize(10);
      textAlign(CENTER, CENTER);
      text(shortNames[i], startX + tabW / 2, tabY + tabH / 2);

      metricTabBounds[i][0] = startX;
      metricTabBounds[i][1] = tabY;
      metricTabBounds[i][2] = tabW;
      metricTabBounds[i][3] = tabH;

      startX += tabW + 5;
    }

    // Zaman Aralığı Butonları (Sağ tarafta 5m, 10m, 15m)
    float timeTabStartX = x + w - 175;
    for (int i = 0; i < windowLabels.length; i++) {
      float tabW = 46;
      boolean isSelected = (selectedWindowIdx == i);

      stroke(isSelected ? color(100, 215, 250) : color(38, 68, 88));
      strokeWeight(1);
      fill(isSelected ? color(25, 55, 75) : color(15, 30, 42));
      rect(timeTabStartX, tabY, tabW, tabH, 4);

      fill(isSelected ? color(255) : color(130, 155, 175));
      textSize(9);
      textAlign(CENTER, CENTER);
      text(windowLabels[i], timeTabStartX + tabW / 2, tabY + tabH / 2);

      windowTabBounds[i][0] = timeTabStartX;
      windowTabBounds[i][1] = tabY;
      windowTabBounds[i][2] = tabW;
      windowTabBounds[i][3] = tabH;

      timeTabStartX += tabW + 4;
    }
  }

  // İstatistik Rozetleri (MIN, MAX, AVG, CURRENT)
  void drawStatsBadge(float bx, float by, float minY, float maxY, String unit, int c) {
    if (currentMetric == METRIC_MULTI) return;

    ArrayList<Float> pts = getActivePointsList();
    if (pts.size() == 0) return;

    float minVal = 999999, maxVal = -999999, sum = 0;
    int count = 0;
    for (int i = 0; i < pts.size(); i++) {
      float v = pts.get(i);
      if (v < minVal) minVal = v;
      if (v > maxVal) maxVal = v;
      sum += v;
      count++;
    }
    float avgVal = (count > 0) ? (sum / count) : 0;
    float curVal = pts.get(pts.size() - 1);

    fill(18, 36, 52, 210);
    stroke(35, 65, 88);
    strokeWeight(1);
    rect(bx, by, 340, 20, 3);

    fill(130, 165, 190);
    textSize(9);
    textAlign(LEFT, CENTER);
    float tx = bx + 8;
    float rowCy = by + 10;

    text("MIN: ", tx, rowCy);
    fill(210); text(nf(minVal, 0, 1), tx + 24, rowCy);

    tx += 65;
    fill(130, 165, 190); text("MAX: ", tx, rowCy);
    fill(210); text(nf(maxVal, 0, 1), tx + 26, rowCy);

    tx += 65;
    fill(130, 165, 190); text("AVG: ", tx, rowCy);
    fill(210); text(nf(avgVal, 0, 1), tx + 24, rowCy);

    tx += 65;
    fill(130, 165, 190); text("CUR: ", tx, rowCy);
    fill(c); text(nf(curVal, 0, 1) + " " + unit, tx + 26, rowCy);
  }

  // Multi-Metrik Lejandı (Sağ üst)
  void drawMultiLegend(float lx, float ly) {
    fill(18, 36, 52, 210);
    stroke(35, 65, 88);
    rect(lx, ly, 275, 20, 3);

    float itemX = lx + 6;
    float itemCy = ly + 10;

    // Speed
    fill(COLOR_SPEED); ellipse(itemX, itemCy, 6, 6);
    fill(190); textSize(9); textAlign(LEFT, CENTER);
    text("Spd (kn)", itemX + 6, itemCy);
    itemX += 66;

    // Temp
    fill(COLOR_TEMP); ellipse(itemX, itemCy, 6, 6);
    fill(190); text("Temp (°C)", itemX + 6, itemCy);
    itemX += 72;

    // Battery
    fill(COLOR_BATTERY); ellipse(itemX, itemCy, 6, 6);
    fill(190); text("Batt (%)", itemX + 6, itemCy);
    itemX += 66;

    // Network
    fill(COLOR_NETWORK); ellipse(itemX, itemCy, 6, 6);
    fill(190); text("Net (%)", itemX + 6, itemCy);
  }

  // Hover Tooltip & Crosshair (Fare ile inceleme)
  void drawHoverTooltip(float mx, float my, float gLeft, float gRight, float gBottom, float gTop,
                        long startTs, long endTs, float minY, float maxY, String unit, int c) {
    long windowDuration = endTs - startTs;
    if (windowDuration <= 0) return;

    long hoverTs = startTs + (long)(((mx - gLeft) / (gRight - gLeft)) * (float)windowDuration);

    // En yakın veri noktasını bul
    int bestIdx = -1;
    long bestDiff = 999999999L;
    for (int i = 0; i < timestamps.size(); i++) {
      long d = Math.abs(timestamps.get(i) - hoverTs);
      if (d < bestDiff) {
        bestDiff = d;
        bestIdx = i;
      }
    }

    if (bestIdx >= 0 && bestDiff < (windowMs / 10)) {
      long ptTs = timestamps.get(bestIdx);
      float ptX = gLeft + ((float)(ptTs - startTs) / (float)windowDuration) * (gRight - gLeft);

      // Dikey İmleç Çizgisi
      stroke(100, 215, 250, 150);
      strokeWeight(1);
      line(ptX, gTop, ptX, gBottom);

      // Tooltip Konumlandırma
      float tipW = 120;
      float tipH = (currentMetric == METRIC_MULTI) ? 68 : 42;
      float tipX = (ptX + tipW + 15 > gRight) ? (ptX - tipW - 10) : (ptX + 10);
      float tipY = constrain(my - tipH / 2, gTop, gBottom - tipH);

      fill(12, 24, 35, 240);
      stroke(50, 100, 130);
      strokeWeight(1);
      rect(tipX, tipY, tipW, tipH, 4);

      long secsAgo = (endTs - ptTs) / 1000L;
      String timeAgoStr = (secsAgo < 60) ? ("-" + secsAgo + "s") : ("-" + (secsAgo / 60) + "m " + (secsAgo % 60) + "s");

      fill(140, 175, 200);
      textSize(9);
      textAlign(LEFT, TOP);
      text("Time: " + timeAgoStr, tipX + 8, tipY + 6);

      if (currentMetric == METRIC_MULTI) {
        fill(COLOR_SPEED); text("Speed: " + nf(speedPoints.get(bestIdx), 0, 1) + " kn", tipX + 8, tipY + 18);
        fill(COLOR_TEMP); text("Temp: " + nf(tempPoints.get(bestIdx), 0, 1) + " °C", tipX + 8, tipY + 30);
        fill(COLOR_BATTERY); text("Battery: " + int(batteryPoints.get(bestIdx).floatValue()) + " %", tipX + 8, tipY + 42);
        fill(COLOR_NETWORK); text("Network: " + int(networkPoints.get(bestIdx).floatValue()) + " %", tipX + 8, tipY + 54);
      } else {
        ArrayList<Float> pts = getActivePointsList();
        float val = pts.get(bestIdx);
        float ptY = map(constrain(val, minY, maxY), minY, maxY, gBottom, gTop);

        // Vurgu Halkası
        noFill();
        stroke(255);
        strokeWeight(1.5f);
        ellipse(ptX, ptY, 8, 8);
        fill(c);
        ellipse(ptX, ptY, 4, 4);

        fill(c);
        textSize(11);
        text(metricNames[currentMetric] + ": " + nf(val, 0, 1) + " " + unit, tipX + 8, tipY + 22);
      }
    }
  }

  ArrayList<Float> getActivePointsList() {
    if (currentMetric == METRIC_TEMP) return tempPoints;
    if (currentMetric == METRIC_BATTERY) return batteryPoints;
    if (currentMetric == METRIC_NETWORK) return networkPoints;
    return speedPoints; // default SPEED
  }

  // Tıklama Olayı (Sekmeler veya Zaman Seçici)
  boolean handleClick(float mx, float my) {
    // 1. Metrik Sekmeleri Kontrolü
    for (int i = 0; i < metricTabBounds.length; i++) {
      if (mx >= metricTabBounds[i][0] && mx <= metricTabBounds[i][0] + metricTabBounds[i][2] &&
          my >= metricTabBounds[i][1] && my <= metricTabBounds[i][1] + metricTabBounds[i][3]) {
        setMetric(i);
        return true;
      }
    }

    // 2. Zaman Aralığı Butonları Kontrolü
    for (int i = 0; i < windowTabBounds.length; i++) {
      if (mx >= windowTabBounds[i][0] && mx <= windowTabBounds[i][0] + windowTabBounds[i][2] &&
          my >= windowTabBounds[i][1] && my <= windowTabBounds[i][1] + windowTabBounds[i][3]) {
        setWindow(i);
        return true;
      }
    }

    return false;
  }
}
