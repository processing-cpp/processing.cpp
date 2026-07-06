function setup() {
    createCanvas(640, 360);
    background(102);
}

function draw() {
    let fromX = mouseX - mouseDX;
    let fromY = mouseY - mouseDY;
    variableEllipse(mouseX, mouseY, fromX, fromY);
}

function variableEllipse(x, y, px, py) {
    let speed = abs(x - px) + abs(y - py);
    stroke(speed);
    ellipse(x, y, speed, speed);
}
