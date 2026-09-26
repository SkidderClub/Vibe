/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public final class MovementUtil {
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static boolean isGoingDiagonally() {
        float yaw = (MovementUtil.mc.thePlayer.rotationYaw % 360.0f + 360.0f) % 360.0f;
        float[] diagonals = new float[]{45.0f, 135.0f, 225.0f, 315.0f};
        float threshold = 10.0f;
        for (float diagonal : diagonals) {
            if (!(Math.abs(yaw - diagonal) < threshold) && !(Math.abs(yaw - (diagonal + 360.0f)) < threshold)) continue;
            return true;
        }
        return false;
    }

    public static double getDirectionKeybinds(float yaw) {
        float rotationYaw = yaw;
        if (!Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindForward.getKeyCode()) && Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindBack.getKeyCode())) {
            rotationYaw += 180.0f;
        }
        float forward = 1.0f;
        if (!Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindForward.getKeyCode()) && Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindBack.getKeyCode())) {
            forward = -0.5f;
        } else if (Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindForward.getKeyCode()) && !Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindBack.getKeyCode())) {
            forward = 0.5f;
        }
        if (Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindLeft.getKeyCode()) && !Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindRight.getKeyCode())) {
            rotationYaw -= 90.0f * forward;
        }
        if (!Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindLeft.getKeyCode()) && Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindRight.getKeyCode())) {
            rotationYaw += 90.0f * forward;
        }
        return Math.toRadians(rotationYaw);
    }

    public static boolean isMoving() {
        return Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindForward.getKeyCode()) || Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindBack.getKeyCode()) || Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindRight.getKeyCode()) || Keyboard.isKeyDown(MovementUtil.mc.gameSettings.keyBindLeft.getKeyCode());
    }
}
