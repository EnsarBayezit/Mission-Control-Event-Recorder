// CSV Veri Kayıt Yöneticisi
import java.util.ArrayList;

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

  void logTelemetry(TelemetryData d) {
    if (d != null) {
      liveTelemetryList.add(d);
      if (telemetryLog != null) {
        telemetryLog.println(d.timestamp + "," + d.lat + "," + d.lon + "," + d.heading + "," + 
                             d.speed + "," + d.battery + "," + d.temp + "," + d.mode + "," + d.network);
        telemetryLog.flush();
      }
    }
  }

  boolean hasLiveData() {
    return liveTelemetryList.size() > 0;
  }

  String exportLiveData() {
    return exportData(liveTelemetryList);
  }

  void logEvent(Event ev) {
    if (eventLog != null && ev != null) {
      String safeDesc = ev.description.replace("\"", "'");
      eventLog.println(ev.getTimeString() + "," + ev.type + ",\"" + safeDesc + "\"," + ev.severity);
      eventLog.flush();
    }
  }

  // Replay ve Live oturumlarındaki verileri CSV dosyası olarak logs/exports/ altına kaydeder.
  String exportData(ArrayList<TelemetryData> data) {
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
