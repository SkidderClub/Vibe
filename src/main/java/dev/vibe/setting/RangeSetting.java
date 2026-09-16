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
        double next=snap(value);setRange(next, Math.max(next,getMax()));
    }

    public void setMax(double value) {
        double next=snap(value);setRange(Math.min(getMin(),next), next);
    }

    /** Captures the selected handle for the complete gesture, including crossing. */
    public Drag beginDrag(double value){return new Drag(this,value);}
    public static final class Drag {
        private final RangeSetting range;private int handle;
        private Drag(RangeSetting range,double value){this.range=range;handle=range.getMin()==range.getMax()&&Math.abs(value-range.getMin())<range.increment*.5?-1:value>(range.getMin()+range.getMax())*.5?1:0;}
        public void move(double value){
            if(handle<0){if(Math.abs(value-range.getMin())<range.increment*.5)return;handle=value>range.getMin()?1:0;}
            if(handle==0)range.setMin(value);else range.setMax(value);
        }
    }

    private double snap(double value) {
        double clamped = Math.max(minimum, Math.min(maximum, value));
        double snapped = Math.round((clamped - minimum) / increment) * increment + minimum;
        return Math.max(minimum,Math.min(maximum,Math.round(snapped * 1000000.0D) / 1000000.0D));
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
