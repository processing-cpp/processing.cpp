/**
 * LoadFile 2
 *
 * Loads a data file about cars. Each element is separated with a tab.
 * Press a mouse button to advance to the next group of entries.
 * Translated to C++ Mode.
 */

struct Record {
    std::string name;
    float mpg;
    int cylinders;
    float displacement;
    float horsepower;
    float weight;
    float acceleration;
    int year;
    float origin;

    Record(std::vector<std::string>& pieces) {
        name         = pieces[0];
        mpg          = toFloat(pieces[1]);
        cylinders    = toInt(pieces[2]);
        displacement = toFloat(pieces[3]);
        horsepower   = toFloat(pieces[4]);
        weight       = toFloat(pieces[5]);
        acceleration = toFloat(pieces[6]);
        year         = toInt(pieces[7]);
        origin       = toFloat(pieces[8]);
    }
};

std::vector<Record> records;
int num = 9;
int startingEntry = 0;

void setup() {
    size(640, 360);
    fill(255);
    noLoop();
    textSize(20);

    std::vector<std::string> lines = loadStrings("data/cars2.tsv");
    for (auto& line : lines) {
        std::vector<std::string> pieces = split(line, '\t');
        if ((int)pieces.size() == 9) {
            records.push_back(Record(pieces));
        }
    }
}

void draw() {
    background(0);
    for (int i = 0; i < num; i++) {
        int thisEntry = startingEntry + i;
        if (thisEntry < (int)records.size()) {
            text(std::to_string(thisEntry) + " > " + records[thisEntry].name, 20, 20 + i * 20);
        }
    }
}

void mousePressed() {
    startingEntry += num;
    if (startingEntry > (int)records.size()) {
        startingEntry = 0;
    }
    redraw();
}
