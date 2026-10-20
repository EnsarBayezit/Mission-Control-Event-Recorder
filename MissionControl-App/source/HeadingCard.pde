class HeadingCard {
  float x, y, w, h; 
  String title;
  
  HeadingCard(float x, float y, float w, float h, String title) { 
    this.x = x; this.y = y; this.w = w; this.h = h; this.title = title; 
  }
  
  void display(float val) {
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
    text(int(val) + "°", cx, cy);
    
    textAlign(LEFT, BASELINE);
  }
}
