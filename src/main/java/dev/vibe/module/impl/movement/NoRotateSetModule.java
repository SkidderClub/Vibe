package dev.vibe.module.impl.movement;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Restores the local camera after vanilla has handled and acknowledged a server correction. */
public final class NoRotateSetModule extends FeatureModule {
    private static final ThreadLocal<Camera> CAMERA = new ThreadLocal<>();

    public NoRotateSetModule() {
        super("NoRotateSet", "Keep your camera direction when the server moves you", Category.MOVEMENT);
    }

    public static void capture() {
        CAMERA.remove();
        NoRotateSetModule module = module(NoRotateSetModule.class);
        Minecraft mc = Minecraft.getMinecraft();
        if (module != null && module.isEnabled() && mc != null && mc.thePlayer != null) {
            CAMERA.set(new Camera(mc.thePlayer));
        }
    }

    public static void restore() {
        Camera camera = CAMERA.get();
        CAMERA.remove();
        if (camera == null || Minecraft.getMinecraft().thePlayer != camera.player) return;
        camera.player.rotationYaw = camera.yaw;
        camera.player.rotationPitch = camera.pitch;
        camera.player.prevRotationYaw = camera.previousYaw;
        camera.player.prevRotationPitch = camera.previousPitch;
    }

    private static final class Camera {
        final EntityPlayerSP player;
        final float yaw, pitch, previousYaw, previousPitch;

        Camera(EntityPlayerSP player) {
            this.player = player;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
            previousYaw = player.prevRotationYaw;
            previousPitch = player.prevRotationPitch;
        }
    }
}
