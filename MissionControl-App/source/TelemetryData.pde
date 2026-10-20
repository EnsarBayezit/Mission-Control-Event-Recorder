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
    this.lat = 36.812345;
    this.lon = 34.641234;
    this.heading = 0.0;
    this.speed = 0.0;
    this.battery = 100;
    this.temp = 62.0;
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

  String getModeString() {
    if (mode == 0) return "MANUAL";
    if (mode == 1) return "AUTO";
    if (mode == 2) return "RTL";
    return "UNKNOWN";
  }
}
