package dev.vibe.ui;

import dev.vibe.module.impl.Esp2DSettings;
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
                        int screenWidth,int screenHeight,boolean texture,float opacity,double seconds) {
        if (failed) return false;
        if(program==0) {
            int vertex=0,fragment=0,linked=0;
            try {
                vertex=compile(GL20.GL_VERTEX_SHADER,VERTEX); fragment=compile(GL20.GL_FRAGMENT_SHADER,FRAGMENT);
                linked=GL20.glCreateProgram();GL20.glAttachShader(linked,vertex);GL20.glAttachShader(linked,fragment);GL20.glLinkProgram(linked);
                if(GL20.glGetProgrami(linked,GL20.GL_LINK_STATUS)==0) throw new IllegalStateException(GL20.glGetProgramInfoLog(linked,4096));
                program=linked;
            } catch(RuntimeException error) {
                failed=true; if(linked!=0)GL20.glDeleteProgram(linked);
                System.err.println("[Vibe] ESP gradient shader unavailable: "+error.getMessage()); return false;
            } finally { if(vertex!=0)GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment); }
        }
        EspGradient gradient=new EspGradient(paint.mode.is("Global Gradient")?settings.global:paint.gradient,seconds);
        GL20.glUseProgram(program);
        GL20.glUniform1i(location("image"),0);GL20.glUniform1i(location("textured"),texture?1:0);
        GL20.glUniform1i(location("count"),paint.mode.is("Static")?1:gradient.colors.length);
        for(int i=0;i<gradient.colors.length;i++) {
            int c=paint.mode.is("Static")?paint.solid.getArgb():gradient.colors[i];GL20.glUniform4f(location("colors["+i+"]"),(c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);
            GL20.glUniform1f(location("stops["+i+"]"),gradient.positions[i]);
        }
        GL20.glUniform2f(location("direction"),gradient.dx,gradient.dy);
        GL20.glUniform1f(location("phase"),paint.mode.is("Rainbow")?(float)((seconds*paint.rainbowSpeed.getDouble())%1):gradient.phase);
        GL20.glUniform1i(location("rainbow"),paint.mode.is("Rainbow")?1:0);
        GL20.glUniform1f(location("saturation"),paint.rainbowSaturation.getFloat());
        GL20.glUniform1f(location("opacity"),opacity);
        VIEWPORT.clear();GL11.glGetInteger(GL11.GL_VIEWPORT,VIEWPORT);
        GL20.glUniform4f(location("viewport"),VIEWPORT.get(0),VIEWPORT.get(1),VIEWPORT.get(2),VIEWPORT.get(3));
        GL20.glUniform2f(location("screen"),screenWidth,screenHeight);
        boolean global=paint.mode.is("Global Gradient");
        GL20.glUniform4f(location("region"),global?0:rect.x,global?0:rect.y,global?screenWidth:rect.w,global?screenHeight:rect.h);
        return true;
    }
    private static final java.util.Map<String,Integer> locations=new java.util.HashMap<String,Integer>();
    private static int location(String name) { Integer result=locations.get(name);if(result==null){result=GL20.glGetUniformLocation(program,name);locations.put(name,result);}return result; }
    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0) {String error=GL20.glGetShaderInfoLog(shader,4096);GL20.glDeleteShader(shader);throw new IllegalStateException(error);}
        return shader;
    }
}
