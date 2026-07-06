/**
 * LoadFile 1
 *
 * Loads a text file that contains two numbers separated by a tab.
 * A new pair of numbers is loaded each frame and used to draw a point.
 * Translated to C++ Mode.
 */

let lines = [];
let index = 0;

function setup() {
    createCanvas(640, 360);
    background(0);
    stroke(255);
    frameRate(12);
    lines = loadStrings("data/positions.txt");
}

function draw() {
    if (index < lines.length) {
        let pieces = split(lines[index], '\t');
        if (pieces.length == 2) {
            let x = map(float(pieces[0]), 0, 100, 0, width);
            let y = map(float(pieces[1]), 0, 100, 0, height);
            point(x, y);
        }
        index++;
    }
}
