package dev.vibe.ui.menu;

import dev.vibe.ui.effect.*;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** A moving lava window underneath a separately composited, correctly premultiplied control layer. */
public final class MenuGlass {
    private static final long EPOCH=System.nanoTime();
    private static final SceneTexture fallback=new SceneTexture();
    private static final IntBuffer clip=BufferUtils.createIntBuffer(16);
    private static final FloatBuffer drops=BufferUtils.createFloatBuffer(32);
    private static final FloatBuffer forms=BufferUtils.createFloatBuffer(32);
    private static long emissionFrame=Long.MIN_VALUE;
    private static long frostedFrame=Long.MIN_VALUE;
    private static int frostedSource;
    private static EffectProgram program,composite,frost;
    private static Framebuffer content,mixed,frostX,frostY;
    private static GuiScreen screen;
    private static MenuTransitions.Rect window;
    private static int scene,guiWidth,guiHeight,destination;
    private static long frameTime;
    private static boolean failed,windowDrawn,simple;
    private static MenuPalette workspacePalette;
    private MenuGlass(){}
    public static boolean active(){return screen!=null;}
    public static MenuPalette currentPalette(){MenuThemes.frame();return MenuThemes.lavaPalette();}
    public static float scale(){return active()?Minecraft.getMinecraft().displayWidth/(float)guiWidth:new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();}
    public static void begin(GuiScreen owner,MainMenuShaderManager shaders,int width,int height){
        begin(owner,shaders,width,height,false);
    }
    /** ClickGUI uses the same material regardless of the selected out-of-world menu style. */
    public static void beginLavalamp(GuiScreen owner,int width,int height){begin(owner,null,width,height,true);}
    private static void begin(GuiScreen owner,MainMenuShaderManager shaders,int width,int height,boolean workspace){
        MenuThemes.frame();
        workspacePalette=workspace?MenuThemes.lavaPalette():null;
        screen=owner;guiWidth=width;guiHeight=height;frameTime=System.nanoTime();windowDrawn=false;window=null;
        Minecraft mc=Minecraft.getMinecraft();destination=GL11.glGetInteger(0x8CA6);scene=shaders==null?0:shaders.getBackgroundTexture();
        simple=!workspace&&MenuThemes.simpleMenus();if(simple)return;
        if(failed)return;
        try(EffectState state=new EffectState()){
            if(scene==0){fallback.capture(0,0,mc.displayWidth,mc.displayHeight,false);scene=fallback.color;}
            if(content==null||content.framebufferWidth!=mc.displayWidth||content.framebufferHeight!=mc.displayHeight){
                if(content!=null)content.deleteFramebuffer();if(mixed!=null)mixed.deleteFramebuffer();
                content=new Framebuffer(mc.displayWidth,mc.displayHeight,false);mixed=new Framebuffer(mc.displayWidth,mc.displayHeight,false);
                content.setFramebufferFilter(GL11.GL_LINEAR);mixed.setFramebufferFilter(GL11.GL_LINEAR);
            }
            prepareFrost(mc.displayWidth,mc.displayHeight,shaders==null?0:shaders.getBackgroundFrame());
            clear(content);
        }catch(Exception failure){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Menu layer allocation unavailable",failure);}
    }
    private static void clear(Framebuffer framebuffer){framebuffer.bindFramebuffer(false);GL11.glColorMask(true,true,true,true);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);}
    /** Cache a reduced-resolution backdrop blur; lava never enters either blur pass. */
    private static void prepareFrost(int width,int height,long version) throws Exception {
        int divisor=workspacePalette==null?Math.max(4,Math.round(2*scale())):Math.max(2,Math.round(scale()));
        int w=Math.max(1,(width+divisor-1)/divisor),h=Math.max(1,(height+divisor-1)/divisor);
        if(frostX==null||frostX.framebufferWidth!=w||frostX.framebufferHeight!=h){
            if(frostX!=null)frostX.deleteFramebuffer();if(frostY!=null)frostY.deleteFramebuffer();
            frostX=new Framebuffer(w,h,false);frostY=new Framebuffer(w,h,false);frostX.setFramebufferFilter(GL11.GL_LINEAR);frostY.setFramebufferFilter(GL11.GL_LINEAR);
            frostedFrame=Long.MIN_VALUE;
        }
        if(version>0&&version==frostedFrame&&scene==frostedSource){scene=frostY.framebufferTexture;return;}
        frostedSource=scene;frostedFrame=version;
        if(frost==null)frost=new EffectProgram("fullscreen.vert","MenuFrost.frag");
        frost.bind();frost.integer("Scene",0);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glViewport(0,0,w,h);
        float radius=(workspacePalette==null?3:1.1F)*scale();frostX.bindFramebuffer(false);FogRenderer.texture(0,scene);frost.vec2("Step",radius/width,0);FogRenderer.quad();
        frostY.bindFramebuffer(false);FogRenderer.texture(0,frostX.framebufferTexture);frost.vec2("Step",0,radius/height);FogRenderer.quad();scene=frostY.framebufferTexture;
    }
    public static void end(){
        GuiScreen owner=screen;
        try{
            if(windowDrawn){
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,destination);
                float old=MenuTransitions.outgoing(owner,frameTime),fresh=MenuTransitions.incoming(owner,frameTime);Framebuffer layer=content;
                if(old>0||fresh<1){
                    try(EffectState state=new EffectState()){clear(mixed);drawLayer(content.framebufferTexture,MenuTransitions.previous(),fresh,old,false);}
                    layer=mixed;
                }
                drawLayer(layer.framebufferTexture,layer.framebufferTexture,1,0,true);
                MenuTransitions.record(owner,window,layer);
            }
        }catch(Exception failure){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Menu layer composition unavailable",failure);}
        finally{OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,destination);screen=null;workspacePalette=null;}
    }
    private static void drawLayer(int current,int previous,float weight,float oldWeight,boolean blend) throws Exception {
        Minecraft mc=Minecraft.getMinecraft();
        try(EffectState state=new EffectState()){
            if(composite==null)composite=new EffectProgram("fullscreen.vert","MenuComposite.frag");composite.bind();
            FogRenderer.texture(0,current);FogRenderer.texture(1,previous);composite.integer("Current",0);composite.integer("Previous",1);
            composite.vec2("Resolution",mc.displayWidth,mc.displayHeight);composite.scalar("CurrentWeight",weight);composite.scalar("PreviousWeight",oldWeight);
            GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);GL11.glDisable(GL11.GL_DEPTH_TEST);
            if(blend){GL11.glEnable(GL11.GL_BLEND);GL14.glBlendEquation(GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);}else GL11.glDisable(GL11.GL_BLEND);
            FogRenderer.quad();
        }
    }
    private static MenuPalette palette(){return workspacePalette==null?MenuThemes.current():workspacePalette;}
    public static boolean pane(int x,int y,int width,int height,int radius,int fill,int border,boolean mainWindow){return pane(x,y,width,height,radius,fill,border,mainWindow,palette());}
    static boolean pane(int x,int y,int width,int height,int radius,int fill,int border,boolean mainWindow,MenuPalette palette){
        return pane(x,y,width,height,radius,fill,border,mainWindow,palette,0,.5F,.5F);
    }
    static boolean button(int x,int y,int width,int height,int fill,int border,float hover,float pointerX,float pointerY){
        return pane(x,y,width,height,10,fill,border,false,palette(),hover,pointerX,pointerY);
    }
    public static boolean control(int x,int y,int width,int height,int radius,int fill,int border,float hover,float pointerX,float pointerY){return pane(x,y,width,height,radius,fill,border,false,palette(),hover,pointerX,pointerY);}
    private static boolean pane(int x,int y,int width,int height,int radius,int fill,int border,boolean mainWindow,MenuPalette palette,float hover,float pointerX,float pointerY){
        if(active()&&simple){flatPane(x,y,width,height,fill,border,mainWindow,hover);return true;}
        if(!active()||failed||scene==0||width<=0||height<=0)return false;
        Minecraft mc=Minecraft.getMinecraft();float sx=mc.displayWidth/(float)guiWidth,sy=mc.displayHeight/(float)guiHeight,scale=Math.min(sx,sy);
        boolean root=mainWindow&&!windowDrawn;
        MenuTransitions.Rect bounds=new MenuTransitions.Rect(x*sx,y*sy,width*sx,height*sy);
        if(root){bounds=MenuTransitions.window(screen,bounds,frameTime);window=bounds;}
        int padding=Math.round(12*scale),left=Math.max(0,(int)Math.floor(bounds.x)-padding),bottom=Math.max(0,(int)Math.floor(mc.displayHeight-bounds.y-bounds.height)-padding);
        int right=Math.min(mc.displayWidth,(int)Math.ceil(bounds.x+bounds.width)+padding),top=Math.min(mc.displayHeight,(int)Math.ceil(mc.displayHeight-bounds.y)+padding);
        if(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)){
            clip.clear();GL11.glGetInteger(GL11.GL_SCISSOR_BOX,clip);left=Math.max(left,clip.get(0));bottom=Math.max(bottom,clip.get(1));right=Math.min(right,clip.get(0)+clip.get(2));top=Math.min(top,clip.get(1)+clip.get(3));
        }
        if(right<=left||top<=bottom)return true;
        try(EffectState state=new EffectState()){
            if(program==null)program=new EffectProgram("fullscreen.vert","MenuGlass.frag");program.bind();prepareDrops();FogRenderer.texture(0,scene);program.integer("Scene",0);
            program.vec2("Resolution",mc.displayWidth,mc.displayHeight);program.vec2("Origin",bounds.x,mc.displayHeight-bounds.y-bounds.height);program.vec2("Size",bounds.width,bounds.height);
            MenuTransitions.Rect lava=window==null||mainWindow||palette!=palette()?bounds:window;
            program.vec2("LavaOrigin",lava.x,mc.displayHeight-lava.y-lava.height);program.vec2("LavaSize",lava.width,lava.height);
            program.scalar("Radius",Math.min(radius*scale,Math.min(bounds.width,bounds.height)*.5F));program.scalar("Scale",scale);program.scalar("Time",(frameTime-EPOCH)/1e9F);program.scalar("Window",mainWindow?1:0);
            program.scalar("Hover",hover);program.vec2("Pointer",pointerX,1-pointerY);
            program.scalar("Clarity",workspacePalette==null?0:1);
            float emphasis=fill==AccountScreenStyle.ACCENT||border==palette.accent?1:Math.max(0,Math.min(1,1-colorDistance(border,palette.accent)/Math.max(1F,colorDistance(AccountScreenStyle.BORDER,palette.accent))));program.scalar("Emphasis",emphasis);
            rgb("Tint",mainWindow?palette.background:fill==AccountScreenStyle.ACCENT?palette.surface:fill);rgb("Accent",palette.accent);rgb("Wax",palette.lava);rgb("Light",palette.light);
            GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(left,bottom,right-left,top-bottom);
            GL11.glEnable(GL11.GL_BLEND);GL14.glBlendEquation(GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            program.scalar("Pass",0);FogRenderer.quad();
            GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            float originY=mc.displayHeight-lava.y-lava.height;
            int poolBottom=Math.max(bottom,(int)Math.floor(originY)),poolTop=Math.min(top,(int)Math.ceil(originY+lava.height*.29F));
            if(poolTop>poolBottom){program.scalar("Pass",1);GL11.glScissor(left,poolBottom,right-left,poolTop-poolBottom);FogRenderer.quad();}
            poolBottom=Math.max(bottom,(int)Math.floor(originY+lava.height*.75F));poolTop=Math.min(top,(int)Math.ceil(originY+lava.height));
            if(poolTop>poolBottom){program.scalar("Pass",1);GL11.glScissor(left,poolBottom,right-left,poolTop-poolBottom);FogRenderer.quad();}
            program.scalar("Pass",2);
            for(int i=0;i<8;i++){
                float dx=drops.get(i*4),dy=drops.get(i*4+1),r=drops.get(i*4+2),stretch=drops.get(i*4+3);if(r<=0)continue;
                float dropRadius=r*lava.height,cx=lava.x+dx*lava.width,cy=originY+dy*lava.height;
                float longest=Math.max(stretch,forms.get(i*4)),shortest=Math.min(stretch,forms.get(i*4));
                float extent=dropRadius*(longest*1.8F+1)+lava.height*.052F*2.85F*longest/shortest,ex=extent,ey=extent;
                int a=Math.max(left,(int)Math.floor(cx-ex)),b=Math.min(right,(int)Math.ceil(cx+ex));
                int c=Math.max(bottom,(int)Math.floor(cy-ey)),d=Math.min(top,(int)Math.ceil(cy+ey));
                if(b<=a||d<=c)continue;
                program.vec4("Drop",dx,dy,r,stretch);program.vec4("Form",forms.get(i*4),forms.get(i*4+1),forms.get(i*4+2),forms.get(i*4+3));
                program.scalar("Depth",LavaLampMotion.depth(i));GL11.glScissor(a,c,b-a,d-c);FogRenderer.quad();
            }
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            program.scalar("Pass",3);GL11.glScissor(left,bottom,right-left,top-bottom);FogRenderer.quad();
        }catch(Exception failure){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Menu glass unavailable",failure);return false;}
        if(root){windowDrawn=true;content.bindFramebuffer(false);}
        return true;
    }
    private static void rgb(String uniform,int color){program.vec3(uniform,(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);}
    private static void flatPane(int x,int y,int w,int h,int fill,int border,boolean root,float hover){
        boolean skeet=MenuThemes.style().equals("Skeet");
        if(skeet){Gui.drawRect(x,y,x+w,y+h,0xFF090909);Gui.drawRect(x+1,y+1,x+w-1,y+h-1,0xFF555555);Gui.drawRect(x+2,y+2,x+w-2,y+h-2,0xFF141414);Gui.drawRect(x+3,y+3,x+w-3,y+h-3,root?0xFF202020:dev.vibe.ui.RenderUtils.blend(0xFF292929,0xFF444444,hover));
            if(root){for(int i=0;i<w-8;i++){float f=i/(float)Math.max(1,w-9);int c=java.awt.Color.HSBtoRGB(.52F+f*.43F,.55F,.9F);Gui.drawRect(x+4+i,y+4,x+5+i,y+5,c);}}}
        else{dev.vibe.ui.MenuRoundedRenderer.rect(x,y,w,h,root?10:5,dev.vibe.ui.RenderUtils.alpha(border,160));dev.vibe.ui.MenuRoundedRenderer.rect(x+1,y+1,w-2,h-2,root?9:4,root?0xF014171C:dev.vibe.ui.RenderUtils.blend(0xFF20252D,0xFF303B49,hover));}
    }
    /** Emitter clocks and randomization are uniform for the whole frame, not per-pixel work. */
    private static void prepareDrops(){
        if(emissionFrame==frameTime)return;
        LavaLampMotion.sample((frameTime-EPOCH)/1e9F,drops,forms);emissionFrame=frameTime;
    }
    private static float colorDistance(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}
}
