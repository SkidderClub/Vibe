package dev.vibe.module.impl;

import dev.vibe.game.slots.SlotEconomy;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.SlotsGui;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the adult-only, strictly virtual Slots arcade. */
public final class SlotsModule extends Module {
    private SlotEconomy economy;

    public SlotsModule() {
        super("Slots", "Adult-only virtual arcade with local encrypted Vibe Tokens", Category.MEME, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) { setEnabled(false); return; }
        minecraft.displayGuiScreen(new SlotsGui(this));
    }

    public synchronized SlotEconomy getEconomy() {
        if (economy == null) {
            Path profile = Minecraft.getMinecraft().mcDataDir.toPath().resolve("vibe/slots-economy.dat");
            economy = SlotEconomy.load(profile);
        }
        return economy;
    }
}
