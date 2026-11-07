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
        FontRenderer font=mc.fontRendererObj;int icon=s.block.isEnabled()?24:0,w=Math.max(s.width.getInt(),font.getStringWidth(text)+20+icon),h=s.bar.isEnabled()?35:29;
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
                try{mc.getRenderItem().renderItemAndEffectIntoGUI(stack,x+7,y+6);}
                finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);}
            }
        }
        if((textColor>>>24)*opacity>=4)font.drawString(text,x+icon+(w-icon-font.getStringWidth(text))/2F,y+(s.bar.isEnabled()?12:10),alpha(textColor,opacity),s.shadow.isEnabled());
        if(s.bar.isEnabled()){
            HudShapes.round(x+8,y+h-7,w-16,2,1,alpha(s.barEmpty.getArgb(),opacity));
            HudShapes.round(x+8,y+h-7,(w-16)*fill,2,1,alpha(s.warning.isEnabled()?textColor:s.accent.getArgb(),opacity));
        }
    }
    private static int alpha(int c,float a){return Math.round((c>>>24)*a)<<24|c&0xFFFFFF;}
}
