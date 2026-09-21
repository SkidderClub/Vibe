package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.SprintModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import org.lwjgl.input.Keyboard;

/** Basic client flight and a ground-spoof movement mode. */
public final class FlyModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Basic", "Basic", "GroundSpoof",
            "AAC1.9.10", "AAC3.0.5", "AAC3.1.6-Gomme", "AAC3.3.12", "AAC3.3.12-Glide", "AAC3.3.13"));
    private final NumberSetting horizontalSpeed = addSetting(new NumberSetting("Horizontal Speed", 0.8D, 0.1D, 3.0D, 0.1D));
    private final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical Speed", 0.35D, 0.1D, 2.0D, 0.05D));
    private final NumberSetting aacSpeed = addSetting(new NumberSetting("AAC1.9.10-Speed", 0.3D, 0.0D, 1.0D, 0.01D,
            () -> mode.is("AAC1.9.10")));
    private final BooleanSetting aacFast = addSetting(new BooleanSetting("AAC3.0.5-Fast", true,
            () -> mode.is("AAC3.0.5")));
    private final NumberSetting aacMotion = addSetting(new NumberSetting("AAC3.3.12-Motion", 10.0D, 0.1D, 10.0D, 0.1D,
            () -> mode.is("AAC3.3.12")));
    private final NumberSetting aacMotion2 = addSetting(new NumberSetting("AAC3.3.13-Motion", 10.0D, 0.1D, 10.0D, 0.1D,
            () -> mode.is("AAC3.3.13")));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private boolean wasFlying;
    private double startY;
    private double jump;
    private int aacTick;
    private boolean aacNoFlag;
    private boolean aacWasDead;
    private int glideTick;

    public FlyModule() {
        super("Fly", "Simple client flight", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        wasFlying = minecraft.thePlayer != null && minecraft.thePlayer.capabilities.isFlying;
        if (minecraft.thePlayer != null) startY = minecraft.thePlayer.posY;
        jump = 3.8D;
        aacTick = 0;
        glideTick = 0;
        aacNoFlag = false;
        aacWasDead = false;
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) {
            return;
        }
        if (mode.getValue().startsWith("AAC")) {
            tickAac();
            return;
        }
        minecraft.thePlayer.capabilities.isFlying = true;
        minecraft.thePlayer.capabilities.setFlySpeed(0.05F);
        minecraft.thePlayer.motionY = 0.0D;
        if (minecraft.gameSettings.keyBindJump.isKeyDown()) {
            minecraft.thePlayer.motionY = verticalSpeed.getDouble();
        } else if (minecraft.gameSettings.keyBindSneak.isKeyDown()) {
            minecraft.thePlayer.motionY = -verticalSpeed.getDouble();
        }
        setHorizontalSpeed(horizontalSpeed.getDouble());
        if (mode.is("GroundSpoof")) {
            minecraft.thePlayer.onGround = true;
        }
    }

    @Override
    protected void onDisable() {
        CombatTimerAccess.setSpeed(1.0F);
        if (minecraft.thePlayer == null) return;
        minecraft.thePlayer.capabilities.isFlying = wasFlying;
        minecraft.thePlayer.motionX = 0.0D;
        minecraft.thePlayer.motionY = 0.0D;
        minecraft.thePlayer.motionZ = 0.0D;
        AacMovementSupport.speedInAir(minecraft.thePlayer, 0.02F);
        minecraft.thePlayer.jumpMovementFactor = 0.02F;
        aacNoFlag = false;
        aacWasDead = false;
        aacTick = 0;
        glideTick = 0;
    }

    private void setHorizontalSpeed(double speed) {
        double forward = minecraft.thePlayer.movementInput.moveForward;
        double strafe = minecraft.thePlayer.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 0.001D) {
            minecraft.thePlayer.motionX = 0.0D;
            minecraft.thePlayer.motionZ = 0.0D;
            return;
        }
        forward /= length;
        strafe /= length;
        double yaw = Math.toRadians(minecraft.thePlayer.rotationYaw);
        minecraft.thePlayer.motionX = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
        minecraft.thePlayer.motionZ = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed;
    }

    private void tickAac() {
        if (mode.is("AAC1.9.10")) {
            if (minecraft.gameSettings.keyBindJump.isKeyDown()) jump += 0.2D;
            if (minecraft.gameSettings.keyBindSneak.isKeyDown()) jump -= 0.2D;
            if (startY + jump > minecraft.thePlayer.posY) {
                send(new C03PacketPlayer(true));
                minecraft.thePlayer.motionY = 0.8D;
                AacMovementSupport.strafe(minecraft.thePlayer, aacSpeed.getDouble());
            }
            AacMovementSupport.strafe(minecraft.thePlayer, currentHorizontalSpeed());
            return;
        }
        if (mode.is("AAC3.0.5")) {
            if (aacTick == 2) minecraft.thePlayer.motionY = 0.1D;
            else if (aacTick > 2) aacTick = 0;
            if (aacFast.isEnabled()) minecraft.thePlayer.jumpMovementFactor =
                    minecraft.thePlayer.movementInput.moveStrafe == 0.0F ? 0.08F : 0.0F;
            aacTick++;
            return;
        }
        if (mode.is("AAC3.1.6-Gomme")) {
            minecraft.thePlayer.capabilities.isFlying = true;
            if (aacTick == 2) minecraft.thePlayer.motionY += 0.05D;
            else if (aacTick > 2) { minecraft.thePlayer.motionY -= 0.05D; aacTick = 0; }
            aacTick++;
            if (!aacNoFlag) send(new C04PacketPlayerPosition(minecraft.thePlayer.posX, minecraft.thePlayer.posY,
                    minecraft.thePlayer.posZ, minecraft.thePlayer.onGround));
            if (minecraft.thePlayer.posY <= 0.0D) aacNoFlag = true;
            return;
        }
        if (mode.is("AAC3.3.12")) {
            if (minecraft.thePlayer.posY < -70.0D) minecraft.thePlayer.motionY = aacMotion.getDouble();
            CombatTimerAccess.setSpeed(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ? 0.2F : 1.0F);
            if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) setRightClickDelay(0);
            return;
        }
        if (mode.is("AAC3.3.12-Glide")) {
            if (!minecraft.thePlayer.onGround) glideTick++;
            if (glideTick == 2) CombatTimerAccess.setSpeed(1.0F);
            else if (glideTick == 12) CombatTimerAccess.setSpeed(0.1F);
            else if (glideTick >= 12 && !minecraft.thePlayer.onGround) {
                glideTick = 0;
                minecraft.thePlayer.motionY = 0.015D;
            }
            return;
        }
        if (minecraft.thePlayer.isDead) aacWasDead = true;
        if (aacWasDead || minecraft.thePlayer.onGround) {
            aacWasDead = false;
            minecraft.thePlayer.motionY = aacMotion2.getDouble();
            minecraft.thePlayer.onGround = false;
        }
        CombatTimerAccess.setSpeed(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ? 0.2F : 1.0F);
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) setRightClickDelay(0);
    }

    private double currentHorizontalSpeed() {
        return Math.sqrt(minecraft.thePlayer.motionX * minecraft.thePlayer.motionX + minecraft.thePlayer.motionZ * minecraft.thePlayer.motionZ);
    }

    private void send(net.minecraft.network.Packet<?> packet) {
        if (minecraft.getNetHandler() != null) minecraft.getNetHandler().addToSendQueue(packet);
    }

    private void setRightClickDelay(int value) {
        try {
            java.lang.reflect.Field field;
            try { field = Minecraft.class.getDeclaredField("rightClickDelayTimer"); }
            catch (NoSuchFieldException ignored) { field = Minecraft.class.getDeclaredField("field_71467_ac"); }
            field.setAccessible(true);
            field.setInt(minecraft, value);
        } catch (ReflectiveOperationException ignored) { }
    }
}
