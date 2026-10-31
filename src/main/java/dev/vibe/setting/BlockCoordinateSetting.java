package dev.vibe.setting;

import java.util.function.BooleanSupplier;

/** Exact block-coordinate entry using every theme's existing text editor. */
public final class BlockCoordinateSetting extends StringSetting {
    public BlockCoordinateSetting(String name, int value, BooleanSupplier visible) {
        super(name, Integer.toString(value), 16, visible);
    }
    @Override protected String sanitize(String value) {
        try {
            double number = Double.parseDouble(value.trim());
            if (Double.isFinite(number) && number == Math.rint(number) && Math.abs(number) <= 30000000)
                return Integer.toString((int)number);
        } catch (RuntimeException ignored) { }
        return getValue() == null ? "0" : getValue();
    }
    public int getInt() { return Integer.parseInt(getValue()); }
    public void setInt(int value) { setValue(Integer.toString(value)); }
}
