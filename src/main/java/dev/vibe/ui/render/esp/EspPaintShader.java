package dev.vibe.ui.render.esp;

import dev.vibe.module.impl.visual.Esp2DSettings;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Colors the actual geometry/glyph mask in screen space, without painting the background. */
final class EspPaintShader {
    private static int program;
    private static boolean failed;
    private static final IntBuffer VIEWPORT=BufferUtils.createIntBuffer(16);
    private static final String VERTEX="#version 120\nvarying vec2 localPoint;void main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;gl_FrontColor=gl_Color;localPoint=gl_Vertex.xy;}";
    private static final String FRAGMENT="#version 120\n"
            +"uniform sampler2D image; uniform int textured; uniform int count; uniform int rainbow;"
            +"uniform vec4 colors[8]; uniform float stops[8]; uniform vec2 direction; uniform float phase;"
            +"uniform vec4 viewport; uniform vec2 screen; uniform vec4 region; uniform float opacity; uniform float saturation;"
            +"varying vec2 localPoint; uniform int shape; uniform vec4 shapeRect; uniform vec4 shapeData; uniform vec2 cornerExtent;"
            +"float coverage(float d,float px){return clamp(.5-d/max(.00001,px),0.,1.);}"
            +"float rectangleMask(vec2 p,vec2 size,vec2 px){if(min(size.x,size.y)<=0.)return 0.;vec2 m=clamp(.5+p/px,0.,1.)-clamp(.5+(p-size)/px,0.,1.);return m.x*m.y;}"
            +"float shapeMask(){vec2 p=localPoint-shapeRect.xy;vec2 halfSize=shapeRect.zw*.5;"
            +"vec2 pp=max(vec2(length(vec2(dFdx(p.x),dFdy(p.x))),length(vec2(dFdx(p.y),dFdy(p.y)))),vec2(.00001));"
            +"vec2 q=abs(p-halfSize)-(halfSize-vec2(shapeData.x));float d=length(max(q,0.))+min(max(q.x,q.y),0.)-shapeData.x;"
            +"float px=max(length(vec2(dFdx(d),dFdy(d))),.00001);"
            +"float m=shape==1?coverage(d,px):max(0.,coverage(d-shapeData.y*.5,px)-coverage(d+shapeData.y*.5,px));"
            +"if(shapeData.x<=0.){float halfWidth=shapeData.y*.5;m=shape==1?rectangleMask(p,shapeRect.zw,pp):max(0.,rectangleMask(p+halfWidth,shapeRect.zw+shapeData.y,pp)-rectangleMask(p-halfWidth,shapeRect.zw-shapeData.y,pp));}"
            +"if(shape==2&&shapeData.z>0.){vec2 edge=min(p,shapeRect.zw-p);"
            +"float horizontal=coverage(edge.x-cornerExtent.x,pp.x)*coverage(edge.y-shapeData.x-shapeData.y*.5,pp.y);"
            +"float vertical=coverage(edge.y-cornerExtent.y,pp.y)*coverage(edge.x-shapeData.x-shapeData.y*.5,pp.x);m*=mix(1.,max(horizontal,vertical),shapeData.z);}return m;}"
            +"void main(){float mask=shape>0?shapeMask():textured==1?texture2D(image,gl_TexCoord[0].xy).a:1.;if(mask<=0.)discard;vec2 p=(gl_FragCoord.xy-viewport.xy)/viewport.zw*screen; p.y=screen.y-p.y;"
            +"vec2 q=p-region.xy; float t=.5+dot(q-region.zw*.5,direction)/max(.001,dot(abs(direction),region.zw));"
            +"vec4 c=colors[0]; if(rainbow==1){vec3 hue=clamp(abs(mod(fract(t+phase)*6.+vec3(0.,4.,2.),6.)-3.)-1.,0.,1.);c=vec4(mix(vec3(1.),hue,saturation),1.);}"
            +"else {t=1.-abs(mod(t+phase,2.)-1.); for(int i=1;i<8;i++){if(i<count){float f=clamp((t-stops[i-1])/max(.00001,stops[i]-stops[i-1]),0.,1.); c=mix(c,colors[i],f);}}}"
            +"gl_FragColor=vec4(c.rgb,c.a*mask*opacity);}";

    static boolean bind(Esp2DSettings settings, Esp2DSettings.Paint paint, EspLayout.Rect rect,
                        int screenWidth,int screenHeight,boolean texture,float opacity,double seconds,int team,boolean hurt,int forcedColor) {
        return bind(settings,paint,rect,screenWidth,screenHeight,texture,opacity,seconds,team,hurt,forcedColor,false,0);
    }
    private static boolean bind(Esp2DSettings settings,Esp2DSettings.Paint paint,EspLayout.Rect rect,int screenWidth,int screenHeight,boolean texture,float opacity,double seconds,int team,boolean hurt,int forcedColor,boolean override,int overrideColor){
        if (failed) return false;
        if(program==0) {
            int vertex=0,fragment=0,linked=0;
            try {
                vertex=compile(GL20.GL_VERTEX_SHADER,VERTEX); fragment=compile(GL20.GL_FRAGMENT_SHADER,FRAGMENT);
                linked=GL20.glCreateProgram();GL20.glAttachShader(linked,vertex);GL20.glAttachShader(linked,fragment);GL20.glLinkProgram(linked);
                if(GL20.glGetProgrami(linked,GL20.GL_LINK_STATUS)==0) throw new IllegalStateException(GL20.glGetProgramInfoLog(linked,4096));
                program=linked;
                resolveLocations();
            } catch(RuntimeException error) {
                failed=true; if(linked!=0)GL20.glDeleteProgram(linked);
                System.err.println("[Vibe] ESP gradient shader unavailable: "+error.getMessage()); return false;
            } finally { if(vertex!=0)GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment); }
        }
        EspGradient gradient=new EspGradient(paint.mode.is("Global Gradient")?settings.global:paint.gradient,seconds,team,hurt);
        boolean solid=override||paint.mode.is("Static")||paint.mode.is("Team")||paint.solid.isHurtOverride(hurt);
        int solidColor=paint.solid.resolve(team,hurt);
        if(paint.mode.is("Team")&&team!=0&&!paint.solid.isHurtOverride(hurt))solidColor=(solidColor&0xFF000000)|(team&0xFFFFFF);
        if(override)solidColor=overrideColor;
        GL20.glUseProgram(program);
        GL20.glUniform1i(shapeLocation,0);
        GL20.glUniform1i(imageLocation,0);GL20.glUniform1i(texturedLocation,texture?1:0);
        GL20.glUniform1i(countLocation,solid?1:gradient.colors.length);
        for(int i=0;i<gradient.colors.length;i++) {
            int c=solid?solidColor:gradient.colors[i];
            if(forcedColor!=0)c=(c&0xFF000000)|(forcedColor&0x00FFFFFF);
            GL20.glUniform4f(COLOR_LOCATIONS[i],(c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);
            GL20.glUniform1f(STOP_LOCATIONS[i],gradient.positions[i]);
        }
        GL20.glUniform2f(directionLocation,gradient.dx,gradient.dy);
        GL20.glUniform1f(phaseLocation,paint.mode.is("Rainbow")?(float)((seconds*paint.rainbowSpeed.getDouble())%1):gradient.phase);
        GL20.glUniform1i(rainbowLocation,!solid&&paint.mode.is("Rainbow")?1:0);
        GL20.glUniform1f(saturationLocation,paint.rainbowSaturation.getFloat());
        GL20.glUniform1f(opacityLocation,opacity);
        if(!viewportValid){VIEWPORT.clear();GL11.glGetInteger(GL11.GL_VIEWPORT,VIEWPORT);viewportValid=true;}
        GL20.glUniform4f(viewportLocation,VIEWPORT.get(0),VIEWPORT.get(1),VIEWPORT.get(2),VIEWPORT.get(3));
        GL20.glUniform2f(screenLocation,screenWidth,screenHeight);
        boolean global=paint.mode.is("Global Gradient");
        GL20.glUniform4f(regionLocation,global?0:rect.x,global?0:rect.y,global?screenWidth:rect.w,global?screenHeight:rect.h);
        return true;
    }
    static boolean shape(Esp2DSettings settings,Esp2DSettings.Paint paint,EspLayout.Rect rect,EspLayout.Rect paintRect,int w,int h,float opacity,double seconds,int team,boolean hurt,int forcedColor,boolean solid,int color,int kind,float radius,float width,float corners,float length){
        if(!bind(settings,paint,paintRect,w,h,false,opacity,seconds,team,hurt,forcedColor,solid,color))return false;
        GL20.glUniform1i(shapeLocation,kind);GL20.glUniform4f(shapeRectLocation,rect.x,rect.y,rect.w,rect.h);
        GL20.glUniform4f(shapeDataLocation,radius,width,corners,0);
        float hx=Math.min(Math.max(0,rect.w/2-radius),Math.max(width,length-radius));
        float hy=Math.min(Math.max(0,rect.h/2-radius),Math.max(width,length-radius));
        GL20.glUniform2f(cornerExtentLocation,radius+hx,radius+hy);return true;
    }
    /**
     * Drawing ESP boxes never changes the viewport, so their paints share one query.
     * Esp2DRenderer calls this before the first paint of a box or batch of boxes.
     */
    static void invalidateViewport() { viewportValid=false; }
    private static boolean viewportValid;
    // Looked up once after linking; the names never change, so per-paint string building is unnecessary.
    private static final int[] COLOR_LOCATIONS=new int[8],STOP_LOCATIONS=new int[8];
    private static int imageLocation,texturedLocation,countLocation,directionLocation,phaseLocation,rainbowLocation,
            saturationLocation,opacityLocation,viewportLocation,screenLocation,regionLocation,shapeLocation,shapeRectLocation,shapeDataLocation,cornerExtentLocation;
    private static void resolveLocations() {
        imageLocation=location("image");texturedLocation=location("textured");countLocation=location("count");
        for(int i=0;i<8;i++){COLOR_LOCATIONS[i]=location("colors["+i+"]");STOP_LOCATIONS[i]=location("stops["+i+"]");}
        directionLocation=location("direction");phaseLocation=location("phase");rainbowLocation=location("rainbow");
        saturationLocation=location("saturation");opacityLocation=location("opacity");viewportLocation=location("viewport");
        screenLocation=location("screen");regionLocation=location("region");
        shapeLocation=location("shape");shapeRectLocation=location("shapeRect");shapeDataLocation=location("shapeData");cornerExtentLocation=location("cornerExtent");
    }
    private static int location(String name) { return GL20.glGetUniformLocation(program,name); }
    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0) {String error=GL20.glGetShaderInfoLog(shader,4096);GL20.glDeleteShader(shader);throw new IllegalStateException(error);}
        return shader;
    }
}
