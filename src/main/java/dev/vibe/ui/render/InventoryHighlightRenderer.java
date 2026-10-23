package dev.vibe.ui.render;

import dev.vibe.ui.effect.EffectState;
import java.lang.reflect.Field;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/** A fixed-size, antialiased slot outline with a slow breathing glow. */
public final class InventoryHighlightRenderer {
    private static final Field LEFT = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i");
    private static final Field TOP = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r");

    private InventoryHighlightRenderer() { }

    public static void draw(GuiContainer screen, float slotX, float slotY, int color) {
        drawAt(screen, slotX, slotY, color, System.currentTimeMillis());
    }

    public static void drawAt(GuiContainer screen, float slotX, float slotY, int color, long time) {
        float x = left(screen) + slotX, y = top(screen) + slotY;
        float pulse = 0.5F - 0.5F * (float) Math.cos(Math.floorMod(time, 1600L) * (Math.PI * 2.0 / 1600.0));
        float alpha = (color >>> 24) / 255.0F;
        try (EffectState ignored = new EffectState()) {
            // Raw GL inside EffectState keeps Minecraft's state cache untouched.
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            fill(x, y, 8, 2, color, alpha * (0.045F + pulse * 0.075F));
            ring(x, y, 11, 5, 9, 3, color, 0, alpha * (0.06F + pulse * 0.12F));
            float edge = alpha * (0.50F + pulse * 0.48F);
            ring(x, y, 9.6F, 3.6F, 9, 3, color, 0, edge);
            ring(x, y, 9, 3, 8.1F, 2.1F, color, edge, edge);
            ring(x, y, 8.1F, 2.1F, 7.6F, 1.6F, color, edge, 0);
        }
    }

    public static int left(GuiContainer screen) { return coordinate(LEFT, screen); }
    public static int top(GuiContainer screen) { return coordinate(TOP, screen); }

    private static int coordinate(Field field, GuiContainer screen) {
        try { return field.getInt(screen); }
        catch (IllegalAccessException e) { throw new IllegalStateException("Cannot read container position", e); }
    }

    private static void fill(float x, float y, float half, float radius, int color, float alpha) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        tint(color, alpha); GL11.glVertex2f(x, y);
        for (int i = 0; i <= 68; i++) vertex(x, y, half, radius, i);
        GL11.glEnd();
    }

    private static void ring(float x, float y, float outer, float outerRadius, float inner, float innerRadius,
                             int color, float outerAlpha, float innerAlpha) {
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= 68; i++) {
            tint(color, outerAlpha); vertex(x, y, outer, outerRadius, i);
            tint(color, innerAlpha); vertex(x, y, inner, innerRadius, i);
        }
        GL11.glEnd();
    }

    private static void vertex(float x, float y, float half, float radius, int index) {
        int corner = (index % 68) / 17;
        double angle = Math.toRadians(-90 + corner * 90 + (index % 17) * 90.0 / 16);
        float cx = x + (corner == 0 || corner == 1 ? half - radius : radius - half);
        float cy = y + (corner == 0 || corner == 3 ? radius - half : half - radius);
        GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
    }

    private static void tint(int color, float alpha) {
        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
    }
}
