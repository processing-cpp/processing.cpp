/**
 * Bounce.
 *
 * When the shape hits the edge of the window, it reverses its direction.
 */

let rad = 60;
let xpos, ypos;

let xspeed = 2.8;
let yspeed = 2.2;

let xdirection = 1;
let ydirection = 1;

function setup()
{
  createCanvas(640, 360);
  noStroke();
  frameRate(30);
  ellipseMode(RADIUS);
  xpos = width/2;
  ypos = height/2;
}

function draw()
{
  background(102);

  xpos = xpos + ( xspeed * xdirection );
  ypos = ypos + ( yspeed * ydirection );

  if (xpos > width-rad || xpos < rad) {
    xdirection *= -1;
  }
  if (ypos > height-rad || ypos < rad) {
    ydirection *= -1;
  }

  ellipse(xpos, ypos, rad, rad);
}
