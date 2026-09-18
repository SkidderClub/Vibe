package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.CustomCrosshairModule;
import java.awt.Color;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Draws a state-safe, high-customisation HUD crosshair. */
public final class CustomCrosshairRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private Field blockDamage;

    public void render() {
        CustomCrosshairModule module = Vibe.getInstance().getModuleManager().getModule(CustomCrosshairModule.class);
        if (module == null || !module.isEnabled() || minecraft.gameSettings.thirdPersonView != 0) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        float movement = minecraft.thePlayer == null ? 0 : (float)Math.hypot(minecraft.thePlayer.motionX, minecraft.thePlayer.motionZ) * 9;
        renderAt(module, resolution.getScaledWidth(), resolution.getScaledHeight(), movement, true);
    }

    /** GTA7 supplies its own dimensions and movement; Minecraft's camera mode does not apply. */
    public boolean renderGta7(int width, int height, float movement) {
        CustomCrosshairModule module = Vibe.getInstance().getModuleManager().getModule(CustomCrosshairModule.class);
        if (module == null || !module.isEnabled()) return false;
        renderAt(module, width, height, movement, false);
        return true;
    }

    private void renderAt(CustomCrosshairModule module, int width, int height, float movement, boolean minecraftWorld) {
        int x = width / 2 + module.getHorizontalOffset().getInt();
        int y = height / 2 + module.getVerticalOffset().getInt();
        int color = color(module);
        if (minecraftWorld && DebugOverlay.isActive() && overlapsDebugText(module, x, y, movement)) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            if (module.getRotate().isEnabled()) {
                GL11.glTranslatef(x, y, 0.0F);
                GL11.glRotatef((System.currentTimeMillis() % 10000L) * 0.036F * module.getRotationSpeed().getFloat(), 0.0F, 0.0F, 1.0F);
                GL11.glTranslatef(-x, -y, 0.0F);
            }
            if (module.getStyle().is("Dot")) {
                dot(x, y, 1, color, module);
            } else if (module.getStyle().is("Circle")) {
                circle(x, y, module.getCircleRadius().getFloat(), module.getCircleSegments().getInt(), color, module);
            } else {
                float dynamic = 0.0F;
                if (module.getStyle().is("Dynamic") && module.getMovementGap().isEnabled()) {
                    dynamic = Math.min(module.getMovementGapAmount().getFloat(),
                            Math.max(0, movement) * module.getMovementGapAmount().getFloat());
                }
                cross(x, y, module.getGap().getInt() + Math.round(dynamic), module.getLength().getInt(),
                        module.getThickness().getInt(), color, module);
                if (module.getStyle().is("Cross Dot") || module.getCenterDot().isEnabled()) {
                    dot(x, y, 1, color, module);
                }
            }
            if (minecraftWorld && module.getBreakCircle().isEnabled()) {
                drawBreakCircle(x, y, module);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
        }
    }

    private boolean overlapsDebugText(CustomCrosshairModule module, int x, int y, float movement) {
        float outline = module.getOutline().isEnabled() ? module.getOutlineSize().getFloat() : 0;
        float extent;
        if (module.getStyle().is("Circle")) {
            extent = module.getCircleRadius().getFloat() + module.getThickness().getFloat() / 2 + outline;
        } else if (module.getStyle().is("Dot")) {
            extent = (1 + 1) / 2 + outline;
            if (module.getRotate().isEnabled()) extent *= Math.sqrt(2);
        } else {
            float dynamic = module.getStyle().is("Dynamic") && module.getMovementGap().isEnabled()
                    ? Math.min(module.getMovementGapAmount().getFloat(), Math.max(0, movement) * module.getMovementGapAmount().getFloat()) : 0;
            float length = module.getGap().getInt() + Math.round(dynamic) + module.getLength().getInt() + 1 + outline;
            float halfWidth = (module.getThickness().getInt() + 1) / 2 + outline;
            extent = Math.max(length, halfWidth);
            if (module.getRotate().isEnabled()) {
                double angle = Math.toRadians((System.currentTimeMillis() % 10000L) * 0.036F * module.getRotationSpeed().getFloat());
                float sin = (float) Math.abs(Math.sin(angle)), cos = (float) Math.abs(Math.cos(angle));
                extent = Math.max(length * cos + halfWidth * sin, length * sin + halfWidth * cos);
            }
            if (module.getStyle().is("Cross Dot") || module.getCenterDot().isEnabled()) {
                float dot = (1 + 1) / 2 + outline;
                if (module.getRotate().isEnabled()) dot *= Math.sqrt(2);
                extent = Math.max(extent, dot);
            }
        }
        if (module.getBreakCircle().isEnabled() && breakProgress() > 0) {
            extent = Math.max(extent, module.getBreakCircleRadius().getFloat() + module.getBreakCircleWidth().getFloat() / 2);
        }
        return DebugOverlay.overlaps(x - extent, y - extent, x + extent, y + extent);
    }

    private void cross(int x, int y, int gap, int length, int thickness, int color, CustomCrosshairModule module) {
        arm(x - gap - length, y - thickness / 2, x - gap, y + (thickness + 1) / 2, color, module);
        arm(x + gap + 1, y - thickness / 2, x + gap + length + 1, y + (thickness + 1) / 2, color, module);
        arm(x - thickness / 2, y - gap - length, x + (thickness + 1) / 2, y - gap, color, module);
        arm(x - thickness / 2, y + gap + 1, x + (thickness + 1) / 2, y + gap + length + 1, color, module);
    }

    private void dot(int x, int y, int size, int color, CustomCrosshairModule module) {
        int side = Math.max(1, size);
        // Use one shared integer anchor for both axes.  This avoids the old
        // radius rounding that shifted a multi-pixel centre dot by one pixel
        // relative to the cross arms at larger GUI scales.
        int left = x - side / 2;
        int top = y - side / 2;
        arm(left, top, left + side, top + side, color, module);
    }

    private void arm(int left, int top, int right, int bottom, int color, CustomCrosshairModule module) {
        if (module.getOutline().isEnabled()) {
            int outline = module.getOutlineSize().getInt();
            Gui.drawRect(left - outline, top - outline, right + outline, bottom + outline,
                    RenderUtils.alpha(module.getOutlineColor().getArgb(), effectiveAlpha(module.getOutlineColor().getArgb(), module)));
        }
        Gui.drawRect(left, top, right, bottom, color);
    }

    private void circle(int x, int y, float radius, int segments, int color, CustomCrosshairModule module) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        if (module.getOutline().isEnabled()) {
            WorldRenderUtils.color(RenderUtils.alpha(module.getOutlineColor().getArgb(), effectiveAlpha(module.getOutlineColor().getArgb(), module)));
            GL11.glLineWidth(module.getThickness().getFloat() + module.getOutlineSize().getFloat() * 2.0F);
            circleVertices(x, y, radius, segments);
        }
        WorldRenderUtils.color(color);
        GL11.glLineWidth(module.getThickness().getFloat());
        circleVertices(x, y, radius, segments);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void circleVertices(int x, int y, float radius, int segments) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int step = 0; step < segments; step++) {
            double angle = Math.PI * 2.0D * step / segments;
            GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    private int color(CustomCrosshairModule module) {
        int alpha = effectiveAlpha(module.getColor().getArgb(), module);
        if (!module.getRainbow().isEnabled()) {
            return RenderUtils.alpha(module.getColor().getArgb(), alpha);
        }
        float hue = (float) ((System.currentTimeMillis() % 12000L) / 12000.0D * module.getRainbowSpeed().getDouble()) % 1.0F;
        return RenderUtils.alpha(Color.HSBtoRGB(hue, 0.82F, 1.0F), alpha);
    }

    private int effectiveAlpha(int configuredColor, CustomCrosshairModule module) {
        return Math.round(((configuredColor >>> 24) & 255) * module.getOpacity().getInt() / 255.0F);
    }

    private void drawBreakCircle(int x, int y, CustomCrosshairModule module) {
        float progress = breakProgress();
        if (progress <= 0.0F) return;
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(module.getBreakCircleWidth().getFloat());
        WorldRenderUtils.color(module.getBreakCircleColor().getArgb());
        GL11.glBegin(GL11.GL_LINE_STRIP);
        int totalSteps = 96;
        int steps = Math.max(1, Math.round(totalSteps * progress));
        for (int index = 0; index <= steps; index++) {
            double angle = -Math.PI * 0.5D + Math.PI * 2.0D * index / totalSteps;
            GL11.glVertex2d(x + Math.cos(angle) * module.getBreakCircleRadius().getFloat(),
                    y + Math.sin(angle) * module.getBreakCircleRadius().getFloat());
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private float breakProgress() {
        try {
            if (blockDamage == null) {
                for (String name : new String[] {"curBlockDamageMP", "field_78770_f"}) try {
                    blockDamage = minecraft.playerController.getClass().getDeclaredField(name);
                    blockDamage.setAccessible(true);
                    break;
                } catch (Exception ignored) {
                }
            }
            return blockDamage == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, blockDamage.getFloat(minecraft.playerController)));
        } catch (Exception ignored) {
            return 0.0F;
        }
    }
}
