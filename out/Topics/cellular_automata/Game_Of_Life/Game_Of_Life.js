let cellSize = 5;
let probabilityOfAliveAtStart = 15;

let interval = 100;
let lastRecordedTime = 0;

let alive;
let dead;

let cells = [];
let cellsBuffer = [];

let pause = false;

function setup() {
  createCanvas(640, 360);

  alive = color(0, 200, 0);
  dead = color(0);

  let cols = width / cellSize;
  let rows = height / cellSize;

  cells = new Array(cols);
  cellsBuffer = new Array(cols);
  for (let x = 0; x < cols; x++) {
    cells[x] = new Array(rows).fill(0);
    cellsBuffer[x] = new Array(rows).fill(0);
  }

  stroke(48);
  noSmooth();
  background(0);

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      let state = random(100);
      if (state > probabilityOfAliveAtStart) state = 0;
      else state = 1;

      cells[x][y] = int(state);
    }
  }
}

function draw() {
  let cols = width / cellSize;
  let rows = height / cellSize;

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      if (cells[x][y] == 1) fill(alive);
      else fill(dead);

      rect(x * cellSize, y * cellSize, cellSize, cellSize);
    }
  }

  if (millis() - lastRecordedTime > interval) {
    if (!pause) {
      iteration();
      lastRecordedTime = millis();
    }
  }

  if (pause && mouseIsPressed) {
    let xCellOver = int(map(mouseX, 0, width, 0, width / cellSize));
    let yCellOver = int(map(mouseY, 0, height, 0, height / cellSize));

    xCellOver = constrain(xCellOver, 0, width / cellSize - 1);
    yCellOver = constrain(yCellOver, 0, height / cellSize - 1);

    if (cellsBuffer[xCellOver][yCellOver] == 1) {
      cells[xCellOver][yCellOver] = 0;
      fill(dead);
    } else {
      cells[xCellOver][yCellOver] = 1;
      fill(alive);
    }
  }
  else if (pause && !mouseIsPressed) {
    for (let x = 0; x < width / cellSize; x++) {
      for (let y = 0; y < height / cellSize; y++) {
        cellsBuffer[x][y] = cells[x][y];
      }
    }
  }
}

function iteration() {
  let cols = width / cellSize;
  let rows = height / cellSize;

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      cellsBuffer[x][y] = cells[x][y];
    }
  }

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {

      let neighbours = 0;

      for (let xx = x - 1; xx <= x + 1; xx++) {
        for (let yy = y - 1; yy <= y + 1; yy++) {

          if (xx >= 0 && xx < cols && yy >= 0 && yy < rows) {
            if (!(xx == x && yy == y)) {
              if (cellsBuffer[xx][yy] == 1) {
                neighbours++;
              }
            }
          }
        }
      }

      if (cellsBuffer[x][y] == 1) {
        if (neighbours < 2 || neighbours > 3) {
          cells[x][y] = 0;
        }
      } else {
        if (neighbours == 3) {
          cells[x][y] = 1;
        }
      }
    }
  }
}

function keyPressed() {
  let cols = width / cellSize;
  let rows = height / cellSize;

  if (key == 'r' || key == 'R') {
    for (let x = 0; x < cols; x++) {
      for (let y = 0; y < rows; y++) {
        let state = random(100);
        if (state > probabilityOfAliveAtStart) state = 0;
        else state = 1;

        cells[x][y] = int(state);
      }
    }
  }

  if (key == ' ') {
    pause = !pause;
  }

  if (key == 'c' || key == 'C') {
    for (let x = 0; x < cols; x++) {
      for (let y = 0; y < rows; y++) {
        cells[x][y] = 0;
      }
    }
  }
}
