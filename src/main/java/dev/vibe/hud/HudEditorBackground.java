package dev.vibe.hud;

import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.effect.SceneTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Captured after vanilla's UI and before Vibe's widgets, avoiding old widget positions in the canvas. */
public final class HudEditorBackground implements AutoCloseable {
    private final SceneTexture scene=new SceneTexture();
    public void capture(Minecraft mc) {
        if(mc.theWorld==null||mc.displayWidth<=0||mc.displayHeight<=0)return;
        try(EffectState ignored=new EffectState()) {scene.capture(0,0,mc.displayWidth,mc.displayHeight,false);}
    }
    public void draw(int width,int height) {
        if(scene.color!=0) {
            try(EffectState ignored=new EffectState()) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,scene.color);GL11.glColor4f(1,1,1,1);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0,1);GL11.glVertex2f(0,0);
                GL11.glTexCoord2f(0,0);GL11.glVertex2f(0,height);
                GL11.glTexCoord2f(1,0);GL11.glVertex2f(width,height);
                GL11.glTexCoord2f(1,1);GL11.glVertex2f(width,0);
                GL11.glEnd();
            }
        } else {
            // Minecraft's own panorama provides a world image in the main menu.
            Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation("textures/gui/title/background/panorama_0.png"));
            GlStateManager.color(1,1,1,1);GlStateManager.enableTexture2D();
            float side=Math.max(width,height),v=(side-height)/2/side*256;
            Gui.drawScaledCustomSizeModalRect(0,0,0,v,256,Math.round(height/side*256),width,height,256,256);
        }
    }
    public boolean hasGameView() {return scene.color!=0;}
    public void close() {scene.close();}
}
