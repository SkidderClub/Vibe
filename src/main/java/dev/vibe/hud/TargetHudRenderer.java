package dev.vibe.hud;

import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.RenderUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

public final class TargetHudRenderer {
    private final NeverLoseFont regular=new NeverLoseFont(java.awt.Font.PLAIN,20);
    private final NeverLoseFont bold=new NeverLoseFont(java.awt.Font.BOLD,21);
    private final List<Particle> sparks=new ArrayList<Particle>();
    private UUID targetId;
    private float displayed,previousHealth;
    private int previousHurt;
    private long lastFrame,lastDemoBurst=-1;

    public int height(TargetHudSettings s) { return s.mode.is("Zeroday")?76:s.mode.is("LiquidGlass")?66:s.mode.is("Rise")?46:42; }
    public int width(TargetHudSettings s,TargetHudData d,FontRenderer font) {
        return Math.max(s.mode.is("Zeroday")?184:130,s.width.getInt());
    }
    public void draw(TargetHudSettings s,TargetHudData d,FontRenderer font,int x,int y,int w,Runnable glass,boolean preview,long now) {
        int h=height(s);
        if(!d.id.equals(targetId)) {
            targetId=d.id;displayed=d.fraction();previousHealth=d.health;previousHurt=d.hurtTicks;
            lastFrame=now;sparks.clear();lastDemoBurst=-1;
        }
        float dt=Math.max(0,Math.min(.1F,(now-lastFrame)/1000F));lastFrame=now;
        displayed=s.smoothing.isEnabled()?displayed+(d.fraction()-displayed)*(1-(float)Math.exp(-dt*12)):d.fraction();
        boolean hit=d.health<previousHealth-.01F||d.hurtTicks>previousHurt;
        if(preview&&now/1800!=lastDemoBurst) {hit=true;lastDemoBurst=now/1800;}
        if(!s.mode.is("Rise")||!s.particles.isEnabled())sparks.clear();
        else if(hit)burst(s,now);
        previousHealth=d.health;previousHurt=d.hurtTicks;
        int color=s.health.getArgb(),end=s.gradient.isEnabled()?s.healthEnd.getArgb():color;
        float hurt=s.damageTint.isEnabled()?Math.min(1,d.hurtTicks/10F):0;
        if(s.mode.is("LiquidGlass"))glass.run();
        else HudShapes.round(x,y,w,h,s.mode.is("Novoline")||s.mode.is("Zeroday")?0:s.radius.getFloat(),s.background.getArgb());
        if(s.mode.is("Novoline")) {
            HudShapes.face(d.skin,x+3,y+3,36,0,hurt);
            font.drawStringWithShadow(font.trimStringToWidth(d.name,w-48),x+44,y+5,s.text.getArgb());
            Gui.drawRect(x+44,y+20,x+w-7,y+33,0x60202025);
            Gui.drawRect(x+44,y+20,x+44+Math.round((w-51)*displayed),y+33,color);
            String pct=percent(d);font.drawStringWithShadow(pct,x+44+(w-51-font.getStringWidth(pct))/2F,y+23,s.text.getArgb());
        } else if(s.mode.is("Tenacity")) {
            HudShapes.face(d.skin,x+5,y+5,32,5,hurt);
            bold.draw(bold.fit(d.name,w-49),x+44,y+4,s.text.getArgb());
            String pct=d.fraction()>=1?"100%":percent(d);regular.draw(pct,x+w-9-regular.width(pct),y+18,s.text.getArgb());
            bar(s,d,x+44,y+29,w-53,4,color,end);
        } else if(s.mode.is("Rise")) {
            HudShapes.face(d.skin,x+7,y+8,30,7,hurt);
            String title=d.verdict(s.absorption.isEnabled())+": "+d.name;
            regular.draw(regular.fit(title,w-51),x+45,y+8,s.text.getArgb());
            String hp=decimal(d.total(s.absorption.isEnabled()));
            float bw=w-58-regular.width(hp);
            bar(s,d,x+45,y+29,bw,5,color,end);
            bold.draw(hp,x+w-8-bold.width(hp),y+23,end);
            drawParticles(s,x+22,y+23,now);
        } else if(s.mode.is("Zeroday")) {
            model(d,x+22,y+65,31,hurt);
            regular.draw(regular.fit(d.name,w-90),x+47,y+5,s.text.getArgb());
            if(s.ping.isEnabled())small(font,d.ping<0?"— ms":d.ping+" ms",x+w-7,y+8,0xFFB7C5D5,true);
            gauge(font,x+59,y+34,d.fraction(),d.total(s.absorption.isEnabled()),color,false,false);
            gauge(font,x+84,y+34,Math.min(1,d.hurtTicks/10F),d.hurtTicks,0xFFEC777E,true,false);
            gauge(font,x+109,y+34,Math.min(1,d.angle/360F),d.angle,0xFF9ACFE5,false,true);
            regular.draw(d.verdict(s.absorption.isEnabled()),x+47,y+53,s.text.getArgb());
            HudShapes.round(x,y+h-2,w*displayed,2,0,color);
        } else {
            HudShapes.face(d.skin,x+10,y+11,39,10,hurt);
            small(font,"Target",x+59,y+9,0xFFCADAE9,false);
            bold.draw(bold.fit(d.name,w-72),x+59,y+17,s.text.getArgb());
            if(s.ping.isEnabled())small(font,d.ping<0?"— ms":d.ping+" ms",x+w-9,y+10,0xFFCADAE9,true);
            bar(s,d,x+59,y+37,w-70,5,color,end);
            regular.draw(decimal(d.total(s.absorption.isEnabled()))+" hp",x+59,y+46,s.text.getArgb());
            String state=d.verdict(s.absorption.isEnabled());small(font,state,x+w-9,y+49,0xFFCADAE9,true);
        }
        GlStateManager.color(1,1,1,1);
    }
    private void bar(TargetHudSettings s,TargetHudData d,float x,float y,float w,float h,int a,int b) {
        HudShapes.round(x,y,w,h,h/2,0x66222732);
        float filled=Math.max(0,Math.min(w,w*displayed));
        HudShapes.gradient(x,y,filled,h,h/2,a,b);
        if(s.absorption.isEnabled()&&d.absorption>0) {
            float extra=Math.min(w-filled,w*d.absorption/d.maxHealth);
            HudShapes.round(x+filled,y,extra,h,h/2,0xFFF0CD70);
        }
    }
    private void gauge(FontRenderer f,int x,int y,float ratio,float value,int color,boolean decimal,boolean angle) {
        HudShapes.arc(x,y,9,1.25F,1,0x553F5366);HudShapes.arc(x,y,9,1.25F,ratio,color);
        String label=decimal?decimal(value):Integer.toString(Math.round(value))+(angle?"°":"");
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(.72F,.72F,1);
        f.drawString(label,-f.getStringWidth(label)/2F,-f.FONT_HEIGHT/2F,0xFFF4F5FA,false);GlStateManager.popMatrix();
    }
    private static void small(FontRenderer f,String text,int x,int y,int color,boolean right) {
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(.75F,.75F,1);
        f.drawString(text,right?-f.getStringWidth(text):0,0,color,false);GlStateManager.popMatrix();
    }
    private static void model(TargetHudData d,int x,int bottom,int size,float hurt) {
        if(d.entity!=null) {
            net.minecraft.client.renderer.entity.RenderManager manager=Minecraft.getMinecraft().getRenderManager();
            float oldY=manager.playerViewY,oldX=manager.playerViewX;boolean shadow=manager.isRenderShadow();
            try { net.minecraft.client.gui.inventory.GuiInventory.drawEntityOnScreen(x,bottom,size,0,0,d.entity); }
            finally {
                manager.setPlayerViewY(oldY);manager.playerViewX=oldX;manager.setRenderShadow(shadow);
                dev.vibe.ui.GuiRenderState.prepare(false);
            }
        } else {
            HudShapes.face(d.skin,x-10,bottom-58,20,0,hurt);
            HudShapes.round(x-11,bottom-37,22,23,2,0xFF47A8A6);
            HudShapes.round(x-11,bottom-14,9,14,1,0xFF515E8D);
            HudShapes.round(x+2,bottom-14,9,14,1,0xFF515E8D);
            HudShapes.round(x-17,bottom-36,6,24,2,0xFFC59475);
            HudShapes.round(x+11,bottom-36,6,24,2,0xFFC59475);
        }
    }
    private void burst(TargetHudSettings s,long now) {
        Random random=new Random(now^targetId.getLeastSignificantBits());
        while(sparks.size()>160)sparks.remove(0);
        for(int i=0;i<s.particleCount.getInt();i++) {
            double angle=random.nextDouble()*Math.PI*2;float speed=17+random.nextFloat()*38;
            sparks.add(new Particle(now,(float)Math.cos(angle)*speed,(float)Math.sin(angle)*speed,s.particleSize.getFloat()*(.65F+random.nextFloat()*.65F)));
        }
    }
    private void drawParticles(TargetHudSettings s,float x,float y,long now) {
        Iterator<Particle> iterator=sparks.iterator();
        while(iterator.hasNext()) {
            Particle p=iterator.next();float life=(now-p.born)/s.particleLifetime.getFloat();
            if(life>=1||life<0) {iterator.remove();continue;}
            float age=(now-p.born)/1000F,fade=(1-life)*(1-life);
            float px=x+p.vx*age,py=y+p.vy*age+9*age*age,size=p.size*(1-life*.5F);
            HudShapes.round(px-size/2,py-size/2,size,size,size/2,
                    RenderUtils.alpha(s.particleColor.getArgb(),Math.round((s.particleColor.getArgb()>>>24)*fade)));
        }
    }
    public int particleCount() { return sparks.size(); }
    public void clear() {sparks.clear();targetId=null;lastFrame=0;}
    public void close() {clear();regular.close();bold.close();}
    private static String percent(TargetHudData d) {return String.format(Locale.ROOT,"%.1f%%",d.fraction()*100);}
    private static String decimal(float n) {return String.format(Locale.ROOT,"%.1f",n);}
    private static final class Particle {
        final long born;final float vx,vy,size;
        Particle(long born,float vx,float vy,float size) {this.born=born;this.vx=vx;this.vy=vy;this.size=size;}
    }
}
