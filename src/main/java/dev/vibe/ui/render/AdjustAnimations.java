package dev.vibe.ui.render;
import dev.vibe.module.impl.visual.AnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
/** Sword BLOCK switch from the supplied OpenAdjust ItemRenderer. */
public final class AdjustAnimations {
    private AdjustAnimations(){}
    public static boolean supports(String mode){return mode!=null&&mode.startsWith("Adjust ");}
    public static boolean transform(AnimationsModule module,float equip,float partial){
        String mode=module.getBlockingStyle().getValue().substring(7);
        float f1=Minecraft.getMinecraft().thePlayer.getSwingProgress(partial),s=MathHelper.sin(MathHelper.sqrt_float(f1)*(float)Math.PI),e=equip*module.adjustEquip.getFloat();
        GlStateManager.translate(module.getOffsetX().getFloat(),module.getOffsetY().getFloat(),module.getOffsetZ().getFloat());
        if(mode.equals("None")){NovolineAnimations.transformFirstPersonItem(e,0);block();return true;}
        switch(mode){
                                    case "Tap":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        GlStateManager.translate(-0.25F, 0.2F, -0.01F);
                                        GL11.glRotated((double)(-s * 42.0F), (double)(s / 1.9F), 0.0D, 9.0D);
                                        GL11.glRotated((double)(-s * 50.0F), (double)0.7F, (double)(s / 1.98F), 0.0D);
                                        block();
                                        break;
                                    case "Bounce":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        block();
                                        GlStateManager.translate(-0.12F, 0.23F, 0.01F);
                                        GlStateManager.rotate(-s * 52.0F, -1.5F, -9.0F, 5.5F);
                                        break;
                                    case "Swank":
                                        NovolineAnimations.transformFirstPersonItem(e / 2.0F, f1);
                                        GlStateManager.rotate(s * 30.0F, -s, -0.0F, 9.0F);
                                        GlStateManager.rotate(s * 40.0F, 1.0F, -s, -0.0F);
                                        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
                                        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                                        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
                                        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                                        break;
                                    case "Punch":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
                                        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                                        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
                                        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                                        GlStateManager.rotate(-s * 30.0F, -5.0F, 0.0F, 9.0F);
                                        GlStateManager.rotate(-s * 10.0F, 1.0F, -0.4F, -0.5F);
                                        break;
                                    case "Dig":
                                        NovolineAnimations.transformFirstPersonItem(e, f1);
                                        GlStateManager.rotate(s * 32.0F, -s, 0.0F, 8.5F);
                                        GlStateManager.rotate(s * 40.0F, 0.5F, -s, 0.0F);
                                        GlStateManager.translate(-0.2F, 0.259F, 0.1F);
                                        block();
                                        break;
                                    case "Horizontal":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        block();
                                        GlStateManager.translate(-0.16F, 0.139F, 0.06F);
                                        GlStateManager.rotate(-s * 32.0F, 84.0F, -0.0F, 133.0F);
                                        break;
                                    case "Shove":
                                        NovolineAnimations.transformFirstPersonItem(e, f1);
                                        GlStateManager.rotate(s * 15.0F, -s, -0.0F, 9.0F);
                                        GlStateManager.rotate(s * 40.0F, 1.0F, -s * 0.5F, -0.0F);
                                        block();
                                        break;
                                    case "Stasis":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        GlStateManager.rotate(-s * 20.0F, s / 2.0F, -0.0F, 9.0F);
                                        GlStateManager.rotate(-s * 30.0F, 1.0F, s / 2.0F, -0.0F);
                                        block();
                                        used(f1);
                                        break;
                                    case "Slide":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        GlStateManager.rotate(-s * 20.0F, 1.0F, 0.0F, 0.0F);
                                        block();
                                        break;
                                    case "Attract":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        GlStateManager.rotate(s * 15.0F, 0.0F, 1.0F, 0.0F);
                                        GlStateManager.rotate(s * 10.0F, 0.0F, 0.0F, 1.0F);
                                        GlStateManager.translate(0.0F, s * 0.1F, 0.0F);
                                        block();
                                        break;
                                    case "Detract":
                                        NovolineAnimations.transformFirstPersonItem(e, 0.0F);
                                        GlStateManager.rotate(-s * 27.5F, -8.0F, -0.0F, 9.0F);
                                        GlStateManager.rotate(-s * 45.0F, 1.0F, s * 0.5F, -0.0F);
                                        block();
                                        break;
                                    case "Vanilla":
                                    default:
                                        NovolineAnimations.transformFirstPersonItem(e, f1);
                                        block();
                                        break;
                                }return true;
    }
    private static void block(){NovolineAnimations.func_178103_d(.2F);}
    private static void used(float swing){GlStateManager.translate(-.4F*MathHelper.sin(MathHelper.sqrt_float(swing)*(float)Math.PI),.2F*MathHelper.sin(MathHelper.sqrt_float(swing)*(float)Math.PI*2),-.2F*MathHelper.sin(swing*(float)Math.PI));}
}
