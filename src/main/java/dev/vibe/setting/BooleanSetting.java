package dev.vibe.setting;

import java.util.function.BooleanSupplier;

public final class BooleanSetting extends Setting<Boolean> {
    private Runnable enableAction;

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

    /** Optional one-shot action, shared by every ClickGUI theme's boolean control. */
    public BooleanSetting onEnable(Runnable action) {
        enableAction = action;
        return this;
    }

    @Override public void setValue(Boolean value) {
        boolean wasEnabled = isEnabled();
        super.setValue(value);
        if (!wasEnabled && isEnabled() && enableAction != null) enableAction.run();
    }
}
