import de.fhpotsdam.unfolding.*;
import de.fhpotsdam.unfolding.core.*;
import de.fhpotsdam.unfolding.data.*;
import de.fhpotsdam.unfolding.events.*;
import de.fhpotsdam.unfolding.geo.*;
import de.fhpotsdam.unfolding.interactions.*;
import de.fhpotsdam.unfolding.mapdisplay.*;
import de.fhpotsdam.unfolding.mapdisplay.shaders.*;
import de.fhpotsdam.unfolding.marker.*;
import de.fhpotsdam.unfolding.providers.*;
import de.fhpotsdam.unfolding.texture.*;
import de.fhpotsdam.unfolding.tiles.*;
import de.fhpotsdam.unfolding.ui.*;
import de.fhpotsdam.unfolding.utils.*;
import de.fhpotsdam.unfolding.utils.ScreenPosition;
import de.fhpotsdam.utils.*;

import hypermedia.net.*;

// Modül Tanımları
UDPReceiver udpReceiver;
EventEngine eventEngine;
DataLogger dataLogger;
ReplayManager replayManager;
MapView mapView;
Graph speedGraph;

// Gelişmiş Kart Tanımları
HeadingCard headingCard;
SpeedCard speedCard;
BatteryCard batteryCard;
EngineTempCard engineTempCard;
SystemHealthPanel healthPanel;

// Sistem Durum Değişkenleri
String connectionState = "DISCONNECTED";
long lastPacketTime = 0;
int frameCounter = 0;
float currentFps = 60;
boolean isLiveMode = true;

// Replay Toolbar - Konum ve Boyut Sabitleri
float toolbarX = 50, toolbarY = 795, toolbarW = 1300, toolbarH = 45;
float btnRestartX = 65, btnPlayX = 105, btnW = 32, btnH = 26, btnY = 804;
float sliderX = 150, sliderW = 630, sliderY = 814, sliderH = 6;
float timeLabelX = 795;
float speed1X = 905, speed2X = 943, speed4X = 981, speedBtnW = 32, speedBtnH = 26, speedBtnY = 804;
float loadLogX = 1025, loadLogY = 804, loadLogW = 135, loadLogH = 26;
float exportX = 1175, exportY = 804, exportW = 155, exportH = 26;

boolean draggingSlider = false;


long appStartMillis = 0;

int configPort = 5555;
String configIP = "127.0.0.1"; //UDP dinleme 

// Üst Bar - Saat / Ayarlar İkonları
float clockX = 1160;
float gearFuncX = 1300, gearFuncY = 20, gearSize = 30;
float gearDecoX = 1345, gearDecoY = 20;
boolean showSettingsPanel = false;

float dispHeading = 0, dispSpeed = 0, dispBattery = 0, dispTemp = 62;
boolean smoothingInitialized = false;

// Export Bildirim Durumu
String lastExportMessage = "";
long lastExportTime = 0;

void setup() {
  size(1400, 850,P2D);
  surface.setTitle("Mission Control - Telemetry & Event Recorder");
  frameRate(60);

  appStartMillis = millis();
  loadConfig();

  // Modüllerin Başlatılması
  udpReceiver = new UDPReceiver(this, configPort);
  eventEngine = new EventEngine();

  dataLogger = new DataLogger();
  eventEngine.setLogger(dataLogger);
  replayManager = new ReplayManager(dataLogger, eventEngine);
  mapView = new MapView(this,50, 110, 420, 380);
  speedGraph = new Graph(500, 570, 860, 210, "SPEED HISTORY (kn)");
  

  // Kartların Başlatılması 
  headingCard = new HeadingCard(490, 110, 270, 210, "HEADING");
  speedCard = new SpeedCard(775, 110, 270, 210, "SPEED");
  batteryCard = new BatteryCard(490, 335, 270, 155, "BATTERY");
  engineTempCard = new EngineTempCard(775, 335, 270, 155, "ENGINE TEMP");
  healthPanel = new SystemHealthPanel(1060, 110, 290, 445);

  lastPacketTime = millis();
}


void loadConfig() {
  String[] lines = loadStrings("config.txt");
  if (lines == null) return;

  for (String rawLine : lines) {
    String line = trim(rawLine);
    if (line.length() == 0 || line.startsWith("#")) continue;

    String[] parts = split(line, '=');
    if (parts.length != 2) continue;

    String key = trim(parts[0]).toLowerCase();
    String val = trim(parts[1]);

    if (key.equals("port")) {
      try {
        configPort = Integer.parseInt(val);
      } catch (Exception e) {
        println("config.txt: gecersiz port degeri, varsayilan (5555) kullaniliyor");
      }
    } else if (key.equals("ip")) {
      configIP = val;
    }
  }
}

void draw() {
  frameCounter++;
  if (frameCounter % 30 == 0) currentFps = frameRate;

  background(9, 17, 26);
  drawTopBar();

  TelemetryData currentData = null;
  if (isLiveMode) {
    udpReceiver.update();
    if (udpReceiver.hasNewPacket()) {
      TelemetryData incoming = udpReceiver.getLatestTelemetry();
      lastPacketTime = millis();
      connectionState = "CONNECTED";
      dataLogger.logTelemetry(incoming);
      eventEngine.evaluate(incoming);
      mapView.updateData(incoming);
      speedGraph.addTelemetry(incoming);
    } else {
      if (millis() - lastPacketTime > 3000) connectionState = "DISCONNECTED";
    }

    if (connectionState.equals("CONNECTED")) {
      currentData = udpReceiver.getLatestTelemetry();
    }
    eventEngine.checkGpsLost(millis() - lastPacketTime, System.currentTimeMillis());
  } else {
    replayManager.update(mapView, speedGraph);
    currentData = replayManager.getCurrentTelemetry();
    connectionState = (currentData != null) ? "REPLAYING" : "OFFLINE";
  }

  mapView.display();
  speedGraph.display(isLiveMode, isLiveMode ? System.currentTimeMillis() : replayManager.getCurrentTimestamp());
  drawTelemetryCards(currentData);
  drawSystemHealthPanel(currentData);
  drawEventTimeline();
  drawReplayToolbar();
  drawSettingsPanel();
}

void drawTopBar() {
  fill(16, 29, 42);
  noStroke();
  rect(0, 0, width, 70);

  fill(103, 220, 255);
  textSize(22);
  textAlign(LEFT, CENTER);
  text("MISSION CONTROL", 35, 35);

  stroke(40, 75, 100);
  fill(isLiveMode ? color(40, 160, 90) : color(25, 45, 65));
  rect(320, 20, 90, 30, 5);
  fill(isLiveMode ? color(255) : color(150));
  textSize(14);
  textAlign(CENTER, CENTER);
  text("LIVE", 365, 35);

  fill(!isLiveMode ? color(40, 110, 180) : color(25, 45, 65));
  rect(420, 20, 90, 30, 5);
  fill(!isLiveMode ? color(255) : color(150));
  text("REPLAY", 465, 35);

  boolean isOnline = connectionState.equals("CONNECTED") || connectionState.equals("REPLAYING");
  boolean isTrouble = connectionState.equals("DISCONNECTED") || connectionState.equals("OFFLINE");

  // Bağlantı koptuğunda durum noktası yanıp sönerek dikkat çeker
  float blink = isTrouble ? (sin(millis() * 0.008) + 1) / 2.0 : 1.0;
  color dotColor = isOnline ? color(80, 230, 120) : lerpColor(color(90, 20, 20), color(255, 70, 70), blink);

  fill(dotColor);
  ellipse(720, 35, 10, 10);
  fill(210);
  textSize(14);
  textAlign(LEFT, CENTER);
  text(isOnline ? "SYSTEM ONLINE" : "SYSTEM OFFLINE", 740, 35);

  // FPS Göstergesi (Bonus)
  fill(120, 180, 210);
  textSize(12);
  textAlign(LEFT, CENTER);
  text("FPS: " + int(currentFps), 940, 35);

  // Saat
  fill(200);
  textSize(15);
  textAlign(LEFT, CENTER);
  text(nf(hour(), 2) + ":" + nf(minute(), 2) + ":" + nf(second(), 2), clockX, 35);

  // Ayarlar (fonksiyonel) İkonu
  drawGearIcon(gearFuncX + gearSize / 2, gearFuncY + gearSize / 2, showSettingsPanel);

  // İkinci İkon 
  drawGearIcon(gearDecoX + gearSize / 2, gearDecoY + gearSize / 2, false);
}

void drawGearIcon(float cx, float cy, boolean active) {
  pushMatrix();
  translate(cx, cy);
  stroke(active ? color(103, 220, 255) : color(140, 160, 175));
  strokeWeight(2);
  noFill();
  ellipse(0, 0, 16, 16);
  for (int i = 0; i < 8; i++) {
    float a = i * TWO_PI / 8.0;
    line(cos(a) * 9, sin(a) * 9, cos(a) * 13, sin(a) * 13);
  }
  noStroke();
  fill(active ? color(103, 220, 255) : color(140, 160, 175));
  ellipse(0, 0, 5, 5);
  popMatrix();
}

void drawSettingsPanel() {
  if (!showSettingsPanel) return;

  float x = 1000, y = 80, w = 340, h = 150;

  fill(14, 28, 40);
  stroke(60, 110, 140);
  strokeWeight(1.5);
  rect(x, y, w, h, 8);

  fill(100, 215, 250);
  textSize(13);
  textAlign(LEFT, CENTER);
  text("SETTINGS / CONFIG", x + 16, y + 22);

  fill(150, 180, 200);
  textSize(11);
  float rowY = y + 48;
  float rowH = 20;

  text("UDP Listen Port: " + configPort, x + 16, rowY);
  text("Config IP (info): " + configIP, x + 16, rowY + rowH);
  text("Telemetry Log: logs/telemetry_*.csv", x + 16, rowY + rowH * 2);
  text("Event Log: logs/events_*.csv", x + 16, rowY + rowH * 3);
  text("Exports: logs/exports/", x + 16, rowY + rowH * 4);
}

void drawTelemetryCards(TelemetryData d) {
  float targetHeading = d != null ? d.heading : dispHeading;
  float targetSpeed = d != null ? d.speed : dispSpeed;
  float targetBattery = d != null ? constrain(d.battery, 0, 100) : dispBattery;
  float targetTemp = d != null ? d.temp : dispTemp;

  if (!smoothingInitialized && d != null) {
    dispHeading = targetHeading;
    dispSpeed = targetSpeed;
    dispBattery = targetBattery;
    dispTemp = targetTemp;
    smoothingInitialized = true;
  }

  // Değer değişimlerinde yumuşak geçiş
  float smooth = 0.25;
  dispHeading = lerpAngle(dispHeading, targetHeading, smooth);
  dispSpeed = lerp(dispSpeed, targetSpeed, smooth);
  dispBattery = lerp(dispBattery, targetBattery, smooth);
  dispTemp = lerp(dispTemp, targetTemp, smooth);

  // Gelişmiş Kartlar
  headingCard.display(dispHeading);

  speedCard.display(dispSpeed);
  if (eventEngine.overspeedActive) {
    drawAlertPulse(speedCard.x, speedCard.y, speedCard.w, speedCard.h);
  }

  batteryCard.display(round(dispBattery));
  if (eventEngine.lowBatteryActive) {
    drawAlertPulse(batteryCard.x, batteryCard.y, batteryCard.w, batteryCard.h);
  }

  engineTempCard.display(dispTemp);
  if (eventEngine.overheatActive) {
    drawAlertPulse(engineTempCard.x, engineTempCard.y, engineTempCard.w, engineTempCard.h);
  }
}

// Alarm oluştuğunda kart etrafında nabız gibi atan (pulse) kırmızı çerçeve
void drawAlertPulse(float x, float y, float w, float h) {
  float pulse = (sin(millis() * 0.008) + 1) / 2.0; // 0..1 arası salınım
  noFill();
  stroke(255, 60, 60, 110 + pulse * 120);
  strokeWeight(2.5 + pulse * 1.5);
  rect(x - 3, y - 3, w + 6, h + 6, 9);
}

// Açı değerlerini (0-360)
float lerpAngle(float current, float target, float t) {
  float diff = target - current;
  while (diff > 180) diff -= 360;
  while (diff < -180) diff += 360;
  float result = current + diff * t;
  while (result < 0) result += 360;
  while (result >= 360) result -= 360;
  return result;
}

void drawSystemHealthPanel(TelemetryData d) {
  long lastPacketTimeMs = millis() - lastPacketTime;
  int uptimeSecs = (int) ((millis() - appStartMillis) / 1000);

  healthPanel.display(d, connectionState, lastPacketTimeMs, uptimeSecs);
}

void drawEventTimeline() {
  float x = 50, y = 505, w = 420, h = 275;
  
  // Kutu Arka Planı
  fill(14, 28, 40);
  stroke(35, 63, 82);
  strokeWeight(1);
  rect(x, y, w, h, 7);

  // Başlık
  fill(100, 215, 250);
  textSize(13);
  textAlign(LEFT, CENTER);
  text("EVENT TIMELINE", x + 18, y + 20);

  // Olayların Listelenmesi
  float listY = y + 45;
  float rowH = 38;
  int maxDisplayEvents = 5; // Kutuya sığacak maksimum olay sayısı

  ArrayList<Event> evList = eventEngine.events;
  
  if (evList.size() == 0) {
    fill(100, 130, 155);
    textSize(11);
    textAlign(CENTER, CENTER);
    text("No active events or alerts recorded.", x + w / 2, y + h / 2);
    textAlign(LEFT, BASELINE);
    return;
  }

  for (int i = 0; i < min(evList.size(), maxDisplayEvents); i++) {
    Event ev = evList.get(i);
    float rowY = listY + (i * rowH);

    // Seviyeye Göre Renk Seçimi (INFO, WARNING, CRITICAL)
    color sevColor;
    if (ev.severity.equals("CRITICAL")) {
      sevColor = color(255, 80, 80); // Kırmızı
    } else if (ev.severity.equals("WARNING")) {
      sevColor = color(240, 180, 40); // Sarı / Turuncu
    } else {
      sevColor = color(90, 200, 255); // Bilgi (Mavi)
    }

    // Olay Satırı Arka Plan Çerçevesi
    fill(18, 35, 50);
    stroke(30, 55, 75);
    rect(x + 12, rowY, w - 24, 32, 4);

    // Sol Kenar Renkli Uyarı Çubuğu
    noStroke();
    fill(sevColor);
    rect(x + 12, rowY, 4, 32, 4);

    // Zaman Damgası
    fill(140, 165, 185);
    textSize(10);
    textAlign(LEFT, CENTER);
    text(ev.getTimeString(), x + 24, rowY + 16);

    // Event Tipi
    fill(sevColor);
    textSize(11);
    text(ev.type, x + 95, rowY + 16);

    // Açıklama Metni
    fill(200);
    textSize(10);
    
    String shortDesc = ev.description;
    if (shortDesc.length() > 28) {
      shortDesc = shortDesc.substring(0, 26) + "..";
    }
    textAlign(RIGHT, CENTER);
    text(shortDesc, x + w - 20, rowY + 16);
  }
  
  textAlign(LEFT, BASELINE);
}

void drawReplayToolbar() {
  boolean replayActive = !isLiveMode && replayManager.hasData();
  boolean hasExportData = (isLiveMode && dataLogger.hasLiveData()) || (!isLiveMode && replayManager.hasData());

  color dim = color(70, 90, 105);
  color normal = color(90, 220, 255);

  // Toolbar Arka Planı
  fill(14, 28, 40);
  stroke(35, 63, 82);
  strokeWeight(1);
  rect(toolbarX, toolbarY, toolbarW, toolbarH, 7);

  // --- Restart Butonu ---
  drawToolbarButtonBg(btnRestartX, btnY, btnW, btnH, replayActive);
  noStroke();
  fill(replayActive ? color(210) : dim);
  rect(btnRestartX + 7, btnY + 5, 3, btnH - 10);
  triangle(btnRestartX + btnW - 9, btnY + 5, btnRestartX + btnW - 9, btnY + btnH - 5, btnRestartX + 11, btnY + btnH / 2);

  // --- Play / Pause Butonu ---
  drawToolbarButtonBg(btnPlayX, btnY, btnW, btnH, replayActive);
  noStroke();
  fill(replayActive ? color(210) : dim);
  if (replayActive && replayManager.isPlaying) {
    // Pause ikonu (iki çubuk)
    rect(btnPlayX + 10, btnY + 5, 5, btnH - 10);
    rect(btnPlayX + btnW - 15, btnY + 5, 5, btnH - 10);
  } else {
    // Play ikonu (üçgen)
    triangle(btnPlayX + 11, btnY + 5, btnPlayX + 11, btnY + btnH - 5, btnPlayX + btnW - 9, btnY + btnH / 2);
  }

  // --- Timeline Slider ---
  stroke(35, 63, 82);
  fill(8, 18, 27);
  rect(sliderX, sliderY, sliderW, sliderH, 3);

  float frac = replayActive ? replayManager.getProgressFraction() : (isLiveMode ? 1.0 : 0);
  noStroke();
  fill(replayActive ? normal : (isLiveMode ? color(40, 160, 90) : dim));
  rect(sliderX, sliderY, sliderW * frac, sliderH, 3);

  float handleX = sliderX + sliderW * frac;
  fill(replayActive ? color(255) : (isLiveMode ? color(80, 230, 120) : dim));
  ellipse(handleX, sliderY + sliderH / 2, 12, 12);

  // --- Zaman Etiketi ---
  fill(replayActive ? color(210) : (isLiveMode ? color(100, 215, 250) : dim));
  textSize(12);
  textAlign(LEFT, CENTER);

  if (!isLiveMode && !replayManager.hasData()) {
    fill(255, 130, 100);
    textAlign(CENTER, CENTER);
    text("NO LOG FILE FOUND IN logs/ — RECORD A LIVE SESSION FIRST", toolbarX + toolbarW / 2, toolbarY + toolbarH / 2);
    textAlign(LEFT, BASELINE);
    return;
  }

  if (isLiveMode) {
    int liveSecs = (int) ((millis() - appStartMillis) / 1000);
    text(formatMMSS(liveSecs) + " (LIVE)", timeLabelX, toolbarY + toolbarH / 2);
  } else {
    String elapsed = formatMMSS(replayActive ? replayManager.getElapsedSeconds() : 0);
    String total = formatMMSS(replayActive ? replayManager.getTotalSeconds() : 0);
    text(elapsed + " / " + total, timeLabelX, toolbarY + toolbarH / 2);
  }

  // --- Hız Butonları ---
  drawSpeedButton(speed1X, speedBtnY, speedBtnW, speedBtnH, "1x", 1, replayActive);
  drawSpeedButton(speed2X, speedBtnY, speedBtnW, speedBtnH, "2x", 2, replayActive);
  drawSpeedButton(speed4X, speedBtnY, speedBtnW, speedBtnH, "4x", 4, replayActive);

  // --- Load Log Butonu (Geçmiş Log Dosyası Seçme) ---
  stroke(38, 68, 88);
  fill(color(25, 55, 75));
  rect(loadLogX, loadLogY, loadLogW, loadLogH, 5);
  fill(color(110, 225, 255));
  textSize(11);
  textAlign(CENTER, CENTER);
  text("LOAD LOG FILE", loadLogX + loadLogW / 2, loadLogY + loadLogH / 2);

  // --- Export Log Butonu (Live & Replay Modu) ---
  stroke(hasExportData ? color(50, 120, 160) : color(38, 68, 88));
  fill(hasExportData ? color(25, 60, 85) : color(18, 30, 40));
  rect(exportX, exportY, exportW, exportH, 5);
  fill(hasExportData ? normal : dim);
  textSize(11);
  textAlign(CENTER, CENTER);
  text("EXPORT LOG", exportX + exportW / 2, exportY + exportH / 2);

  // Export Bildirim Rozeti (Toast Notification)
  if (millis() - lastExportTime < 3500 && lastExportMessage.length() > 0) {
    textSize(11);
    float toastW = textWidth(lastExportMessage) + 30;
    float toastH = 28;
    float toastX = exportX + exportW - toastW;
    float toastY = toolbarY - 36;

    fill(14, 38, 55, 240);
    stroke(80, 230, 120);
    strokeWeight(1.5);
    rect(toastX, toastY, toastW, toastH, 6);

    fill(80, 230, 120);
    textAlign(CENTER, CENTER);
    text("✔ " + lastExportMessage, toastX + toastW / 2, toastY + toastH / 2);
  }

  textAlign(LEFT, BASELINE);
}

void drawToolbarButtonBg(float x, float y, float w, float h, boolean active) {
  stroke(38, 68, 88);
  fill(active ? color(25, 45, 65) : color(18, 30, 40));
  rect(x, y, w, h, 5);
}

void drawSpeedButton(float x, float y, float w, float h, String label, int spd, boolean active) {
  boolean selected = active && replayManager.playbackSpeed == spd;
  stroke(38, 68, 88);
  fill(selected ? color(30, 90, 120) : (active ? color(18, 30, 40) : color(14, 24, 32)));
  rect(x, y, w, h, 5);
  fill(selected ? color(255) : (active ? color(180) : color(70, 90, 105)));
  textSize(11);
  textAlign(CENTER, CENTER);
  text(label, x + w / 2, y + h / 2);
  textAlign(LEFT, BASELINE);
}

String formatMMSS(int totalSecs) {
  int m = totalSecs / 60;
  int s = totalSecs % 60;
  return nf(m, 2) + ":" + nf(s, 2);
}

boolean isOverRect(float mx, float my, float x, float y, float w, float h) {
  return mx >= x && mx <= x + w && my >= y && my <= y + h;
}

void mousePressed() {
  if (mouseX >= 320 && mouseX <= 410 && mouseY >= 20 && mouseY <= 50) {
    isLiveMode = true;
    eventEngine.loggingEnabled = true;
    eventEngine.resetDetectionState();
  } else if (mouseX >= 420 && mouseX <= 510 && mouseY >= 20 && mouseY <= 50) {
    isLiveMode = false;
    eventEngine.loggingEnabled = false; // Replay'de yeniden hesaplanan eventler live CSV'ye karışmasın
    if (!replayManager.hasData()) {
      replayManager.loadLatestLog();
    }
    replayManager.syncState(mapView, speedGraph);
  } else if (isOverRect(mouseX, mouseY, gearFuncX, gearFuncY, gearSize, gearSize)) {
    showSettingsPanel = !showSettingsPanel;
  }

  // Harita Zoom Butonları
  mapView.handleMouseClick(mouseX, mouseY);

  // Grafik Sekme & Zaman Aralığı Butonları Tıklamaları
  speedGraph.handleClick(mouseX, mouseY);

  // Gösterge Kartlarına Tıklayarak İlgili Grafiğe Hızlı Odaklanma
  if (isOverRect(mouseX, mouseY, speedCard.x, speedCard.y, speedCard.w, speedCard.h)) {
    speedGraph.setMetric(speedGraph.METRIC_SPEED);
  } else if (isOverRect(mouseX, mouseY, engineTempCard.x, engineTempCard.y, engineTempCard.w, engineTempCard.h)) {
    speedGraph.setMetric(speedGraph.METRIC_TEMP);
  } else if (isOverRect(mouseX, mouseY, batteryCard.x, batteryCard.y, batteryCard.w, batteryCard.h)) {
    speedGraph.setMetric(speedGraph.METRIC_BATTERY);
  } else if (isOverRect(mouseX, mouseY, healthPanel.x, healthPanel.y, healthPanel.w, healthPanel.h)) {
    speedGraph.setMetric(speedGraph.METRIC_NETWORK);
  }

  // LOAD LOG FILE Butonu (Her zaman tıklanabilir)
  if (!isLiveMode && isOverRect(mouseX, mouseY, loadLogX, loadLogY, loadLogW, loadLogH)) {
    selectInput("Replay İçin Telemetri CSV Dosyası Seçin (.csv):", "logFileSelected");
    return;
  }

  // EXPORT LOG Butonu (Hem Live hem Replay modunda çalışır)
  if (isOverRect(mouseX, mouseY, exportX, exportY, exportW, exportH)) {
    if (isLiveMode) {
      if (dataLogger.hasLiveData()) {
        String outPath = dataLogger.exportLiveData();
        if (outPath != null) {
          lastExportMessage = "Live Log Exported: " + outPath;
          lastExportTime = millis();
          println("[Export] " + lastExportMessage);
        }
      } else {
        lastExportMessage = "No live telemetry to export yet.";
        lastExportTime = millis();
        println("[Export] " + lastExportMessage);
      }
    } else {
      if (replayManager.hasData()) {
        String outPath = dataLogger.exportData(replayManager.replayDataList);
        if (outPath != null) {
          lastExportMessage = "Replay Log Exported: " + outPath;
          lastExportTime = millis();
          println("[Export] " + lastExportMessage);
        }
      }
    }
    return;
  }

  // Replay toolbar diğer kontrolleri
  if (!isLiveMode && replayManager.hasData()) {
    if (isOverRect(mouseX, mouseY, btnRestartX, btnY, btnW, btnH)) {
      replayManager.restart(mapView, speedGraph);
    } else if (isOverRect(mouseX, mouseY, btnPlayX, btnY, btnW, btnH)) {
      replayManager.togglePlayPause();
    } else if (isOverRect(mouseX, mouseY, speed1X, speedBtnY, speedBtnW, speedBtnH)) {
      replayManager.setSpeed(1);
    } else if (isOverRect(mouseX, mouseY, speed2X, speedBtnY, speedBtnW, speedBtnH)) {
      replayManager.setSpeed(2);
    } else if (isOverRect(mouseX, mouseY, speed4X, speedBtnY, speedBtnW, speedBtnH)) {
      replayManager.setSpeed(4);
    } else if (isOverRect(mouseX, mouseY, sliderX, sliderY - 8, sliderW, sliderH + 16)) {
      draggingSlider = true;
      float frac = (mouseX - sliderX) / sliderW;
      replayManager.seekToFraction(frac, mapView, speedGraph);
    }
  }
}

void mouseDragged() {
  if (draggingSlider && !isLiveMode && replayManager.hasData()) {
    float frac = (mouseX - sliderX) / sliderW;
    replayManager.seekToFraction(frac, mapView, speedGraph);
  }
}

void mouseReleased() {
  draggingSlider = false;
}

void logFileSelected(File selection) {
  if (selection == null) {
    println("[Replay] Dosya secimi iptal edildi.");
  } else {
    String path = selection.getAbsolutePath();
    println("[Replay] Secilen dosya: " + path);
    replayManager.loadLogFile(path);
    replayManager.syncState(mapView, speedGraph);
    isLiveMode = false;
    eventEngine.loggingEnabled = false;
  }
}
void receive(byte[] data) {
  if (udpReceiver != null) {
    udpReceiver.receive(data, "127.0.0.1", configPort);
  }
}

void receive(byte[] data, String ip, int port) {
  if (udpReceiver != null) {
    udpReceiver.receive(data, ip, port);
  }
}
