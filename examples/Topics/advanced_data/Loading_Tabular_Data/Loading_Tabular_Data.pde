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

struct Bubble {
    float x, y, diameter;
    std::string name;
    bool over = false;

    Bubble(float x, float y, float diameter, std::string name)
        : x(x), y(y), diameter(diameter), name(name) {}

    void rollover(float px, float py) {
        over = dist(px, py, x, y) < diameter / 2;
    }

    void display() {
        stroke(0);
        strokeWeight(2);
        noFill();
        ellipse(x, y, diameter, diameter);
        if (over) {
            fill(0);
            textAlign(CENTER);
            text(name, x, y + diameter / 2 + 20);
        }
    }
};

std::vector<Bubble> bubbles;
Table* table = nullptr;

void loadData() {
    table = loadTable("data/data.csv", "header");

    if (!table) {
        println("Failed to load data.csv");
        return;
    }

    bubbles.clear();
    for (int i = 0; i < table->getRowCount(); i++) {
        float x           = table->getFloat(i, "x");
        float y           = table->getFloat(i, "y");
        float diameter    = table->getFloat(i, "diameter");
        std::string name  = table->getString(i, "name");
        bubbles.push_back(Bubble(x, y, diameter, name));
    }

    println("Loaded " + std::to_string(bubbles.size()) + " bubbles");
}

void setup() {
    size(640, 360);
    loadData();
}

void draw() {
    background(255);
    for (auto& b : bubbles) {
        b.display();
        b.rollover(mouseX, mouseY);
    }
    textAlign(LEFT);
    fill(0);
    text("Click to add bubbles.", 10, height - 10);
}

void mousePressed() {
    if (!table) return;

    // Add a new row
    std::vector<std::string>& row = table->addRow();
    TableRow tr(row, table->columns);
    tr.setFloat(table->getColumnIndex("x"),        mouseX);
    tr.setFloat(table->getColumnIndex("y"),        mouseY);
    tr.setFloat(table->getColumnIndex("diameter"), random(40, 80));
    tr.setString(table->getColumnIndex("name"),    "Blah");

    // Cap at 10 rows
    if (table->getRowCount() > 10) {
        table->removeRow(0);
    }

    saveTable("data/data.csv", *table);
    loadData();
}
