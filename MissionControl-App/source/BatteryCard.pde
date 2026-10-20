class BatteryCard {
  float x, y, w, h; 
  String title;
  
  BatteryCard(float x, float y, float w, float h, String title) { 
    this.x = x; this.y = y; this.w = w; this.h = h; this.title = title; 
  }
  
  void display(int battVal) {
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
    color themeColor = isLow ? color(255, 80, 80) : color(90, 220, 255);
    color activeBarColor = isLow ? color(255, 80, 80) : color(80, 230, 120);
    
    float cx = x + 70;
    float cy = y + h / 2 + 10;
    
    // Pil Gövdesi 
    noFill();
    stroke(themeColor);
    strokeWeight(2.5);
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
