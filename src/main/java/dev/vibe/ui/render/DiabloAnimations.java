package dev.vibe.ui.render;

import dev.vibe.module.impl.visual.AnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** The Diablo and Exhi cases from Diablo 2.1's ItemRenderer, at their source defaults. */
public final class DiabloAnimations {
    private DiabloAnimations(){}
    public static boolean supports(String mode){return "Diablo".equals(mode)||"ExihiDiablo".equals(mode);}
    public static boolean transform(AnimationsModule module,float partial){
        float swing=Minecraft.getMinecraft().thePlayer.getSwingProgress(partial);
        GlStateManager.translate(module.getOffsetX().getFloat(),module.getOffsetY().getFloat(),module.getOffsetZ().getFloat());
        if(module.getBlockingStyle().is("Diablo")){
            GL11.glTranslated(-.1,.4,0);NovolineAnimations.transformFirstPersonItem(0,0);
            float v=MathHelper.sin(MathHelper.sqrt_float(swing)*1.9415927F);
            GlStateManager.translate(.35F,-.15F,.15F);
            GlStateManager.rotate(-v*62/2F,5,v,20);GlStateManager.rotate(-v*80,10,-v,0);
        }else{
            float v=MathHelper.sin((float)(MathHelper.sqrt_float(swing)*3.1D));
            GL11.glTranslated(-.1,0,0);NovolineAnimations.transformFirstPersonItem(-.45F,0);
            GlStateManager.rotate(-v*60/2F,v/2F,0,4);GlStateManager.rotate(-v*30,1,v/2F,0);
        }
        NovolineAnimations.func_178103_d(.2F);return true;
    }
}
