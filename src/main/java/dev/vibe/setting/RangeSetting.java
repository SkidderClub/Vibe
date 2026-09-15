package dev.vibe.setting;

import java.util.function.BooleanSupplier;

/** A bounded two-handle slider used for min/max values such as CPS and delays. */
public final class RangeSetting extends Setting<RangeSetting.Range> {

    private final double minimum;
    private final double maximum;
    private final double increment;

    public RangeSetting(String name, double lower, double upper, double minimum, double maximum, double increment) {
        this(name, lower, upper, minimum, maximum, increment, null);
    }

    public RangeSetting(String name, double lower, double upper, double minimum, double maximum, double increment,
            BooleanSupplier visibleWhen) {
        super(name, new Range(lower, upper), visibleWhen);
        this.minimum = Math.min(minimum, maximum);
        this.maximum = Math.max(minimum, maximum);
        this.increment = increment <= 0.0D ? 0.01D : increment;
        setRange(lower, upper);
    }

    @Override
    protected Range sanitize(Range value) {
        double lower = snap(value == null ? minimum : value.lower);
        double upper = snap(value == null ? maximum : value.upper);
        return lower <= upper ? new Range(lower, upper) : new Range(upper, lower);
    }

    public void setRange(double lower, double upper) {
        setValue(new Range(lower, upper));
    }

    public double getMin() {
        return getValue().lower;
    }

    public double getMax() {
        return getValue().upper;
    }

    public float getMinFloat() {
        return (float) getMin();
    }

    public float getMaxFloat() {
        return (float) getMax();
    }

    public int getMinInt() {
        return (int) Math.round(getMin());
    }

    public int getMaxInt() {
        return (int) Math.round(getMax());
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

    public void setMin(double value) {
        setRange(value, getMax());
    }

    public void setMax(double value) {
        setRange(getMin(), value);
    }

    private double snap(double value) {
        double clamped = Math.max(minimum, Math.min(maximum, value));
        double snapped = Math.round((clamped - minimum) / increment) * increment + minimum;
        return Math.round(snapped * 1000000.0D) / 1000000.0D;
    }

    public static final class Range {
        private final double lower;
        private final double upper;

        private Range(double lower, double upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }
}
