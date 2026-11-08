package dev.vibe.hud;

import dev.vibe.module.impl.client.HudModule;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.*;
import net.minecraft.util.*;

/** Each potion keeps its own full-duration baseline; refreshed effects refill their timer bar. */
final class EffectsRenderer {
    private final Map<Integer,Integer> durations=new HashMap<>();
    private Object player;
    private static final ResourceLocation ICONS=new ResourceLocation("textures/gui/container/inventory.png");
    void clear(){durations.clear();player=null;}
    void draw(HudManager manager,HudModule hud,HudManager.HudElement element,ScaledResolution res,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();OverlaySettings.Effects s=hud.effects;
        if(player!=mc.thePlayer){clear();player=mc.thePlayer;}
        List<PotionEffect> effects=new ArrayList<>();if(!preview&&mc.thePlayer!=null)effects.addAll(mc.thePlayer.getActivePotionEffects());
        if(preview){effects.add(new PotionEffect(Potion.moveSpeed.id,1240,1));effects.add(new PotionEffect(Potion.damageBoost.id,830,0));effects.add(new PotionEffect(Potion.regeneration.id,240,0));}
        Set<Integer> active=new HashSet<>();if(!preview)for(PotionEffect effect:effects){active.add(effect.getPotionID());int duration=effect.getDuration();durations.put(effect.getPotionID(),Math.max(1,Math.max(duration,durations.getOrDefault(effect.getPotionID(),0))));}
        if(!preview)durations.keySet().retainAll(active);
        if(s.sort.is("Time"))effects.sort(Comparator.comparingInt(PotionEffect::getDuration));
        else if(s.sort.is("Name"))effects.sort(Comparator.comparing(e->I18n.format(e.getEffectName())));
        int body=s.names.isEnabled()&&s.timers.isEnabled()?17:8,barSpace=s.bar.isEnabled()?(int)Math.ceil(s.barHeight.getDouble())+5:0;
        int w=s.width.getInt(),row=Math.max(s.rowHeight.getInt(),Math.max(s.icons.isEnabled()?24:0,body+barSpace+6)),gap=s.gap.getInt(),count=Math.min(effects.size(),Math.max(1,(int)((res.getScaledHeight()-16)/element.getScale())/(row+gap)));
        int h=count==0?0:count*(row+gap)-gap;
        element.ensureOnScreen(res,w,Math.max(1,h));int x=element.left(res,w),y=element.top(res,h);element.setBounds(x,y,w,h);
        boolean upwards=element.getY()>res.getScaledHeight()/2;
        for(int i=0;i<count;i++){
            PotionEffect effect=effects.get(i);Potion potion=Potion.potionTypes[effect.getPotionID()];if(potion==null)continue;
            int yy=y+(upwards?count-1-i:i)*(row+gap);HudOverlayRenderer.panel(manager,hud,element,s,x,yy,w,row);
            int textLeft=x+7,color=s.potionColors.isEnabled()?0xFF000000|potion.getLiquidColor():s.accent.getArgb();
            if(s.icons.isEnabled()&&potion.hasStatusIcon()){
                int icon=potion.getStatusIconIndex();mc.getTextureManager().bindTexture(ICONS);GlStateManager.color(1,1,1,1);
                Gui.drawModalRectWithCustomSizedTexture(x+7,yy+(row-18)/2,icon%8*18,198+icon/8*18,18,18,256,256);textLeft+=25;
            }
            String name=I18n.format(effect.getEffectName());if(s.amplifier.isEnabled()&&effect.getAmplifier()>0)name+=" "+level(effect.getAmplifier()+1);
            String timer=effect.getIsPotionDurationMax()?"∞":Potion.getDurationString(effect);
            int textY=yy+(row-barSpace-body)/2;
            if(s.names.isEnabled())mc.fontRendererObj.drawString(mc.fontRendererObj.trimStringToWidth(name,w-(textLeft-x)-7),textLeft,textY,s.text.getArgb(),s.shadow.isEnabled());
            if(s.timers.isEnabled())mc.fontRendererObj.drawString(timer,textLeft,textY+(s.names.isEnabled()?10:0),color,s.shadow.isEnabled());
            if(s.bar.isEnabled()){
                float fraction=effect.getIsPotionDurationMax()?1:Math.min(1,effect.getDuration()/(float)(preview?1600:durations.get(effect.getPotionID())));
                float height=s.barHeight.getFloat();HudShapes.round(x+5,yy+row-height-2,w-10,height,height/2,0x483B4758);
                HudShapes.round(x+5,yy+row-height-2,(w-10)*fraction,height,height/2,color);
            }
        }
    }
    private static String level(int value){String[] roman={"","I","II","III","IV","V","VI","VII","VIII","IX","X"};return value<roman.length?roman[value]:String.valueOf(value);}
}
