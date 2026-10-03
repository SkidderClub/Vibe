package dev.vibe.ui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Nested GUI clipping that preserves the enclosing scroll panel. */
public final class GuiClip implements AutoCloseable {
    private final boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final IntBuffer previous = BufferUtils.createIntBuffer(16);
    public GuiClip(int x, int y, int width, int height) {
        this((float) x, (float) y, (float) (x + Math.max(0, width)), (float) (y + Math.max(0, height)));
    }
    private GuiClip(float leftEdge, float topEdge, float rightEdge, float bottomEdge) {
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        int left = Math.round(leftEdge * scale), right = Math.round(rightEdge * scale);
        int bottom = mc.displayHeight - Math.round(bottomEdge * scale), top = mc.displayHeight - Math.round(topEdge * scale);
        if (enabled) {
            left = Math.max(left, previous.get(0)); bottom = Math.max(bottom, previous.get(1));
            right = Math.min(right, previous.get(0) + previous.get(2)); top = Math.min(top, previous.get(1) + previous.get(3));
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }
    /**
     * Clips a rectangle given in the current model-view space, so widgets drawn inside a translated or scaled
     * GUI (such as the HUD editor's preview) clip exactly where they draw.
     */
    public static GuiClip local(float x, float y, float width, float height) {
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float scaleX = matrix.get(0), scaleY = matrix.get(5), offsetX = matrix.get(12), offsetY = matrix.get(13);
        float left = offsetX + x * scaleX, right = offsetX + (x + Math.max(0, width)) * scaleX;
        float top = offsetY + y * scaleY, bottom = offsetY + (y + Math.max(0, height)) * scaleY;
        return new GuiClip(Math.min(left, right), Math.min(top, bottom), Math.max(left, right), Math.max(top, bottom));
    }
    @Override public void close() {
        GL11.glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
        if (!enabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
