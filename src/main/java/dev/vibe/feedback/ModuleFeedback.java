package dev.vibe.feedback;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.ToggleSoundModule;
import net.minecraft.client.Minecraft;

/** One path for keybinds, ClickGUI, commands, scripts and extensions. */
public final class ModuleFeedback {
    private final ModuleNotifications notifications = new ModuleNotifications();
    private boolean ready;

    public void start() { notifications.clear(); ready = true; }
    public ModuleNotifications notifications() { return notifications; }

    /** Message cards share the configured widget, themes and stacking with toggle feedback. */
    public static void message(String id, String sender, String body) {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getFeedback() == null || !vibe.getFeedback().ready || vibe.getModuleManager() == null
                || (vibe.getConfig() != null && vibe.getConfig().isLoading())) return;
        dev.vibe.module.impl.client.HudModule hud = vibe.getModuleManager().getModule(dev.vibe.module.impl.client.HudModule.class);
        if (hud == null || !hud.getHudElements().isSelectedIgnoreCase("notifications")) return;
        long now = System.nanoTime() / 1000000L;
        ModuleNotifications queue = vibe.getFeedback().notifications;
        queue.configure(hud.notifications.limit.getInt(), hud.notifications.durationMillis(), now);
        queue.addMessage(id, sender, body, now);
    }

    public static void changed(Module module) {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getFeedback() == null || !vibe.getFeedback().ready
                || vibe.getModuleManager() == null || vibe.isExtensionLoading()
                || (vibe.getConfig() != null && vibe.getConfig().isLoading())) return;
        long now = System.nanoTime() / 1000000L;
        dev.vibe.module.impl.client.HudModule hud = vibe.getModuleManager().getModule(dev.vibe.module.impl.client.HudModule.class);
        if (hud != null) vibe.getFeedback().notifications.configure(hud.notifications.limit.getInt(), hud.notifications.durationMillis(), now);
        vibe.getFeedback().notifications.add(module.getId(), module.getName(), module.isEnabled(), now);
        ToggleSoundModule sound = vibe.getModuleManager().getModule(ToggleSoundModule.class);
        // Disabling ToggleSound still acknowledges that final toggle.
        if (sound == null || (!sound.isEnabled() && sound != module) || sound.volume.getDouble() <= 0) return;
        Minecraft minecraft = vibe.getMinecraft();
        if (minecraft == null || minecraft.getSoundHandler() == null) return;
        net.minecraft.client.audio.ISound cue = sound.sound(module.isEnabled());
        Runnable playback = () -> {
            try { minecraft.getSoundHandler().playSound(cue); }
            catch (RuntimeException ignored) { /* An unavailable audio device must not prevent a module toggle. */ }
        };
        if (minecraft.isCallingFromMinecraftThread()) playback.run(); else minecraft.addScheduledTask(playback);
    }
}
