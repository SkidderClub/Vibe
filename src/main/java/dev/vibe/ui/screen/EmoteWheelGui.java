package dev.vibe.ui.screen;

import dev.vibe.module.impl.meme.EmoteWheelModule;
import dev.vibe.features.EmotePoses;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.lwjgl.opengl.GL11;
import java.io.IOException;

/** Three large radial targets, plus a live preview using the player's own skin. */
public final class EmoteWheelGui extends GuiScreen {
    private final EmoteWheelModule module;private final String[] names={"Dab","Sit","Stop"};
    public EmoteWheelGui(EmoteWheelModule m){module=m;}
    private int choice(int mx,int my){double x=mx-width/2.,y=my-height/2.;if(x*x+y*y<30*30||x*x+y*y>115*115)return -1;double angle=Math.atan2(y,x)+Math.PI/2;if(angle<0)angle+=Math.PI*2;return (int)(angle/(Math.PI*2/3));}
    @Override public void drawScreen(int mx,int my,float partial){drawRect(0,0,width,height,0x66070A10);int cx=width/2,cy=height/2,selected=choice(mx,my);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        for(int sector=0;sector<3;sector++){int c=sector==selected?0xDC596DD2:0xE51A1D24;dev.vibe.features.WorldDraw.color(c);GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=0;i<=32;i++){double a=-Math.PI/2+(sector+(i/32.))*(Math.PI*2/3)+.025;GL11.glVertex2d(cx+Math.cos(a)*38,cy+Math.sin(a)*38);GL11.glVertex2d(cx+Math.cos(a)*108,cy+Math.sin(a)*108);}GL11.glEnd();}GL11.glPopAttrib();
        for(int i=0;i<3;i++){double a=-Math.PI/2+(i+.5)*Math.PI*2/3;drawCenteredString(fontRendererObj,names[i],cx+(int)(Math.cos(a)*73),cy+(int)(Math.sin(a)*73)-4,0xFFF2F3F7);}
        drawCenteredString(fontRendererObj,"Emotes",cx,cy-135,0xFFFFFFFF);drawCenteredString(fontRendererObj,"Choose an emote",cx,cy+123,0xFFB6BAC5);
        if(mc.thePlayer!=null){EmotePoses.preview=selected<0?module.selected():names[selected];try{GuiInventory.drawEntityOnScreen(Math.max(40,cx-170),cy+65,48,0,0,mc.thePlayer);}finally{EmotePoses.preview=null;}}
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(button==0){int i=choice(x,y);if(i>=0){module.select(names[i]);mc.displayGuiScreen(null);}}}
    @Override public boolean doesGuiPauseGame(){return false;}
}
