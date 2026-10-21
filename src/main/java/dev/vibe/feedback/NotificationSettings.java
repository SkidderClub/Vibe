package dev.vibe.feedback;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.Consumer;

/** Shared by the HUD inspector, ClickGUI, chat commands and saved profiles. */
public final class NotificationSettings {
    public final NumberSetting duration = new NumberSetting("Notifications Duration", 3.2, 1, 10, .2);
    public final NumberSetting limit = new NumberSetting("Notifications Maximum", 4, 1, 8, 1);
    public final NumberSetting width = new NumberSetting("Notifications Width", 190, 140, 320, 5);
    public final NumberSetting height = new NumberSetting("Notifications Height", 38, 30, 60, 2);
    public final NumberSetting gap = new NumberSetting("Notifications Gap", 7, 2, 16, 1);
    public final BooleanSetting animation = new BooleanSetting("Notifications Animation", true);
    public final ModeSetting font = new ModeSetting("Notifications Font", "Bold", "Bold", "Regular");
    public final BooleanSetting icons = new BooleanSetting("Notifications Icons", true);
    public final BooleanSetting status = new BooleanSetting("Notifications Status", true);
    public final BooleanSetting progress = new BooleanSetting("Notifications Progress", true);
    public final BooleanSetting shadow = new BooleanSetting("Notifications Shadow", true);
    public final BooleanSetting border = new BooleanSetting("Notifications Border", true);
    public final NumberSetting radius = new NumberSetting("Notifications Corners", 9, 0, 18, 1);
    public final NumberSetting opacity = new NumberSetting("Notifications Background Opacity", 1, .2, 1, .05);
    public final ColorSetting enabledColor = new ColorSetting("Notifications Enabled Color", 0xFF89DDB6);
    public final ColorSetting disabledColor = new ColorSetting("Notifications Disabled Color", 0xFFEBADB3);
    public final ColorSetting background = new ColorSetting("Notifications Background Color", 0xF8181D25);
    public final ColorSetting text = new ColorSetting("Notifications Text Color", 0xFFF0F3F8);
    public final List<Setting<?>> all;

    public NotificationSettings(Consumer<Setting<?>> register) {
        all = Collections.unmodifiableList(Arrays.<Setting<?>>asList(duration, limit, width, height, gap, animation,
                font, icons, status, progress, shadow, border, radius, opacity, enabledColor, disabledColor, background, text));
        all.forEach(register);
    }
    public long durationMillis() { return Math.round(duration.getDouble() * 1000); }
    public int stride() { return height.getInt() + gap.getInt(); }
}
