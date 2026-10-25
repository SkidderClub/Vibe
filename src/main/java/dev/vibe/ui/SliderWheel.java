package dev.vibe.ui;

import dev.vibe.setting.*;

/** Shared wheel increments for every settings surface; consumes numeric tracks even at a bound. */
public final class SliderWheel {
    private SliderWheel() { }
    public static int nearestHandle(RangeSetting range,double fraction) {
        double value=range.getMinimum()+Math.max(0,Math.min(1,fraction))*(range.getMaximum()-range.getMinimum());
        return value>(range.getMin()+range.getMax())*.5?1:0;
    }
    public static boolean adjust(Setting<?> setting,double notches,int handle) {
        if(notches==0||!Double.isFinite(notches))return false;
        if(setting instanceof NumberSetting) {
            NumberSetting n=(NumberSetting)setting;n.setValue(n.getDouble()+notches*n.getIncrement());return true;
        }
        if(setting instanceof RangeSetting) {
            RangeSetting r=(RangeSetting)setting;
            if(handle==0)r.setMin(Math.min(r.getMax(),r.getMin()+notches*r.getIncrement()));
            else r.setMax(Math.max(r.getMin(),r.getMax()+notches*r.getIncrement()));
            return true;
        }
        return false;
    }
    public static double notches(int delta) {return delta==0?0:Math.copySign(Math.max(1,Math.abs(delta)/120D),delta);}
}
