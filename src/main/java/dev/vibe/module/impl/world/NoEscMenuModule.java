package dev.vibe.module.impl.world;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Suppress automatic pause requests while preserving explicitly pressed Escape. */
public final class NoEscMenuModule extends Module {
    public final BooleanSetting allowManual = addSetting(new BooleanSetting("Allow manual", true));
    private static final ThreadLocal<Boolean> MANUAL = new ThreadLocal<>();

    public NoEscMenuModule() { super("No ESC Menu", "Prevent the pause menu opening when leaving the game window", Category.WORLD, Keyboard.KEY_NONE); }

    public boolean blocks(boolean manual) {
        Minecraft mc = Minecraft.getMinecraft();
        return isEnabled() && mc != null && mc.thePlayer != null && mc.theWorld != null
                && (!manual || !allowManual.isEnabled());
    }
    public static boolean blocksMenuHook() {
        Vibe vibe = Vibe.getInstance();
        NoEscMenuModule module = vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(NoEscMenuModule.class);
        return module != null && module.blocks(Boolean.TRUE.equals(MANUAL.get()));
    }
    /** Only the actual Escape branch in runTick enters this scope. */
    public static void openManualHook(Object client) {
        Boolean previous = MANUAL.get();
        MANUAL.set(Boolean.TRUE);
        try { ((Minecraft) client).displayInGameMenu(); }
        finally { if (previous == null) MANUAL.remove(); else MANUAL.set(previous); }
    }
}
