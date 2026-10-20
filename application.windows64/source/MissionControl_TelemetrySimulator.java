import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import hypermedia.net.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class MissionControl_TelemetrySimulator extends PApplet {



/*
  MISSION CONTROL - TELEMETRY SIMULATOR
  Processing 3.5.4
  Library: UDP (hypermedia.net)

  Default destination:
  127.0.0.1:5555

  Packet format:
  timestamp,lat,lon,heading,speed,battery,temp,mode,network

  Example:
  1723290000123,36.812345,34.641234,90.00,8.40,74,61.0,1,72

  MODE:
  0 = MANUAL
  1 = AUTO
  2 = RTL
*/

UDP udp;

String targetIP = "127.0.0.1";
int targetPort = 5555;

float lat = 36.812345f;
float lon = 34.641234f;
float heading = 90.0f;
float speedKn = 8.0f;
float battery = 85.0f;
float engineTemp = 62.0f;
float network = 90.0f;

int mode = 0;

boolean sending = true;
boolean autoRoute = true;
boolean gpsEnabled = true;

int sendIntervalMs = 100; // 10 Hz
int lastSendMs = 0;
int lastUpdateMs = 0;

float routePhase = 0;

ArrayList<SimButton> buttons = new ArrayList<SimButton>();

public void setup() {
  
  surface.setTitle("Mission Control - Telemetry Simulator");
  frameRate(60);

  udp = new UDP(this, 6005);

  buttons.add(new SimButton(40, 525, 185, 42, "ENGINE START", 1));
  buttons.add(new SimButton(235, 525, 185, 42, "TEMP HIGH", 2));
  buttons.add(new SimButton(430, 525, 185, 42, "LOW BATTERY", 3));
  buttons.add(new SimButton(625, 525, 185, 42, "GPS LOSS", 4));
  buttons.add(new SimButton(820, 525, 185, 42, "NETWORK DROP", 5));

  buttons.add(new SimButton(40, 580, 185, 42, "RAPID TURN", 6));
  buttons.add(new SimButton(235, 580, 185, 42, "MODE CHANGE", 7));
  buttons.add(new SimButton(430, 580, 185, 42, "OVERSPEED", 8));
  buttons.add(new SimButton(625, 580, 185, 42, "NORMALIZE", 9));
  buttons.add(new SimButton(820, 580, 185, 42, "ROUTE ON/OFF", 10));

  lastUpdateMs = millis();
}

public void draw() {
  updateSimulation();
  sendTelemetry();
  drawUI();
}

public void updateSimulation() {
  int now = millis();
  float dt = max(0.001f, (now - lastUpdateMs) / 1000.0f);
  lastUpdateMs = now;

  if (autoRoute && gpsEnabled) {
    routePhase += dt * 0.16f;

    float desiredHeading = (90 + 55 * sin(routePhase)) % 360;
    heading = approachAngle(heading, desiredHeading, 20 * dt);

    float metersPerSec = speedKn * 0.514444f;
    float distanceM = metersPerSec * dt;

    float headingRad = radians(heading);

    float dLat = (distanceM * cos(headingRad)) / 111320.0f;
    float cosLat = max(0.2f, cos(radians(lat)));
    float dLon = (distanceM * sin(headingRad)) / (111320.0f * cosLat);

    lat += dLat;
    lon += dLon;
  }

  // Normal slow battery drain
  if (battery > 0) {
    battery -= dt * 0.012f;
  }

  // Temperature naturally trends toward 62 C
  engineTemp += (62.0f - engineTemp) * min(1.0f, dt * 0.05f);

  // Network naturally trends toward 90%
  network += (90.0f - network) * min(1.0f, dt * 0.08f);

  // Speed naturally trends toward 8 kn
  speedKn += (8.0f - speedKn) * min(1.0f, dt * 0.04f);
}

public void sendTelemetry() {
  if (!sending) return;

  int now = millis();
  if (now - lastSendMs < sendIntervalMs) return;
  lastSendMs = now;

  // GPS LOST scenario: no packet at all.
  // This allows the receiver to detect "GPS 3 sec gelmezse".
  if (!gpsEnabled) return;

  String packet =
    System.currentTimeMillis() + "," +
    nf(lat, 0, 6) + "," +
    nf(lon, 0, 6) + "," +
    nf(normalizeAngle(heading), 0, 2) + "," +
    nf(speedKn, 0, 2) + "," +
    PApplet.parseInt(constrain(battery, 0, 100)) + "," +
    nf(engineTemp, 0, 1) + "," +
    mode + "," +
    PApplet.parseInt(constrain(network, 0, 100));

  udp.send(packet, targetIP, targetPort);

  println(packet);
}

public void drawUI() {
  background(9, 17, 26);

  fill(16, 29, 42);
  noStroke();
  rect(0, 0, width, 72);

  fill(103, 220, 255);
  textSize(24);
  text("MISSION CONTROL - TELEMETRY SIMULATOR", 35, 44);

  fill(sending ? color(80, 230, 120) : color(230, 80, 80));
  textSize(15);
  text(sending ? "● SENDING" : "● STOPPED", 870, 42);

  drawCard(35, 95, 300, 390, "CONNECTION");
  drawCard(355, 95, 310, 390, "TELEMETRY");
  drawCard(685, 95, 330, 390, "STATUS");

  fill(205);
  textSize(15);
  text("Target IP", 55, 145);
  drawValueBox(55, 160, 240, 38, targetIP);

  text("UDP Port", 55, 225);
  drawValueBox(55, 240, 240, 38, str(targetPort));

  text("Send rate", 55, 305);
  drawValueBox(55, 320, 240, 38, "10 Hz / 100 ms");

  text("Packet", 55, 385);
  fill(125, 155, 175);
  textSize(12);
  text("timestamp, lat, lon, heading,", 55, 408);
  text("speed, battery, temp, mode, network", 55, 428);

  fill(210);
  textSize(15);
  text("LAT", 375, 145);
  textValue(nf(lat, 0, 6), 540, 145);

  text("LON", 375, 190);
  textValue(nf(lon, 0, 6), 540, 190);

  text("HEADING", 375, 235);
  textValue(nf(normalizeAngle(heading), 0, 1) + "°", 540, 235);

  text("SPEED", 375, 280);
  textValue(nf(speedKn, 0, 1) + " kn", 540, 280);

  text("BATTERY", 375, 325);
  textValue(PApplet.parseInt(battery) + "%", 540, 325);

  text("ENGINE TEMP", 375, 370);
  textValue(nf(engineTemp, 0, 1) + " °C", 540, 370);

  text("NETWORK", 375, 415);
  textValue(PApplet.parseInt(network) + "%", 540, 415);

  text("MODE", 375, 460);
  textValue(modeName(mode), 540, 460);

  fill(210);
  textSize(15);
  text("GPS", 710, 145);
  statusValue(gpsEnabled ? "ONLINE" : "LOST", 900, 145, gpsEnabled);

  text("AUTO ROUTE", 710, 195);
  statusValue(autoRoute ? "ACTIVE" : "OFF", 900, 195, autoRoute);

  text("ENGINE", 710, 245);
  statusValue(engineTemp < 80 ? "NORMAL" : "OVERHEAT", 900, 245, engineTemp < 80);

  text("POWER", 710, 295);
  statusValue(battery >= 20 ? "NORMAL" : "LOW", 900, 295, battery >= 20);

  text("NETWORK", 710, 345);
  statusValue(network >= 30 ? "NORMAL" : "WEAK", 900, 345, network >= 30);

  text("SPEED", 710, 395);
  statusValue(speedKn <= 15 ? "NORMAL" : "OVERSPEED", 900, 395, speedKn <= 15);

  fill(130, 160, 180);
  textSize(13);
  text("SPACE : Start / Stop sending", 710, 438);
  text("N     : Normalize", 710, 461);

  for (SimButton b : buttons) b.drawButton();

  fill(110, 145, 165);
  textSize(12);
  text("Scenario buttons intentionally create rule violations for the intern's Event Engine.", 40, 655);
}

public void drawCard(float x, float y, float w, float h, String title) {
  fill(14, 28, 40);
  stroke(35, 63, 82);
  strokeWeight(1);
  rect(x, y, w, h, 7);

  fill(100, 215, 250);
  textSize(14);
  text(title, x + 18, y + 28);
}

public void drawValueBox(float x, float y, float w, float h, String value) {
  fill(8, 18, 27);
  stroke(41, 70, 89);
  rect(x, y, w, h, 5);

  fill(210);
  textSize(14);
  text(value, x + 12, y + 24);
}

public void textValue(String s, float x, float y) {
  fill(90, 220, 255);
  textAlign(RIGHT);
  text(s, x + 95, y);
  textAlign(LEFT);
}

public void statusValue(String s, float x, float y, boolean ok) {
  fill(ok ? color(80, 225, 120) : color(255, 100, 75));
  textAlign(RIGHT);
  text(s, x + 75, y);
  textAlign(LEFT);
}

public void mousePressed() {
  for (SimButton b : buttons) {
    if (b.isMouseOver()) {
      triggerScenario(b.actionId);
      break;
    }
  }
}

public void keyPressed() {
  if (key == ' ') sending = !sending;
  if (key == 'n' || key == 'N') normalizeScenario();
}

public void triggerScenario(int id) {
  if (id == 1) {
    engineTemp = 62;
    println("SCENARIO: ENGINE START");
  }
  else if (id == 2) {
    engineTemp = 90;
    println("SCENARIO: TEMP HIGH");
  }
  else if (id == 3) {
    battery = 15;
    println("SCENARIO: LOW BATTERY");
  }
  else if (id == 4) {
    gpsEnabled = false;
    println("SCENARIO: GPS LOSS - packets stopped");
  }
  else if (id == 5) {
    network = 20;
    println("SCENARIO: NETWORK DROP");
  }
  else if (id == 6) {
    heading = normalizeAngle(heading + 95);
    println("SCENARIO: RAPID TURN");
  }
  else if (id == 7) {
    mode++;
    if (mode > 2) mode = 0;
    println("SCENARIO: MODE CHANGE -> " + modeName(mode));
  }
  else if (id == 8) {
    speedKn = 19;
    println("SCENARIO: OVERSPEED");
  }
  else if (id == 9) {
    normalizeScenario();
  }
  else if (id == 10) {
    autoRoute = !autoRoute;
    println("AUTO ROUTE: " + autoRoute);
  }
}

public void normalizeScenario() {
  gpsEnabled = true;
  autoRoute = true;
  speedKn = 8;
  battery = 85;
  engineTemp = 62;
  network = 90;
  mode = 0;
  println("SCENARIO: NORMALIZED");
}

public float normalizeAngle(float a) {
  while (a < 0) a += 360;
  while (a >= 360) a -= 360;
  return a;
}

public float shortestAngleDiff(float from, float to) {
  float diff = normalizeAngle(to) - normalizeAngle(from);
  if (diff > 180) diff -= 360;
  if (diff < -180) diff += 360;
  return diff;
}

public float approachAngle(float current, float target, float maxStep) {
  float diff = shortestAngleDiff(current, target);
  if (abs(diff) <= maxStep) return normalizeAngle(target);
  return normalizeAngle(current + (diff > 0 ? maxStep : -maxStep));
}

public String modeName(int m) {
  if (m == 0) return "MANUAL";
  if (m == 1) return "AUTO";
  if (m == 2) return "RTL";
  return "UNKNOWN";
}

class SimButton {
  float x, y, w, h;
  String label;
  int actionId;

  SimButton(float x, float y, float w, float h, String label, int actionId) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    this.label = label;
    this.actionId = actionId;
  }

  public void drawButton() {
    boolean hover = isMouseOver();

    stroke(38, 68, 88);
    fill(hover ? color(29, 55, 72) : color(18, 36, 49));
    rect(x, y, w, h, 6);

    fill(hover ? color(110, 225, 255) : color(205));
    textSize(12);
    textAlign(CENTER, CENTER);
    text(label, x + w/2, y + h/2);
    textAlign(LEFT, BASELINE);
  }

  public boolean isMouseOver() {
    return mouseX >= x && mouseX <= x+w &&
           mouseY >= y && mouseY <= y+h;
  }
}
  public void settings() {  size(1050, 690); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "MissionControl_TelemetrySimulator" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
