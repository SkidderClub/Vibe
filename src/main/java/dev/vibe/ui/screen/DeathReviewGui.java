package dev.vibe.ui.screen;
import dev.vibe.module.impl.movement.FreecamModule;
import java.io.IOException;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
/** Render-camera controls only; GuiScreen never forwards ordinary player input. */
public final class DeathReviewGui extends GuiScreen {
    private final FreecamModule camera;
    public DeathReviewGui(FreecamModule camera){this.camera=camera;allowUserInput=false;}
    @Override public void initGui(){buttonList.clear();buttonList.add(new dev.vibe.ui.menu.VibeMenuButton(new GuiButton(1,width/2-154,height-30,150,20,"Respawn")));buttonList.add(new dev.vibe.ui.menu.VibeMenuButton(new GuiButton(2,width/2+4,height-30,150,20,"Back to death screen")));}
    @Override public void drawScreen(int mx,int my,float partial){Gui.drawRect(0,height-62,width,height,0xA0101520);drawCenteredString(fontRendererObj,"Death review",width/2,height-56,0xFFE8F2FF);drawCenteredString(fontRendererObj,"Move with your movement keys. Hold right mouse to look. Use F5 to view the drone.",width/2,height-43,0xFFADBDD1);super.drawScreen(mx,my,partial);}
    @Override protected void actionPerformed(GuiButton button){if(button.id==1){if(mc.thePlayer!=null)mc.thePlayer.respawnPlayer();mc.displayGuiScreen(null);}else mc.displayGuiScreen(new GuiGameOver());}
    @Override public void updateScreen(){if(mc.theWorld==null||mc.thePlayer==null||!mc.thePlayer.isDead){mc.displayGuiScreen(null);return;}camera.tick();}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(new GuiGameOver());else if(key==mc.gameSettings.keyBindTogglePerspective.getKeyCode())camera.cyclePerspective();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){camera.endDeathReview();}
}
