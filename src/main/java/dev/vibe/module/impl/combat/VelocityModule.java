package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.combat.CombatRangeSupport;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.movement.AacMovementSupport;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;

/** Packet and input velocity controls with individually selectable modes. */
public final class VelocityModule extends Module {

    private static final String INTAVE = "Intave14";
    private static final int TRANSACTION_TIMEOUT = 20;

    private final MultiSelectSetting mode = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Normal", "Jump", "AACReverse", "Cancel", INTAVE), Arrays.asList("Normal")));
    private final NumberSetting horizontal = addSetting(new NumberSetting("Horizontal", 0.0D, -100.0D, 100.0D, 1.0D,
            () -> mode.isSelected("Normal")));
    private final NumberSetting vertical = addSetting(new NumberSetting("Vertical", 0.0D, -100.0D, 100.0D, 1.0D,
            () -> mode.isSelected("Normal")));
    private final NumberSetting reverseStrength = addSetting(new NumberSetting("Reverse Strength", 1.0D, 0.1D, 1.0D, 0.05D,
            () -> mode.isSelected("AACReverse")));
    private final BooleanSetting intaveExplosions = addSetting(new BooleanSetting("Explosions", false,
            () -> mode.isSelected(INTAVE)));
    private final BooleanSetting onlyPlayerDamage = addSetting(new BooleanSetting("Only Player Damage", true));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private static Field motionX, motionY, motionZ;
    private static Field explosionX, explosionY, explosionZ;
    private static Field reportedYaw, reportedPitch;
    private boolean velocityInput;
    private volatile boolean awaitingTransaction;
    private volatile int awaitingSince;
    private volatile boolean freezeNextTick;
    private boolean frozen;
    private boolean holdingRotation;
    private float heldYaw, heldPitch;

    public VelocityModule() {
        super("Velocity", "Reduce received knockback", Category.COMBAT, Keyboard.KEY_NONE);
    }

    /** Handles S12 in the inbound packet path before the game applies it. */
    public boolean handleInbound(Packet<?> packet) {
        if (isEnabled() && mode.isSelected(INTAVE) && handleIntave(packet)) return true;
        if (!isEnabled() || minecraft.thePlayer == null || !(packet instanceof S12PacketEntityVelocity)) return false;
        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (onlyPlayerDamage.isEnabled() && velocity.getEntityID() != minecraft.thePlayer.getEntityId()) return false;
        if (mode.isSelected("Cancel")) return true;
        if (mode.isSelected("AACReverse")) {
            velocityInput = true;
            return false;
        }
        if (!mode.isSelected("Normal") || velocity.getEntityID() != minecraft.thePlayer.getEntityId()) return false;
        int horizontalValue = horizontal.getInt();
        int verticalValue = vertical.getInt();
        writeVelocity(velocity, velocity.getMotionX() / 100 * horizontalValue,
                velocity.getMotionY() / 100 * verticalValue, velocity.getMotionZ() / 100 * horizontalValue);
        return horizontalValue == 0 && verticalValue == 0;
    }

    private boolean handleIntave(Packet<?> packet) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            resetIntave();
            return false;
        }
        if (packet instanceof S32PacketConfirmTransaction) {
            if (awaitingTransaction && !((S32PacketConfirmTransaction) packet).func_148888_e()) {
                awaitingTransaction = false;
                freezeNextTick = true;
            }
            return false;
        }
        if (player.isRiding()) return false;
        boolean cancel = false;
        if (packet instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) packet).getEntityID() != player.getEntityId()) return false;
            cancel = true;
        } else if (packet instanceof S27PacketExplosion && intaveExplosions.isEnabled()) {
            S27PacketExplosion explosion = (S27PacketExplosion) packet;
            if (explosion.func_149149_c() == 0.0F && explosion.func_149144_d() == 0.0F
                    && explosion.func_149147_e() == 0.0F) return false;
            if (!clearExplosionMotion(explosion)) return false;
        } else {
            return false;
        }
        awaitingTransaction = true;
        awaitingSince = player.ticksExisted;
        return cancel;
    }

    private boolean freezeUpdate(Object entity) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || entity != player) return false;
        frozen = false;
        if (!isEnabled() || !mode.isSelected(INTAVE)) {
            resetIntave();
            return false;
        }
        if (awaitingTransaction && Math.abs(player.ticksExisted - awaitingSince) > TRANSACTION_TIMEOUT) {
            awaitingTransaction = false;
        }
        if (!freezeNextTick) return false;
        freezeNextTick = false;
        if (player.isRiding()) return false;
        frozen = true;
        player.prevDistanceWalkedModified = player.distanceWalkedModified;
        player.prevPosX = player.posX;
        player.prevPosY = player.posY;
        player.prevPosZ = player.posZ;
        player.prevCameraYaw = player.cameraYaw;
        player.prevCameraPitch = player.cameraPitch;
        player.prevSwingProgress = player.swingProgress;
        player.prevRenderArmYaw = player.renderArmYaw;
        player.prevRenderArmPitch = player.renderArmPitch;
        return true;
    }

    public void beforeWalkingUpdate(Object entity) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (!frozen || player == null || entity != player) return;
        try {
            if (reportedYaw == null) {
                reportedYaw = field(EntityPlayerSP.class, "lastReportedYaw", "field_175164_bL");
                reportedPitch = field(EntityPlayerSP.class, "lastReportedPitch", "field_175165_bM");
            }
            float yaw = reportedYaw.getFloat(player);
            float pitch = reportedPitch.getFloat(player);
            heldYaw = player.rotationYaw;
            heldPitch = player.rotationPitch;
            holdingRotation = true;
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
        } catch (ReflectiveOperationException ignored) { }
    }

    public void afterWalkingUpdate(Object entity) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (!frozen || player == null || entity != player) return;
        frozen = false;
        if (holdingRotation) {
            player.rotationYaw = heldYaw;
            player.rotationPitch = heldPitch;
            holdingRotation = false;
        }
        player.onGround = isOnGroundForIntave(player);
    }

    private boolean isOnGroundForIntave(EntityPlayerSP player) {
        double motionY = Math.abs(player.motionY) < 0.005D ? 0.0D : player.motionY;
        double fallStep = (motionY - 0.08D) * 0.98F;
        AxisAlignedBB boundingBox = player.getEntityBoundingBox();
        double allowedStep = fallStep;
        for (AxisAlignedBB collision : player.worldObj.getCollidingBoundingBoxes(player,
                boundingBox.addCoord(0.0D, fallStep, 0.0D))) {
            allowedStep = collision.calculateYOffset(boundingBox, allowedStep);
        }
        return allowedStep != fallStep && fallStep < 0.0D;
    }

    private void resetIntave() {
        awaitingTransaction = false;
        freezeNextTick = false;
        frozen = false;
    }

    public static boolean freezeUpdateHook(Object entity) {
        Vibe vibe = Vibe.getInstance();
        VelocityModule module = vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(VelocityModule.class);
        return module != null && module.freezeUpdate(entity);
    }

    /** Reverse mode is the source-backed LiquidSense/AAC reverse branch. */
    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || !mode.isSelected("AACReverse")) {
            velocityInput = false;
            return;
        }
        EntityLivingBase player = minecraft.thePlayer;
        if (!velocityInput) return;
        if (player.hurtTime > 0 && !player.onGround && !CombatRangeSupport.isInWeb(player)
                && !AacMovementSupport.inLiquid(minecraft.thePlayer)) {
            AacMovementSupport.strafe(minecraft.thePlayer,
                    AacMovementSupport.horizontalSpeed(minecraft.thePlayer) * reverseStrength.getDouble());
        } else if (player.hurtTime <= 0 || player.onGround) {
            velocityInput = false;
        }
    }

    /** Exact Gothaj Jump move-input branch. */
    public void applyJumpInput(MovementInput input) {
        if (!isEnabled() || !mode.isSelected("Jump") || input == null || minecraft.thePlayer == null
                || minecraft.objectMouseOver == null || minecraft.objectMouseOver.entityHit == null) return;
        EntityLivingBase player = minecraft.thePlayer;
        if (player.hurtTime <= 0 || player.isBurning() || CombatRangeSupport.isInWeb(player)) return;
        input.moveForward = 1.0F;
        if (player.hurtTime > 8) input.jump = true;
    }

    @Override protected void onEnable() {
        resetIntave();
    }

    @Override protected void onDisable() {
        velocityInput = false;
        resetIntave();
    }

    private static boolean clearExplosionMotion(S27PacketExplosion packet) {
        try {
            if (explosionX == null) {
                explosionX =field(S27PacketExplosion.class, "field_149152_f", "field_149152_f");
                explosionY = field(S27PacketExplosion.class, "field_149153_g", "field_149153_g");
                explosionZ = field(S27PacketExplosion.class, "field_149159_h", "field_149159_h");
            }
            explosionX.setFloat(packet, 0.0F);
            explosionY.setFloat(packet, 0.0F);
            explosionZ.setFloat(packet, 0.0F);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Field field(Class<?> owner, String name, String obfuscated) throws NoSuchFieldException {
        Field field;
        try { field = owner.getDeclaredField(name); }
        catch (NoSuchFieldException ignored) { field = owner.getDeclaredField(obfuscated); }
        field.setAccessible(true);
        return field;
    }

    private static void writeVelocity(S12PacketEntityVelocity packet, int x, int y, int z) {
        try {
            if (motionX == null) {
                motionX = field("motionX", "field_149415_b", 1);
                motionY = field("motionY", "field_149416_c", 2);
                motionZ = field("motionZ", "field_149414_d", 3);
            }
            motionX.setInt(packet, x);
            motionY.setInt(packet, y);
            motionZ.setInt(packet, z);
        } catch (ReflectiveOperationException ignored) {
            // If a nonstandard packet mapping hides the fields, pass S12
            // unchanged instead of applying a repeated tick mutation.
        }
    }

    private static Field field(String name, String obfuscated, int intIndex) throws NoSuchFieldException {
        for (String candidate : new String[] {name, obfuscated}) {
            try {
                Field field = S12PacketEntityVelocity.class.getDeclaredField(candidate);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        int seen = 0;
        for (Field field : S12PacketEntityVelocity.class.getDeclaredFields()) {
            if (field.getType() == Integer.TYPE && seen++ == intIndex) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException(name);
    }
}
