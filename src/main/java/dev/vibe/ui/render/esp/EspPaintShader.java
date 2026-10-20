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
    private static final String VERTEX="#version 120\nvoid main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;gl_FrontColor=gl_Color;}";
    private static final String FRAGMENT="#version 120\n"
            +"uniform sampler2D image; uniform int textured; uniform int count; uniform int rainbow;"
            +"uniform vec4 colors[8]; uniform float stops[8]; uniform vec2 direction; uniform float phase;"
            +"uniform vec4 viewport; uniform vec2 screen; uniform vec4 region; uniform float opacity; uniform float saturation;"
            +"void main(){vec2 p=(gl_FragCoord.xy-viewport.xy)/viewport.zw*screen; p.y=screen.y-p.y;"
            +"vec2 q=p-region.xy; float t=.5+dot(q-region.zw*.5,direction)/max(.001,dot(abs(direction),region.zw));"
            +"vec4 c=colors[0]; if(rainbow==1){vec3 hue=clamp(abs(mod(fract(t+phase)*6.+vec3(0.,4.,2.),6.)-3.)-1.,0.,1.);c=vec4(mix(vec3(1.),hue,saturation),1.);}"
            +"else {t=1.-abs(mod(t+phase,2.)-1.); for(int i=1;i<8;i++){if(i<count){float f=clamp((t-stops[i-1])/max(.00001,stops[i]-stops[i-1]),0.,1.); c=mix(c,colors[i],f);}}}"
            +"float mask=textured==1?texture2D(image,gl_TexCoord[0].xy).a:1.;gl_FragColor=vec4(c.rgb,c.a*mask*opacity);}";

    static boolean bind(Esp2DSettings settings, Esp2DSettings.Paint paint, EspLayout.Rect rect,
                        int screenWidth,int screenHeight,boolean texture,float opacity,double seconds,int team,boolean hurt,int forcedColor) {
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
        boolean solid=paint.mode.is("Static")||paint.mode.is("Team")||paint.solid.isHurtOverride(hurt);
        int solidColor=paint.solid.resolve(team,hurt);
        if(paint.mode.is("Team")&&team!=0&&!paint.solid.isHurtOverride(hurt))solidColor=(solidColor&0xFF000000)|(team&0xFFFFFF);
        GL20.glUseProgram(program);
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
    /**
     * Drawing ESP boxes never changes the viewport, so their paints share one query.
     * Esp2DRenderer calls this before the first paint of a box or batch of boxes.
     */
    static void invalidateViewport() { viewportValid=false; }
    private static boolean viewportValid;
    // Looked up once after linking; the names never change, so per-paint string building is unnecessary.
    private static final int[] COLOR_LOCATIONS=new int[8],STOP_LOCATIONS=new int[8];
    private static int imageLocation,texturedLocation,countLocation,directionLocation,phaseLocation,rainbowLocation,
            saturationLocation,opacityLocation,viewportLocation,screenLocation,regionLocation;
    private static void resolveLocations() {
        imageLocation=location("image");texturedLocation=location("textured");countLocation=location("count");
        for(int i=0;i<8;i++){COLOR_LOCATIONS[i]=location("colors["+i+"]");STOP_LOCATIONS[i]=location("stops["+i+"]");}
        directionLocation=location("direction");phaseLocation=location("phase");rainbowLocation=location("rainbow");
        saturationLocation=location("saturation");opacityLocation=location("opacity");viewportLocation=location("viewport");
        screenLocation=location("screen");regionLocation=location("region");
    }
    private static int location(String name) { return GL20.glGetUniformLocation(program,name); }
    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0) {String error=GL20.glGetShaderInfoLog(shader,4096);GL20.glDeleteShader(shader);throw new IllegalStateException(error);}
        return shader;
    }
}
