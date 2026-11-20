package dev.vibe.module.impl.client;

import dev.vibe.chat.ClientChat;
import dev.vibe.input.JInputMouseProvider;
import dev.vibe.input.RawMouseHelper;
import dev.vibe.input.RawMouseInputController;
import dev.vibe.input.RawMouseProvider;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Replaces LWJGL 2's cursor-based mouse deltas with direct unaccelerated
 * hardware input via JInput (Windows {@code WM_INPUT} / platform HID).
 */
public final class RawMouseInputModule extends Module {

    public static final String MODE_ALL_MICE = "All Mice";
    public static final String MODE_LOCK_ACTIVE = "Lock Active";

    private final ModeSetting mouseSelection = addSetting(new ModeSetting(
            "Mouse Selection", MODE_ALL_MICE, MODE_ALL_MICE, MODE_LOCK_ACTIVE));
    private final ModeSetting backend = addSetting(new ModeSetting(
            "Backend",
            RawMouseProvider.Backend.AUTO.getLabel(),
            RawMouseProvider.Backend.AUTO.getLabel(),
            RawMouseProvider.Backend.WINDOWS_RAW.getLabel(),
            RawMouseProvider.Backend.DEFAULT.getLabel()));
    private final NumberSetting pollDelay = addSetting(new NumberSetting("Poll Delay", 1.0D, 0.0D, 5.0D, 1.0D));
    private final BooleanSetting vanillaFallback = addSetting(new BooleanSetting("Vanilla Fallback", true));
    private final BooleanSetting rescanMouse = addSetting(new BooleanSetting("Rescan Mouse", false)
            .onEnable(this::triggerRescan));

    private final RawMouseInputController controller;
    private final AtomicReference<String> pendingNotice = new AtomicReference<String>();
    private RawMouseHelper installedHelper;
    private MouseHelper originalHelper;
    private RawMouseProvider.Backend appliedBackend = RawMouseProvider.Backend.AUTO;

    public RawMouseInputModule() {
        this(new JInputMouseProvider());
    }

    public RawMouseInputModule(RawMouseProvider provider) {
        super("RawMouseInput", "Bypass OS mouse acceleration with direct hardware input",
                Category.CLIENT, Keyboard.KEY_NONE);
        this.controller = new RawMouseInputController(provider, RawMouseInputModule::isGameCapturingMouse);
    }

    public ModeSetting getMouseSelection() {
        return mouseSelection;
    }

    public ModeSetting getBackend() {
        return backend;
    }

    public NumberSetting getPollDelay() {
        return pollDelay;
    }

    public BooleanSetting getVanillaFallback() {
        return vanillaFallback;
    }

    public BooleanSetting getRescanMouse() {
        return rescanMouse;
    }

    public RawMouseInputController getController() {
        return controller;
    }

    @Override
    protected void onEnable() {
        syncSettingsToController();
        appliedBackend = selectedBackend();
        controller.start(appliedBackend);
        installMouseHelper();
    }

    @Override
    protected void onDisable() {
        restoreMouseHelper();
        controller.stop();
        pendingNotice.set(null);
    }

    /** Keeps live settings, MouseHelper installation, and scan notifications synchronized. */
    public void tick() {
        flushNotice();
        if (!isEnabled()) {
            return;
        }
        syncSettingsToController();
        installMouseHelper();
        RawMouseProvider.Backend desired = selectedBackend();
        if (desired != appliedBackend) {
            appliedBackend = desired;
            controller.requestRescan(desired, false, this::onScanComplete);
        }
    }

    /** Stops the background polling thread cleanly on client shutdown. */
    public void shutdown() {
        restoreMouseHelper();
        controller.shutdown();
    }

    private void triggerRescan() {
        rescanMouse.setEnabled(false);
        if (isConfigLoading()) {
            return;
        }
        if (!isEnabled()) {
            queueNotice("RawMouseInput is disabled; enable it to scan hardware mice.");
            return;
        }
        RawMouseProvider.Backend desired = selectedBackend();
        appliedBackend = desired;
        if (!controller.requestRescan(desired, true, this::onScanComplete)) {
            queueNotice("RawMouseInput: wait a moment before rescanning again.");
        }
    }

    private void onScanComplete(RawMouseInputController.ScanResult result) {
        if (result == null) {
            return;
        }
        if (result.deviceCount <= 0) {
            queueNotice("RawMouseInput: no raw mouse devices found (" + result.backend.getLabel()
                    + "); " + (vanillaFallback.isEnabled() ? "using vanilla fallback." : "mouse input paused."));
            return;
        }
        if (result.manual) {
            String summary = result.deviceCount == 1
                    ? result.deviceNames.get(0)
                    : result.deviceCount + " mouse devices";
            queueNotice("RawMouseInput: detected " + summary + " (" + result.backend.getLabel() + ").");
        }
    }

    private void syncSettingsToController() {
        controller.setLockActive(mouseSelection.is(MODE_LOCK_ACTIVE));
        controller.setPollDelayMs(pollDelay.getInt());
    }

    private RawMouseProvider.Backend selectedBackend() {
        return RawMouseProvider.Backend.fromLabel(backend.getValue());
    }

    private void installMouseHelper() {
        Minecraft mc = safeMinecraft();
        if (mc == null || mc.mouseHelper == null) {
            return;
        }
        if (mc.mouseHelper == installedHelper && installedHelper != null) {
            return;
        }
        MouseHelper base = mc.mouseHelper instanceof RawMouseHelper
                ? ((RawMouseHelper) mc.mouseHelper).getDelegate()
                : mc.mouseHelper;
        originalHelper = base;
        installedHelper = new RawMouseHelper(base, controller, vanillaFallback::isEnabled);
        mc.mouseHelper = installedHelper;
    }

    private void restoreMouseHelper() {
        Minecraft mc = safeMinecraft();
        if (mc != null && installedHelper != null && mc.mouseHelper == installedHelper && originalHelper != null) {
            mc.mouseHelper = originalHelper;
        }
        installedHelper = null;
        originalHelper = null;
    }

    private void queueNotice(String message) {
        pendingNotice.set(message);
    }

    private void flushNotice() {
        String message = pendingNotice.getAndSet(null);
        if (message == null) {
            return;
        }
        Minecraft mc = safeMinecraft();
        if (mc != null && mc.thePlayer != null) {
            ClientChat.send(message);
        }
    }

    private static Minecraft safeMinecraft() {
        try {
            return Minecraft.getMinecraft();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isGameCapturingMouse() {
        Minecraft mc = safeMinecraft();
        if (mc == null || !mc.inGameHasFocus || mc.currentScreen != null) {
            return false;
        }
        try {
            return Display.isCreated() && Display.isActive() && Mouse.isCreated() && Mouse.isGrabbed();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
