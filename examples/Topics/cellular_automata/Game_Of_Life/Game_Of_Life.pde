#include <vector>

int cellSize = 5;
float probabilityOfAliveAtStart = 15;

int interval = 100;
int lastRecordedTime = 0;

int alive;
int dead;

std::vector<std::vector<int>> cells;
std::vector<std::vector<int>> cellsBuffer;

bool pause = false;

void setup() {
  size(640, 360);

  alive = color(0, 200, 0);
  dead = color(0);

  int cols = width / cellSize;
  int rows = height / cellSize;

  cells.resize(cols, std::vector<int>(rows));
  cellsBuffer.resize(cols, std::vector<int>(rows));

  stroke(48);
  noSmooth();
  background(0);

  for (int x = 0; x < cols; x++) {
    for (int y = 0; y < rows; y++) {
      float state = random(100);
      if (state > probabilityOfAliveAtStart) state = 0;
      else state = 1;

      cells[x][y] = (int)state;
    }
  }
}

void draw() {
  int cols = width / cellSize;
  int rows = height / cellSize;

  for (int x = 0; x < cols; x++) {
    for (int y = 0; y < rows; y++) {
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

  if (pause && _mousePressed) {
    int xCellOver = (int)map(mouseX, 0, width, 0, width / cellSize);
    int yCellOver = (int)map(mouseY, 0, height, 0, height / cellSize);

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
  else if (pause && !_mousePressed) {
    for (int x = 0; x < width / cellSize; x++) {
      for (int y = 0; y < height / cellSize; y++) {
        cellsBuffer[x][y] = cells[x][y];
      }
    }
  }
}

void iteration() {
  int cols = width / cellSize;
  int rows = height / cellSize;

  for (int x = 0; x < cols; x++) {
    for (int y = 0; y < rows; y++) {
      cellsBuffer[x][y] = cells[x][y];
    }
  }

  for (int x = 0; x < cols; x++) {
    for (int y = 0; y < rows; y++) {

      int neighbours = 0;

      for (int xx = x - 1; xx <= x + 1; xx++) {
        for (int yy = y - 1; yy <= y + 1; yy++) {

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

void keyPressed() {
  int cols = width / cellSize;
  int rows = height / cellSize;

  if (key == 'r' || key == 'R') {
    for (int x = 0; x < cols; x++) {
      for (int y = 0; y < rows; y++) {
        float state = random(100);
        if (state > probabilityOfAliveAtStart) state = 0;
        else state = 1;

        cells[x][y] = (int)state;
      }
    }
  }

  if (key == ' ') {
    pause = !pause;
  }

  if (key == 'c' || key == 'C') {
    for (int x = 0; x < cols; x++) {
      for (int y = 0; y < rows; y++) {
        cells[x][y] = 0;
      }
    }
  }
}
