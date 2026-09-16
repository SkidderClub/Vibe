#version 120
uniform sampler2D Source;
uniform sampler2D Mask;
uniform vec2 Step;
uniform int Composite;
varying vec2 UV;
void main(){
    vec4 sum=vec4(0.0);float weight=0.0;
    for(int i=-12;i<=12;i++){
        float w=exp(-float(i*i)/32.0);
        sum+=texture2D(Source,UV+Step*float(i))*w;weight+=w;
    }
    sum/=weight;
    if(Composite==1){
        // Halo only outside the model: opacity remains linear on its surface.
        float outside=1.0-step(0.01,texture2D(Mask,UV).a);
        gl_FragColor=vec4(sum.rgb*2.2*outside,0.0);
    }else gl_FragColor=sum;
}
