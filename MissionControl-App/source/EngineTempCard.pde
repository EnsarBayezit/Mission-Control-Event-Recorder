// Engine Temperature Gösterge Paneli ve Termometre Simülasyonu
class EngineTempCard {
  float x, y, w, h; 
  String title;
  
  EngineTempCard(float x, float y, float w, float h, String title) { 
    this.x = x; this.y = y; this.w = w; this.h = h; this.title = title; 
  }
  
  void display(float tempVal) {
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
    color themeColor = isOverheat ? color(255, 80, 80) : color(90, 220, 255);
    color activeBarColor = isOverheat ? color(255, 80, 80) : color(80, 160, 255); // Görseldeki mavi tonu
    
    float cx = x + 70;
    float cy = y + h / 2 + 10;
    
    //1. Termometre İkonu Çizimi
    noFill();
    stroke(themeColor);
    strokeWeight(2.5);
    
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
