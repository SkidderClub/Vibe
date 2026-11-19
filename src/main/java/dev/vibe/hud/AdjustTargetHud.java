package dev.vibe.hud;
import dev.vibe.ui.adjust.AdjustFonts;
import dev.vibe.ui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import java.util.*;
/** Adjust's 24px card: 16px portrait, Tahoma name, half-size equipment and a 3px trailing bar. */
public final class AdjustTargetHud {
    private final Map<UUID,Health> health=new LinkedHashMap<UUID,Health>(16,.75F,true){protected boolean removeEldestEntry(Map.Entry<UUID,Health> e){return size()>16;}};
    public int width(TargetHudData d){return Math.max(80,(int)Math.ceil(AdjustFonts.HUD.width(d.name)+21));}
    public void draw(TargetHudSettings s,TargetHudData d,int x,int y,int w,long now){
        Health h=health.get(d.id);if(h==null){h=new Health(d.fraction(),now);health.put(d.id,h);}h.update(d.fraction(),now,s.smoothing.isEnabled());
        HudShapes.round(x,y,w,24,0,0xAA131313);
        HudShapes.face(d.skin,x+2,y+2,16,0,s.damageTint.isEnabled()?Math.min(1,d.hurtTicks/21.5F):0);
        AdjustFonts.HUD.draw(d.name,x+19.5F,y+3.5F,0xDCE6E6EC);
        float delta=d.ownHealth-d.total(s.absorption.isEnabled());
        if(Math.abs(delta)>.05){String t=String.format(java.util.Locale.ROOT,"%+.1f",delta);AdjustFonts.HUD_SMALL.draw(t,x+w-2-AdjustFonts.HUD_SMALL.width(t),y+16,0xDCE6E6EC);}
        HudShapes.round(x+1.5F,y+19.5F,w-3,3,0,0x7F27272A);
        HudShapes.round(x+2,y+20,w-4,2,0,0xAA131313);
        dev.vibe.module.impl.client.HudModule hud=dev.vibe.features.FeatureModule.module(dev.vibe.module.impl.client.HudModule.class);
        int c=s.originalColours.isEnabled()&&hud!=null?hud.getAccent().getArgb():s.health.getArgb(),dark=RenderUtils.blend(c,0xFF000000,.37F);
        HudShapes.round(x+2,y+20,(w-4)*h.trail,2,0,dark);
        HudShapes.round(x+2,y+20,(w-4)*h.value,2,0,c);
        if(s.absorption.isEnabled()&&d.absorption>0){int bright=c&0xFF000000|Math.min(255,(int)((c>>16&255)/.45F))<<16|Math.min(255,(int)((c>>8&255)/.45F))<<8|Math.min(255,(int)((c&255)/.45F));HudShapes.round(x+2,y+20,(w-4)*Math.min(1,d.absorption/d.maxHealth),2,0,bright);}
        ItemStack[] items=d.entity==null?new ItemStack[]{new ItemStack(Items.diamond_sword),new ItemStack(Items.diamond_helmet),new ItemStack(Items.diamond_chestplate),new ItemStack(Items.diamond_leggings),new ItemStack(Items.diamond_boots)}:new ItemStack[]{d.entity.getHeldItem(),d.entity.getCurrentArmor(3),d.entity.getCurrentArmor(2),d.entity.getCurrentArmor(1),d.entity.getCurrentArmor(0)};
        float xx=x+20;for(ItemStack item:items)if(item!=null){drawItem(item,xx,y+10);xx+=8;}
    }
    public static void drawItem(ItemStack item,float x,float y){
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(.5F,.5F,1);
        try(dev.vibe.ui.render.HudItemGeometryRenderer.Scope ignored=dev.vibe.ui.render.HudItemGeometryRenderer.begin()){
            dev.vibe.ui.render.HudItemGeometryRenderer.prepareItem();Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(item,0,0);
        }finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);}
    }
    public void clear(){health.clear();}
    private static final class Health{
        float value,trail,start,trailStart,target;long changed;
        Health(float v,long now){value=trail=start=trailStart=target=v;changed=now;}
        void update(float v,long now,boolean smooth){if(v!=target){start=value;trailStart=trail;target=v;changed=now;}float t=Math.min(1,(now-changed)/220F),u=Math.min(1,(now-changed)/1660F);value=smooth?start+(target-start)*(t>=1?1:1-(float)Math.pow(2,-10*t)):v;trail=smooth?trailStart+(target-trailStart)*(u>=1?1:1-(float)Math.pow(2,-10*u)):v;}
    }
}
