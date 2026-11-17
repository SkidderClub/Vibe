package dev.vibe.ui.render;

import dev.vibe.module.impl.visual.AnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Blocking switch and transforms ported from the supplied Novoline ItemRenderer, without rewriting vanilla item rendering. */
public final class NovolineAnimations {
    public static final String[] MODES={"Swank","Swing","Swang","Swong","Swaing","Punch","Stella","Styles","Slide","Interia","Ethereal","1.7","Sigma","Exhibition","Smooth","Spinning"};
    private static AnimationsModule module;
    private static float partialTicks, height;
    private static int i;
    private NovolineAnimations(){}
    public static boolean supports(String mode){return java.util.Arrays.asList(MODES).contains(mode)||DiabloAnimations.supports(mode);}
    public static void frame(AnimationsModule selected){
        i++;
        if(selected!=null&&selected.isEnabled()){
            float target=Minecraft.getMinecraft().thePlayer!=null&&Minecraft.getMinecraft().thePlayer.getHeldItem()!=null?10:20;
            height+=Math.signum(target-height);
        }
    }
    public static void begin(AnimationsModule selected,float partial){module=selected;partialTicks=partial;}
    public static void end(){module=null;}
    public static boolean active(){return module!=null;}
    public static boolean transform(float equip,float ignoredSwing){
        if(module==null)return false;
        if(DiabloAnimations.supports(module.getBlockingStyle().getValue()))return DiabloAnimations.transform(module,partialTicks);
        float f=equip-.2F+height/100F;
        float f1=Minecraft.getMinecraft().thePlayer.getSwingProgress(partialTicks);
        float var9=MathHelper.sin(MathHelper.sqrt_float(f1)*(float)Math.PI);
        GlStateManager.translate(module.getOffsetX().getFloat(),module.getOffsetY().getFloat(),module.getOffsetZ().getFloat());
                            switch (module.getBlockingStyle().getValue()) {
                                case "Punch":
                                    transformFirstPersonItem(f, 0.0f);
                                    func_178103_d(0.2F);
                                    GlStateManager.translate(0.1f, 0.2f, 0.3f);
                                    GlStateManager.rotate(-var9 * 30.0f, -5.0f, 0.0f, 9.0f);
                                    GlStateManager.rotate(-var9 * 10.0f, 1.0f, -0.4f, -0.5f);
                                    break;
                                case "Stella":
                                    transformFirstPersonItem(-0.1f, f1);
                                    GlStateManager.translate(-0.5F, 0.4F, -0.2F);
                                    GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                                    GlStateManager.rotate(-70.0F, 1.0F, 0.0F, 0.0F);
                                    GlStateManager.rotate(40.0F, 0.0F, 1.0F, 0.0F);
                                    break;
                                case "1.7":
                                    transformFirstPersonItem(-0.1f, f1);
                                    func_178103_d(0.2F);
                                    break;
                                case "Styles":
                                    transformFirstPersonItem(f, 0.0F);
                                    func_178103_d(0.2F);
                                    float var11 = MathHelper.sin(MathHelper.sqrt_float(f1) * (float)Math.PI);
                                    GlStateManager.translate(-0.05f, 0.2f, 0.0f);
                                    GlStateManager.rotate(-var11 * 70.0f / 2.0f, -8.0f, -0.0f, 9.0f);
                                    GlStateManager.rotate(-var11 * 70.0f, 1.0f, -0.4f, -0.0f);
                                    break;
                                case "Swank":
                                    transformFirstPersonItem(f / 2.0F, f1);
                                    float var151 = MathHelper.sin(MathHelper.sqrt_float(f1) * (float)Math.PI);
                                    GlStateManager.rotate(var151 * 30.0F, -var151, -0.0F, 9.0F);
                                    GlStateManager.rotate(var151 * 40.0F, 1.0F, -var151, -0.0F);
                                    func_178103_d(0.4F);
                                    break;
                                case "Swang":
                                    transformFirstPersonItem(f / 2.0F, f1);
                                    float var152 = MathHelper.sin(MathHelper.sqrt_float(f1) * (float)Math.PI);
                                    GlStateManager.rotate(var152 * 30.0F / 2.0F, -var152, -0.0F, 9.0F);
                                    GlStateManager.rotate(var152 * 40.0F, 1.0F, -var152 / 2.0F, -0.0F);
                                    func_178103_d(0.4F);
                                    break;
                                case "Swong":
                                    transformFirstPersonItem(f / 2.0F, 0.0F);
                                    float var153 = MathHelper.sin(f1 * f1 * (float)Math.PI);
                                    GlStateManager.rotate(-var153 * 40.0F / 2.0F, var153 / 2.0F, -0.0F, 9.0F);
                                    GlStateManager.rotate(-var153 * 30.0F, 1.0F, var153 / 2.0F, -0.0F);
                                    func_178103_d(0.4F);
                                    break;
                                case "Swaing":
                                    transformFirstPersonItem(f / 2.0F, -0.2F);
                                    float var154 = MathHelper.sin(f1 * f1 * (float)Math.PI);
                                    GlStateManager.rotate(-var154 / 19.0F, var154 / 20.0F, -0.0F, 9.0F);
                                    GlStateManager.rotate(-var154 * 30.0F, 10.0F, var154 / 50.0F, 0.0F);
                                    func_178103_d(0.4F);
                                    break;
                                case "Swing":
                                    transformFirstPersonItem(f / 2.0F, f1);
                                    func_178103_d(0.4F);
                                    break;
                                case "Smooth":
                                    transformFirstPersonItem(f / 1.5F, 0.0f);
                                    func_178103_d(0.2F);
                                    GlStateManager.translate(-0.05f, 0.3f, 0.3f);
                                    GlStateManager.rotate(-var9 * 140.0f, 8.0f, 0.0f, 8.0f);
                                    GlStateManager.rotate(var9 * 90.0f, 8.0f, 0.0f, 8.0f);
                                    break;
                                case "Sigma":
                                    transformFirstPersonItem(f * 0.5f, 0);
                                    GlStateManager.rotate(-var9 * 55 / 2.0F, -8.0F, -0.0F, 9.0F);
                                    GlStateManager.rotate(-var9 * 45, 1.0F, var9 / 2, -0.0F);
                                    func_178103_d(0.2F);
                                    GL11.glTranslated(1.2, 0.3, 0.5);
                                    GL11.glTranslatef(-1, Minecraft.getMinecraft().thePlayer.isSneaking() ? -0.1F : -0.2F, 0.2F);
                                    break;
                                case "Slide":
                                    transformFirstPersonItem(f, 0.0f);
                                    func_178103_d(0.2F);
                                    GlStateManager.translate(-0.4f, 0.3f, 0.0f);
                                    GlStateManager.rotate(-var9 * 35.0f, -8.0f, -0.0f, 9.0f);
                                    GlStateManager.rotate(-var9 * 70.0f, 1.0f, -0.4f, -0.0f);
                                    GL11.glTranslatef(-0.05f, Minecraft.getMinecraft().thePlayer.isSneaking() ? -0.2f : 0.0f, 0.1f);
                                    break;
                                case "Interia":
                                    transformFirstPersonItem(0.05f, f1);
                                    GlStateManager.translate(-0.5F, 0.5F, 0.0F);
                                    GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                                    GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
                                    GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                                    break;
                                case "Ethereal":
                                    transformFirstPersonItem(f, 0.0f);
                                    func_178103_d(0.2F);
                                    GlStateManager.translate(-0.05f, 0.2f, 0.2f);
                                    GlStateManager.rotate(-var9 * 70.0f / 2.0f, -8.0f, -0.0f, 9.0f);
                                    GlStateManager.rotate(-var9 * 70.0f, 1.0f, -0.4f, -0.0f);
                                    break;
                                case "Exhibition":
                                    GL11.glTranslated(-0.04D, 0.13D, 0.0D);
                                    transformFirstPersonItem(f / 2.5F, 0.0f);
                                    GlStateManager.rotate(-var9 * 40.0F / 2.0F, var9 / 2.0F, 1.0F, 4.0F);
                                    GlStateManager.rotate(-var9 * 30.0F, 1.0F, var9 / 3.0F, -0.0F);
                                    func_178103_d(0.2F);
                                    break;
                                case "Spinning":
                                    GL11.glTranslated(-0.04D, 0.1, 0.0D);
                                    transformFirstPersonItem(f / 2.5F, 0.0f);
                                    GlStateManager.rotate(-90, 1, 0, 0.2f);
                                    GlStateManager.rotate(i, 0, -1, 0);
                                    break;
                            }

        return true;
    }
    static void transformFirstPersonItem(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swingProgress * swingProgress * (float)Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float)Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    static void func_178103_d(float qq) {
        GlStateManager.translate(-0.5F, qq, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

}
