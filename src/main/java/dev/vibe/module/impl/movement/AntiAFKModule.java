package dev.vibe.module.impl.movement;

import dev.vibe.Vibe;
import dev.vibe.input.IdleActionSchedule;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.AutoClickerModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Short normal player inputs; EntityPlayerSP and PlayerControllerMP own all resulting packets. */
public final class AntiAFKModule extends Module {
    public final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Move", "Switch Items", "Jump", "Rotate", "Left Click"), Collections.singleton("Rotate")));
    public final NumberSetting idleDelay = addSetting(new NumberSetting("Idle Delay (s)", 30, 0, 600, 1));
    public final RangeSetting actionDelay = addSetting(new RangeSetting("Action Delay (ms)", 2000, 5000, 250, 60000, 50));
    public final NumberSetting moveDuration = addSetting(new NumberSetting("Move Duration (ticks)", 2, 1, 6, 1, () -> modes.isSelected("Move")));
    public final NumberSetting rotation = addSetting(new NumberSetting("Rotation Angle", 15, 1, 90, 1, () -> modes.isSelected("Rotate")));
    private final IdleActionSchedule schedule = new IdleActionSchedule();
    private final Random random = new Random();
    private World world;
    private EntityPlayerSP player;
    private int moveTicks, jumpTicks, nextMode, direction;
    private boolean pendingClick, ownedClick;

    public AntiAFKModule() { super("AntiAFK", "Perform normal player actions after you become idle", Category.MOVEMENT, Keyboard.KEY_NONE); }
    @Override protected void onEnable() { reset(now()); }
    @Override protected void onDisable() { reset(now()); world = null; player = null; }
    private static long now() { return System.nanoTime() / 1000000L; }
    private void reset(long time) { cancelActions(); schedule.userInput(time); }
    private void cancelActions() {
        moveTicks = jumpTicks = 0; pendingClick = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (ownedClick && mc != null && mc.gameSettings != null) VanillaClicks.restore(mc.gameSettings.keyBindAttack);
        ownedClick = false;
    }

    public void tick() { tick(now()); }
    /** Time is passed in for deterministic verification of the same production input path. */
    public void tick(long time) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || mc == null || mc.theWorld == null || mc.thePlayer == null) {
            reset(time); world = null; player = null; return;
        }
        if (world != mc.theWorld || player != mc.thePlayer) {
            reset(time); world = mc.theWorld; player = mc.thePlayer;
        }
        if (mc.currentScreen != null || mc.isGamePaused() || player.isDead || player.isRiding() || player.isPlayerSleeping()) {
            reset(time); return;
        }
        boolean freecam = FreecamModule.cameraActiveHook();
        if (heldInput(mc, freecam)) { reset(time); return; }
        MoveFixModule fix = module(MoveFixModule.class);
        AutoWalkModule walk = module(AutoWalkModule.class);
        ScaffoldModule scaffold = module(ScaffoldModule.class);
        if (fix != null && fix.hasForcedMovement() || walk != null && walk.isDriving()
                || scaffold != null && scaffold.isEnabled()) { cancelActions(); return; }
        if (!schedule.ready(time, (long) (idleDelay.getDouble() * 1000))) return;
        List<String> options = modes.getOptions();
        for (int i = 0; i < options.size(); i++) {
            String action = options.get((nextMode + i) % options.size());
            if (!modes.isSelected(action)) continue;
            if (action.equals("Move")) {
                if (!player.onGround || player.isInWater() || player.isInLava()) continue;
                // Two short sideways steps alternate, limiting long-term drift.
                direction = direction == 1 ? -1 : 1; moveTicks = moveDuration.getInt();
            } else if (action.equals("Jump")) {
                if (!player.onGround) continue;
                jumpTicks = 1;
            } else if (action.equals("Switch Items")) {
                if (player.isUsingItem()) continue;
                int slot = (player.inventory.currentItem + 1) % 9;
                int key = mc.gameSettings.keyBindsHotbar[slot].getKeyCode();
                if (key != Keyboard.KEY_NONE) KeyBinding.onTick(key);
                else player.inventory.changeCurrentItem(-1); // Vanilla wheel path for an unbound hotbar key.
            } else if (action.equals("Rotate")) {
                player.setAngles(rotation.getFloat() / .15F * (random.nextBoolean() ? 1 : -1), 0);
            } else if (action.equals("Left Click")) {
                if (player.isUsingItem()) continue;
                pendingClick = true;
            }
            nextMode = (nextMode + i + 1) % options.size();
            schedule.acted(time, (long) actionDelay.getMin(), (long) actionDelay.getMax(), random);
            return;
        }
    }
    public void tickEnd() {
        if (moveTicks > 0) moveTicks--;
        if (jumpTicks > 0) jumpTicks--;
        if (ownedClick) { VanillaClicks.restore(Minecraft.getMinecraft().gameSettings.keyBindAttack); ownedClick = false; }
        pendingClick = false;
    }

    private void apply(MovementInput input, boolean freecamBody) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || player == null || mc.thePlayer != player || mc.currentScreen != null) return;
        if (moveTicks > 0 && modes.isSelected("Move")) input.moveStrafe += direction * (input.sneak ? .3F : 1F);
        if (jumpTicks > 0 && modes.isSelected("Jump")) input.jump = true;
        if (freecamBody) {
            MoveFixModule fix = module(MoveFixModule.class);
            if (fix != null) fix.correctInjectedInput(input);
        }
    }
    /** Runs inside the existing MoveFix wrapper, ahead of silent rotation correction. */
    public static void inputHook(MovementInput input) {
        AntiAFKModule anti = module(AntiAFKModule.class);
        if (anti != null && !FreecamModule.cameraActiveHook()) anti.apply(input, false);
    }
    /** Runs after Freecam diverts the user's camera keys away from their original body. */
    public static void freecamBodyInputHook(MovementInput input) {
        AntiAFKModule anti = module(AntiAFKModule.class);
        if (anti != null) anti.apply(input, true);
    }
    /** Called after physical mouse events/combat clickers, before vanilla consumes attack presses. */
    public static void prepareClickHook() {
        AntiAFKModule anti = module(AntiAFKModule.class);
        if (anti == null || !anti.isEnabled() || !anti.pendingClick) return;
        anti.pendingClick = false;
        Minecraft mc = Minecraft.getMinecraft();
        AutoClickerModule clicker = module(AutoClickerModule.class);
        KillAuraModule aura = module(KillAuraModule.class);
        if (mc.currentScreen != null || mc.thePlayer != anti.player || !mc.inGameHasFocus || mc.thePlayer.isUsingItem()
                || VanillaClicks.physicallyDown(mc.gameSettings.keyBindAttack)
                || clicker != null && clicker.isEnabled() || aura != null && aura.isEnabled()) return;
        VanillaClicks.pulseAttack(mc.gameSettings.keyBindAttack); anti.ownedClick = true;
    }

    public void keyboardInput(int key) {
        if (isEnabled() && !cameraKey(Minecraft.getMinecraft(), key, FreecamModule.cameraActiveHook())) reset(now());
    }
    public void mouseInput(int button, int wheel, int dx, int dy) {
        boolean camera = FreecamModule.cameraActiveHook();
        if (isEnabled() && (button >= 0 && !cameraKey(Minecraft.getMinecraft(), button - 100, camera)
                || wheel != 0 || !camera && (dx != 0 || dy != 0))) reset(now());
    }
    /** A single scheduled vanilla attack may reach the body; manual Freecam clicks stay blocked. */
    public static boolean bodyClickBlockingHook(boolean cameraActive) {
        AntiAFKModule anti = module(AntiAFKModule.class);
        return cameraActive && (anti == null || !anti.isEnabled() || !anti.ownedClick);
    }
    public static void lookInputHook(float yaw, float pitch) {
        AntiAFKModule anti = module(AntiAFKModule.class);
        if (anti != null && anti.isEnabled() && !FreecamModule.cameraActiveHook() && (yaw != 0 || pitch != 0)) anti.reset(now());
    }
    private static boolean heldInput(Minecraft mc, boolean freecam) {
        if (Keyboard.isCreated()) for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++)
            if (Keyboard.isKeyDown(key) && !cameraKey(mc, key, freecam)) return true;
        if (Mouse.isCreated()) for (int button = 0; button < Mouse.getButtonCount(); button++)
            if (Mouse.isButtonDown(button) && !cameraKey(mc, button - 100, freecam)) return true;
        return false;
    }
    public static boolean cameraKey(Minecraft mc, int key, boolean freecam) {
        if (!freecam || mc == null || mc.gameSettings == null || key == 0) return false;
        return key == mc.gameSettings.keyBindForward.getKeyCode() || key == mc.gameSettings.keyBindBack.getKeyCode()
                || key == mc.gameSettings.keyBindLeft.getKeyCode() || key == mc.gameSettings.keyBindRight.getKeyCode()
                || key == mc.gameSettings.keyBindJump.getKeyCode() || key == mc.gameSettings.keyBindSneak.getKeyCode()
                || key == mc.gameSettings.keyBindSprint.getKeyCode();
    }
    private static <T extends Module> T module(Class<T> type) {
        return Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(type);
    }
}
