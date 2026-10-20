import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

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
import java.util.ArrayList; 
import java.util.Date; 
import java.text.SimpleDateFormat; 
import java.util.ArrayList; 
import java.util.ArrayList; 
import java.util.ArrayList; 
import java.io.File; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class MissionControl extends PApplet {




















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

public void setup() {
  
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


public void loadConfig() {
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

public void draw() {
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

public void drawTopBar() {
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
  float blink = isTrouble ? (sin(millis() * 0.008f) + 1) / 2.0f : 1.0f;
  int dotColor = isOnline ? color(80, 230, 120) : lerpColor(color(90, 20, 20), color(255, 70, 70), blink);

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
  text("FPS: " + PApplet.parseInt(currentFps), 940, 35);

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

public void drawGearIcon(float cx, float cy, boolean active) {
  pushMatrix();
  translate(cx, cy);
  stroke(active ? color(103, 220, 255) : color(140, 160, 175));
  strokeWeight(2);
  noFill();
  ellipse(0, 0, 16, 16);
  for (int i = 0; i < 8; i++) {
    float a = i * TWO_PI / 8.0f;
    line(cos(a) * 9, sin(a) * 9, cos(a) * 13, sin(a) * 13);
  }
  noStroke();
  fill(active ? color(103, 220, 255) : color(140, 160, 175));
  ellipse(0, 0, 5, 5);
  popMatrix();
}

public void drawSettingsPanel() {
  if (!showSettingsPanel) return;

  float x = 1000, y = 80, w = 340, h = 150;

  fill(14, 28, 40);
  stroke(60, 110, 140);
  strokeWeight(1.5f);
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

public void drawTelemetryCards(TelemetryData d) {
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
  float smooth = 0.25f;
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
public void drawAlertPulse(float x, float y, float w, float h) {
  float pulse = (sin(millis() * 0.008f) + 1) / 2.0f; // 0..1 arası salınım
  noFill();
  stroke(255, 60, 60, 110 + pulse * 120);
  strokeWeight(2.5f + pulse * 1.5f);
  rect(x - 3, y - 3, w + 6, h + 6, 9);
}

// Açı değerlerini (0-360)
public float lerpAngle(float current, float target, float t) {
  float diff = target - current;
  while (diff > 180) diff -= 360;
  while (diff < -180) diff += 360;
  float result = current + diff * t;
  while (result < 0) result += 360;
  while (result >= 360) result -= 360;
  return result;
}

public void drawSystemHealthPanel(TelemetryData d) {
  long lastPacketTimeMs = millis() - lastPacketTime;
  int uptimeSecs = (int) ((millis() - appStartMillis) / 1000);

  healthPanel.display(d, connectionState, lastPacketTimeMs, uptimeSecs);
}

public void drawEventTimeline() {
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
    int sevColor;
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

public void drawReplayToolbar() {
  boolean replayActive = !isLiveMode && replayManager.hasData();
  boolean hasExportData = (isLiveMode && dataLogger.hasLiveData()) || (!isLiveMode && replayManager.hasData());

  int dim = color(70, 90, 105);
  int normal = color(90, 220, 255);

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

  float frac = replayActive ? replayManager.getProgressFraction() : (isLiveMode ? 1.0f : 0);
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
    strokeWeight(1.5f);
    rect(toastX, toastY, toastW, toastH, 6);

    fill(80, 230, 120);
    textAlign(CENTER, CENTER);
    text("✔ " + lastExportMessage, toastX + toastW / 2, toastY + toastH / 2);
  }

  textAlign(LEFT, BASELINE);
}

public void drawToolbarButtonBg(float x, float y, float w, float h, boolean active) {
  stroke(38, 68, 88);
  fill(active ? color(25, 45, 65) : color(18, 30, 40));
  rect(x, y, w, h, 5);
}

public void drawSpeedButton(float x, float y, float w, float h, String label, int spd, boolean active) {
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

public String formatMMSS(int totalSecs) {
  int m = totalSecs / 60;
  int s = totalSecs % 60;
  return nf(m, 2) + ":" + nf(s, 2);
}

public boolean isOverRect(float mx, float my, float x, float y, float w, float h) {
  return mx >= x && mx <= x + w && my >= y && my <= y + h;
}

public void mousePressed() {
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

public void mouseDragged() {
  if (draggingSlider && !isLiveMode && replayManager.hasData()) {
    float frac = (mouseX - sliderX) / sliderW;
    replayManager.seekToFraction(frac, mapView, speedGraph);
  }
}

public void mouseReleased() {
  draggingSlider = false;
}

public void logFileSelected(File selection) {
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
public void receive(byte[] data) {
  if (udpReceiver != null) {
    udpReceiver.receive(data, "127.0.0.1", configPort);
  }
}

public void receive(byte[] data, String ip, int port) {
  if (udpReceiver != null) {
    udpReceiver.receive(data, ip, port);
  }
}
class BatteryCard {
  float x, y, w, h; 
  String title;
  
  BatteryCard(float x, float y, float w, float h, String title) { 
    this.x = x; this.y = y; this.w = w; this.h = h; this.title = title; 
  }
  
  public void display(int battVal) {
    battVal = constrain(battVal, 0, 100);
    // Kart Arka Planı
    fill(14, 28, 40); 
    stroke(35, 63, 82); 
    rect(x, y, w, h, 7);
    
    // Başlık
    fill(100, 215, 250); 
    textSize(13); 
    textAlign(LEFT, CENTER);
    text(title, x + 18, y + 20);
    
    boolean isLow = battVal < 20;
    int themeColor = isLow ? color(255, 80, 80) : color(90, 220, 255);
    int activeBarColor = isLow ? color(255, 80, 80) : color(80, 230, 120);
    
    float cx = x + 70;
    float cy = y + h / 2 + 10;
    
    // Pil Gövdesi 
    noFill();
    stroke(themeColor);
    strokeWeight(2.5f);
    // Ana gövde
    rect(cx - 20, cy - 30, 40, 60, 5);
    // Üst kutup başı
    fill(themeColor);
    rect(cx - 8, cy - 38, 16, 8, 3);
    
    //2. Görseldeki Gibi Kademeli (Bölmeli) Çubuklar
    noStroke();
    int totalSegments = 4; // Pil içindeki kademe sayısı
    float innerX = cx - 15;
    float innerY = cy - 25;
    float innerW = 30;
    float innerH = 50;
    
    float segmentH = (innerH - (totalSegments - 1) * 4) / totalSegments;
    
    // Yüzdeye göre kaç kademenin yanacağını hesapla
    int activeSegments = round(map(constrain(battVal, 0, 100), 0, 100, 0, totalSegments));
    
    for (int i = 0; i < totalSegments; i++) {
      
      int segIndex = totalSegments - 1 - i;
      
      if (segIndex < activeSegments) {
        fill(activeBarColor); 
      } else {
        fill(25, 45, 65);   
      }
      
      float segY = innerY + i * (segmentH + 4);
      rect(innerX, segY, innerW, segmentH, 2);
    }
    
    //Dijital Yüzde ve Alt Bar Yazısı
    fill(themeColor);
    textSize(26);
    textAlign(LEFT, CENTER);
    text(battVal + "%", x + 125, cy - 4);
    
    // Alt Mini Progress Bar
    float barX = x + 125;
    float barY = cy + 24;
    float barW = 120;
    float barH = 10;
    
    fill(20, 35, 50);
    stroke(40, 70, 95);
    strokeWeight(1);
    rect(barX, barY, barW, barH, 3);
    
    noStroke();
    fill(activeBarColor);
    float activeW = map(constrain(battVal, 0, 100), 0, 100, 0, barW - 4);
    rect(barX + 2, barY + 2, activeW, barH - 4, 2);
    
    textAlign(LEFT, BASELINE);
  }
}
// CSV Veri Kayıt Yöneticisi


class DataLogger {
  PrintWriter telemetryLog;
  PrintWriter eventLog;
  ArrayList<TelemetryData> liveTelemetryList = new ArrayList<TelemetryData>();

  DataLogger() {
    String timeStampStr = nf(year(), 4) + nf(month(), 2) + nf(day(), 2) + "_" + nf(hour(), 2) + nf(minute(), 2) + nf(second(), 2);
    
    // Telemetri CSV Dosyası Oluşturma
    telemetryLog = createWriter("logs/telemetry_" + timeStampStr + ".csv");
    telemetryLog.println("timestamp,lat,lon,heading,speed,battery,temp,mode,network");
    telemetryLog.flush();

    // Event CSV Dosyası Oluşturma
    eventLog = createWriter("logs/events_" + timeStampStr + ".csv");
    eventLog.println("time,type,description,severity");
    eventLog.flush();
  }

  public void logTelemetry(TelemetryData d) {
    if (d != null) {
      liveTelemetryList.add(d);
      if (telemetryLog != null) {
        telemetryLog.println(d.timestamp + "," + d.lat + "," + d.lon + "," + d.heading + "," + 
                             d.speed + "," + d.battery + "," + d.temp + "," + d.mode + "," + d.network);
        telemetryLog.flush();
      }
    }
  }

  public boolean hasLiveData() {
    return liveTelemetryList.size() > 0;
  }

  public String exportLiveData() {
    return exportData(liveTelemetryList);
  }

  public void logEvent(Event ev) {
    if (eventLog != null && ev != null) {
      String safeDesc = ev.description.replace("\"", "'");
      eventLog.println(ev.getTimeString() + "," + ev.type + ",\"" + safeDesc + "\"," + ev.severity);
      eventLog.flush();
    }
  }

  // Replay ve Live oturumlarındaki verileri CSV dosyası olarak logs/exports/ altına kaydeder.
  public String exportData(ArrayList<TelemetryData> data) {
    if (data == null || data.size() == 0) return null;

    try {
      String timeStampStr = nf(year(), 4) + nf(month(), 2) + nf(day(), 2) + "_" + nf(hour(), 2) + nf(minute(), 2) + nf(second(), 2);
      String outPath = "logs/exports/export_" + timeStampStr + ".csv";

      PrintWriter exportWriter = createWriter(outPath);
      exportWriter.println("timestamp,lat,lon,heading,speed,battery,temp,mode,network");
      for (TelemetryData d : data) {
        exportWriter.println(d.timestamp + "," + d.lat + "," + d.lon + "," + d.heading + "," +
                              d.speed + "," + d.battery + "," + d.temp + "," + d.mode + "," + d.network);
      }
      exportWriter.flush();
      exportWriter.close();

      return outPath;
    } catch (Exception e) {
      println("[DataLogger] Export hatasi: " + e.getMessage());
      return null;
    }
  }
}
// Engine Temperature Gösterge Paneli ve Termometre Simülasyonu
class EngineTempCard {
  float x, y, w, h; 
  String title;
  
  EngineTempCard(float x, float y, float w, float h, String title) { 
    this.x = x; this.y = y; this.w = w; this.h = h; this.title = title; 
  }
  
  public void display(float tempVal) {
    // Kart Arka Planı
    fill(14, 28, 40); 
    stroke(35, 63, 82); 
    rect(x, y, w, h, 7);
    
    // Başlık
    fill(100, 215, 250); 
    textSize(13); 
    textAlign(LEFT, CENTER);
    text(title, x + 18, y + 20);
    
    // Aşırı Isınma Kontrolü (> 80 °C)
    boolean isOverheat = tempVal > 80;
    int themeColor = isOverheat ? color(255, 80, 80) : color(90, 220, 255);
    int activeBarColor = isOverheat ? color(255, 80, 80) : color(80, 160, 255); // Görseldeki mavi tonu
    
    float cx = x + 70;
    float cy = y + h / 2 + 10;
    
    //1. Termometre İkonu Çizimi
    noFill();
    stroke(themeColor);
    strokeWeight(2.5f);
    
    // Termometre Üst Borusu
    rect(cx - 7, cy - 35, 14, 45, 7);
    // Termometre Alt Topu (Hazne)
    ellipse(cx, cy + 12, 26, 26);
    
    // Termometre İçi Doluluk (0 - 120 °C)
    noStroke();
    fill(activeBarColor);
    ellipse(cx, cy + 12, 18, 18); // Alt hazne doluluğu
    
    float fillHeight = map(constrain(tempVal, 20, 120), 20, 120, 0, 38);
    rect(cx - 4, (cy + 8) - fillHeight, 8, fillHeight, 2);
    
    //2. Dijital Sıcaklık Yazısı
    fill(themeColor);
    textSize(26);
    textAlign(LEFT, CENTER);
    text(nf(tempVal, 0, 1) + " °C", x + 125, cy - 4);
    
    //3. Görseldeki Gibi Kademeli Bloklar (Alt Progress Bar)
    float barX = x + 125;
    float barY = cy + 24;
    float totalW = 125;
    int totalSegments = 5; //5 bölmeli
    float segW = (totalW - (totalSegments - 1) * 4) / totalSegments;
    
    // Sıcaklığa göre kaç kademenin yanacağı (20°C - 100°C)
    int activeSegments = round(map(constrain(tempVal, 20, 100), 20, 100, 0, totalSegments));
    
    for (int i = 0; i < totalSegments; i++) {
      if (i < activeSegments) {
        fill(activeBarColor);
      } else {
        fill(25, 45, 65);
      }
      
      float segX = barX + i * (segW + 4);
      noStroke();
      rect(segX, barY, segW, 10, 2);
    }
    
    textAlign(LEFT, BASELINE);
  }
}



// Olay (Event) Veri Yapısı
class Event {
  long timestamp;
  String type;
  String description;
  String severity; // INFO, WARNING, CRITICAL

  Event(long timestamp, String type, String description, String severity) {
    this.timestamp = timestamp;
    this.type = type;
    this.description = description;
    this.severity = severity;
  }

  public String getTimeString() {
    Date date = new Date(timestamp);
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    return sdf.format(date);
  }
}
class EventEngine {
  ArrayList<Event> events = new ArrayList<Event>();
  DataLogger logger = null;
  boolean loggingEnabled = true;

  public void setLogger(DataLogger logger) {
    this.logger = logger;
  }

  boolean lowBatteryActive = false;
  boolean overheatActive = false;
  boolean overspeedActive = false;
  boolean gpsLostActive = false;
  boolean weakNetworkActive = false;
  boolean rapidTurnActive = false;
  int lastMode = -1;
  float lastHeading = -1;

  public void resetDetectionState() {
    lowBatteryActive = false;
    overheatActive = false;
    overspeedActive = false;
    gpsLostActive = false;
    weakNetworkActive = false;
    rapidTurnActive = false;
    lastMode = -1;
    lastHeading = -1;
  }

  // Telemetriye bağlı 6 kural. GPS LOST buradan çıkarıldı — bkz. checkGpsLost().
  public void evaluate(TelemetryData d) {
    long ts = d.timestamp;

    if (d.battery < 20) {
      if (!lowBatteryActive) {
        addEvent(ts, "LOW BATTERY", "Battery level dropped to " + d.battery + "%", "CRITICAL");
        lowBatteryActive = true;
      }
    } else {
      lowBatteryActive = false;
    }

    if (d.temp > 80) {
      if (!overheatActive) {
        addEvent(ts, "ENGINE OVERHEAT", "Engine temperature reached " + nf(d.temp, 0, 1) + " C", "CRITICAL");
        overheatActive = true;
      }
    } else {
      overheatActive = false;
    }

    if (d.speed > 15) {
      if (!overspeedActive) {
        addEvent(ts, "OVERSPEED", "Speed exceeded safe limit: " + nf(d.speed, 0, 1) + " kn", "WARNING");
        overspeedActive = true;
      }
    } else {
      overspeedActive = false;
    }

    if (d.network < 30) {
      if (!weakNetworkActive) {
        addEvent(ts, "CONNECTION WEAK", "Network signal quality fell to " + PApplet.parseInt(d.network) + "%", "WARNING");
        weakNetworkActive = true;
      }
    } else {
      weakNetworkActive = false;
    }

    if (lastHeading >= 0) {
      float headingDiff = abs(d.heading - lastHeading);
      if (headingDiff > 45 && headingDiff < 315) {
        if (!rapidTurnActive) {
          addEvent(ts, "RAPID TURN", "Sharp course change detected (" + PApplet.parseInt(headingDiff) + " deg shift)", "WARNING");
          rapidTurnActive = true;
        }
      } else {
        rapidTurnActive = false;
      }
    }
    lastHeading = d.heading;

    if (lastMode != -1 && d.mode != lastMode) {
      addEvent(ts, "MODE CHANGE", "System operation mode switched to ID " + d.mode, "INFO");
    }
    lastMode = d.mode;
  }

  // GPS LOST için TEK yetkili kontrol noktası — hem LIVE hem REPLAY buradan geçer.
  //  gapMs: LIVE'da "şimdi - son gerçek paket zamanı"; REPLAY'de
  //         ReplayManager.getCurrentGapMs() (kayıtlı iki örnek arası fark)
  //  eventTimeMs: event'in taşıyacağı zaman damgası (epoch ms)
  public void checkGpsLost(long gapMs, long eventTimeMs) {
    if (gapMs > 3000) {
      if (!gpsLostActive) {
        addEvent(eventTimeMs, "GPS LOST", "No telemetry packets received for 3+ seconds", "CRITICAL");
        gpsLostActive = true;
      }
    } else {
      gpsLostActive = false;
    }
  }

  public void addEvent(long ts, String type, String desc, String sev) {
    Event ev = new Event(ts, type, desc, sev);
    events.add(0, ev);
    if (events.size() > 50) {
      events.remove(events.size() - 1);
    }
    if (logger != null && loggingEnabled) {
      logger.logEvent(ev);
    }
  }
}


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

  public void addData(float speedVal, long timestamp) {
    addData(timestamp, speedVal, 62.0f, 100.0f, 90.0f);
  }

  public void addTelemetry(TelemetryData d) {
    if (d == null) return;
    addData(d.timestamp, (float)d.speed, (float)d.temp, (float)d.battery, (float)d.network);
  }

  public void addData(long timestamp, float speedVal, float tempVal, float battVal, float netVal) {
    timestamps.add(timestamp);
    speedPoints.add(speedVal);
    tempPoints.add(tempVal);
    batteryPoints.add(battVal);
    networkPoints.add(netVal);
    pruneOld(timestamp);
  }

  public void clear() {
    timestamps.clear();
    speedPoints.clear();
    tempPoints.clear();
    batteryPoints.clear();
    networkPoints.clear();
  }

  public void rebuildHistory(ArrayList<TelemetryData> dataList, int upToIndex) {
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

  public void pruneOld(long nowTs) {
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

  public void setMetric(int metricIdx) {
    if (metricIdx >= 0 && metricIdx <= 4) {
      currentMetric = metricIdx;
    }
  }

  public void setWindow(int windowIdx) {
    if (windowIdx >= 0 && windowIdx < availableWindows.length) {
      selectedWindowIdx = windowIdx;
      windowMs = availableWindows[windowIdx];
    }
  }

  // Standart display() çağrısı (Live varsayılanı)
  public void display() {
    display(true, System.currentTimeMillis());
  }

  // Replay duyarlı display çağrısı
  public void display(boolean isLive, long replayCurrentTs) {
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
      text(PApplet.parseInt(yVal) + (i == 0 ? " " + unit : ""), graphLeft - 8, gy);
    }

    // 5. Uyarı / Eşik Çizgisi (Varsa kesikli kırmızı çizgi)
    if (limitVal >= minY && limitVal <= maxY && currentMetric != METRIC_MULTI) {
      float limitY = map(limitVal, minY, maxY, graphBottom, graphTop);
      stroke(COLOR_LIMIT);
      strokeWeight(1.2f);
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
        label = "-" + PApplet.parseInt(minsAgo) + "m";
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
  public void drawSeries(ArrayList<Float> pts, float minY, float maxY, long startTs, long endTs,
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
  public void drawLivePulseDot(ArrayList<Float> pts, float minY, float maxY, long startTs, long endTs,
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
  public void drawControlTabs() {
    float startX = x + 245;
    float tabY = y + 10;
    float tabH = 20;

    // Metrik Butonları
    String[] shortNames = { "SPEED", "TEMP", "BATTERY", "NETWORK", "MULTI" };
    int[] tabColors = { COLOR_SPEED, COLOR_TEMP, COLOR_BATTERY, COLOR_NETWORK, color(230, 230, 255) };

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
  public void drawStatsBadge(float bx, float by, float minY, float maxY, String unit, int c) {
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
  public void drawMultiLegend(float lx, float ly) {
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
  public void drawHoverTooltip(float mx, float my, float gLeft, float gRight, float gBottom, float gTop,
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
        fill(COLOR_BATTERY); text("Battery: " + PApplet.parseInt(batteryPoints.get(bestIdx).floatValue()) + " %", tipX + 8, tipY + 42);
        fill(COLOR_NETWORK); text("Network: " + PApplet.parseInt(networkPoints.get(bestIdx).floatValue()) + " %", tipX + 8, tipY + 54);
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

  public ArrayList<Float> getActivePointsList() {
    if (currentMetric == METRIC_TEMP) return tempPoints;
    if (currentMetric == METRIC_BATTERY) return batteryPoints;
    if (currentMetric == METRIC_NETWORK) return networkPoints;
    return speedPoints; // default SPEED
  }

  // Tıklama Olayı (Sekmeler veya Zaman Seçici)
  public boolean handleClick(float mx, float my) {
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
class HeadingCard {
  float x, y, w, h; 
  String title;
  
  HeadingCard(float x, float y, float w, float h, String title) { 
    this.x = x; this.y = y; this.w = w; this.h = h; this.title = title; 
  }
  
  public void display(float val) {
    // Kart Arka Planı
    fill(14, 28, 40); 
    stroke(35, 63, 82); 
    rect(x, y, w, h, 7);
    
    // Başlık
    fill(100, 215, 250); 
    textSize(13); 
    textAlign(LEFT, CENTER);
    text(title, x + 18, y + 20);
    
    float cx = x + w / 2;
    float cy = y + h / 2 + 12;
    float radius = 62;
    
    // Dış Çift Katmanlı Çember Tasarımı ---
    noFill();
    stroke(45, 75, 100);
    strokeWeight(3);
    ellipse(cx, cy, radius * 2, radius * 2);
    
    stroke(25, 45, 65);
    strokeWeight(1);
    ellipse(cx, cy, (radius - 8) * 2, (radius - 8) * 2);
    
    // Pusula Çizgileri ve Bölmeleri (Ticks)
    for (int i = 0; i < 360; i += 5) {
      float angle = radians(i);
      boolean isMajor = (i % 30 == 0); // 12 büyük aralık (30 derecede bir)
      
      float outerR = radius - 3;
      float innerR = isMajor ? (radius - 10) : (radius - 6);
      
      float x1 = cx + cos(angle - HALF_PI) * outerR;
      float y1 = cy + sin(angle - HALF_PI) * outerR;
      float x2 = cx + cos(angle - HALF_PI) * innerR;
      float y2 = cy + sin(angle - HALF_PI) * innerR;
      
      stroke(isMajor ? 140 : 70, isMajor ? 175 : 100, isMajor ? 200 : 120);
      strokeWeight(isMajor ? 2 : 1);
      line(x1, y1, x2, y2);
    }
    
    // Yön Harfleri
    fill(160, 190, 215);
    textSize(12);
    textAlign(CENTER, CENTER);
    text("N", cx, cy - radius - 12);
    text("S", cx, cy + radius + 12);
    text("W", cx - radius - 12, cy);
    text("E", cx + radius + 12, cy);
    
    // Dönen Mavi İbre (Heading) 
    pushMatrix();
    translate(cx, cy);
    rotate(radians(val));
    
    noStroke();
    fill(90, 220, 255);
    beginShape();
    vertex(0, -radius + 6);
    vertex(-6, 8);
    vertex(6, 8);
    endShape(CLOSE);
    
    popMatrix();
    
    // Dijital Derece Yazısı
    fill(14, 28, 40, 200);
    noStroke();
    rectMode(CENTER);
    rect(cx, cy, 76, 32, 5);
    rectMode(CORNER);
    
    fill(255);
    textSize(22);
    textAlign(CENTER, CENTER);
    text(PApplet.parseInt(val) + "°", cx, cy);
    
    textAlign(LEFT, BASELINE);
  }
}


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

  public void updateData(TelemetryData d) {
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

  public void clearTrail() {
    trail.clear();
  }

  public void rebuildTrail(ArrayList<TelemetryData> dataList, int upToIndex) {
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

  public boolean isStale() {
    return hasFix && (millis() - lastUpdateMillis > staleThresholdMs);
  }

  public float plotLeft() { return x + 6; }
  public float plotRight() { return x + w - 6; }
  public float plotTop() { return y + 36; }
  public float plotBottom() { return y + h - 6; }

  public void display() {
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

      float blink = (sin(millis() * 0.008f) + 1) / 2.0f;
      fill(255, 70, 70, 140 + blink * 115);
      textSize(13);
      textAlign(CENTER, CENTER);
      text("\u26A0 GPS SIGNAL LOST", x + w / 2, y + h / 2);
    }

    textAlign(LEFT, BASELINE);
  }

  public void drawGrid() {
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

  public void drawTrail() {
    if (trail.size() < 2 || unfoldingMap == null) return;

    pushStyle();
    // Kesikli Yeşil/Cyan Rota Çizgisi
    stroke(40, 220, 130, 220);
    strokeWeight(2.5f);
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

  public void drawVehicle() {
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
    float pulse = (sin(millis() * 0.005f) + 1) / 2.0f;

    // En Dış Mesafe Çemberi (Örn: 200m eşdeğeri)
    noFill();
    stroke(40, 200, 110, 45);
    strokeWeight(1);
    ellipse(px, py, 180, 180);

    // Orta Mesafe Çemberi (Örn: 100m eşdeğeri)
    stroke(40, 210, 120, 75);
    strokeWeight(1.2f);
    ellipse(px, py, 115, 115);

    // İç Dinamik Radar / Doğruluk Alanı (Yeşil Işıltı)
    fill(35, 185, 95, 75);
    noStroke();
    ellipse(px, py, 60, 60);

    // Canlı Nabız (Pulse) Çemberi
    noFill();
    stroke(50, 230, 130, 80 + pulse * 90);
    strokeWeight(1.5f);
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
    strokeWeight(1.5f);
    beginShape();
    vertex(0, -18);   // Burun (İleri yön)
    vertex(12, 14);   // Sağ kanat
    vertex(0, 8);     // İç oyuk merkez
    vertex(-12, 14);  // Sol kanat
    endShape(CLOSE);
    popMatrix();

    popStyle();
  }

  public void drawZoomButtons() {
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

  public void drawCompass() {
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

  public void drawCoordinatesAndScale() {
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
    strokeWeight(1.5f);
    line(scaleX, scaleY, scaleX + scaleW, scaleY);
    line(scaleX, scaleY - 4, scaleX, scaleY + 1);
    line(scaleX + scaleW, scaleY - 4, scaleX + scaleW, scaleY + 1);

    fill(220);
    textSize(9);
    textAlign(CENTER, BOTTOM);
    text("500 m", scaleX + scaleW / 2, scaleY - 3);

    popStyle();
  }

  public void handleMouseClick(float mx, float my) {
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



class ReplayManager {
  EventEngine eventEngine;
  ArrayList<TelemetryData> replayDataList = new ArrayList<TelemetryData>();

  int currentIndex = 0;
  boolean isPlaying = false;
  int playbackSpeed = 1;

  long playbackClockMs = 0;      // Replay'in kendi iç zaman sayacı (0 = kaydın başı)
  long totalDurationMs = 0;      // Kaydın toplam süresi
  int lastUpdateMillis = 0;      // Gerçek zamanda son update() çağrısı 

  ReplayManager(DataLogger logger, EventEngine eventEngine) {
    this.eventEngine = eventEngine;
  }

  public void loadLatestLog() {
    String latestPath = findLatestTelemetryLog();
    if (latestPath == null) {
      loadLogFile("logs/sample_mission.csv");
    } else {
      loadLogFile(latestPath);
    }
  }

  public String findLatestTelemetryLog() {
    File dir = new File(sketchPath("logs"));
    if (!dir.exists() || !dir.isDirectory()) return null;

    File[] files = dir.listFiles();
    if (files == null) return null;

    String best = null;
    for (File f : files) {
      String name = f.getName();
      if (name.startsWith("telemetry_") && name.endsWith(".csv")) {
        if (best == null || name.compareTo(best) > 0) {
          best = name;
        }
      }
    }
    return (best == null) ? null : "logs/" + best;
  }

  public void loadLogFile(String path) {
    replayDataList.clear();
    String[] lines = loadStrings(path);
    if (lines == null || lines.length <= 1) {
      println("[Replay] Gecersiz veya bos dosya: " + path);
      return;
    }

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.length() == 0) continue;
      String[] tokens = split(line, ',');

      if (tokens.length == 9) {
        try {
          long ts = Long.parseLong(tokens[0].trim());
          double lat = Double.parseDouble(tokens[1].trim().replace(',', '.'));
          double lon = Double.parseDouble(tokens[2].trim().replace(',', '.'));
          float head = Float.parseFloat(tokens[3].trim().replace(',', '.'));
          float spd = Float.parseFloat(tokens[4].trim().replace(',', '.'));
          int batt = constrain(Integer.parseInt(tokens[5].trim()), 0, 100);
          float tmp = Float.parseFloat(tokens[6].trim().replace(',', '.'));
          int mod = constrain(Integer.parseInt(tokens[7].trim()), 0, 2);
          int net = constrain(Integer.parseInt(tokens[8].trim()), 0, 100);

          head = ((head % 360.0f) + 360.0f) % 360.0f;
          replayDataList.add(new TelemetryData(ts, lat, lon, head, spd, batt, tmp, mod, net));
        } catch (Exception e) {
          println("Replay Parse Error at line " + i + ": " + e.getMessage());
        }
      } else if (tokens.length == 14) {
        try {
          long ts = Long.parseLong(tokens[0].trim());
          double lat = Double.parseDouble(tokens[1].trim() + "." + tokens[2].trim());
          double lon = Double.parseDouble(tokens[3].trim() + "." + tokens[4].trim());
          float head = Float.parseFloat(tokens[5].trim() + "." + tokens[6].trim());
          float speed = Float.parseFloat(tokens[7].trim() + "." + tokens[8].trim());
          int batt = constrain(Integer.parseInt(tokens[9].trim()), 0, 100);
          float tmp = Float.parseFloat(tokens[10].trim() + "." + tokens[11].trim());
          int mod = constrain(Integer.parseInt(tokens[12].trim()), 0, 2);
          int net = constrain(Integer.parseInt(tokens[13].trim()), 0, 100);

          head = ((head % 360.0f) + 360.0f) % 360.0f;
          replayDataList.add(new TelemetryData(ts, lat, lon, head, speed, batt, tmp, mod, net));
        } catch (Exception e) {
          println("Replay Parse Error (14 fields) at line " + i + ": " + e.getMessage());
        }
      }
    }

    currentIndex = 0;
    playbackClockMs = 0;
    lastUpdateMillis = millis();

    if (replayDataList.size() > 0) {
      long first = replayDataList.get(0).timestamp;
      long last = replayDataList.get(replayDataList.size() - 1).timestamp;
      long duration = last - first;
      totalDurationMs = (duration < 0) ? 0 : duration;
    } else {
      totalDurationMs = 0;
    }

    isPlaying = true;
    println("[Replay] Basariyla yuklendi: " + path + " (" + replayDataList.size() + " kayit, Sure: " + (totalDurationMs/1000) + "s)");
  }

  // Harita, grafik ve event timeline'i currentIndex'e tam senkronize eder
  public void syncState(MapView map, Graph graph) {
    if (replayDataList.size() == 0) return;
    
    // 1. EventEngine durumunu sıfırla ve baştan currentIndex'e kadar olan olayları hesapla
    if (eventEngine != null) {
      eventEngine.events.clear();
      eventEngine.resetDetectionState();
      for (int i = 0; i <= currentIndex; i++) {
        eventEngine.evaluate(replayDataList.get(i));
      }
    }

    // 2. Harita rota izini currentIndex'e kadar yeniden kur
    if (map != null) {
      map.rebuildTrail(replayDataList, currentIndex);
    }

    // 3. Hız grafiğini currentIndex'e kadar olan son 5 dakika verisiyle doldur
    if (graph != null) {
      graph.rebuildHistory(replayDataList, currentIndex);
    }
  }

  public void play() {
    if (replayDataList.size() == 0) return;
    if (currentIndex >= replayDataList.size() - 1) {
      restart(null, null);
    }
    isPlaying = true;
    lastUpdateMillis = millis();
  }

  public void pause() {
    isPlaying = false;
  }

  public void togglePlayPause() {
    if (isPlaying) pause(); else play();
  }

  public void restart(MapView map, Graph graph) {
    currentIndex = 0;
    playbackClockMs = 0;
    lastUpdateMillis = millis();
    isPlaying = true;
    if (map != null && graph != null) {
      syncState(map, graph);
    } else if (eventEngine != null) {
      eventEngine.events.clear();
      eventEngine.resetDetectionState();
    }
  }

  public void setSpeed(int spd) {
    playbackSpeed = spd;
  }

  public void seekToFraction(float frac, MapView map, Graph graph) {
    if (replayDataList.size() == 0) return;
    frac = constrain(frac, 0, 1);
    playbackClockMs = (long) (totalDurationMs * frac);
    currentIndex = indexForClock(playbackClockMs);
    lastUpdateMillis = millis();
    syncState(map, graph);
  }

  public void update(MapView map, Graph graph) {
    if (!isPlaying || replayDataList.size() == 0) return;

    int now = millis();
    int dtMs = now - lastUpdateMillis;
    lastUpdateMillis = now;

    playbackClockMs += (long) (dtMs * playbackSpeed);

    if (playbackClockMs >= totalDurationMs) {
      playbackClockMs = totalDurationMs;
      currentIndex = replayDataList.size() - 1;
      isPlaying = false;
      return;
    }

    int oldIndex = currentIndex;
    int newIndex = indexForClock(playbackClockMs);

    if (newIndex != oldIndex) {
      // İleri akarken aradaki paketleri de işle (olay kaçırmamak için)
      for (int i = oldIndex + 1; i <= newIndex; i++) {
        TelemetryData d = replayDataList.get(i);
        if (eventEngine != null) eventEngine.evaluate(d);
        if (map != null) map.updateData(d);
        if (graph != null) graph.addTelemetry(d);
      }
      currentIndex = newIndex;
    }
  }

  public int indexForClock(long clockMs) {
    if (replayDataList.size() == 0) return 0;
    long baseTs = replayDataList.get(0).timestamp;
    long targetTs = baseTs + clockMs;

    int idx = currentIndex;
    int n = replayDataList.size();

    while (idx < n - 1 && replayDataList.get(idx + 1).timestamp <= targetTs) {
      idx++;
    }
    while (idx > 0 && replayDataList.get(idx).timestamp > targetTs) {
      idx--;
    }
    return idx;
  }

  public long getCurrentTimestamp() {
    if (replayDataList.size() > 0 && currentIndex < replayDataList.size()) {
      return replayDataList.get(currentIndex).timestamp;
    }
    return 0;
  }

  public TelemetryData getCurrentTelemetry() {
    if (replayDataList.size() > 0 && currentIndex < replayDataList.size()) {
      return replayDataList.get(currentIndex);
    }
    return null;
  }

  public boolean hasData() {
    return replayDataList.size() > 0;
  }

  public float getProgressFraction() {
    if (totalDurationMs <= 0) return 0;
    return constrain((float) playbackClockMs / (float) totalDurationMs, 0, 1);
  }

  public int getElapsedSeconds() {
    return (int) (playbackClockMs / 1000);
  }

  public int getTotalSeconds() {
    return (int) (totalDurationMs / 1000);
  }
}
class SpeedCard {
  float x, y, w, h;
  String title;

  SpeedCard(float x, float y, float w, float h, String title) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    this.title = title;
  }

  public void display(float speedVal) {
    //Kart Arka Planı
    fill(14, 28, 40);
    stroke(35, 63, 82);
    rect(x, y, w, h, 7);

    // Başlık
    fill(100, 215, 250);
    textSize(13);
    textAlign(LEFT, CENTER);
    text(title, x + 18, y + 20);

    float cx = x + w / 2;
    float cy = y + h / 2 + 10;
    float radius = 58;

    // Kadran açı sınırları
    float startAngle = radians(135);
    float endAngle = radians(405);

    //Arka Plan Çemberi
    noFill();
    stroke(30, 52, 70);
    strokeWeight(4);
    arc(cx, cy, radius * 2, radius * 2, startAngle, endAngle);

    //Anlık Hıza Göre Dinamik Mavi Doluluk Yayı (0 - 30 km/s)
    float constrainedSpeed = constrain(speedVal, 0, 30);
    float activeEndAngle = map(constrainedSpeed, 0, 30, startAngle, endAngle);
    
    if (constrainedSpeed > 0) {
      noFill();
      stroke(90, 220, 255);
      strokeWeight(4.5f);
      arc(cx, cy, radius * 2, radius * 2, startAngle, activeEndAngle);
    }
    //10
    int totalMajorDivisions = 10;
    for (int i = 0; i <= totalMajorDivisions; i++) {
      float currentAngle = map(i, 0, totalMajorDivisions, startAngle, endAngle);

      // Büyük bölme çizgisi
      float outerR = radius - 1;
      float innerR = radius - 8;

      float x1 = cx + cos(currentAngle) * outerR;
      float y1 = cy + sin(currentAngle) * outerR;
      float x2 = cx + cos(currentAngle) * innerR;
      float y2 = cy + sin(currentAngle) * innerR;

      stroke(150, 185, 210);
      strokeWeight(2);
      line(x1, y1, x2, y2);

      // 10 parçaya karşılık gelen değerler
      float valLabel = (i * 30.0f) / totalMajorDivisions;
      float textR = radius + 15;
      float tx = cx + cos(currentAngle) * textR;
      float ty = cy + sin(currentAngle) * textR;

      fill(140, 170, 195);
      textSize(10);
      textAlign(CENTER, CENTER);
      text(nf(valLabel, 0, valLabel == (int)valLabel ? 0 : 1), tx, ty);
    }

    //minik alt değerler
    for (int i = 0; i < totalMajorDivisions; i++) {
      float angleA = map(i, 0, totalMajorDivisions, startAngle, endAngle);
      float angleB = map(i + 1, 0, totalMajorDivisions, startAngle, endAngle);

      for (int sub = 1; sub <= 2; sub++) {
        float subAngle = lerp(angleA, angleB, sub / 3.0f);
        float x1 = cx + cos(subAngle) * (radius - 1);
        float y1 = cy + sin(subAngle) * (radius - 1);
        float x2 = cx + cos(subAngle) * (radius - 5);
        float y2 = cy + sin(subAngle) * (radius - 5);

        stroke(80, 110, 130);
        strokeWeight(1);
        line(x1, y1, x2, y2);
      }
    }

    //Ortadaki Dijital Hız Değeri ve Birim Yazısı
    fill(255);
    textSize(28);
    textAlign(CENTER, CENTER);
    text(nf(speedVal, 0, 1), cx, cy - 4);

    fill(150, 180, 205);
    textSize(12);
    text("kn", cx, cy + 24);

    textAlign(LEFT, BASELINE);
  }
}
// Sağ Taraf Sistem Sağlığı ve Durum Paneli
class SystemHealthPanel {
  float x, y, w, h;

  SystemHealthPanel(float x, float y, float w, float h) {
    this.x = x; this.y = y; this.w = w; this.h = h;
  }

  public void display(TelemetryData d, String connState, long lastPacketMs, int uptimeSecs) {
    //Panel Gövdesi
    fill(14, 28, 40);
    stroke(35, 63, 82);
    strokeWeight(1);
    rect(x, y, w, h, 7);

    // --- SYSTEM HEALTH BÖLÜMÜ ---
    fill(100, 215, 250);
    textSize(13);
    textAlign(LEFT, CENTER);
    text("SYSTEM HEALTH", x + 18, y + 25);

    float rowStartX = x + 18;
    float rowStartY = y + 55;
    float rowGap = 36;

    // Veri yoksa güvenli değerler
    float net = (d != null) ? constrain(d.network, 0, 100) : 0;
    float eng = (d != null) ? d.temp : 62;
    int bat = (d != null) ? constrain(d.battery, 0, 100) : 0;
    
    drawHealthRow("GPS", (connState.equals("CONNECTED") ? 100 : 0), rowStartX, rowStartY);
    drawHealthRow("NETWORK", PApplet.parseInt(net), rowStartX, rowStartY + rowGap);
    drawHealthRow("ENGINE", (eng < 80 ? 91 : 20), rowStartX, rowStartY + rowGap * 2);
    drawHealthRow("POWER", bat, rowStartX, rowStartY + rowGap * 3);

    // Ayırıcı
    stroke(35, 63, 82);
    line(x + 10, y + 215, x + w - 10, y + 215);

    // --- CURRENT STATUS BÖLÜMÜ ---
    fill(100, 215, 250);
    textSize(13);
    textAlign(LEFT, CENTER);
    text("CURRENT STATUS", x + 18, y + 245);

    float statY = y + 285;
    drawStatusRow("MODE", (d != null ? modeName(d.mode) : "MANUAL"), color(80, 230, 120), x + 18, statY);
    drawStatusRow("CONNECTION", connState, (connState.equals("CONNECTED") ? color(80, 230, 120) : color(255, 80, 80)), x + 18, statY + 30);
    drawStatusRow("LAST PACKET", lastPacketMs + " ms", color(200), x + 18, statY + 60);
    drawStatusRow("UPTIME", formatUptime(uptimeSecs), color(200), x + 18, statY + 90);

    textAlign(LEFT, BASELINE);
  }

  public void drawHealthRow(String label, int val, float rx, float ry) {
    fill(210); textSize(12); textAlign(LEFT, CENTER);
    text(label, rx, ry);
    
    // Bar grafiği
    float barX = rx + 85;
    int active = round(map(constrain(val, 0, 100), 0, 100, 0, 6));
    int c = (val < 30) ? color(255, 80, 80) : color(80, 230, 120);
    for(int i=0; i<6; i++) {
      fill(i < active ? c : color(25, 45, 60));
      rect(barX + i*13, ry-6, 10, 12, 2);
    }
    // Değer yazısı
    fill(210); textAlign(RIGHT, CENTER); text(val + "%", x + w - 18, ry);
    textAlign(LEFT, BASELINE);
  }

  public void drawStatusRow(String label, String val, int col, float rx, float ry) {
    fill(180); textSize(12); textAlign(LEFT, CENTER);
    text(label, rx, ry);
    fill(col); textAlign(RIGHT, CENTER);
    text(val, x + w - 18, ry);
    textAlign(LEFT, BASELINE);
  }
  
  public String formatUptime(int s) { return nf(s/3600, 2) + ":" + nf((s%3600)/60, 2) + ":" + nf(s%60, 2); }
  public String modeName(int m) { return (m==0) ? "MANUAL" : (m==1) ? "AUTO" : "RTL"; }
}
class TelemetryData {
  long timestamp;
  double lat;
  double lon;
  float heading;
  float speed;
  int battery;
  float temp;
  int mode;
  int network;

  // Boş Constructor (UDPReceiver için)
  TelemetryData() {
    this.timestamp = 0;
    this.lat = 36.812345f;
    this.lon = 34.641234f;
    this.heading = 0.0f;
    this.speed = 0.0f;
    this.battery = 100;
    this.temp = 62.0f;
    this.mode = 0;
    this.network = 90;
  }

  // Parametreli Constructor
  TelemetryData(long ts, double lat, double lon, float heading, float speed, int battery, float temp, int mode, int network) {
    this.timestamp = ts;
    this.lat = lat;
    this.lon = lon;
    this.heading = heading;
    this.speed = speed;
    this.battery = battery;
    this.temp = temp;
    this.mode = mode;
    this.network = network;
  }

  public String getModeString() {
    if (mode == 0) return "MANUAL";
    if (mode == 1) return "AUTO";
    if (mode == 2) return "RTL";
    return "UNKNOWN";
  }
}
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

  public void update() {
    // hypermedia.net UDP arka planda thread ile calisir
  }

  public void receive(byte[] data, String ip, int port) {
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
  public TelemetryData parsePacket(String message) {
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

  public TelemetryData sanitizeData(long ts, double lat, double lon, float heading, float speed, int battery, float temp, int mode, int network) {
    heading = ((heading % 360.0f) + 360.0f) % 360.0f;
    speed = max(0.0f, speed);
    battery = constrain(battery, 0, 100);
    temp = constrain(temp, -50.0f, 200.0f);
    mode = constrain(mode, 0, 2);
    network = constrain(network, 0, 100);

    return new TelemetryData(ts, lat, lon, heading, speed, battery, temp, mode, network);
  }

  public boolean hasNewPacket() {
    if (newPacket) {
      newPacket = false;
      return true;
    }
    return false;
  }

  public TelemetryData getLatestTelemetry() {
    return latestData;
  }
}
  public void settings() {  size(1400, 850,P2D); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "MissionControl" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
