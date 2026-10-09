package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.world.BedAuraModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

/** RavenBS's billboard showing the currently mined block's progress. */
public final class BreakProgressModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Percentage", "Percentage", "Second", "Decimal"));
    private final BooleanSetting showManual = addSetting(new BooleanSetting("Show manual", true));
    private final BooleanSetting showBedAura = addSetting(new BooleanSetting("Show BedAura", true));
    private final BooleanSetting fadeIn = addSetting(new BooleanSetting("Fade in", false));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private static Field damageField;
    private static boolean damageFieldResolved;
    private float progress;
    private BlockPos block;
    private String progressText = "";

    public BreakProgressModule() {
        super("BreakProgress", "Show mining progress above the block", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null
                || minecraft.playerController == null || minecraft.thePlayer.capabilities.isCreativeMode
                || !minecraft.thePlayer.capabilities.allowEdit) {
            reset();
            return;
        }
        BedAuraModule aura = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class);
        if (showBedAura.isEnabled() && aura != null && aura.isEnabled() && aura.getTarget() != null) {
            float damage = aura.getAuraBreakProgress();
            if (damage > 0.0F) {
                progress = Math.min(1.0F, damage);
                block = aura.getTarget();
                updateText();
                return;
            }
        }
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (showManual.isEnabled() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.getBlockPos() != null) {
            progress = damageProgress();
            if (progress != 0.0F) {
                block = hit.getBlockPos();
                updateText();
                return;
            }
        }
        reset();
    }

    private float damageProgress() {
        return controllerProgress(minecraft.playerController);
    }

    public static float controllerProgress(net.minecraft.client.multiplayer.PlayerControllerMP controller) {
        if (controller == null) return 0.0F;
        if (!damageFieldResolved) {
            damageFieldResolved = true;
            for (String name : new String[] {"curBlockDamageMP", "field_78770_f"}) try {
                damageField = net.minecraft.client.multiplayer.PlayerControllerMP.class.getDeclaredField(name);
                damageField.setAccessible(true);
                break;
            } catch (Exception ignored) { }
        }
        try { return damageField == null ? 0.0F : damageField.getFloat(controller); }
        catch (Exception ignored) { return 0.0F; }
    }

    private void updateText() {
        if (mode.is("Percentage")) {
            progressText = (int) (100.0D * (progress / 1.0D)) + "%";
        } else if (mode.is("Decimal")) {
            progressText = String.valueOf(round(progress, 2));
        } else {
            float strength = dev.vibe.module.impl.world.RavenBlockAccess.getBlockHardness(
                    minecraft.theWorld.getBlockState(block).getBlock(), minecraft.thePlayer.getHeldItem(), false, false);
            double seconds = round((1.0F - progress) / strength / 20.0D, 1);
            progressText = seconds == 0.0D ? "0" : seconds + "s";
        }
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10.0D, places);
        return Math.round(value * scale) / scale;
    }

    private void reset() { progress = 0.0F; block = null; progressText = ""; }
    @Override protected void onDisable() { reset(); }
    public ModeSetting getMode() { return mode; }
    public BooleanSetting getFadeIn() { return fadeIn; }
    public float getProgress() { return progress; }
    public BlockPos getBlock() { return block; }
    public String getProgressText() { return progressText; }
}
