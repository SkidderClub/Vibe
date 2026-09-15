package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockStone;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

/** Tunes Minecraft's normal local block-damage controller with explicit exclusions. */
public final class FastBreakModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Speed", "Speed", "FinishFaster"));
    private final NumberSetting speed = addSetting(new NumberSetting("Speed Multiplier", 1.65D, 1.0D, 4.0D, 0.05D,
            () -> mode.is("Speed")));
    private final NumberSetting finishAt = addSetting(new NumberSetting("Finish At (%)", 82.0D, 10.0D, 99.0D, 1.0D,
            () -> mode.is("FinishFaster")));
    private final BooleanSetting onlyWithTools = addSetting(new BooleanSetting("Only With Tools", true));
    private final MultiSelectSetting excluded = addSetting(new MultiSelectSetting("Not On",
            Arrays.asList("Obsidian", "Woods", "Stones", "Bed"), Arrays.asList("Obsidian")));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private Field blockHitDelay;
    private Field blockDamage;

    public FastBreakModule() {
        super("FastBreak", "Tune the normal block-damage controller", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null
                || minecraft.objectMouseOver == null || minecraft.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !minecraft.gameSettings.keyBindAttack.isKeyDown()) return;
        Block block = minecraft.theWorld.getBlockState(minecraft.objectMouseOver.getBlockPos()).getBlock();
        if (excluded(block) || (onlyWithTools.isEnabled() && !hasTool(block))) return;
        try {
            if (blockHitDelay == null) blockHitDelay = field("blockHitDelay", "field_78781_i");
            if (blockDamage == null) blockDamage = field("curBlockDamageMP", "field_78770_f");
            if (blockHitDelay != null) blockHitDelay.setInt(minecraft.playerController, 0);
            if (blockDamage == null) return;
            float damage = blockDamage.getFloat(minecraft.playerController);
            if (mode.is("Speed")) {
                // This runs before PlayerControllerMP adds its normal one-tick
                // hardness. Multiplying the accumulated damage did nothing at
                // the start of a block (zero stayed zero); add exactly the
                // missing portion of vanilla's per-tick progress instead.
                float vanillaStep = block.getPlayerRelativeBlockHardness(minecraft.thePlayer, minecraft.theWorld,
                        minecraft.objectMouseOver.getBlockPos());
                float extra = vanillaStep * Math.max(0.0F, speed.getFloat() - 1.0F);
                blockDamage.setFloat(minecraft.playerController, Math.min(0.999F, damage + extra));
            } else if (damage >= finishAt.getFloat() / 100.0F) {
                blockDamage.setFloat(minecraft.playerController, 1.0F);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean hasTool(Block block) {
        ItemStack held = minecraft.thePlayer.getHeldItem();
        return held != null && held.getStrVsBlock(block) > 1.0F;
    }

    private boolean excluded(Block block) {
        if (excluded.isSelected("Obsidian") && block == Blocks.obsidian) return true;
        if (excluded.isSelected("Bed") && block instanceof BlockBed) return true;
        if (excluded.isSelected("Stones") && block instanceof BlockStone) return true;
        return excluded.isSelected("Woods") && (block instanceof BlockLog || block == Blocks.planks
                || block == Blocks.wooden_slab || block == Blocks.oak_stairs);
    }

    private Field field(String... names) {
        for (String name : names) try {
            Field field = minecraft.playerController.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
        }
        return null;
    }
}
