package dev.vibe.module.impl.world;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockStone;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
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

    public FastBreakModule() {
        super("FastBreak", "Tune the normal block-damage controller", Category.WORLD, Keyboard.KEY_NONE);
    }

    /** Native controller reads keep the hit delay for ineligible blocks and normal gameplay. */
    public static int delayHook(int delay, Object controller, Object position) {
        FastBreakModule module = module();
        return eligible(module, controller, position) ? 0 : delay;
    }

    /** Scale the real hardness increment once, never the accumulated progress. */
    public static float stepHook(float step, Object controller, Object position) {
        FastBreakModule module = module();
        return eligible(module, controller, position) && module.mode.is("Speed") ? step * module.speed.getFloat() : step;
    }

    /** Complete on the same native tick that reaches the chosen threshold. */
    public static float progressHook(float next, Object controller, Object position) {
        FastBreakModule module = module();
        return eligible(module, controller, position) && module.mode.is("FinishFaster")
                && next >= module.finishAt.getFloat() / 100F ? Math.max(1F, next) : next;
    }

    private static boolean eligible(FastBreakModule module, Object controller, Object position) {
        return module != null && position instanceof BlockPos && module.applies(controller, (BlockPos)position);
    }

    private boolean applies(Object controller, BlockPos position) {
        if (!isEnabled() || controller != minecraft.playerController || minecraft.thePlayer == null
                || minecraft.theWorld == null || minecraft.currentScreen != null || minecraft.thePlayer.isSpectator()
                || minecraft.playerController.isInCreativeMode()) return false;
        Block block = minecraft.theWorld.getBlockState(position).getBlock();
        return block != Blocks.air && !excluded(block) && (!onlyWithTools.isEnabled() || hasTool(block));
    }

    private static FastBreakModule module() {
        dev.vibe.Vibe vibe = dev.vibe.Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(FastBreakModule.class);
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

}
