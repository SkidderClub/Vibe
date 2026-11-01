package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.combat.SprintResetCycle;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class SprintResetModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "WTap", "WTap", "Sneak", "NoStop"));
    private final NumberSetting delay = addSetting(new NumberSetting("Delay (ms)", 200.0D, 0.0D, 400.0D, 0.5D));
    private final NumberSetting stopDuration = addSetting(new NumberSetting("Stop Duration (ms)", 100.0D, 50.0D, 300.0D, 0.5D));
    private final BooleanSetting randomize = addSetting(new BooleanSetting("Randomize", true));
    private final BooleanSetting damaged = addSetting(new BooleanSetting("Damaged", false));
    // Crab defaults its shared Weapons whitelist to swords.
    private final BooleanSetting holdingWeapon = addSetting(new BooleanSetting("Holding Weapon", false));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final SprintResetCycle cycle = new SprintResetCycle(new Random());
    private Object world, player;
    private String activeMode;
    private boolean controllingKey;
    private int controlledKeyCode;
    private int lastAttackerTime;

    public SprintResetModule() {
        super("Sprint Reset", "Crab sprint reset with WTap, Sneak and NoStop", Category.COMBAT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null
                || minecraft.thePlayer.isDead || minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            clear();
            return;
        }

        if (world != minecraft.theWorld || player != minecraft.thePlayer || !mode.getValue().equals(activeMode)) {
            clear();
            world = minecraft.theWorld;
            player = minecraft.thePlayer;
            activeMode = mode.getValue();
            lastAttackerTime = minecraft.thePlayer.getLastAttackerTime();
        }

        // Manual attacks precede the entity tick; aura attacks can follow it.
        // Track changes too so Forge tick ordering cannot hide a successful hit.
        final int attackTime = minecraft.thePlayer.getLastAttackerTime();
        cycle.tick(System.nanoTime() / 1.0E9D,
                attackTime != lastAttackerTime ? attackTime : minecraft.thePlayer.ticksExisted,
                attackTime, minecraft.thePlayer.hurtTime,
                !holdingWeapon.isEnabled() || minecraft.thePlayer.getHeldItem() != null
                        && minecraft.thePlayer.getHeldItem().getItem() instanceof ItemSword,
                delay.getFloat() / 50.0F, stopDuration.getFloat() / 50.0F,
                randomize.isEnabled(), damaged.isEnabled());
        lastAttackerTime = attackTime;
        applyInput();
    }

    public boolean isStopping() {
        return isEnabled() && cycle.isStopping();
    }

    public void applyInput() {
        if (isStopping() && (minecraft.thePlayer != player || minecraft.theWorld != world
                || minecraft.thePlayer == null || minecraft.thePlayer.isDead || minecraft.currentScreen != null
                || !minecraft.inGameHasFocus || !mode.getValue().equals(activeMode))) {
            clear();
            return;
        }

        if (!isStopping()) {
            releaseKey();
            return;
        }

        final int keyCode = (mode.is("Sneak") ? minecraft.gameSettings.keyBindSneak
                : mode.is("NoStop") ? minecraft.gameSettings.keyBindSprint : minecraft.gameSettings.keyBindForward).getKeyCode();
        if (controllingKey && controlledKeyCode != keyCode)
            releaseKey();

        controlledKeyCode = keyCode;
        controllingKey = true;
        KeyBinding.setKeyBindState(keyCode, mode.is("Sneak"));
        minecraft.thePlayer.setSprinting(false);
    }

    /** Reassert after LWJGL key events, immediately before vanilla samples movement. */
    public static void prepareInputHook() {
        final Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) return;

        final SprintResetModule module = vibe.getModuleManager().getModule(SprintResetModule.class);
        if (module == null) return;

        if (module.minecraft.thePlayer == null || module.minecraft.currentScreen != null
                || !module.minecraft.inGameHasFocus || !module.mode.getValue().equals(module.activeMode))
            module.clear();
        else
            module.applyInput();
    }

    private void releaseKey() {
        if (!controllingKey) return;

        final int key = controlledKeyCode;
        KeyBinding.setKeyBindState(key, minecraft.currentScreen == null && minecraft.inGameHasFocus
                && (key > 0 ? Keyboard.isCreated() && key < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(key)
                : key < 0 && Mouse.isCreated() && key + 100 >= 0 && key + 100 < Mouse.getButtonCount()
                && Mouse.isButtonDown(key + 100)));
        controllingKey = false;
        // NoStop restores the sprint flag as well as the physical sprint key.
        if ("NoStop".equals(activeMode) && minecraft.thePlayer != null && minecraft.thePlayer == player
                && !minecraft.thePlayer.isDead && minecraft.currentScreen == null && minecraft.inGameHasFocus)
            minecraft.thePlayer.setSprinting(true);
    }

    private void clear() {
        releaseKey();
        cycle.reset();
        world = null;
        player = null;
        activeMode = null;
    }

    @Override
    protected void onEnable() {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }
}
