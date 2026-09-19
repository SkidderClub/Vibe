package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.combat.RotationMath;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.RangeSetting;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/**
 * Tarasande-style fake-rotation transport and movement correction.
 *
 * <p>The fake rotation is applied to the local player only while vanilla
 * builds its walking packet, then immediately restored.  Consequently vanilla
 * updates its last-reported yaw/pitch from the same values the server receives,
 * without changing the first-person camera.  Silent input correction is the
 * same nine-candidate selection used by Tarasande's {@code Silent} component.</p>
 */
public final class MoveFixModule extends Module {

    private final ModeSetting correctMovement = addSetting(new ModeSetting("Correct Movement", "Off",
            "Off", "Prevent Backwards Sprinting", "Direct", "Silent"));
    private final BooleanSetting adjustThirdPersonModel = addSetting(new BooleanSetting("Adjust Third Person Model", true));
    private final BooleanSetting raycast = addSetting(new BooleanSetting("Raycast", false));
    private final RangeSetting rotateBackSpeed = addSetting(new RangeSetting("Rotate Back Speed", 8.0D, 14.0D,
            0.0D, 180.0D, 0.25D));

    private volatile FakeRotation fakeRotation;
    private FakeRotation previousRenderRotation;
    private volatile String rotationOwner;
    // A pathing module can supply a movement vector in server-rotation space.
    // This is deliberately separate from the user's movement policy: a bot
    // must still walk toward its target when MoveFix is configured as Off.
    private volatile String forcedMovementOwner;
    private volatile float forcedForward, forcedStrafe;
    private volatile boolean forcedJump;
    private final Random rotationRandom = new Random();
    private EntityPlayerSP hookedPlayer;
    private EntityPlayerSP rotationPlayer;
    private float transportYaw;
    private boolean transported;
    private EntityPlayerSP packetPlayer;
    private float packetYaw;
    private float packetPitch;
    private EntityPlayerSP renderedPlayer;
    private float renderedYaw;
    private float renderedPreviousYaw;
    private float renderedPitch;
    private float renderedPreviousPitch;
    private float renderedHeadYaw;
    private float renderedPreviousHeadYaw;
    private float renderedBodyYaw;
    private float renderedPreviousBodyYaw;

    private static Field packetYawField;
    private static Field packetPitchField;
    private static Field packetRotatingField;
    private static boolean packetFieldsResolved;

    public MoveFixModule() {
        super("MoveFix", "Corrects movement for fake server rotations", Category.MOVEMENT, Keyboard.KEY_NONE);
        // Tarasande's rotation service is a core feature, not an optional
        // toggle; the module exposes its movement policy while the service
        // remains available to every rotation producer.
        setEnabled(true);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    /** Installs the input hook before vanilla consumes keyboard state. */
    public void installInputHook() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || player == hookedPlayer) {
            return;
        }
        player.movementInput = new MoveFixInput(player.movementInput, this);
        hookedPlayer = player;
    }

    /** Supplies a fake rotation from one producer, such as the Test module. */
    public void setFakeRotation(String owner, float yaw, float pitch) {
        if (owner == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        float continuousYaw = RotationMath.nearest(yaw, getRotationYaw());
        rotationOwner = owner;
        fakeRotation = new FakeRotation(continuousYaw, RotationMath.clamp(pitch, -90.0F, 90.0F));
    }

    /** Snapshot once before any producer changes this tick's rotation. */
    public void beginRotationTick() {
        // A cancelled Forge render may have skipped its Post event.
        if (renderedPlayer != null) endPlayerRender(renderedPlayer);
        syncRotationPlayer();
        previousRenderRotation = activeRotation();
    }

    private void syncRotationPlayer() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (rotationPlayer != player) {
            rotationPlayer = player;
            fakeRotation = null;
            previousRenderRotation = null;
            rotationOwner = null;
            transported = false;
        }
    }

    /** Producers resume an in-progress return instead of snapping to the camera. */
    public float getRotationYaw() {
        syncRotationPlayer();
        FakeRotation current = activeRotation();
        float camera = rotationPlayer == null ? 0.0F : rotationPlayer.rotationYaw;
        return current != null ? current.yaw : transported ? RotationMath.nearest(camera, transportYaw) : camera;
    }

    public float getRotationPitch() {
        syncRotationPlayer();
        FakeRotation current = activeRotation();
        return current != null ? current.pitch : rotationPlayer == null ? 0.0F : rotationPlayer.rotationPitch;
    }

    /** Removes a fake rotation only when its producer owns the current value. */
    public void clearFakeRotation(String owner) {
        if (owner == null || owner.equals(rotationOwner)) {
            rotationOwner = null;
        }
    }

    /** Feed a controlled movement vector through the same silent rotation path as player input. */
    public void setForcedMovement(String owner, float forward, float strafe, boolean jump) {
        if (owner == null || !Float.isFinite(forward) || !Float.isFinite(strafe)) return;
        forcedMovementOwner = owner;
        forcedForward = Math.max(-1.0F, Math.min(1.0F, forward));
        forcedStrafe = Math.max(-1.0F, Math.min(1.0F, strafe));
        forcedJump = jump;
    }

    /** Stops a producer without clearing input owned by another producer. */
    public void clearForcedMovement(String owner) {
        if (owner == null || owner.equals(forcedMovementOwner)) {
            forcedMovementOwner = null;
            forcedForward = forcedStrafe = 0.0F;
            forcedJump = false;
        }
    }

    /** Advances an unowned fake rotation toward the local camera. */
    public void tick() {
        syncRotationPlayer();
        if (rotationOwner != null || fakeRotation == null) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            fakeRotation = null;
            return;
        }
        float speed = rotateBackSpeed();
        if (speed <= 0.0F) {
            fakeRotation = null;
            return;
        }
        FakeRotation current = fakeRotation;
        float yaw = approachAngle(current.yaw, player.rotationYaw, speed);
        float pitch = approach(current.pitch, player.rotationPitch, speed);
        if (Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(player.rotationYaw - yaw)) < 0.001F
                && Math.abs(player.rotationPitch - pitch) < 0.001F) {
            fakeRotation = null;
        } else {
            fakeRotation = new FakeRotation(yaw, pitch);
        }
    }

    public ModeSetting getCorrectMovement() {
        return correctMovement;
    }

    /** Alias for Vibe UIs which display the setting as the module mode. */
    public ModeSetting getMode() {
        return correctMovement;
    }

    public BooleanSetting getAdjustThirdPersonModel() {
        return adjustThirdPersonModel;
    }

    public BooleanSetting getRaycast() {
        return raycast;
    }

    public RangeSetting getRotateBackSpeed() {
        return rotateBackSpeed;
    }

    private FakeRotation activeRotation() {
        return isEnabled() ? fakeRotation : null;
    }

    private float rotateBackSpeed() {
        double min = rotateBackSpeed.getMin();
        double max = rotateBackSpeed.getMax();
        return (float) (min + (max <= min ? 0.0D : rotationRandom.nextDouble() * (max - min)));
    }

    private static float approach(float current, float target, float amount) {
        float difference = target - current;
        return Math.abs(difference) <= amount ? target : current + Math.copySign(amount, difference);
    }

    private static float approachAngle(float current, float target, float amount) {
        return current + RotationMath.clamp(RotationMath.difference(target, current), -amount, amount);
    }

    private boolean usesDirectYaw() {
        return forcedMovementOwner != null || correctMovement.is("Direct") || correctMovement.is("Silent");
    }

    /**
     * Exact port of Tarasande's Silent input handler.  It compares the desired
     * real-camera movement vector with every legal keyboard input at fake yaw
     * and writes the closest forward/sideways pair.
     */
    private void correctKeyboardInput(MovementInput input) {
        FakeRotation rotation = activeRotation();
        Minecraft minecraft = Minecraft.getMinecraft();
        // This wrapper is the player's keyboard input even when another Vibe
        // wrapper (for example NoSlow) sits outside it.  No other input can
        // call this private wrapper method.
        if (rotation == null || !correctMovement.is("Silent")) {
            return;
        }
        if (input.moveForward == 0.0F && input.moveStrafe == 0.0F) {
            return;
        }

        // MovementInputFromOptions applies the sneak slowdown before this
        // handler sees the values.  Comparing that 0.3-sized vector with
        // full-strength keyboard candidates made the idle (0, 0) candidate
        // the nearest one, so Silent MoveFix could completely eat movement
        // while sneaking.  Keep the input magnitude in every candidate.
        float inputScale = Math.max(Math.abs(input.moveForward), Math.abs(input.moveStrafe));
        if (inputScale < 0.0001F) {
            return;
        }

        float realYaw = minecraft.thePlayer.rotationYaw;
        float fakeYaw = rotation.yaw;
        double moveX = input.moveStrafe * Math.cos(Math.toRadians(realYaw))
                - input.moveForward * Math.sin(Math.toRadians(realYaw));
        double moveZ = input.moveForward * Math.cos(Math.toRadians(realYaw))
                + input.moveStrafe * Math.sin(Math.toRadians(realYaw));

        double[] bestMovement = null;
        for (int forward = -1; forward <= 1; forward++) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                double newMoveX = strafe * inputScale * Math.cos(Math.toRadians(fakeYaw))
                        - forward * inputScale * Math.sin(Math.toRadians(fakeYaw));
                double newMoveZ = forward * inputScale * Math.cos(Math.toRadians(fakeYaw))
                        + strafe * inputScale * Math.sin(Math.toRadians(fakeYaw));
                double deltaX = newMoveX - moveX;
                double deltaZ = newMoveZ - moveZ;
                double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                if (bestMovement == null || bestMovement[0] > distance) {
                    bestMovement = new double[] {distance, forward, strafe};
                }
            }
        }
        if (bestMovement != null) {
            input.moveForward = (float) bestMovement[1] * inputScale;
            input.moveStrafe = (float) bestMovement[2] * inputScale;
        }
    }

    /** Called by the walking-packet transformer at its PRE_PACKET boundary. */
    public void beginPacketRotation(Object entity) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (entity != minecraft.thePlayer || minecraft.thePlayer == null || packetPlayer != null) {
            return;
        }
        float yaw = getRotationYaw();
        float pitch = getRotationPitch();
        EntityPlayerSP player = minecraft.thePlayer;
        packetPlayer = player;
        packetYaw = player.rotationYaw;
        packetPitch = player.rotationPitch;
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        transportYaw = yaw;
        transported = true;
    }

    /** Called by the walking-packet transformer at its POST boundary. */
    public void endPacketRotation(Object entity) {
        if (packetPlayer == null || packetPlayer != entity) {
            return;
        }
        packetPlayer.rotationYaw = packetYaw;
        packetPlayer.rotationPitch = packetPitch;
        packetPlayer = null;
    }

    /**
     * Tarasande's default third-person-model adjustment, scoped to rendering
     * so it never changes the local first-person camera or packet state.
     */
    public void beginPlayerRender(EntityPlayerSP player) {
        FakeRotation rotation = activeRotation();
        FakeRotation previous = previousRenderRotation;
        Minecraft minecraft = Minecraft.getMinecraft();
        // GuiInventory has its own deliberate, mirrored model transform.  It
        // is not a third-person world render, so applying the silent server
        // rotation here makes the inventory preview appear inverted whenever
        // F5 was enabled before opening the inventory.
        if ((rotation == null && previous == null) || !adjustThirdPersonModel.isEnabled() || player != minecraft.thePlayer
                || minecraft.gameSettings.thirdPersonView == 0 || minecraft.currentScreen instanceof GuiInventory
                || renderedPlayer != null) {
            return;
        }
        renderedPlayer = player;
        renderedYaw = player.rotationYaw;
        renderedPreviousYaw = player.prevRotationYaw;
        renderedPitch = player.rotationPitch;
        renderedPreviousPitch = player.prevRotationPitch;
        renderedHeadYaw = player.rotationYawHead;
        renderedPreviousHeadYaw = player.prevRotationYawHead;
        renderedBodyYaw = player.renderYawOffset;
        renderedPreviousBodyYaw = player.prevRenderYawOffset;
        // Vanilla interpolates prev/current fields using the render partial
        // tick. Keeping distinct endpoints also smooths acquisition and the
        // final tick of the return to the camera after ownership is released.
        player.prevRotationYaw = previous == null ? renderedPreviousYaw : previous.yaw;
        player.rotationYaw = RotationMath.nearest(rotation == null ? renderedYaw : rotation.yaw, player.prevRotationYaw);
        player.prevRotationPitch = previous == null ? renderedPreviousPitch : previous.pitch;
        player.rotationPitch = rotation == null ? renderedPitch : rotation.pitch;
        player.prevRotationYawHead = previous == null ? renderedPreviousHeadYaw : previous.yaw;
        player.rotationYawHead = RotationMath.nearest(rotation == null ? renderedHeadYaw : rotation.yaw, player.prevRotationYawHead);
        player.prevRenderYawOffset = previous == null ? renderedPreviousBodyYaw : previous.yaw;
        player.renderYawOffset = RotationMath.nearest(rotation == null ? renderedBodyYaw : rotation.yaw, player.prevRenderYawOffset);
    }

    public void endPlayerRender(EntityPlayerSP player) {
        if (renderedPlayer != player) {
            return;
        }
        player.rotationYaw = renderedYaw;
        player.prevRotationYaw = renderedPreviousYaw;
        player.rotationPitch = renderedPitch;
        player.prevRotationPitch = renderedPreviousPitch;
        player.rotationYawHead = renderedHeadYaw;
        player.prevRotationYawHead = renderedPreviousHeadYaw;
        player.renderYawOffset = renderedBodyYaw;
        player.prevRenderYawOffset = renderedPreviousBodyYaw;
        renderedPlayer = null;
    }

    private float movementYaw(Object entity, float vanillaYaw) {
        FakeRotation rotation = activeRotation();
        return rotation != null && usesDirectYaw() && entity == Minecraft.getMinecraft().thePlayer
                ? rotation.yaw : vanillaYaw;
    }

    /** Tarasande's PreventBackwardsSprinting test, adapted to 1.8.9's flag. */
    private float sprintForward(float vanillaForward, Object entity) {
        FakeRotation rotation = activeRotation();
        if (rotation == null || !correctMovement.is("Prevent Backwards Sprinting")
                || entity != Minecraft.getMinecraft().thePlayer) {
            return vanillaForward;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player.movementInput.moveForward == 0.0F && player.movementInput.moveStrafe == 0.0F) {
            return vanillaForward;
        }
        // RotationUtil.getYaw(Vec2f(sideways, forward)) + player.yaw from
        // Tarasande's PlayerUtil.getMoveDirection().
        float movementDirection = (float) Math.toDegrees(Math.atan2(player.movementInput.moveForward,
                player.movementInput.moveStrafe)) - 90.0F + player.rotationYaw;
        float difference = net.minecraft.util.MathHelper.wrapAngleTo180_float(rotation.yaw - movementDirection);
        return Math.abs(difference) <= 45.0F ? vanillaForward : 0.0F;
    }

    /** Tarasande's PreventRotationLeak equivalent for any independently-sent C03. */
    public void applyToOutgoing(Packet<?> packet) {
        FakeRotation rotation = activeRotation();
        if (rotation == null || !(packet instanceof C03PacketPlayer) || !((C03PacketPlayer) packet).getRotating()) {
            return;
        }
        resolvePacketFields();
        if (packetYawField == null || packetPitchField == null || packetRotatingField == null) {
            return;
        }
        try {
            packetYawField.setFloat(packet, rotation.yaw);
            packetPitchField.setFloat(packet, rotation.pitch);
            packetRotatingField.setBoolean(packet, true);
        } catch (IllegalAccessException ignored) {
            // The normal walking-packet scope still handles vanilla C03s.
        }
    }

    private static void resolvePacketFields() {
        if (packetFieldsResolved) {
            return;
        }
        packetFieldsResolved = true;
        packetYawField = findField(C03PacketPlayer.class, Float.TYPE, "yaw", "field_149476_e");
        packetPitchField = findField(C03PacketPlayer.class, Float.TYPE, "pitch", "field_149473_f");
        packetRotatingField = findField(C03PacketPlayer.class, Boolean.TYPE, "rotating", "field_149481_i");
    }

    private static Field findField(Class<?> type, Class<?> fieldType, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                if (field.getType() == fieldType) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapped name, then the type-only fallback.
            }
        }
        int wanted = fieldType == Float.TYPE && names.length > 0 && names[0].equals("pitch") ? 1 : 0;
        int seen = 0;
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() == fieldType && seen++ == wanted) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    @Override
    protected void onDisable() {
        fakeRotation = null;
        previousRenderRotation = null;
        rotationOwner = null;
        clearForcedMovement(null);
        if (packetPlayer != null) {
            packetPlayer.rotationYaw = packetYaw;
            packetPlayer.rotationPitch = packetPitch;
            packetPlayer = null;
        }
        if (renderedPlayer != null) {
            endPlayerRender(renderedPlayer);
        }
    }

    public static void beginPacketRotationHook(Object entity) {
        MoveFixModule module = module();
        if (module != null) {
            module.beginPacketRotation(entity);
        }
    }

    public static void endPacketRotationHook(Object entity) {
        MoveFixModule module = module();
        if (module != null) {
            module.endPacketRotation(entity);
            BedAuraModule bedAura = Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class);
            if (bedAura != null) bedAura.afterWalkingUpdate(entity);
        }
    }

    public static float movementYawHook(Object entity, float vanillaYaw) {
        MoveFixModule module = module();
        return module == null ? vanillaYaw : module.movementYaw(entity, vanillaYaw);
    }

    public static float sprintForwardHook(float vanillaForward, Object entity) {
        MoveFixModule module = module();
        return module == null ? vanillaForward : module.sprintForward(vanillaForward, entity);
    }

    /** Equivalent to Tarasande's local-player fake rotation-vector override. */
    public static float lookYawHook(Object entity, float vanillaYaw) {
        MoveFixModule module = module();
        FakeRotation rotation = module == null ? null : module.activeRotation();
        return rotation != null && entity == Minecraft.getMinecraft().thePlayer ? rotation.yaw : vanillaYaw;
    }

    /** Equivalent to Tarasande's local-player fake rotation-vector override. */
    public static float lookPitchHook(Object entity, float vanillaPitch) {
        MoveFixModule module = module();
        FakeRotation rotation = module == null ? null : module.activeRotation();
        return rotation != null && entity == Minecraft.getMinecraft().thePlayer ? rotation.pitch : vanillaPitch;
    }

    /** Re-evaluates vanilla mouse-over using the active fake rotation. */
    public static void raycastHook(float partialTicks) {
        MoveFixModule module = module();
        if (module != null) {
            module.recalculateRaycast(partialTicks);
        }
    }

    private void recalculateRaycast(float partialTicks) {
        FakeRotation rotation = activeRotation();
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity view = minecraft.getRenderViewEntity();
        if (!raycast.isEnabled() || rotation == null || minecraft.theWorld == null || minecraft.playerController == null
                || view == null || view != minecraft.thePlayer) {
            return;
        }
        double reach = minecraft.playerController.getBlockReachDistance();
        boolean longReach = minecraft.playerController.extendedReach();
        boolean survivalLimit = !longReach && reach > 3.0D;
        double entityReach = longReach ? 6.0D : reach;
        Vec3 eyes = view.getPositionEyes(partialTicks);
        Vec3 look = lookVector(rotation.yaw, rotation.pitch);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition block = minecraft.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        double blockDistance = block == null ? reach : eyes.distanceTo(block.hitVec);
        Entity pointed = null;
        Vec3 pointedHit = null;
        double closest = entityReach;
        AxisAlignedBB search = view.getEntityBoundingBox().addCoord(look.xCoord * reach,
                look.yCoord * reach, look.zCoord * reach).expand(1.0D, 1.0D, 1.0D);
        List<?> candidates = minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(view, search);
        for (Object value : candidates) {
            if (!(value instanceof Entity)) continue;
            Entity entity = (Entity) value;
            if (!entity.canBeCollidedWith()) continue;
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB bounds = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = bounds.calculateIntercept(eyes, end);
            if (bounds.isVecInside(eyes)) {
                pointed = entity;
                pointedHit = intercept == null ? eyes : intercept.hitVec;
                closest = 0.0D;
            } else if (intercept != null) {
                double distance = eyes.distanceTo(intercept.hitVec);
                if (distance < closest || closest == 0.0D) {
                    if (entity != view.ridingEntity || view.canRiderInteract()) {
                        pointed = entity;
                        pointedHit = intercept.hitVec;
                        closest = distance;
                    }
                }
            }
        }
        if (pointed != null && survivalLimit && eyes.distanceTo(pointedHit) > 3.0D) {
            pointed = null;
        }
        minecraft.pointedEntity = null;
        if (pointed != null && (block == null || closest < blockDistance)) {
            minecraft.objectMouseOver = new MovingObjectPosition(pointed, pointedHit);
            minecraft.pointedEntity = pointed;
        } else {
            minecraft.objectMouseOver = block;
        }
    }

    private static Vec3 lookVector(float yaw, float pitch) {
        float yawRadians = -yaw * 0.017453292F - (float) Math.PI;
        float pitchRadians = -pitch * 0.017453292F;
        float cosineYaw = net.minecraft.util.MathHelper.cos(yawRadians);
        float sineYaw = net.minecraft.util.MathHelper.sin(yawRadians);
        float cosinePitch = -net.minecraft.util.MathHelper.cos(pitchRadians);
        float sinePitch = net.minecraft.util.MathHelper.sin(pitchRadians);
        return new Vec3(sineYaw * cosinePitch, sinePitch, cosineYaw * cosinePitch);
    }

    private static MoveFixModule module() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(MoveFixModule.class);
    }

    private static final class MoveFixInput extends MovementInput {
        private final MovementInput vanillaInput;
        private final MoveFixModule module;

        private MoveFixInput(MovementInput vanillaInput, MoveFixModule module) {
            this.vanillaInput = vanillaInput;
            this.module = module;
        }

        @Override
        public void updatePlayerMoveState() {
            vanillaInput.updatePlayerMoveState();
            moveStrafe = vanillaInput.moveStrafe;
            moveForward = vanillaInput.moveForward;
            jump = vanillaInput.jump;
            sneak = vanillaInput.sneak;
            if (module.forcedMovementOwner != null) {
                moveForward = module.forcedForward;
                moveStrafe = module.forcedStrafe;
                jump = jump || module.forcedJump;
            } else {
                module.correctKeyboardInput(this);
            }
        }
    }

    private static final class FakeRotation {
        private final float yaw;
        private final float pitch;

        private FakeRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
