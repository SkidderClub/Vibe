package dev.vibe.hud;

import dev.vibe.input.VanillaClicks;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.*;

/** Shows real bound keys without consuming input or counting synthetic module presses. */
final class KeystrokesRenderer {
    private final float[] press=new float[7];private long previous;
    void draw(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();OverlaySettings.Keystrokes s=hud.keystrokes;GameSettings g=mc.gameSettings;
        int size=s.size.getInt(),gap=s.gap.getInt(),w=size*3+gap*2,rows=2+(s.leftClick.isEnabled()||s.rightClick.isEnabled()?1:0)+(s.space.isEnabled()?1:0),h=rows*size+(rows-1)*gap;
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h);e.setBounds(x,y,w,h);
        long now=System.nanoTime();float dt=previous==0?1:Math.min(.1F,(now-previous)/1e9F);previous=now;
        KeyBinding[] keys={g.keyBindForward,g.keyBindLeft,g.keyBindBack,g.keyBindRight,g.keyBindAttack,g.keyBindUseItem,g.keyBindJump};
        for(int i=0;i<press.length;i++){boolean down=preview?i==0||i==4:mc.currentScreen==null&&(i==4||i==5?org.lwjgl.input.Mouse.isCreated()&&org.lwjgl.input.Mouse.isButtonDown(i-4):VanillaClicks.physicallyDown(keys[i]));float t=s.animation.isEnabled()?1-(float)Math.exp(-dt*s.speed.getFloat()):1;press[i]+=((down?1:0)-press[i])*t;}
        cell(manager,hud,e,s,x+size+gap,y,size,size,keys[0],0,preview);
        for(int i=1;i<=3;i++)cell(manager,hud,e,s,x+(i-1)*(size+gap),y+size+gap,size,size,keys[i],i,preview);
        int yy=y+2*(size+gap);boolean l=s.leftClick.isEnabled(),r=s.rightClick.isEnabled();
        if(l||r){int cw=l&&r?(w-gap)/2:w;if(l)cell(manager,hud,e,s,x,yy,cw,size,keys[4],4,preview);if(r)cell(manager,hud,e,s,l?x+cw+gap:x,yy,l?w-cw-gap:w,size,keys[5],5,preview);yy+=size+gap;}
        if(s.space.isEnabled())cell(manager,hud,e,s,x,yy,w,size,keys[6],6,preview);
    }
    private void cell(HudManager manager,HudModule hud,HudManager.HudElement e,OverlaySettings.Keystrokes s,int x,int y,int w,int h,KeyBinding binding,int index,boolean preview){
        HudOverlayRenderer.panel(manager,hud,e,s,x,y,w,h);float amount=press[index];
        if(amount>.001F){int color=RenderUtils.alpha(s.pressed.getArgb(),Math.round(s.pressed.getAlpha()*amount));HudShapes.round(x+1,y+1,w-2,h-2,Math.min(s.radius.getFloat(),h/2F),color);}
        String label=index==4?"LMB":index==5?"RMB":index==6?"Space":GameSettings.getKeyDisplayString(binding.getKeyCode());
        if(index==6&&binding.getKeyCode()!=org.lwjgl.input.Keyboard.KEY_SPACE)label=GameSettings.getKeyDisplayString(binding.getKeyCode());
        FontRenderer font=Minecraft.getMinecraft().fontRendererObj;float scale=Math.min(s.textScale.getFloat(),(w-6)/(float)Math.max(1,font.getStringWidth(label)));
        GlStateManager.pushMatrix();try{GlStateManager.translate(x+w/2F,y+h/2F,0);GlStateManager.scale(scale,scale,1);font.drawString(label,-font.getStringWidth(label)/2F,-4,RenderUtils.blend(s.text.getArgb(),s.pressedText.getArgb(),amount),s.shadow.isEnabled());}finally{GlStateManager.popMatrix();}
    }
}
