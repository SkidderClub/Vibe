/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class MoveUtil {
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static void correctMovement(MoveInputEvent event, float yaw) {
        boolean up = event.isKeyForward();
        boolean down = event.isKeyBack();
        boolean left = event.isKeyLeft();
        boolean right = event.isKeyRight();
        if (MoveUtil.mc.thePlayer != null && (up != down || left != right)) {
            float playerYaw = MoveUtil.mc.thePlayer.rotationYaw;
            float delta = up == down ? (left ? -90.0f : 90.0f) : (up ? (left == right ? 0.0f : (left ? -45.0f : 45.0f)) : (left == right ? 180.0f : (left ? -135.0f : 135.0f)));
            float y = MathHelper.wrapAngleTo180_float(playerYaw + delta - yaw);
            right = false;
            left = false;
            down = false;
            up = false;
            if (y >= -22.5f && y < 22.5f) {
                up = true;
            }
            if (y < -22.5f && y >= -67.5f) {
                left = true;
                up = true;
            }
            if (y < -67.5f && y >= -112.5f) {
                left = true;
            }
            if (y < -112.5f && y >= -157.5f) {
                left = true;
                down = true;
            }
            if (y <= -157.5f || y >= 157.5f) {
                down = true;
            }
            if (y >= 112.5f && y < 157.5f) {
                right = true;
                down = true;
            }
            if (y >= 67.5f && y < 112.5f) {
                right = true;
            }
            if (y >= 22.5f && y < 67.5f) {
                right = true;
                up = true;
            }
        }
        event.setKeyForward(up);
        event.setKeyBack(down);
        event.setKeyLeft(left);
        event.setKeyRight(right);
        event.recomputeFromKeys();
    }
}
