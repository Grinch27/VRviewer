// Verification: full-screen ray projection; both output eyes share the same decoded texture.
attribute vec2 aPosition;
varying vec2 vPosition;
void main() {
    vPosition = aPosition;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
