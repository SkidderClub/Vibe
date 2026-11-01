#version 120
varying vec2 uv;
uniform sampler2D Scene;
uniform vec2 Step;
void main(){
    vec3 color=texture2D(Scene,uv).rgb*.2270270270;
    color+=texture2D(Scene,uv+Step*1.3846153846).rgb*.3162162162;
    color+=texture2D(Scene,uv-Step*1.3846153846).rgb*.3162162162;
    color+=texture2D(Scene,uv+Step*3.2307692308).rgb*.0702702703;
    color+=texture2D(Scene,uv-Step*3.2307692308).rgb*.0702702703;
    gl_FragColor=vec4(color,1.);
}
