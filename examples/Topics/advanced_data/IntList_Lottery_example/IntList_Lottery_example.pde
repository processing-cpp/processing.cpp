#include <vector>

std::vector<int> lottery;
std::vector<int> results;
std::vector<int> ticket;

void showList(std::vector<int>& list, float x, float y) {
  for (int i = 0; i < (int)list.size(); i++) {
    int val = list[i];

    stroke(255);
    noFill();
    ellipse(x + i * 32, y, 24, 24);

    textAlign(CENTER);
    fill(255);
    text(val, x + i * 32, y + 6);
  }
}

void setup() {
  size(640, 360);
  frameRate(30);

  for (int i = 0; i < 20; i++) {
    lottery.push_back(i);
  }

  for (int i = 0; i < 5; i++) {
    int index = (int)random(lottery.size());
    ticket.push_back(lottery[index]);
  }
}

void draw() {
  background(51);

  // shuffle lottery manually (since std::vector has no shuffle)
  for (int i = 0; i < (int)lottery.size(); i++) {
    int j = (int)random(lottery.size());
    std::swap(lottery[i], lottery[j]);
  }

  showList(lottery, 16, 48);
  showList(results, 16, 100);
  showList(ticket, 16, 140);

  for (int i = 0; i < (int)results.size(); i++) {
    if (results[i] == ticket[i]) {
      fill(0, 255, 0, 100);
    } else {
      fill(255, 0, 0, 100);
    }
    ellipse(16 + i * 32, 140, 24, 24);
  }

  if (frameCount % 30 == 0) {
    if (results.size() < 5) {
      int val = lottery[0];
      lottery.erase(lottery.begin());
      results.push_back(val);
    } else {
      for (int i = 0; i < (int)results.size(); i++) {
        lottery.push_back(results[i]);
      }
      results.clear();
    }
  }
}
