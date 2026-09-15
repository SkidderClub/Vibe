package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

/** Highly configurable overlay for the block currently under the crosshair. */
public final class BlockOverlayModule extends Module {

    private final BooleanSetting outline = addSetting(new BooleanSetting("Outline", true));
    private final ModeSetting outlineMode = addSetting(new ModeSetting("Outline Mode", "Fade", () -> outline.isEnabled(),
            "Static", "Fade", "Rainbow"));
    private final ColorSetting outlinePrimary = addSetting(new ColorSetting("Outline Primary Color", 0xFFEFC2FF,
            () -> outline.isEnabled() && !outlineMode.is("Rainbow")));
    private final ColorSetting outlineSecondary = addSetting(new ColorSetting("Outline Secondary Color", 0xFF43E7D4,
            () -> outline.isEnabled() && outlineMode.is("Fade")));
    private final NumberSetting outlineWidth = addSetting(new NumberSetting("Outline Width", 1.6D, 0.5D, 5.0D, 0.1D,
            () -> outline.isEnabled()));

    private final BooleanSetting fill = addSetting(new BooleanSetting("Fill", true));
    private final ModeSetting fillMode = addSetting(new ModeSetting("Fill Mode", "Fade", () -> fill.isEnabled(),
            "Static", "Fade", "Rainbow"));
    private final ColorSetting fillPrimary = addSetting(new ColorSetting("Fill Primary Color", 0x482A6CFF,
            () -> fill.isEnabled() && !fillMode.is("Rainbow")));
    private final ColorSetting fillSecondary = addSetting(new ColorSetting("Fill Secondary Color", 0x4856F3C8,
            () -> fill.isEnabled() && fillMode.is("Fade")));
    private final ModeSetting breakAnimation = addSetting(new ModeSetting("Break Animation", "Inward Fill", () -> fill.isEnabled(),
            "Inward Fill", "Dissolve", "Pulse Shatter"));
    private final NumberSetting animationSpeed = addSetting(new NumberSetting("Animation Speed", 1.0D, 0.25D, 3.0D, 0.05D));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", false));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private BlockPos target;
    private float breakProgress;
    private Field damageField;
    private boolean damageFieldResolved;

    public BlockOverlayModule() {
        super("BlockOverlay", "Customize the selected block outline and fill", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            target = null;
            breakProgress = 0.0F;
            return;
        }
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.getBlockPos() == null) {
            target = null;
            breakProgress = 0.0F;
            return;
        }
        target = hit.getBlockPos();
        // curBlockDamageMP is the authoritative client controller state.
        // getIsHittingBlock can already be false on the completion tick,
        // which previously made every break animation disappear before it
        // was visible.
        breakProgress = Math.max(0.0F, Math.min(1.0F, damageProgress()));
    }

    private float damageProgress() {
        if (!damageFieldResolved) {
            damageFieldResolved = true;
            for (String name : new String[] {"curBlockDamageMP", "field_78770_f"}) {
                try {
                    damageField = minecraft.playerController.getClass().getDeclaredField(name);
                    damageField.setAccessible(true);
                    break;
                } catch (Exception ignored) { }
            }
        }
        try {
            return damageField == null ? 0.0F : damageField.getFloat(minecraft.playerController);
        } catch (Exception ignored) {
            return 0.0F;
        }
    }

    public BooleanSetting getOutline() { return outline; }
    public ModeSetting getOutlineMode() { return outlineMode; }
    public ColorSetting getOutlinePrimary() { return outlinePrimary; }
    public ColorSetting getOutlineSecondary() { return outlineSecondary; }
    public NumberSetting getOutlineWidth() { return outlineWidth; }
    public BooleanSetting getFill() { return fill; }
    public ModeSetting getFillMode() { return fillMode; }
    public ColorSetting getFillPrimary() { return fillPrimary; }
    public ColorSetting getFillSecondary() { return fillSecondary; }
    public ModeSetting getBreakAnimation() { return breakAnimation; }
    public NumberSetting getAnimationSpeed() { return animationSpeed; }
    public BooleanSetting getThroughWalls() { return throughWalls; }
    public BlockPos getTarget() { return target; }
    public float getBreakProgress() { return breakProgress; }
}
