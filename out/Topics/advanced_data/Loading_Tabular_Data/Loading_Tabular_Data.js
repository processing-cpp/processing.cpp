/**
 * Loading Tabular Data
 * Translated to C++ Mode from Processing Java by Daniel Shiffman.
 *
 * Loads bubble data from a CSV file and allows adding
 * new bubbles by clicking.
 *
 * CSV format (data/data.csv):
 *   x,y,diameter,name
 *   160,103,43.19838,Happy
 *   372,137,52.42526,Sad
 *   273,235,61.14072,Joyous
 *   121,179,44.758068,Melancholy
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
let table = null;

function loadData() {
  table = loadTable("data/data.csv", "header");

  if (table == null) {
    println("Failed to load data.csv");
    return;
  }

  bubbles = [];
  for (let i = 0; i < table.getRowCount(); i++) {
    let x = table.getNum(i, "x");
    let y = table.getNum(i, "y");
    let diameter = table.getNum(i, "diameter");
    let name = table.getString(i, "name");
    bubbles.push(new Bubble(x, y, diameter, name));
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
  if (!table) return;

  let row = table.addRow();
  row.setNum("x", mouseX);
  row.setNum("y", mouseY);
  row.setNum("diameter", random(40, 80));
  row.setString("name", "Blah");

  if (table.getRowCount() > 10) {
    table.removeRow(0);
  }

  saveTable(table, "data/data.csv");
  loadData();
}
