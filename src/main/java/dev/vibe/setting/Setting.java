package dev.vibe.setting;

import dev.vibe.language.LanguageManager;
import java.util.function.BooleanSupplier;

/** A value exposed by a module and rendered by the ClickGUI. */
public abstract class Setting<T> {

    private final String name;
    private final BooleanSupplier visibleWhen;
    private final T defaultValue;
    private T value;

    protected Setting(String name, T defaultValue) {
        this(name, defaultValue, null);
    }

    protected Setting(String name, T defaultValue, BooleanSupplier visibleWhen) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.visibleWhen = visibleWhen;
    }

    public String getName() {
        return LanguageManager.translate(name);
    }

    /** Stable, untranslated setting key used for profile serialization. */
    public String getRawName() { return name; }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = sanitize(value);
    }

    public void resetToDefault() {
        setValue(defaultValue);
    }

    protected T sanitize(T value) {
        return value;
    }

    public boolean isVisible() {
        return visibleWhen == null || visibleWhen.getAsBoolean();
    }
}
