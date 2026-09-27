package dev.vibe.event;

import dev.vibe.language.LanguageManager;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Localizes ordinary vanilla buttons created by Vibe screens and events. */
public final class GuiButtonLocalizationEvents {
    private final Map<GuiButton, String> sourceLabels = new WeakHashMap<GuiButton, String>();

    @SubscribeEvent
    public void afterScreenButtonsAreCreated(GuiScreenEvent.InitGuiEvent.Post event) {
        for (GuiButton button : event.buttonList) {
            if (button == null || button.displayString == null) continue;
            String source = sourceLabels.get(button);
            if (source == null) {
                source = button.displayString;
                sourceLabels.put(button, source);
            }
            button.displayString = LanguageManager.translate(source);
        }
    }
}
