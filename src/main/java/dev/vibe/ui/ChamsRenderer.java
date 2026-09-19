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
    private static boolean directPreview;
    private static int previewDepth;
    static void beginPreview(){previewDepth++;}
    static void endPreview(){previewDepth=Math.max(0,previewDepth-1);if(previewDepth==0)ChamsGlow.finish();}

    private ChamsRenderer() { }

    public static void beginWorld() { worldDepth++; }
    public static void endWorld() { worldDepth = Math.max(0, worldDepth - 1); if(worldDepth==0)ChamsGlow.finish(); }

    private static EspModule module(Object candidate) {
        if (directPreview || worldDepth == 0 || !(candidate instanceof EntityLivingBase)) return null;
        Vibe vibe = Vibe.getInstance();
        Minecraft mc = Minecraft.getMinecraft();
        if (vibe == null || vibe.getModuleManager() == null || mc == null
                || mc.theWorld == null || mc.thePlayer == null) return null;
        EspModule esp = vibe.getModuleManager().getModule(EspModule.class);
        dev.vibe.module.impl.HypixelModule hypixel = vibe.getModuleManager().getModule(dev.vibe.module.impl.HypixelModule.class);
        if (hypixel != null && hypixel.suppressVisuals()) return null;
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
        EntityLivingBase living=(EntityLivingBase)entity;
        draw(esp,armor,opacity,()->model.render(entity,swing,amount,age,yaw,pitch,scale),false,
                esp.resolvedProfile(esp.profileFor(living)),esp.teamColor(living),living.hurtTime>0,-1,overrideColor(living));
    }

    /** Shared with the offscreen driver check; geometry already has vanilla's transforms and pose. */
    static void draw(EspModule esp, boolean armor, float opacity, Runnable geometry) {
        draw(esp, armor, opacity, geometry, false);
    }

    /** GTA7 meshes supply their surface colors as vertex colors instead of a skin texture. */
    public static void drawNative(EspModule esp, Runnable geometry) { draw(esp, false, 1, geometry, true); }

    private static void draw(EspModule esp, boolean armor, float opacity, Runnable geometry, boolean nativeMesh) {
        draw(esp,armor,opacity,geometry,nativeMesh,0,0,false,-1,0);
    }

    static void preview(EspModule esp,int profile,boolean occluded,int team,boolean hurt,boolean armor,Runnable geometry) {
        boolean previous=directPreview;directPreview=true;
        try { draw(esp,armor,1,geometry,false,esp.resolvedProfile(profile),team,hurt,occluded?0:1,0); }
        finally {directPreview=previous;}
    }

    private static void draw(EspModule esp, boolean armor, float opacity, Runnable geometry, boolean nativeMesh,
                             int profile,int team,boolean hurt,int previewSide,int overrideColor) {
        try (EffectState state = new EffectState()) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            if(nativeMesh) GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            boolean shader = prepareMaterial();
            if (shader) {
                material.bind();
                material.integer("Skin", 0);
                material.integer("NativeMesh", nativeMesh ? 1 : 0);
                material.integer("MaskPass",0);
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
            EspModule.ChamsSettings hidden=esp.getChams(profile,false),visible=esp.getChams(profile,true);
            if(previewSide!=1)pass(hidden,armor,opacity,previewSide==0?GL11.GL_ALWAYS:GL11.GL_GREATER,false,shader,geometry,team,hurt,overrideColor);
            // Write the visible model back to the depth buffer. Cape and other
            // vanilla player layers are rendered afterwards and must still be
            // occluded by the body rather than painting through its front.
            if(previewSide!=0)pass(visible,armor,opacity,previewSide==1?GL11.GL_ALWAYS:GL11.GL_LEQUAL,true,shader,geometry,team,hurt,overrideColor);
            if(shader&&((previewSide!=1&&hidden.getMode().is("Glow"))||(previewSide!=0&&visible.getMode().is("Glow")))) {
                ChamsGlow.capture(()->{
                    if(nativeMesh)GL11.glDisable(GL11.GL_CULL_FACE);
                    material.integer("MaskPass",1);
                    try {
                        if(previewSide!=1&&hidden.getMode().is("Glow"))pass(hidden,armor,opacity,previewSide==0?GL11.GL_ALWAYS:GL11.GL_GREATER,false,true,geometry,team,hurt,overrideColor);
                        if(previewSide!=0&&visible.getMode().is("Glow"))pass(visible,armor,opacity,previewSide==1?GL11.GL_ALWAYS:GL11.GL_LEQUAL,false,true,geometry,team,hurt,overrideColor);
                    } finally {material.integer("MaskPass",0);}
                });
            }
            if(worldDepth==0&&previewDepth==0)ChamsGlow.finish();
        } finally {
            // Models may use GlStateManager.color internally; force its next caller to resync.
            GlStateManager.resetColor();
        }
    }

    private static void pass(EspModule.ChamsSettings settings, boolean armor, float opacity,
                             int depth, boolean writeDepth, boolean shader, Runnable geometry,int team,boolean hurt,int overrideColor) {
        int color=settings.getColor().resolve(team,hurt);
        if(overrideColor!=0)color=(color&0xFF000000)|(overrideColor&0x00FFFFFF);
        float r=(color>>16&255)/255F,g=(color>>8&255)/255F,b=(color&255)/255F;
        float alpha = (color>>>24) / 255.0F * Math.max(0, Math.min(1, opacity));
        if (alpha <= 0 || (armor && !settings.getArmor().isEnabled())) return;
        GL11.glDepthFunc(depth);
        GL11.glDepthMask(writeDepth);
        if (shader) {
            material.vec3("Tint",r,g,b);
            material.scalar("Opacity", alpha);
            material.integer("ShowSkin", settings.getShowSkin().isEnabled() ? 1 : 0);
            material.integer("Material", settings.getMode().is("Glow") ? 1 : settings.getMode().is("Metallic") ? 2 : 0);
        } else {
            if (settings.getShowSkin().isEnabled()) GL11.glEnable(GL11.GL_TEXTURE_2D);
            else GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(r,g,b,alpha);
        }
        geometry.run();
    }

    /** Murder Mystery colours are an explicit ESP override, including Chams. */
    private static int overrideColor(EntityLivingBase entity) {
        Vibe vibe=Vibe.getInstance();
        if(vibe==null||vibe.getModuleManager()==null)return 0;
        dev.vibe.module.impl.HypixelModule hypixel=vibe.getModuleManager().getModule(dev.vibe.module.impl.HypixelModule.class);
        return hypixel==null?0:hypixel.visualColor(entity);
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
