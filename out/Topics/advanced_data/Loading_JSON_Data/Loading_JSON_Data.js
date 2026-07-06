/**
 * Loading JSON Data
 * Translated to C++ Mode from Processing Java by Daniel Shiffman.
 */

class Bubble {
  constructor(x, y, diameter, name) {
    this.x = x;
    this.y = y;
    this.diameter = diameter;
    this.name = name;
    this.over = false;
  }

  rollover(px, py) {
    this.over = dist(px, py, this.x, this.y) < this.diameter / 2;
  }

  display() {
    stroke(0);
    strokeWeight(2);
    noFill();
    ellipse(this.x, this.y, this.diameter, this.diameter);
    if (this.over) {
      fill(0);
      textAlign(CENTER);
      text(this.name, this.x, this.y + this.diameter / 2 + 20);
    }
  }
}

let bubbles = [];
let json;

function loadData() {
  json = loadJSONObject("data/data.json");

  if (json == null || !json.hasKey("bubbles")) {
    println("Failed to load data.json");
    return;
  }

  let bubbleData = json.getJSONArray("bubbles");
  bubbles = [];

  for (let i = 0; i < bubbleData.size(); i++) {
    let b = bubbleData.getJSONObject(i);
    if (!b.hasKey("position")) continue;
    let x = b.getJSONObject("position").getFloat("x");
    let y = b.getJSONObject("position").getFloat("y");
    let diameter = b.getFloat("diameter");
    let label = b.getString("label");
    bubbles.push(new Bubble(x, y, diameter, label));
  }

  println("Loaded " + bubbles.length + " bubbles");
}

function setup() {
  createCanvas(640, 360);
  loadData();
}

function draw() {
  background(255);
  for (let b of bubbles) {
    b.display();
    b.rollover(mouseX, mouseY);
  }
  textAlign(LEFT);
  fill(0);
  text("Click to add bubbles.", 10, height - 10);
}

function mousePressed() {
  let bubbleData = json.getJSONArray("bubbles");
  let newBubble = new JSONObject();
  let pos = new JSONObject();
  pos.setFloat("x", mouseX);
  pos.setFloat("y", mouseY);
  newBubble.setJSONObject("position", pos);
  newBubble.setFloat("diameter", random(40, 80));
  newBubble.setString("label", "New label");
  bubbleData.setJSONObject(bubbleData.size(), newBubble);

  if (bubbleData.size() > 10) {
    bubbleData.remove(0);
  }

  saveJSONObject("data/data.json", json);
  loadData();
}
