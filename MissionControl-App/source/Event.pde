import java.util.Date;
import java.text.SimpleDateFormat;

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

  String getTimeString() {
    Date date = new Date(timestamp);
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    return sdf.format(date);
  }
}
