class EventEngine {
  ArrayList<Event> events = new ArrayList<Event>();
  DataLogger logger = null;
  boolean loggingEnabled = true;

  void setLogger(DataLogger logger) {
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

  void resetDetectionState() {
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
  void evaluate(TelemetryData d) {
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
        addEvent(ts, "CONNECTION WEAK", "Network signal quality fell to " + int(d.network) + "%", "WARNING");
        weakNetworkActive = true;
      }
    } else {
      weakNetworkActive = false;
    }

    if (lastHeading >= 0) {
      float headingDiff = abs(d.heading - lastHeading);
      if (headingDiff > 45 && headingDiff < 315) {
        if (!rapidTurnActive) {
          addEvent(ts, "RAPID TURN", "Sharp course change detected (" + int(headingDiff) + " deg shift)", "WARNING");
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
  void checkGpsLost(long gapMs, long eventTimeMs) {
    if (gapMs > 3000) {
      if (!gpsLostActive) {
        addEvent(eventTimeMs, "GPS LOST", "No telemetry packets received for 3+ seconds", "CRITICAL");
        gpsLostActive = true;
      }
    } else {
      gpsLostActive = false;
    }
  }

  void addEvent(long ts, String type, String desc, String sev) {
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
