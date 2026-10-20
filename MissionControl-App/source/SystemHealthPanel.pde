// Sağ Taraf Sistem Sağlığı ve Durum Paneli
class SystemHealthPanel {
  float x, y, w, h;

  SystemHealthPanel(float x, float y, float w, float h) {
    this.x = x; this.y = y; this.w = w; this.h = h;
  }

  void display(TelemetryData d, String connState, long lastPacketMs, int uptimeSecs) {
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
    drawHealthRow("NETWORK", int(net), rowStartX, rowStartY + rowGap);
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

  void drawHealthRow(String label, int val, float rx, float ry) {
    fill(210); textSize(12); textAlign(LEFT, CENTER);
    text(label, rx, ry);
    
    // Bar grafiği
    float barX = rx + 85;
    int active = round(map(constrain(val, 0, 100), 0, 100, 0, 6));
    color c = (val < 30) ? color(255, 80, 80) : color(80, 230, 120);
    for(int i=0; i<6; i++) {
      fill(i < active ? c : color(25, 45, 60));
      rect(barX + i*13, ry-6, 10, 12, 2);
    }
    // Değer yazısı
    fill(210); textAlign(RIGHT, CENTER); text(val + "%", x + w - 18, ry);
    textAlign(LEFT, BASELINE);
  }

  void drawStatusRow(String label, String val, color col, float rx, float ry) {
    fill(180); textSize(12); textAlign(LEFT, CENTER);
    text(label, rx, ry);
    fill(col); textAlign(RIGHT, CENTER);
    text(val, x + w - 18, ry);
    textAlign(LEFT, BASELINE);
  }
  
  String formatUptime(int s) { return nf(s/3600, 2) + ":" + nf((s%3600)/60, 2) + ":" + nf(s%60, 2); }
  String modeName(int m) { return (m==0) ? "MANUAL" : (m==1) ? "AUTO" : "RTL"; }
}
