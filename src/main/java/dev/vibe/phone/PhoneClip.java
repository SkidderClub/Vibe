package dev.vibe.phone;

import java.nio.DoubleBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** A local rectangle that follows the phone's transforms, including its tilt and app animation. */
final class PhoneClip implements AutoCloseable {
    private final boolean[] enabled = new boolean[4];
    private final DoubleBuffer[] previous = new DoubleBuffer[4];

    PhoneClip(float x, float y, float width, float height) {
        double[][] planes = {{1, 0, 0, -x}, {-1, 0, 0, x + width}, {0, 1, 0, -y}, {0, -1, 0, y + height}};
        for (int i = 0; i < 4; i++) {
            int plane = GL11.GL_CLIP_PLANE0 + i;
            enabled[i] = GL11.glIsEnabled(plane);
            previous[i] = BufferUtils.createDoubleBuffer(4); GL11.glGetClipPlane(plane, previous[i]);
            DoubleBuffer equation = BufferUtils.createDoubleBuffer(4); equation.put(planes[i]).flip();
            GL11.glClipPlane(plane, equation); GL11.glEnable(plane);
        }
    }

    @Override public void close() {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            // glGetClipPlane returns eye-space equations; restore with an identity model view.
            for (int i = 0; i < 4; i++) {
                int plane = GL11.GL_CLIP_PLANE0 + i;
                GL11.glClipPlane(plane, previous[i]);
                if (enabled[i]) GL11.glEnable(plane); else GL11.glDisable(plane);
            }
        } finally { GL11.glPopMatrix(); GL11.glMatrixMode(mode); }
    }
}
