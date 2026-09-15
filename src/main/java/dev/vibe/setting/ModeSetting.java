package dev.vibe.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ModeSetting extends Setting<String> {

    private List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        this(name, defaultValue, null, modes);
    }

    public ModeSetting(String name, String defaultValue, BooleanSupplier visibleWhen, String... modes) {
        super(name, defaultValue, visibleWhen);
        replaceModes(Arrays.asList(modes));
        setValue(defaultValue);
    }

    @Override
    protected String sanitize(String value) {
        if (value != null) {
            for (String mode : modes) {
                if (mode.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return modes.get(0);
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }

    public List<String> getModes() {
        return modes;
    }

    /**
     * Refreshes folder-backed choices (Waifu, ROMs, shaders) without forcing
     * the user to restart the game. The current choice is retained whenever
     * it still exists; otherwise the first supplied option becomes active.
     */
    public void replaceModes(java.util.Collection<String> values) {
        List<String> safe = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty() && !safe.contains(value)) safe.add(value);
            }
        }
        if (safe.isEmpty()) throw new IllegalArgumentException("A mode setting needs at least one mode");
        String previous = getValue();
        modes = Collections.unmodifiableList(safe);
        if (previous != null) setValue(previous);
    }

    public void cycle(boolean backwards) {
        int index = modes.indexOf(getValue());
        int offset = backwards ? -1 : 1;
        setValue(modes.get((index + offset + modes.size()) % modes.size()));
    }
}
