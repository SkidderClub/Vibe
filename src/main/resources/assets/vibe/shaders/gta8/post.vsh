varying vec2 vUV;
varying vec2 vNdc;

void main() {
    vNdc = gl_Vertex.xy;
    vUV = gl_Vertex.xy * .5 + .5;
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
}
