package dev.vibe.hud;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.*;

/** Faint glyph halos must survive vanilla's 10% alpha cutoff without leaking render state. */
final class TextGlowScope implements AutoCloseable {
    private static final TextGlowScope NONE=new TextGlowScope();
    private final boolean active,blend;
    private final int function,src,dst,srcAlpha,dstAlpha;
    private final float reference;
    private TextGlowScope(){active=blend=false;function=src=dst=srcAlpha=dstAlpha=0;reference=0;}
    private TextGlowScope(boolean ignored){
        active=true;blend=GL11.glIsEnabled(GL11.GL_BLEND);function=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);reference=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.enableBlend();GL11.glEnable(GL11.GL_BLEND);GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.001F);GL11.glAlphaFunc(GL11.GL_GREATER,.001F);
    }
    static TextGlowScope begin(boolean glow){
        if(!glow)return NONE;
        try{GLContext.getCapabilities();}catch(RuntimeException noContext){return NONE;}
        return new TextGlowScope(true);
    }
    @Override public void close(){if(!active)return;GlStateManager.alphaFunc(function,reference);GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);
        if(blend){GlStateManager.enableBlend();GL11.glEnable(GL11.GL_BLEND);}else{GlStateManager.disableBlend();GL11.glDisable(GL11.GL_BLEND);}}
}
