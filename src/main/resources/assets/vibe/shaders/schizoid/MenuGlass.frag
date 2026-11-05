#version 120
uniform sampler2D Scene;
uniform vec2 Resolution,Origin,Size,LavaOrigin,LavaSize;
uniform float Radius,Scale,Emphasis,Window,Time,Pass,Depth;
uniform vec3 Tint,Accent,Wax,Light;
uniform vec4 Drop,Form;
uniform float Hover,Fade;
uniform vec2 Pointer;
vec4 unite(vec4 a,vec4 b,float amount){float h=clamp(.5+.5*(b.x-a.x)/amount,0.,1.);vec4 result=mix(b,a,h);result.x-=amount*h*(1.-h);return result;}
vec3 radiance(float distance){
    float outside=max(distance,0.);
    // One physical falloff for the whole liquid field keeps light continuous
    // where different-sized blobs join either reservoir.
    float radius=.052;
    float halo=exp(-outside/max(radius*.28,2./LavaSize.y));
    float bloom=exp(-outside/max(radius*1.05,4./LavaSize.y));
    float falloff=1.-smoothstep(radius*2.15,radius*2.85,outside);
    return mix(Wax,Light,halo*.45)*(halo*.42+bloom*.22)*falloff;
}
void main(){
    vec2 local=gl_FragCoord.xy-Origin-Size*.5;
    vec2 q=abs(local)-Size*.5+Radius;
    float paneDistance=length(max(q,0.))+min(max(q.x,q.y),0.)-Radius;
    float aa=max(.7,Scale*.55);
    float pane=1.-smoothstep(-aa,aa,paneDistance);
    vec2 bevel=local-clamp(local,-Size*.5+Radius,Size*.5-Radius);
    vec2 outward=bevel/max(length(bevel),.001);
    if(Pass<.5){
        // Refraction affects only the frosted backdrop, never the wax or text.
        float bend=exp(-abs(paneDistance)/max(1.,Scale*9.));
        vec2 refracted=(gl_FragCoord.xy-outward*bend*Scale*4.)/Resolution;
        vec3 scene=texture2D(Scene,refracted).rgb;
        vec3 color=mix(scene*(Window>.5?.96:.79),Tint,Window>.5?.18:.44)+Accent*Emphasis*.045;
        float outside=exp(-max(paneDistance,0.)/max(1.,Scale*3.1))*(.045+Emphasis*.025);
        gl_FragColor=vec4(mix(Accent*.55,color,pane),(pane+(1.-pane)*outside)*(1.-Fade));return;
    }
    if(Pass>2.5){
        // The glass surface sits above the luminous liquid, including its rims.
        float rim=exp(-abs(paneDistance+Scale*.35)/max(.7,Scale*.65));
        float inner=exp(-abs(paneDistance+Scale*2.6)/max(.8,Scale*.9));
        float light=clamp(dot(outward,normalize(vec2(-.55,.85))),0.,1.);
        float reflection=pow(max(0.,1.-abs(local.x/Size.x+local.y/Size.y*.7+.22)/.36),3.);
        float alpha=(rim*(.16+light*.37+Emphasis*.10)+inner*.055+reflection*(Window>.5?.035:.014))*pane;
        vec3 reflectionColor=mix(Accent,vec3(1.),.74);
        if(Hover>.001){
            vec2 point=local/Size+.5;
            vec2 delta=(point-Pointer)*vec2(Size.x/Size.y,1.);
            float heat=exp(-dot(delta,delta)*1.8);
            vec2 drift=delta+vec2(.42*sin(Time*.8),.22*cos(Time*.6));
            float waxLight=exp(-dot(drift,drift)*2.4);
            float flow=.65+.35*sin(point.x*5.+Time*1.3);
            float glow=(heat*.13+waxLight*.09+(rim*.46+inner*.13)*flow)*pane;
            glow+=exp(-max(paneDistance,0.)/max(1.,Scale*2.8))*(1.-pane)*(.09+heat*.13);
            alpha+=Hover*glow;
            reflectionColor=mix(reflectionColor,mix(Accent,Light,.45),Hover*.82);
        }
        gl_FragColor=vec4(reflectionColor,clamp(alpha,0.,.85)*(1.-Fade));return;
    }
    if(pane<=0.)discard;
    vec2 p=(gl_FragCoord.xy-LavaOrigin)/LavaSize.y;
    float wave=.008*sin(p.x*7.+Time*.7)+.006*sin(p.x*12.-Time*.46);
    float bottomDistance=p.y-.105-wave;
    float upperWave=.009*sin(p.x*6.2-Time*.47)+.005*sin(p.x*13.+Time*.61);
    float topDistance=.925+upperWave-p.y;
    float distance=min(bottomDistance,topDistance);
    vec4 wax=bottomDistance<topDistance?vec4(bottomDistance,0.,clamp(1.+bottomDistance/.10,0.,.97),.055):vec4(topDistance,0.,-clamp(1.+topDistance/.085,0.,.97),.052);
    float contribution=1.;
    if(Pass>1.5){
        vec2 stretch=vec2(Drop.w,Form.x);
        vec2 offset=p-vec2(Drop.x*LavaSize.x/LavaSize.y,Drop.y);
        vec2 rotated=vec2(Form.y*offset.x+Form.z*offset.y,-Form.z*offset.x+Form.y*offset.y);
        vec2 delta=rotated/(stretch*Drop.z);
        // A slowly changing pear-shaped cross section, rather than a circle
        // stretched uniformly. The derivative keeps its highlights attached.
        float bendY=delta.y/sqrt(1.+.25*delta.y*delta.y);
        float bendDerivative=pow(1.+.25*delta.y*delta.y,-1.5);
        vec2 shape=vec2(delta.x*(1.+.12*bendY)+Form.w*(bendY*bendY-.25),delta.y);
        vec2 gradient=vec2(shape.x*(1.+.12*bendY),shape.y+(.12*delta.x+2.*Form.w*bendY)*bendDerivative*shape.x)/stretch;
        gradient=vec2(Form.y*gradient.x-Form.z*gradient.y,Form.z*gradient.x+Form.y*gradient.y);
        vec2 normalXY=gradient/max(length(gradient),.001)*min(length(shape),.995);
        vec4 drop=vec4((length(shape)-1.)*Drop.z*min(stretch.x,stretch.y),normalXY,Drop.z);
        wax=unite(wax,drop,max(.012,Drop.z*.58));
        // The reservoir is already present. Only paint the part changed by
        // this drop; repainting the pool's halo exposes the draw rectangle.
        contribution=smoothstep(0.,max(.001,Drop.z*.12),distance-wax.x);
        if(contribution<=0.)discard;
    }
    float edge=.8/LavaSize.y;
    float solid=1.-smoothstep(-edge,edge,wax.x);
    vec3 color=Light;
    if(solid>0.){
        vec2 xy=wax.yz;xy*=min(1.,.995/max(length(xy),.001));
        vec3 normal=vec3(xy,sqrt(max(.001,1.-dot(xy,xy))));
        // Translucent wax is lit from within: saturated edges, a broad hot core,
        // and gentle volume shading instead of a hard metallic specular spot.
        float core=normal.z*normal.z;
        vec3 hot=mix(Light,Wax,.15);
        color=mix(Wax,hot,.45+core*.44);
        float broad=pow(max(0.,dot(normal,normalize(vec3(-.4,.6,1.6)))),4.);
        color+=Light*broad*.055;
        color*=mix(1.,.62,smoothstep(.85,.98,p.y));
        if(Pass>1.5)color*=.94+Depth*.06;
    }
    vec3 glow=radiance(wax.x);
    // The pool's illumination is already present. Add only the extra radiance
    // created by this union; repainting the full glow causes cones and rings.
    if(Pass>1.5)glow=max(vec3(0.),glow-radiance(distance));
    float alpha=solid*(Window>.5?1.:.32)*pane*contribution;
    // Premultiplied surface plus additive emission: the halo illuminates glass
    // without painting an opaque colored disk over the transmitted background.
    vec3 emission=glow*(Window>.5?1.:.38)*(1.-solid)*pane;
    gl_FragColor=vec4(color*alpha+emission,alpha)*(1.-Fade);
}
