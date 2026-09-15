package dev.vibe.ui;

import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Nested GUI clipping that preserves the enclosing scroll panel. */
final class GuiClip implements AutoCloseable {
    private final boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final IntBuffer previous = BufferUtils.createIntBuffer(16);
    GuiClip(int x, int y, int width, int height) {
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        int left = x * scale, bottom = mc.displayHeight - (y + height) * scale;
        int right = left + Math.max(0, width) * scale, top = bottom + Math.max(0, height) * scale;
        if (enabled) {
            left = Math.max(left, previous.get(0)); bottom = Math.max(bottom, previous.get(1));
            right = Math.min(right, previous.get(0) + previous.get(2)); top = Math.min(top, previous.get(1) + previous.get(3));
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }
    @Override public void close() {
        GL11.glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
        if (!enabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
