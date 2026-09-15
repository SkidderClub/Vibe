package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.StringSetting;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import dev.vibe.input.ClickStats;
import org.lwjgl.input.Keyboard;

/** Reduces the local use-item cooldown while honoring a configurable block blacklist. */
public final class FastPlaceModule extends Module {

    private final NumberSetting startDelay = addSetting(new NumberSetting("Start Delay (ms)", 0.0D, 0.0D, 1000.0D, 10.0D));
    private final NumberSetting placeDelay = addSetting(new NumberSetting("Place Delay (ms)", 0.0D, 0.0D, 500.0D, 5.0D));
    private final BooleanSetting randomize = addSetting(new BooleanSetting("Randomize Delays", false));
    private final RangeSetting randomDelay = addSetting(new RangeSetting("Random Delay (ms)", 0.0D, 100.0D, 0.0D, 500.0D, 5.0D,
            () -> randomize.isEnabled()));
    private final BooleanSetting blockOnly = addSetting(new BooleanSetting("Blocks Only", true));
    private final MultiSelectSetting blacklist = addSetting(new MultiSelectSetting("Blacklist",
            Arrays.asList("minecraft:chest", "minecraft:ender_chest", "minecraft:crafting_table", "minecraft:anvil", "minecraft:obsidian", "minecraft:tnt"),
            Collections.singletonList("minecraft:obsidian")));
    private final StringSetting blacklistIds = addSetting(new StringSetting("Blacklist IDs", "", 180, null));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private Field rightClickDelay;
    private long heldSince;

    public FastPlaceModule() {
        super("FastPlace", "Tune local block placement delay", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) {
            heldSince = 0L;
            return;
        }
        if (!minecraft.gameSettings.keyBindUseItem.isKeyDown()) {
            heldSince = 0L;
            return;
        }
        if (heldSince == 0L) {
            heldSince = System.currentTimeMillis();
        }
        ItemStack held = minecraft.thePlayer.getHeldItem();
        long now = System.currentTimeMillis();
        if (now - heldSince < startDelay.getInt() || !canPlace(held)) {
            return;
        }
        if (setRightClickDelay(Math.max(0, nextDelay() / 50))) {
            // A cooldown reduction represents a real next vanilla use-item
            // opportunity while the key is held, so expose it to the shared
            // right-click CPS readout as well.
            ClickStats.recordRight();
        }
    }

    private boolean canPlace(ItemStack held) {
        if (!blockOnly.isEnabled()) {
            return true;
        }
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) held.getItem()).getBlock();
        Object registryName = Block.blockRegistry.getNameForObject(block);
        String id = registryName == null ? "" : registryName.toString();
        return !blacklist.isSelected(id) && !inTypedBlacklist(id);
    }

    private int nextDelay() {
        if (!randomize.isEnabled()) {
            return placeDelay.getInt();
        }
        int minimum = randomDelay.getMinInt();
        int maximum = randomDelay.getMaxInt();
        return minimum + (maximum == minimum ? 0 : random.nextInt(maximum - minimum + 1));
    }

    private boolean inTypedBlacklist(String id) {
        String[] entries = blacklistIds.getValue().split(",");
        for (String entry : entries) {
            if (id.equalsIgnoreCase(entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean setRightClickDelay(int delay) {
        try {
            if (rightClickDelay == null) {
                rightClickDelay = findField("rightClickDelayTimer", "field_71467_ac");
            }
            if (rightClickDelay != null && rightClickDelay.getInt(minecraft) > delay) {
                rightClickDelay.setInt(minecraft, delay);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Field findField(String... names) {
        for (String name : names) {
            try {
                Field field = Minecraft.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
