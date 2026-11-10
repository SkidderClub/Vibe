package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.inventory.HotbarSwitches;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.ui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

final class BlockCounterRenderer {
    private final BlockCounterState state=new BlockCounterState();
    private final HudTween amount=new HudTween(),bar=new HudTween(),visibility=new HudTween();
    private Object world;
    BlockCounterRenderer(){visibility.snap(0);}
    void reset(){state.reset();visibility.snap(0);world=null;}
    private boolean enabled(Class<? extends Module> type){Module m=Vibe.getInstance().getModuleManager().getModule(type);return m!=null&&m.isEnabled();}
    void draw(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();OverlaySettings.BlockCounter s=hud.blockCounter;
        if(!preview&&world!=mc.theWorld){reset();world=mc.theWorld;}
        boolean visible=preview||BlockCounterState.allowed(s.scaffold.isEnabled(),s.eagle.isEnabled(),enabled(ScaffoldModule.class),enabled(dev.vibe.module.impl.movement.EagleModule.class));
        long now=System.nanoTime()/1000000;float duration=s.animate.isEnabled()?s.duration.getFloat():0;
        int slot=HotbarSwitches.selectedSlot(),count=preview?180:BlockCounterState.count(mc.thePlayer==null?null:mc.thePlayer.inventory,slot,s.calculate.is("Total"));
        float fraction=preview?.7F:state.fraction(count,visible,s.calculate.getValue());
        float opacity=preview?1:visibility.update(visible?1:0,now,duration,s.easing.getValue());
        int shown=preview?count:Math.max(0,Math.round(amount.update(count,now,duration,s.easing.getValue())));
        float fill=preview?fraction:bar.update(fraction,now,duration,s.easing.getValue());
        String text=shown+(s.label.isEnabled()?shown==1?" block":" blocks":"");
        boolean circle=s.bar.isEnabled()&&s.barMode.is("Circle");
        FontRenderer font=mc.fontRendererObj;int pad=s.compact.isEnabled()?6:10,icon=s.block.isEnabled()?21:0;
        String percent=Math.round(fill*100)+"%";int ring=circle?s.ringSize.getInt()+6:0;
        int textWidth=font.getStringWidth(text),contentWidth=textWidth+icon+ring;
        if(s.percentage.isEnabled()&&!circle)contentWidth+=font.getStringWidth(percent)+7;
        int w=Math.max(s.compact.isEnabled()?0:s.width.getInt(),contentWidth+pad*2);
        int h=Math.max(s.block.isEnabled()?26:22,circle?s.ringSize.getInt()+pad*2:s.bar.isEnabled()?28:22);
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h);e.setBounds(x,y,w,h);
        if(opacity<.02F)return;
        String theme=manager.theme(e,hud);int textColor=s.text.getArgb();
        if(s.warning.isEnabled())textColor=count<=Math.min(s.redAt.getInt(),s.yellowAt.getInt())?s.red.getArgb():count<=Math.max(s.redAt.getInt(),s.yellowAt.getInt())?s.yellow.getArgb():textColor;
        if("LiquidGlass".equals(theme)||"Lavalamp".equals(theme))manager.drawOverlayGlass(hud,e,s,x,y,w,h,opacity);
        else{
            HudShapes.round(x,y,w,h,"Skeet".equals(theme)?1:s.radius.getFloat(),alpha(s.background.getArgb(),opacity));
            if(s.border.isEnabled())RenderUtils.roundedOutline(x,y,x+w,y+h,"Skeet".equals(theme)?1:s.radius.getFloat(),1,alpha(s.accent.getArgb(),.4F*opacity));
            if("Skeet".equals(theme))Gui.drawRect(x+2,y+2,x+w-2,y+3,alpha(s.accent.getArgb(),opacity));
        }
        if(s.block.isEnabled()&&opacity>.35F){
            ItemStack stack=preview?new ItemStack(Blocks.stone):mc.thePlayer==null?null:mc.thePlayer.inventory.mainInventory[slot];
            if(!BlockCounterState.block(stack)&&mc.thePlayer!=null&&s.calculate.is("Total"))for(int i=0;i<9;i++)if(BlockCounterState.block(mc.thePlayer.inventory.mainInventory[i])){stack=mc.thePlayer.inventory.mainInventory[i];break;}
            if(BlockCounterState.block(stack)){
                RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();
                try{mc.getRenderItem().renderItemAndEffectIntoGUI(stack,x+pad,y+(h-16-(s.bar.isEnabled()&&!circle?4:0))/2);}
                finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);}
            }
        }
        int textY=y+(h-8-(s.bar.isEnabled()&&!circle?4:0))/2;
        if((textColor>>>24)*opacity>=4)font.drawString(text,x+pad+icon,textY,alpha(textColor,opacity),s.shadow.isEnabled());
        if(s.bar.isEnabled()){
            int barColor=s.warning.isEnabled()&&count<=Math.max(s.yellowAt.getInt(),s.redAt.getInt())?textColor:s.barColor.getArgb();
            if(circle){float size=s.ringSize.getFloat(),cx=x+w-pad-size/2,cy=y+h/2F;
                HudShapes.arc(cx,cy,size/2-s.barThickness.getFloat()/2,s.barThickness.getFloat(),1,alpha(s.barEmpty.getArgb(),opacity));
                if(s.barGlow.isEnabled()&&HudBarGlow.glass(theme))HudBarGlow.ring(cx,cy,size/2-s.barThickness.getFloat()/2,s.barThickness.getFloat(),fill,alpha(barColor,opacity),s.barGlowStrength.getFloat());
                HudShapes.arc(cx,cy,size/2-s.barThickness.getFloat()/2,s.barThickness.getFloat(),fill,alpha(barColor,opacity));
                if(s.percentage.isEnabled()&&(textColor>>>24)*opacity>=4){
                    float scale=Math.min(.75F,(size-5)/font.getStringWidth(percent));GlStateManager.pushMatrix();GlStateManager.translate(cx,cy,0);GlStateManager.scale(scale,scale,1);
                    font.drawString(percent,-font.getStringWidth(percent)/2F,-4,alpha(textColor,opacity),false);GlStateManager.popMatrix();
                }
            }else{
                float thickness=s.barThickness.getFloat();HudShapes.round(x+pad,y+h-4-thickness,w-pad*2,thickness,thickness/2,alpha(s.barEmpty.getArgb(),opacity));
                if(s.barGlow.isEnabled()&&HudBarGlow.glass(theme))HudBarGlow.line(x+pad,y+h-4-thickness,(w-pad*2)*fill,thickness,alpha(barColor,opacity),s.barGlowStrength.getFloat());
                HudShapes.round(x+pad,y+h-4-thickness,(w-pad*2)*fill,thickness,thickness/2,alpha(barColor,opacity));
                if(s.percentage.isEnabled()&&(textColor>>>24)*opacity>=4)font.drawString(percent,x+w-pad-font.getStringWidth(percent),textY,alpha(textColor,opacity),s.shadow.isEnabled());
            }
        }
    }
    private static int alpha(int c,float a){return Math.round((c>>>24)*a)<<24|c&0xFFFFFF;}
}
