package dev.vibe.setting;

import java.util.function.BooleanSupplier;

public final class StringSetting extends Setting<String> {

    private final int maxLength;

    public StringSetting(String name, String defaultValue) {
        this(name, defaultValue, 96, null);
    }

    public StringSetting(String name, String defaultValue, int maxLength, BooleanSupplier visibleWhen) {
        super(name, defaultValue, visibleWhen);
        this.maxLength = Math.max(1, maxLength);
        setValue(defaultValue);
    }

    @Override
    protected String sanitize(String value) {
        String safe = value == null ? "" : value;
        return safe.length() > maxLength ? safe.substring(0, maxLength) : safe;
    }

    public int getMaxLength() {
        return maxLength;
    }
}
