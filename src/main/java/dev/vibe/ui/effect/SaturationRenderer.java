package dev.vibe.ui.effect;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;

/**
 * A dedicated colour-convolve pass, kept separate from Minecraft's active
 * shader group just as RavenBS keeps its saturation shader on EntityRenderer.
 */
public final class SaturationRenderer {
    private static final ResourceLocation SHADER_LOCATION = new ResourceLocation("minecraft:shaders/post/color_convolve.json");
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();
    private static ShaderGroup shader;
    private static Field shadersField;
    private static boolean shadersFieldResolved;
    private static int width = -1;
    private static int height = -1;
    private static float applied = Float.NaN;

    private SaturationRenderer() { }

    public static void update(boolean enabled, float saturation) {
        if (!enabled) {
            remove();
            return;
        }
        if (MINECRAFT.theWorld == null || MINECRAFT.entityRenderer == null || !OpenGlHelper.shadersSupported) return;
        if (shader == null && !create()) return;
        resizeIfNeeded();
        if (Float.compare(applied, saturation) != 0) {
            applied = saturation;
            applySaturation();
        }
    }

    /** Called by the EntityRenderer transformer after its outline framebuffer. */
    public static void render(float partialTicks) {
        if (shader == null || !OpenGlHelper.shadersSupported) return;
        resizeIfNeeded();
        GlStateManager.matrixMode(5890);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        shader.loadShaderGroup(partialTicks);
        GlStateManager.popMatrix();
    }

    public static boolean shaderActiveHook(boolean vanilla) {
        return vanilla || shader != null && OpenGlHelper.shadersSupported;
    }

    public static Object shaderGroupHook(Object vanilla) {
        return vanilla == null && shader != null && OpenGlHelper.shadersSupported ? shader : vanilla;
    }

    public static void resizeHook(int newWidth, int newHeight) {
        if (shader != null) {
            width = newWidth;
            height = newHeight;
            shader.createBindFramebuffers(width, height);
        }
    }

    private static boolean create() {
        try {
            shader = new ShaderGroup(MINECRAFT.getTextureManager(), MINECRAFT.getResourceManager(),
                    MINECRAFT.getFramebuffer(), SHADER_LOCATION);
            width = MINECRAFT.displayWidth;
            height = MINECRAFT.displayHeight;
            shader.createBindFramebuffers(width, height);
            applied = Float.NaN;
            return true;
        } catch (IOException failure) {
            failure.printStackTrace();
            shader = null;
            return false;
        }
    }

    private static void resizeIfNeeded() {
        if (shader == null || width == MINECRAFT.displayWidth && height == MINECRAFT.displayHeight) return;
        width = MINECRAFT.displayWidth;
        height = MINECRAFT.displayHeight;
        shader.createBindFramebuffers(width, height);
    }

    private static void applySaturation() {
        for (Shader entry : shaders()) {
            ShaderUniform uniform = entry.getShaderManager().getShaderUniform("Saturation");
            if (uniform != null) uniform.set(applied);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Shader> shaders() {
        if (shader == null) return Collections.emptyList();
        if (!shadersFieldResolved) {
            shadersFieldResolved = true;
            for (String name : new String[] {"listShaders", "field_148031_d"}) {
                try {
                    shadersField = ShaderGroup.class.getDeclaredField(name);
                    shadersField.setAccessible(true);
                    break;
                } catch (Exception ignored) { }
            }
        }
        try {
            Object value = shadersField == null ? null : shadersField.get(shader);
            return value instanceof List ? (List<Shader>) value : Collections.<Shader>emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static void remove() {
        if (shader != null) shader.deleteShaderGroup();
        shader = null;
        width = height = -1;
        applied = Float.NaN;
    }
}
