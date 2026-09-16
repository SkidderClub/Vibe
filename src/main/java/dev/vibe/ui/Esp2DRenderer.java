package dev.vibe.ui;

import dev.vibe.module.impl.Esp2DSettings;
import dev.vibe.module.impl.Esp2DSettings.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** One measured layout and drawing implementation for Minecraft, GTA7 and the editor. */
public final class Esp2DRenderer {
    public static final class Actor {
        public final String name, itemName;
        public final float health, maxHealth, armor, distance, opacity;
        public final ItemStack item;
        public String nativeItem = "";
        public int teamColor,profile;public boolean hurt;
        public final ItemStack[] equipment;
        public Actor(String name,float health,float maxHealth,float armor,float distance,String itemName,
                     ItemStack item,ItemStack[] equipment,float opacity) {
            this.name=clean(name);this.health=health;this.maxHealth=maxHealth;this.armor=armor;this.distance=distance;
            this.itemName=clean(itemName);this.item=item;this.equipment=equipment==null?new ItemStack[0]:equipment;this.opacity=opacity;
        }
        private static String clean(String text) { return text==null?"":EnumChatFormatting.getTextWithoutFormattingCodes(text); }
    }
    public static final class Frame {
        public final EspLayout.Rect box;
        public final Map<String,EspLayout.Rect> elements;
        public final float scale;
        Frame(EspLayout.Rect box,Map<String,EspLayout.Rect> elements,float scale) {this.box=box;this.elements=elements;this.scale=scale;}
    }
    private final Minecraft mc=Minecraft.getMinecraft();
    public Frame measure(Esp2DSettings settings,Actor actor,EspLayout.Rect box) {
        float scale=EspLayout.scale(box.h,settings.distanceScaling.getFloat());
        List<EspLayout.Request> requests=new ArrayList<EspLayout.Request>();
        for(Element e:settings.elements) {
            if(!e.enabled.isEnabled() || e.kind==Kind.BOX || !available(e,settings,actor))continue;
            float s=scale*e.scale.getFloat(),w,h;
            if(e.kind==Kind.BAR){w=e.vertical()?e.width.getFloat()*s:box.w;h=e.vertical()?box.h:e.width.getFloat()*s;}
            else if(e.kind==Kind.TEXT){TextStyle t=e.resolvedText();float size=t.size.getFloat()/9*s;String text=label(e,settings,actor);w=textWidth(t,text)*size;h=textHeight(t,text)*size;}
            else {int count=e.kind==Kind.ARMOR?equipmentCount(actor):1;w=16*s*(e.kind==Kind.ARMOR&&!e.vertical()?count:1);h=16*s*(e.kind==Kind.ARMOR&&e.vertical()?count:1);}
            float pad=e.outline.isEnabled()?e.outlineWidth.getFloat()*s:0;
            if(e.kind==Kind.TEXT && e.resolvedText().shadow.isEnabled())pad=Math.max(pad,s*e.resolvedText().size.getFloat()/9);
            if(e.backgroundEnabled.isEnabled())pad=Math.max(pad,2*s);
            requests.add(new EspLayout.Request(e.title,e.position.getValue(),w,h,e.order.getFloat(),e.offset.getFloat()*box.h/180f,pad));
        }
        float boxPad=settings.box.enabled.isEnabled()?(settings.box.width.getFloat()+ (settings.box.outline.isEnabled()?settings.box.outlineWidth.getFloat():0))*scale*settings.box.scale.getFloat():0;
        Map<String,EspLayout.Rect> result=EspLayout.arrange(box.expand(boxPad),requests,settings.gap.getFloat()*scale);
        return new Frame(box,result,scale);
    }
    private boolean available(Element e,Esp2DSettings s,Actor a) {
        if(e==s.itemName)return !a.itemName.isEmpty();if(e==s.itemIcon)return a.item!=null || !a.nativeItem.isEmpty();
        if(e==s.armor)return equipmentCount(a)>0;return true;
    }
    private int equipmentCount(Actor a) {int n=0;for(ItemStack item:a.equipment)if(item!=null)n++;return n;}
    private String label(Element e,Esp2DSettings s,Actor a) {
        if(e==s.name)return a.name;if(e==s.itemName)return a.itemName;
        if(e==s.distance)return Math.round(a.distance*(e.metric.is("Feet")?3.280839895:1))+(e.metric.is("Feet")?" ft":" m");
        return String.format(Locale.ROOT,"%.0f HP",Math.max(0,a.health));
    }
    private NeverLoseFont font(TextStyle t) {return t.font.is("Sans Bold")?NeverLoseFont.BOLD:NeverLoseFont.REGULAR;}
    private float textWidth(TextStyle t,String value) {return t.font.is("Minecraft")?mc.fontRendererObj.getStringWidth(value):font(t).width(value);}
    private float textHeight(TextStyle t,String text) {return t.font.is("Minecraft")?8:font(t).inkHeight(text);}

    public Frame draw(Esp2DSettings settings,Actor actor,EspLayout.Rect box,int screenWidth,int screenHeight,boolean editor) {
        Frame frame=measure(settings,actor,box);
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GuiRenderState.prepare(false);GL20.glUseProgram(0);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            double seconds=System.nanoTime()/1.0e9;
            Element b=settings.box;
            if(b.enabled.isEnabled() && (editor||!blocked(box)))drawBox(settings,b,actor,frame,screenWidth,screenHeight,seconds);
            for(Element e:settings.elements) {
                EspLayout.Rect r=frame.elements.get(e.title);if(r==null||(!editor&&blocked(r)))continue;
                float s=frame.scale*e.scale.getFloat();
                if(e.kind==Kind.BAR){
                    float outline=e.outline.isEnabled()?e.outlineWidth.getFloat()*s:0;
                    if(outline>0)border(r,outline,alpha(e.outlineColor.resolve(actor.teamColor,actor.hurt),actor.opacity));
                    if(e.backgroundEnabled.isEnabled())solid(r,alpha(e.background.resolve(actor.teamColor,actor.hurt),actor.opacity));
                    float ratio=e==settings.healthBar?actor.health/Math.max(.001F,actor.maxHealth):actor.armor/20;
                    ratio=Math.max(0,Math.min(1,ratio));
                    EspLayout.Rect fill=e.vertical()?new EspLayout.Rect(r.x,r.bottom()-r.h*ratio,r.w,r.h*ratio):new EspLayout.Rect(r.x,r.y,r.w*ratio,r.h);
                    paint(settings,e.color,r,screenWidth,screenHeight,false,actor,seconds);quad(fill);GL20.glUseProgram(0);
                }else if(e.kind==Kind.TEXT){drawText(settings,e,label(e,settings,actor),r,s,actor,screenWidth,screenHeight,seconds);}
                else {
                    if(e.backgroundEnabled.isEnabled())solid(r.expand(2*s),alpha(e.background.resolve(actor.teamColor,actor.hurt),actor.opacity));
                    if(e.outline.isEnabled())border(r,e.outlineWidth.getFloat()*s,alpha(e.outlineColor.resolve(actor.teamColor,actor.hurt),actor.opacity));
                    if(e.kind==Kind.ICON){if(actor.item!=null)drawItem(actor.item,r.x,r.y,s,actor.opacity);else drawNativeItem(actor.nativeItem,r,s,actor.opacity);}
                    else {int i=0;for(ItemStack item:actor.equipment)if(item!=null){drawItem(item,r.x+(e.vertical()?0:i*16*s),r.y+(e.vertical()?i*16*s:0),s,actor.opacity);i++;}}
                }
            }
        } finally {
            GL20.glUseProgram(program);GL11.glPopAttrib();
            // Fonts/items go through Minecraft's cache; reconcile texture and toggles after raw GL restoration.
            GlStateManager.bindTexture(0);GlStateManager.bindTexture(texture);
            syncState();GlStateManager.resetColor();
        }
        return frame;
    }
    private boolean blocked(EspLayout.Rect r) {return DebugOverlay.overlaps((int)r.x,(int)r.y,(int)Math.ceil(r.right()),(int)Math.ceil(r.bottom()));}
    private void drawBox(Esp2DSettings settings,Element e,Actor a,Frame f,int w,int h,double seconds) {
        EspLayout.Rect r=f.box;float s=f.scale*e.scale.getFloat(),line=e.width.getFloat()*s;
        float radius=Math.min(Math.min(r.w,r.h)/2,e.rounding.getFloat()*s);
        boolean corners=e.corners.isEnabled()&&a.distance>=e.cornerDistance.getFloat();
        float length=Math.min(r.w,r.h)*e.cornerLength.getFloat();
        if(e.backgroundEnabled.isEnabled())solidRounded(r,radius,alpha(e.background.resolve(a.teamColor,a.hurt),a.opacity));
        if(e.outline.isEnabled()) {
            GlStateManager.disableTexture2D();
            color(alpha(e.outlineColor.resolve(a.teamColor,a.hurt),a.opacity));
            stroke(r,radius,line+2*e.outlineWidth.getFloat()*s,corners,length+e.outlineWidth.getFloat()*s);
        }
        paint(settings,e.color,r,w,h,false,a,seconds);stroke(r,radius,line,corners,length);GL20.glUseProgram(0);
    }
    private void drawText(Esp2DSettings settings,Element e,String value,EspLayout.Rect r,float scale,Actor actor,int w,int h,double seconds) {
        TextStyle t=e.resolvedText();float size=t.size.getFloat()/9*scale,opacity=actor.opacity;
        if(e.backgroundEnabled.isEnabled())solid(r.expand(2*scale),alpha(e.background.resolve(actor.teamColor,actor.hurt),opacity));
        GlStateManager.pushMatrix();GlStateManager.translate(r.x,r.y-(t.font.is("Minecraft")?0:font(t).inkTop(value)*size),0);GlStateManager.scale(size,size,1);
        try {
            if(e.outline.isEnabled()) {
                float d=e.outlineWidth.getFloat()*scale/size;
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)if(x!=0||y!=0)text(t,value,x*d,y*d,alpha(e.outlineColor.resolve(actor.teamColor,actor.hurt),opacity));
            }
            if(t.shadow.isEnabled())text(t,value,1,1,alpha(0xB0000000,opacity));
            boolean shader=paint(settings,t.color,r,w,h,true,actor,seconds);
            text(t,value,0,0,shader?0xFFFFFFFF:alpha(fallback(settings,t.color,r,w,h,seconds,actor),opacity));
        } finally {GL20.glUseProgram(0);GlStateManager.popMatrix();}
    }
    private void text(TextStyle t,String value,float x,float y,int color) {
        if((color>>>24)==0)return;
        if(t.font.is("Minecraft"))mc.fontRendererObj.drawString(value,x,y,color,false);else font(t).draw(value,x,y,color);
    }
    private boolean paint(Esp2DSettings settings,Paint paint,EspLayout.Rect rect,int w,int h,boolean texture,Actor actor,double seconds) {
        GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);
        if(!texture)GlStateManager.disableTexture2D();
        boolean shader=EspPaintShader.bind(settings,paint,rect,w,h,texture,actor.opacity,seconds,actor.teamColor,actor.hurt);
        if(!shader) {GL20.glUseProgram(0);color(alpha(fallback(settings,paint,rect,w,h,seconds,actor),actor.opacity));}
        return shader;
    }
    private int fallback(Esp2DSettings settings,Paint paint,EspLayout.Rect rect,int w,int h,double seconds,Actor actor){
        int c=paint.solid.resolve(actor.teamColor,actor.hurt);if(paint.solid.isHurtOverride(actor.hurt))return c;
        if(paint.mode.is("Team"))return actor.teamColor==0?c:(c&0xFF000000)|(actor.teamColor&0xFFFFFF);
        if(paint.mode.is("Rainbow"))return java.awt.Color.HSBtoRGB((float)((seconds*paint.rainbowSpeed.getDouble())%1),paint.rainbowSaturation.getFloat(),1);
        if(!paint.mode.is("Static")){boolean global=paint.mode.is("Global Gradient");EspGradient g=new EspGradient(global?settings.global:paint.gradient,seconds,actor.teamColor,actor.hurt);return g.sample(global?rect.x+rect.w/2:rect.w/2,global?rect.y+rect.h/2:rect.h/2,global?w:rect.w,global?h:rect.h);}
        return c;
    }
    private void drawNativeItem(String kind,EspLayout.Rect r,float scale,float opacity) {
        GlStateManager.pushMatrix();GlStateManager.translate(r.x,r.y,0);GlStateManager.scale(scale,scale,1);
        try {
            int metal=alpha(0xFFE0E6F2,opacity),grip=alpha(0xFF8E9BAF,opacity);
            if(kind.equals("Knife")){solid(new EspLayout.Rect(7,1,2,10),metal);solid(new EspLayout.Rect(5,10,6,1),grip);solid(new EspLayout.Rect(7,11,2,4),grip);}
            else {solid(new EspLayout.Rect(2,5,kind.equals("Pistol")?11:14,3),metal);solid(new EspLayout.Rect(3,8,3,6),grip);
                if(!kind.equals("Pistol"))solid(new EspLayout.Rect(9,8,2,4),grip);}
        }finally{GlStateManager.popMatrix();}
    }
    private void drawItem(ItemStack item,float x,float y,float scale,float opacity) {
        if(item==null)return;
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL20.glUseProgram(0);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x,y,0);GlStateManager.scale(scale,scale,scale);
            GlStateManager.enableTexture2D();GlStateManager.enableDepth();RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.color(1,1,1,opacity);mc.getRenderItem().renderItemAndEffectIntoGUI(item,0,0);
        }finally {RenderHelper.disableStandardItemLighting();GlStateManager.popMatrix();GL11.glPopAttrib();GlStateManager.bindTexture(0);GlStateManager.bindTexture(texture);syncState();}
    }
    private static void syncState() {
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        if(texture){GlStateManager.disableTexture2D();GlStateManager.enableTexture2D();}else{GlStateManager.enableTexture2D();GlStateManager.disableTexture2D();}
        if(blend){GlStateManager.disableBlend();GlStateManager.enableBlend();}else{GlStateManager.enableBlend();GlStateManager.disableBlend();}
        if(depth){GlStateManager.disableDepth();GlStateManager.enableDepth();}else{GlStateManager.enableDepth();GlStateManager.disableDepth();}
        if(alpha){GlStateManager.disableAlpha();GlStateManager.enableAlpha();}else{GlStateManager.enableAlpha();GlStateManager.disableAlpha();}
        if(lighting){GlStateManager.disableLighting();GlStateManager.enableLighting();}else{GlStateManager.enableLighting();GlStateManager.disableLighting();}
        boolean mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);GlStateManager.depthMask(!mask);GlStateManager.depthMask(mask);
        int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);GlStateManager.depthFunc(func==GL11.GL_ALWAYS?GL11.GL_LESS:GL11.GL_ALWAYS);GlStateManager.depthFunc(func);
        int src=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.tryBlendFuncSeparate(src==1?0:1,dst==0?1:0,srcAlpha==1?0:1,dstAlpha==0?1:0);GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);
        int alphaFunc=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);float ref=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        GlStateManager.alphaFunc(alphaFunc==GL11.GL_ALWAYS?GL11.GL_LESS:GL11.GL_ALWAYS,ref);GlStateManager.alphaFunc(alphaFunc,ref);
        boolean fog=GL11.glIsEnabled(GL11.GL_FOG),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),material=GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        if(fog){GlStateManager.disableFog();GlStateManager.enableFog();}else{GlStateManager.enableFog();GlStateManager.disableFog();}
        if(cull){GlStateManager.disableCull();GlStateManager.enableCull();}else{GlStateManager.enableCull();GlStateManager.disableCull();}
        if(material){GlStateManager.disableColorMaterial();GlStateManager.enableColorMaterial();}else{GlStateManager.enableColorMaterial();GlStateManager.disableColorMaterial();}
        for(int i=0;i<2;i++){boolean light=GL11.glIsEnabled(GL11.GL_LIGHT0+i);if(light){GlStateManager.disableLight(i);GlStateManager.enableLight(i);}else{GlStateManager.enableLight(i);GlStateManager.disableLight(i);}}
    }
    private static int alpha(int color,float opacity) {return color&0xFFFFFF|Math.round((color>>>24)*Math.max(0,Math.min(1,opacity)))<<24;}
    static void color(int c) {GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);}
    public static void solid(EspLayout.Rect r,int color) {GL20.glUseProgram(0);GlStateManager.disableTexture2D();GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);color(color);quad(r);}
    private static void border(EspLayout.Rect r,float width,int color) {
        solid(new EspLayout.Rect(r.x-width,r.y-width,r.w+2*width,width),color);
        solid(new EspLayout.Rect(r.x-width,r.bottom(),r.w+2*width,width),color);
        solid(new EspLayout.Rect(r.x-width,r.y,width,r.h),color);
        solid(new EspLayout.Rect(r.right(),r.y,width,r.h),color);
    }
    private static void quad(EspLayout.Rect r) {
        GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(r.x,r.y);GL11.glVertex2f(r.x,r.bottom());GL11.glVertex2f(r.right(),r.bottom());GL11.glVertex2f(r.right(),r.y);GL11.glEnd();
    }
    private static void solidRounded(EspLayout.Rect r,float radius,int color) {
        if((color>>>24)==0)return;GlStateManager.disableTexture2D();color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(r.x+r.w/2,r.y+r.h/2);
        for(int corner=0;corner<4;corner++)for(int step=0;step<=8;step++){
            double angle=Math.toRadians((corner%4)*90+180+step*90.0/8);
            float cx=(corner%4==0||corner%4==3)?r.x+radius:r.right()-radius,cy=(corner%4<2)?r.y+radius:r.bottom()-radius;
            GL11.glVertex2d(cx+Math.cos(angle)*radius,cy+Math.sin(angle)*radius);
        }GL11.glVertex2f(r.x,r.y+radius);GL11.glEnd();
    }
    private static void stroke(EspLayout.Rect r,float radius,float width,boolean corners,float length) {
        // Tessellated strips preserve fractional widths and round joins at every distance.
        for(int corner=0;corner<4;corner++){
            float cx=(corner==0||corner==3)?r.x+radius:r.right()-radius,cy=corner<2?r.y+radius:r.bottom()-radius;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for(int step=0;step<=12;step++){
                double a=Math.toRadians(corner*90+180+step*90.0/12);
                GL11.glVertex2d(cx+Math.cos(a)*(radius+width/2),cy+Math.sin(a)*(radius+width/2));
                GL11.glVertex2d(cx+Math.cos(a)*Math.max(0,radius-width/2),cy+Math.sin(a)*Math.max(0,radius-width/2));
            }GL11.glEnd();
        }
        float horizontal=Math.max(0,r.w-2*radius),vertical=Math.max(0,r.h-2*radius);
        float hx=corners?Math.min(horizontal/2,Math.max(width,length-radius)):horizontal/2;
        float vy=corners?Math.min(vertical/2,Math.max(width,length-radius)):vertical/2;
        for(int side=0;side<2;side++){
            float y=side==0?r.y:r.bottom();
            quad(new EspLayout.Rect(r.x+radius,y-width/2,hx,width));quad(new EspLayout.Rect(r.right()-radius-hx,y-width/2,hx,width));
            float x=side==0?r.x:r.right();
            quad(new EspLayout.Rect(x-width/2,r.y+radius,width,vy));quad(new EspLayout.Rect(x-width/2,r.bottom()-radius-vy,width,vy));
        }
    }
}
