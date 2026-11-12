package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.features.WorldDraw;
import dev.vibe.module.impl.client.NameProtectModule;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.menu.MenuFont;
import dev.vibe.ui.MenuRoundedRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.item.*;
import java.util.UUID;
import org.lwjgl.opengl.GL11;

/** Compact CS notices and Fortnite's text-only elimination feed. */
final class KillFeedRenderer {
    private static final MenuFont FONT=MenuFont.BOLD;
    private KillFeedRenderer(){}
    static boolean involved(SessionJournal.Entry e,UUID id,String name){
        return id!=null&&(id.equals(e.actorId)||id.equals(e.victimId))
                ||name!=null&&!name.isEmpty()&&(e.actorId==null&&name.equalsIgnoreCase(e.actor)||e.victimId==null&&name.equalsIgnoreCase(e.victim));
    }
    static void draw(String theme,DashboardSettings settings,SessionJournal.Entry e,int x,int y,int width,float alpha,boolean preview){
        if("CounterStrike".equals(theme))counterStrike(settings,e,x,y,width,alpha,preview);
        else fortnite(e,x,y,width,alpha);
    }
    private static String protect(String text){NameProtectModule n=Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);return n==null?text:n.protectText(text);}
    private static void ink(String text,float x,float y,int color,float alpha){FONT.draw(text,x+1,y+1,RenderUtils.alpha(0xFF000000,Math.round(190*alpha)));FONT.draw(text,x,y,RenderUtils.alpha(color,Math.round(255*alpha)));}
    private static void counterStrike(DashboardSettings s,SessionJournal.Entry e,int x,int y,int width,float alpha,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();boolean self=preview?involved(e,null,"You"):involved(e,mc.thePlayer==null?null:mc.thePlayer.getUniqueID(),mc.thePlayer==null?mc.getSession().getUsername():mc.thePlayer.getName());
        boolean weapon=e.weapon!=null&&s.weapons.isEnabled()&&(e.weapon.getItem() instanceof ItemSword||e.weapon.getItem() instanceof ItemBow||e.weapon.getItem() instanceof ItemAxe);
        int icon=weapon?27:11,available=width-icon-20;
        String killer=FONT.fit(protect(e.actor),Math.max(0,available*.48F)),victim=FONT.fit(protect(e.victim.isEmpty()?"Unknown":e.victim),Math.max(0,available-FONT.width(killer)));
        int row=Math.min(width,(int)Math.ceil(FONT.width(killer)+FONT.width(victim))+icon+20),left=x+width-row;
        MenuRoundedRenderer.rect(left,y,row,20,2,RenderUtils.alpha(self?0xFFE33239:0xFF080A0C,Math.round((self?255:175)*alpha)));
        MenuRoundedRenderer.rect(left+1,y+1,row-2,18,1,RenderUtils.alpha(0xFF121419,Math.round(205*alpha)));
        float cursor=left+7;
        if(!killer.isEmpty()){ink(killer,cursor,y+4,0xFFE5C479,alpha);cursor+=FONT.width(killer)+5;}
        if(weapon){weapon(e.weapon,cursor,y+5,alpha);cursor+=18;}
        skull(cursor,y+7,alpha);cursor+=11;
        ink(victim,cursor,y+4,0xFF98BDDE,alpha);
        if(!e.confirmed)ink("?",left-8,y+4,0xFFB4BBC6,alpha);
    }
    private static void fortnite(SessionJournal.Entry e,int x,int y,int width,float alpha){
        String actor=protect(e.actor),victim=protect(e.victim.isEmpty()?"Unknown":e.victim);
        String verb=actor.isEmpty()?" was eliminated":e.confirmed?" eliminated ":" may have eliminated ";
        String suffix=actor.isEmpty()?"":weaponName(e.weapon);
        float fixed=FONT.width(verb+suffix),budget=Math.max(0,width-fixed-2);
        actor=FONT.fit(actor,actor.isEmpty()?0:budget*.48F);victim=FONT.fit(victim,Math.max(0,budget-FONT.width(actor)));
        float at=x;
        if(e.actor.isEmpty()){ink(victim,at,y+2,0xFFFF6D71,alpha);at+=FONT.width(victim);ink(verb,at,y+2,0xFFF2F3F5,alpha);return;}
        ink(actor,at,y+2,0xFFF1D878,alpha);at+=FONT.width(actor);ink(verb,at,y+2,0xFFF2F3F5,alpha);at+=FONT.width(verb);
        ink(victim,at,y+2,0xFFFF7379,alpha);at+=FONT.width(victim);ink(suffix,at,y+2,0xFFF2F3F5,alpha);
    }
    static String weaponName(ItemStack stack){if(stack==null)return "";Item item=stack.getItem();return item instanceof ItemSword?" with a sword":item instanceof ItemBow?" with a bow":item instanceof ItemAxe?" with an axe":"";}
    private static void skull(float x,float y,float alpha){boolean clipped=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);try(EffectState state=new EffectState()){if(clipped)GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);WorldDraw.color(RenderUtils.alpha(0xFFF5F5F5,Math.round(255*alpha)));GL11.glBegin(GL11.GL_QUADS);quad(x,y,7,5);quad(x+2,y+5,3,2);GL11.glEnd();WorldDraw.color(RenderUtils.alpha(0xFF121419,Math.round(255*alpha)));GL11.glBegin(GL11.GL_QUADS);quad(x+1,y+2,2,2);quad(x+4,y+2,2,2);GL11.glEnd();}}
    private static void weapon(ItemStack stack,float x,float y,float alpha){boolean clipped=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);try(EffectState state=new EffectState()){if(clipped)GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(2);WorldDraw.color(RenderUtils.alpha(0xFFF4F4F4,Math.round(255*alpha)));GL11.glBegin(GL11.GL_LINES);
        if(stack.getItem() instanceof ItemBow){for(int i=0;i<12;i++){double a=-Math.PI/2+i*Math.PI/12,b=a+Math.PI/12;GL11.glVertex2d(x+4+Math.cos(a)*7,y+5+Math.sin(a)*5);GL11.glVertex2d(x+4+Math.cos(b)*7,y+5+Math.sin(b)*5);}GL11.glVertex2f(x+4,y);GL11.glVertex2f(x+4,y+10);}
        else{GL11.glVertex2f(x+2,y+10);GL11.glVertex2f(x+12,y);GL11.glVertex2f(x+2,y+4);GL11.glVertex2f(x+8,y+10);}GL11.glEnd();}}
    private static void quad(float x,float y,float w,float h){GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);}
}
