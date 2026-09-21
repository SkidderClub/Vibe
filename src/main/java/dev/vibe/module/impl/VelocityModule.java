package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;

/** Packet and input velocity controls with individually selectable modes. */
public final class VelocityModule extends Module {

    private final MultiSelectSetting mode = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Normal", "Jump", "AACReverse", "Cancel"), Arrays.asList("Normal")));
    private final NumberSetting horizontal = addSetting(new NumberSetting("Horizontal", 0.0D, -100.0D, 100.0D, 1.0D,
            () -> mode.isSelected("Normal")));
    private final NumberSetting vertical = addSetting(new NumberSetting("Vertical", 0.0D, -100.0D, 100.0D, 1.0D,
            () -> mode.isSelected("Normal")));
    private final NumberSetting reverseStrength = addSetting(new NumberSetting("Reverse Strength", 1.0D, 0.1D, 1.0D, 0.05D,
            () -> mode.isSelected("AACReverse")));
    private final BooleanSetting onlyPlayerDamage = addSetting(new BooleanSetting("Only Player Damage", true));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private static Field motionX, motionY, motionZ;
    private boolean velocityInput;

    public VelocityModule() {
        super("Velocity", "Reduce received knockback", Category.COMBAT, Keyboard.KEY_NONE);
    }

    /** Handles S12 in the inbound packet path before the game applies it. */
    public boolean handleInbound(Packet<?> packet) {
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

    @Override protected void onDisable() {
        velocityInput = false;
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
