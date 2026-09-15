package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.EspModule;
import dev.vibe.module.impl.QolModule;
import dev.vibe.module.impl.TargetsModule;
import dev.vibe.setting.ColorSetting;
import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.*;

/** Two complementary depth passes replace the original model, so color alpha remains meaningful. */
public final class ChamsRenderer {
    private static int worldDepth;
    private static EffectProgram material;
    private static boolean shaderFailed;

    private ChamsRenderer() { }

    public static void beginWorld() { worldDepth++; }
    public static void endWorld() { worldDepth = Math.max(0, worldDepth - 1); }

    private static EspModule module(Object candidate) {
        if (worldDepth == 0 || !(candidate instanceof EntityLivingBase)) return null;
        Vibe vibe = Vibe.getInstance();
        Minecraft mc = Minecraft.getMinecraft();
        if (vibe == null || vibe.getModuleManager() == null || mc == null
                || mc.theWorld == null || mc.thePlayer == null) return null;
        EspModule esp = vibe.getModuleManager().getModule(EspModule.class);
        if (esp == null || !esp.isEnabled() || !esp.getModes().isSelected("Chams")) return null;
        EntityLivingBase entity = (EntityLivingBase) candidate;
        if (entity.worldObj != mc.theWorld || !entity.isEntityAlive() || entity.isDead) return null;
        if (entity == mc.thePlayer) return mc.gameSettings.thirdPersonView != 0 ? esp : null;
        TargetsModule targets = vibe.getModuleManager().getModule(TargetsModule.class);
        return (targets != null && targets.canVisualize(entity)) || antiInvisible(entity) != null ? esp : null;
    }

    public static boolean appliesTo(Object entity) { return module(entity) != null; }

    private static QolModule antiInvisible(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer) || !entity.isInvisible()) return null;
        QolModule qol = Vibe.getInstance().getModuleManager().getModule(QolModule.class);
        return qol != null && qol.isEnabled() && qol.getFeatures().isSelected("AntiInvisibility") ? qol : null;
    }

    public static void renderBody(Object model, Object entity, float swing, float amount, float age,
                                  float yaw, float pitch, float scale) {
        render((ModelBase) model, (Entity) entity, swing, amount, age, yaw, pitch, scale, false);
    }

    public static void renderArmor(Object model, Object entity, float swing, float amount, float age,
                                   float yaw, float pitch, float scale) {
        render((ModelBase) model, (Entity) entity, swing, amount, age, yaw, pitch, scale, true);
    }

    public static int armorColor(Object armor, Object stack, Object entity) {
        return appliesTo(entity) ? -1 : ((ItemArmor) armor).getColor((ItemStack) stack);
    }

    public static boolean armorHasEffect(Object stack, Object entity) {
        return !appliesTo(entity) && ((ItemStack) stack).hasEffect();
    }

    private static void render(ModelBase model, Entity entity, float swing, float amount, float age,
                               float yaw, float pitch, float scale, boolean armor) {
        EspModule esp = module(entity);
        if (esp == null) {
            model.render(entity, swing, amount, age, yaw, pitch, scale);
            return;
        }
        QolModule qol = antiInvisible((EntityLivingBase) entity);
        float opacity = qol == null ? 1 : qol.getInvisibleAlpha().getFloat() / 255.0F;
        draw(esp, armor, opacity, () -> model.render(entity, swing, amount, age, yaw, pitch, scale));
    }

    /** Shared with the offscreen driver check; geometry already has vanilla's transforms and pose. */
    static void draw(EspModule esp, boolean armor, float opacity, Runnable geometry) {
        try (EffectState state = new EffectState()) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            boolean shader = prepareMaterial();
            if (shader) {
                material.bind();
                material.integer("Skin", 0);
            } else {
                GL20.glUseProgram(0);
                // Full-bright fallback must not inherit lightmap or hurt-flash combiners.
                for (int unit = 1; unit <= 2; unit++) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                }
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            }
            pass(esp.getInvisibleChams(), armor, opacity, GL11.GL_GREATER, shader, geometry);
            pass(esp.getVisibleChams(), armor, opacity, GL11.GL_LEQUAL, shader, geometry);
        } finally {
            // Models may use GlStateManager.color internally; force its next caller to resync.
            GlStateManager.resetColor();
        }
    }

    private static void pass(EspModule.ChamsSettings settings, boolean armor, float opacity,
                             int depth, boolean shader, Runnable geometry) {
        ColorSetting color = settings.getColor();
        float alpha = color.getAlpha() / 255.0F * Math.max(0, Math.min(1, opacity));
        if (alpha <= 0 || (armor && !settings.getArmor().isEnabled())) return;
        GL11.glDepthFunc(depth);
        if (shader) {
            material.vec3("Tint", color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
            material.scalar("Opacity", alpha);
            material.integer("ShowSkin", settings.getShowSkin().isEnabled() ? 1 : 0);
            material.integer("Material", settings.getMode().is("Glow") ? 1 : settings.getMode().is("Metallic") ? 2 : 0);
        } else {
            if (settings.getShowSkin().isEnabled()) GL11.glEnable(GL11.GL_TEXTURE_2D);
            else GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, alpha);
        }
        geometry.run();
    }

    private static boolean prepareMaterial() {
        if (material == null && !shaderFailed) {
            try { material = new EffectProgram("Chams.vert", "Chams.frag"); }
            catch (Exception failure) {
                shaderFailed = true;
                LogManager.getLogger("Vibe").warn("Chams shader unavailable; using Flat rendering", failure);
            }
        }
        return material != null;
    }

    static boolean hasShaderFailed() { return shaderFailed; }
}
