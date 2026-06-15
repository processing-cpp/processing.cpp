/**
 * Loading JSON Data
 * Translated to C++ Mode from Processing Java by Daniel Shiffman.
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
JSONValue json;

void loadData() {
    json = loadJSONObject("data/data.json");

    if (!json.isObject() || !json.hasKey("bubbles")) {
        println("Failed to load data.json");
        return;
    }

    JSONArray& bubbleData = json["bubbles"].getArray();
    bubbles.clear();

    for (auto& b : bubbleData) {
        if (!b.hasKey("position")) continue;
        float x           = b["position"]["x"].getFloat();
        float y           = b["position"]["y"].getFloat();
        float diameter    = b["diameter"].getFloat();
        std::string label = b["label"].getString();
        bubbles.push_back(Bubble(x, y, diameter, label));
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
    // Build position object
    JSONObject pos;
    pos["x"] = JSONValue((double)mouseX);
    pos["y"] = JSONValue((double)mouseY);

    // Build bubble object
    JSONObject newBubble;
    newBubble["position"] = JSONValue(pos);
    newBubble["diameter"] = JSONValue((double)random(40, 80));
    newBubble["label"]    = JSONValue(std::string("New label"));

    // Append to array
    JSONArray& bubbleData = json["bubbles"].getArray();
    bubbleData.push_back(JSONValue(newBubble));

    if ((int)bubbleData.size() > 10) {
        bubbleData.erase(bubbleData.begin());
    }

    saveJSONObject("data/data.json", json);
    loadData();
}
