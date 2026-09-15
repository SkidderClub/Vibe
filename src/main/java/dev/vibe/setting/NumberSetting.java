package dev.vibe.setting;

import java.util.function.BooleanSupplier;

public final class NumberSetting extends Setting<Double> {

    private final double minimum;
    private final double maximum;
    private final double increment;

    public NumberSetting(String name, double defaultValue, double minimum, double maximum, double increment) {
        this(name, defaultValue, minimum, maximum, increment, null);
    }

    public NumberSetting(String name, double defaultValue, double minimum, double maximum, double increment,
            BooleanSupplier visibleWhen) {
        super(name, defaultValue, visibleWhen);
        this.minimum = Math.min(minimum, maximum);
        this.maximum = Math.max(minimum, maximum);
        this.increment = increment <= 0.0D ? 0.01D : increment;
        setValue(defaultValue);
    }

    @Override
    protected Double sanitize(Double value) {
        double safe = value == null ? minimum : Math.max(minimum, Math.min(maximum, value));
        double snapped = Math.round((safe - minimum) / increment) * increment + minimum;
        return Math.max(minimum, Math.min(maximum, Math.round(snapped * 1000000.0D) / 1000000.0D));
    }

    public double getDouble() {
        return getValue();
    }

    public float getFloat() {
        return getValue().floatValue();
    }

    public int getInt() {
        return (int) Math.round(getValue());
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    public double getIncrement() {
        return increment;
    }

    public void increase() {
        setValue(getValue() + increment);
    }

    public void decrease() {
        setValue(getValue() - increment);
    }
}
