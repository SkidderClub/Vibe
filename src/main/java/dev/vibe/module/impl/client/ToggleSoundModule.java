package dev.vibe.module.impl.client;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Locale;
import net.minecraft.client.audio.ISound;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

public final class ToggleSoundModule extends Module {
    public final ModeSetting style = addSetting(new ModeSetting("Style", "Soft", "Soft", "Pop", "Chime"));
    public final NumberSetting volume = addSetting(new NumberSetting("Volume", 35, 0, 100, 1));

    public ToggleSoundModule() {
        super("ToggleSound", "Play a soft sound when enabling or disabling modules", Category.CLIENT, Keyboard.KEY_NONE);
    }

    public ISound sound(boolean enabled) {
        final ResourceLocation location = new ResourceLocation("vibe", "toggle." + style.getValue().toLowerCase(Locale.ROOT) + (enabled ? ".on" : ".off"));
        final float gain = volume.getFloat() / 100;
        return new ISound() {
            @Override public ResourceLocation getSoundLocation() { return location; }
            @Override public boolean canRepeat() { return false; }
            @Override public int getRepeatDelay() { return 0; }
            @Override public float getVolume() { return gain; }
            @Override public float getPitch() { return 1; }
            @Override public float getXPosF() { return 0; }
            @Override public float getYPosF() { return 0; }
            @Override public float getZPosF() { return 0; }
            @Override public AttenuationType getAttenuationType() { return AttenuationType.NONE; }
        };
    }
}
