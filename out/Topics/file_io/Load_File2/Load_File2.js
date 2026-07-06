/**
 * LoadFile 2
 *
 * Loads a data file about cars. Each element is separated with a tab.
 * Press a mouse button to advance to the next group of entries.
 * Translated to C++ Mode.
 */

class Record {
  constructor(pieces) {
    this.name = pieces[0];
    this.mpg = float(pieces[1]);
    this.cylinders = int(pieces[2]);
    this.displacement = float(pieces[3]);
    this.horsepower = float(pieces[4]);
    this.weight = float(pieces[5]);
    this.acceleration = float(pieces[6]);
    this.year = int(pieces[7]);
    this.origin = float(pieces[8]);
  }
}

let records = [];
let num = 9;
let startingEntry = 0;

function setup() {
  createCanvas(640, 360);
  fill(255);
  noLoop();
  textSize(20);

  let lines = loadStrings("data/cars2.tsv");
  for (let line of lines) {
    let pieces = split(line, '\t');
    if (pieces.length == 9) {
      records.push(new Record(pieces));
    }
  }
}

function draw() {
  background(0);
  for (let i = 0; i < num; i++) {
    let thisEntry = startingEntry + i;
    if (thisEntry < records.length) {
      text(thisEntry + " > " + records[thisEntry].name, 20, 20 + i * 20);
    }
  }
}

function mousePressed() {
  startingEntry += num;
  if (startingEntry > records.length) {
    startingEntry = 0;
  }
  redraw();
}
