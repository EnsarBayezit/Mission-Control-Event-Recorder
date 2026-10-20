import java.util.ArrayList;
import java.io.File;

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

  void loadLatestLog() {
    String latestPath = findLatestTelemetryLog();
    if (latestPath == null) {
      loadLogFile("logs/sample_mission.csv");
    } else {
      loadLogFile(latestPath);
    }
  }

  String findLatestTelemetryLog() {
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

  void loadLogFile(String path) {
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
  void syncState(MapView map, Graph graph) {
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

  void play() {
    if (replayDataList.size() == 0) return;
    if (currentIndex >= replayDataList.size() - 1) {
      restart(null, null);
    }
    isPlaying = true;
    lastUpdateMillis = millis();
  }

  void pause() {
    isPlaying = false;
  }

  void togglePlayPause() {
    if (isPlaying) pause(); else play();
  }

  void restart(MapView map, Graph graph) {
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

  void setSpeed(int spd) {
    playbackSpeed = spd;
  }

  void seekToFraction(float frac, MapView map, Graph graph) {
    if (replayDataList.size() == 0) return;
    frac = constrain(frac, 0, 1);
    playbackClockMs = (long) (totalDurationMs * frac);
    currentIndex = indexForClock(playbackClockMs);
    lastUpdateMillis = millis();
    syncState(map, graph);
  }

  void update(MapView map, Graph graph) {
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

  int indexForClock(long clockMs) {
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

  long getCurrentTimestamp() {
    if (replayDataList.size() > 0 && currentIndex < replayDataList.size()) {
      return replayDataList.get(currentIndex).timestamp;
    }
    return 0;
  }

  TelemetryData getCurrentTelemetry() {
    if (replayDataList.size() > 0 && currentIndex < replayDataList.size()) {
      return replayDataList.get(currentIndex);
    }
    return null;
  }

  boolean hasData() {
    return replayDataList.size() > 0;
  }

  float getProgressFraction() {
    if (totalDurationMs <= 0) return 0;
    return constrain((float) playbackClockMs / (float) totalDurationMs, 0, 1);
  }

  int getElapsedSeconds() {
    return (int) (playbackClockMs / 1000);
  }

  int getTotalSeconds() {
    return (int) (totalDurationMs / 1000);
  }
}
