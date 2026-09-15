package dev.vibe.setting;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Stores a color as ARGB and accepts readable #RRGGBB / #RRGGBBAA input. */
public final class ColorSetting extends Setting<Integer> {

    public ColorSetting(String name, int argb) {
        super(name, argb);
    }

    public ColorSetting(String name, int argb, BooleanSupplier visibleWhen) {
        super(name, argb, visibleWhen);
    }

    public int getArgb() {
        return getValue();
    }

    public int getRed() {
        return (getValue() >>> 16) & 255;
    }

    public int getGreen() {
        return (getValue() >>> 8) & 255;
    }

    public int getBlue() {
        return getValue() & 255;
    }

    public int getAlpha() {
        return (getValue() >>> 24) & 255;
    }

    public void setRgba(int red, int green, int blue, int alpha) {
        int safeRed = clamp(red);
        int safeGreen = clamp(green);
        int safeBlue = clamp(blue);
        int safeAlpha = clamp(alpha);
        setValue((safeAlpha << 24) | (safeRed << 16) | (safeGreen << 8) | safeBlue);
    }

    public String getHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", getRed(), getGreen(), getBlue(), getAlpha());
    }

    public boolean setHex(String text) {
        if (text == null) {
            return false;
        }
        String raw = text.trim().replace("#", "");
        if (raw.length() != 6 && raw.length() != 8) {
            return false;
        }
        try {
            int red = Integer.parseInt(raw.substring(0, 2), 16);
            int green = Integer.parseInt(raw.substring(2, 4), 16);
            int blue = Integer.parseInt(raw.substring(4, 6), 16);
            int alpha = raw.length() == 8 ? Integer.parseInt(raw.substring(6, 8), 16) : 255;
            setRgba(red, green, blue, alpha);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int clamp(int number) {
        return Math.max(0, Math.min(255, number));
    }
}
