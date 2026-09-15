package dev.vibe.setting;

import java.util.function.BooleanSupplier;

public final class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public BooleanSetting(String name, boolean defaultValue, BooleanSupplier visibleWhen) {
        super(name, defaultValue, visibleWhen);
    }

    public boolean isEnabled() {
        return getValue();
    }

    public void setEnabled(boolean enabled) {
        setValue(enabled);
    }

    public void toggle() {
        setValue(!getValue());
    }
}
