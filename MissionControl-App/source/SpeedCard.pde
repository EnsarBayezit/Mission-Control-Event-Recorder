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

  void display(float speedVal) {
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
      strokeWeight(4.5);
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
      float valLabel = (i * 30.0) / totalMajorDivisions;
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
        float subAngle = lerp(angleA, angleB, sub / 3.0);
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
