/*
 * Gothaj RotationComponent, revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Smoothing, noise, normalization and sensitivity snapping are retained.
 * Vibe's MoveFix owns packet/look/model transport and movement yaw.
 */
package dev.vibe.module.impl.scaffold;

import dev.vibe.Vibe;
import dev.vibe.module.impl.MoveFixModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class RotationComponent {
    private static final String OWNER = "scaffold";
    private final Minecraft mc = Minecraft.getMinecraft();
    public float[] rotations, lastRotations, targetRotations, lastServerRotations;
    private double yawSpeed, pitchSpeed;
    private boolean shouldMoveFix;
    public boolean active;

    public void initialize() {
        lastServerRotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        targetRotations = lastServerRotations;
        lastRotations = lastServerRotations;
        rotations = lastServerRotations;
    }

    public void setRotations(float yaw, float pitch, double yawSpeed, double pitchSpeed, boolean shouldMoveFix) {
        if (lastRotations == null) initialize();
        targetRotations = new float[] {yaw, pitch};
        this.yawSpeed = yawSpeed;
        this.pitchSpeed = pitchSpeed;
        this.shouldMoveFix = shouldMoveFix;
        active = true;
        fixRotations();
    }

    public void onUpdate() {
        if (!active || rotations == null || lastRotations == null || targetRotations == null || lastServerRotations == null)
            initialize();
        if (active) fixRotations();
    }

    public void onInput(MoveInputEvent event) {
        if (active && shouldMoveFix && rotations != null && ownsRotation())
            MoveUtil.correctMovement(event, rotations[0]);
    }

    public void onMotion() {
        if (active && rotations != null) {
            rotations[0] = normalize(rotations[0], lastRotations[0]);
            rotations[1] = normalize(rotations[1], lastRotations[1]);
            publish();
            lastServerRotations = new float[] {rotations[0], rotations[1]};
            if (Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw)) < 1.0F
                    && Math.abs(rotations[1] - mc.thePlayer.rotationPitch) < 1.0F) active = false;
            lastRotations = rotations;
        } else lastRotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        targetRotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    }

    public void fixRotations() {
        if (lastRotations == null || targetRotations == null) return;
        float yawChange = 0.0F, pitchChange = 0.0F;
        if (lastRotations[1] != rotations[1]) yawChange = (float) ((Math.random() - Math.random()) / 3.0);
        if (lastRotations[0] != rotations[0]) pitchChange = (float) ((Math.random() - Math.random()) / 3.0);
        float lastYaw = lastRotations[0], lastPitch = lastRotations[1];
        float targetYaw = normalize(targetRotations[0] + yawChange, lastYaw);
        float targetPitch = normalize(targetRotations[1] + pitchChange, lastPitch);
        targetYaw = RotationUtil.smoothRotation(lastYaw, targetYaw, (float) (yawSpeed + (Math.random() - Math.random()) * 3.0));
        targetPitch = RotationUtil.smoothRotation(lastPitch, targetPitch, (float) (pitchSpeed + (Math.random() - Math.random()) * 3.0));
        float[] fixed = RotationUtil.getFixedRotations(new float[] {targetYaw, targetPitch}, new float[] {lastYaw, lastPitch});
        rotations = new float[] {fixed[0], MathHelper.clamp_float(fixed[1], -90.0F, 90.0F)};
        publish();
        if (mc.entityRenderer != null) mc.entityRenderer.getMouseOver(1.0F);
    }

    private static float normalize(float target, float previous) {
        while (target - previous < -180.0F) target += 360.0F;
        while (target - previous >= 180.0F) target -= 360.0F;
        return target;
    }

    private void publish() {
        MoveFixModule moveFix = moveFix();
        if (moveFix != null) moveFix.setScaffoldRotation(rotations[0], rotations[1], shouldMoveFix);
    }

    public boolean ownsRotation() {
        MoveFixModule moveFix = moveFix();
        return moveFix != null && moveFix.isRotationOwner(OWNER);
    }

    public void afterMotion() {
        if (!active) {
            MoveFixModule moveFix = moveFix();
            if (moveFix != null) moveFix.finishScaffoldRotation();
        }
    }

    public void reset() {
        active = false;
        rotations = lastRotations = targetRotations = lastServerRotations = null;
        MoveFixModule moveFix = moveFix();
        if (moveFix != null) moveFix.finishScaffoldRotation();
    }

    private static MoveFixModule moveFix() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(MoveFixModule.class);
    }
}
