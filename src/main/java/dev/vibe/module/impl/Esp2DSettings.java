package dev.vibe.module.impl;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Persisted appearance shared by the HUD, GTA7 and the interactive editor. */
public final class Esp2DSettings {
    public final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final Consumer<Setting<?>> register;
    private final BooleanSupplier visible;
    private final String keyPrefix;
    public final NumberSetting distanceScaling;
    public final NumberSetting gap;
    public final Gradient global;
    public final TextStyle text;
    public final List<Element> elements = new ArrayList<Element>();
    public final Element box, healthBar, armorBar, name, distance, itemName, itemIcon, health, armor;

    public Esp2DSettings(Consumer<Setting<?>> register, BooleanSupplier visible) {
        this(register,visible,"");
    }
    public Esp2DSettings(Consumer<Setting<?>> register, BooleanSupplier visible,String keyPrefix) {
        this.register = register;
        this.visible = visible;
        this.keyPrefix=keyPrefix;
        distanceScaling = add(new NumberSetting(keyPrefix+"2D Distance Scaling", 1, 0, 1, .05, visible));
        gap = add(new NumberSetting(keyPrefix+"2D Element Gap", 3, 1, 12, .5, visible));
        global = new Gradient(keyPrefix+"2D Global Gradient", () -> visible.getAsBoolean() && usesGlobalGradient());
        text = new TextStyle(keyPrefix+"2D Default Text", () -> visible.getAsBoolean() && usesDefaultText());
        box = element("Box", Kind.BOX, "Top", true);
        healthBar = element("Health Bar", Kind.BAR, "Left", true);
        armorBar = element("Armor Bar", Kind.BAR, "Right", false);
        name = element("Name", Kind.TEXT, "Top", true);
        distance = element("Distance", Kind.TEXT, "Bottom", true);
        itemName = element("Item Name", Kind.TEXT, "Bottom", true);
        itemIcon = element("Item Icon", Kind.ICON, "Bottom", false);
        health = element("Health", Kind.TEXT, "Left Up", false);
        armor = element("Armor Display", Kind.ARMOR, "Right", false);
        healthBar.color.solid.setValue(0xFF53E88C);
        armorBar.color.solid.setValue(0xFF64A8FF);
    }

    public boolean usesGlobalGradient() {
        for(Element e:elements)if(e.enabled.isEnabled() && (e.kind==Kind.BOX || e.kind==Kind.BAR || e.kind==Kind.TEXT)
                && (e.kind==Kind.TEXT?e.resolvedText().color:e.color).mode.is("Global Gradient"))return true;
        return false;
    }
    public boolean usesDefaultText() {
        for(Element e:elements)if(e.enabled.isEnabled() && e.kind==Kind.TEXT && e.useDefaultText.isEnabled())return true;
        return false;
    }
    private <T extends Setting<?>> T add(T setting) { settings.add(setting); register.accept(setting); return setting; }
    private Element element(String title, Kind kind, String side, boolean enabled) {
        Element result = new Element(title, kind, side, enabled, elements.size()); elements.add(result); return result;
    }
    public enum Kind { BOX, BAR, TEXT, ICON, ARMOR }

    public final class Element {
        public final String title, prefix;
        public final Kind kind;
        public final BooleanSetting enabled, outline, useDefaultText, corners, backgroundEnabled;
        public final NumberSetting scale, width, outlineWidth, rounding, cornerLength, cornerDistance, order, offset;
        public final ModeSetting position, metric;
        public final ColorSetting background, outlineColor;
        public final Paint color;
        public final TextStyle text;
        public final List<Setting<?>> settings;

        private Element(String title, Kind kind, String side, boolean on, int index) {
            this.title = title; this.kind = kind; prefix = keyPrefix+"2D " + title + " ";
            int start = Esp2DSettings.this.settings.size();
            enabled = add(new BooleanSetting(prefix + "Enabled", on, visible));
            BooleanSupplier shown = () -> visible.getAsBoolean() && enabled.isEnabled();
            scale = add(new NumberSetting(prefix + "Scale", 1, .25, 4, .05, shown));
            position = add(new ModeSetting(prefix + "Position", side, () -> shown.getAsBoolean() && kind != Kind.BOX,
                    kind == Kind.BAR || kind == Kind.ARMOR ? new String[]{"Left", "Top", "Bottom", "Right"}
                    : new String[]{"Top", "Bottom", "Left Up", "Left Down", "Right Up", "Right Down"}));
            order = add(new NumberSetting(prefix + "Order", index, 0, 100, 1, () -> shown.getAsBoolean() && kind != Kind.BOX));
            offset = add(new NumberSetting(prefix + "Along Edge", 0, -200, 200, 1, () -> shown.getAsBoolean() && kind != Kind.BOX));
            width = add(new NumberSetting(prefix + "Width", kind == Kind.BOX ? 1.5 : 2, .5, 12, .25,
                    () -> shown.getAsBoolean() && (kind == Kind.BAR || kind == Kind.BOX)));
            outline = add(new BooleanSetting(prefix + "Outline", kind == Kind.BAR || kind == Kind.BOX, shown));
            outlineWidth = add(new NumberSetting(prefix + "Outline Width", 1, .25, 4, .25, () -> shown.getAsBoolean() && outline.isEnabled()));
            outlineColor = add(new ColorSetting(prefix + "Outline Color", 0xCF000000, () -> shown.getAsBoolean() && outline.isEnabled()));
            backgroundEnabled=add(new BooleanSetting(prefix+"Background Enabled",false,shown));
            background = add(new ColorSetting(prefix + "Background", 0x80000000,
                    () -> shown.getAsBoolean() && backgroundEnabled.isEnabled()));
            corners = add(new BooleanSetting(prefix + "Corners Only", true, () -> shown.getAsBoolean() && kind == Kind.BOX));
            cornerLength = add(new NumberSetting(prefix + "Corner Length", .25, .05, .5, .01, () -> shown.getAsBoolean() && kind == Kind.BOX && corners.isEnabled()));
            cornerDistance = add(new NumberSetting(prefix + "Corner Distance", 0, 0, 100, 1, () -> shown.getAsBoolean() && kind == Kind.BOX && corners.isEnabled()));
            rounding = add(new NumberSetting(prefix + "Rounding", 0, 0, 24, .5, () -> shown.getAsBoolean() && kind == Kind.BOX));
            useDefaultText = add(new BooleanSetting(prefix + "Use Default Text", true, () -> shown.getAsBoolean() && kind == Kind.TEXT));
            text = new TextStyle(prefix + "Text", () -> shown.getAsBoolean() && kind == Kind.TEXT && !useDefaultText.isEnabled());
            color = new Paint(prefix + "Color", () -> shown.getAsBoolean() && kind != Kind.TEXT && kind != Kind.ICON && kind != Kind.ARMOR);
            metric = add(new ModeSetting(prefix + "Metric", "Meters", () -> shown.getAsBoolean() && "Distance".equals(title), "Meters", "Feet"));
            settings = Collections.unmodifiableList(new ArrayList<Setting<?>>(Esp2DSettings.this.settings.subList(start, Esp2DSettings.this.settings.size())));
        }
        public TextStyle resolvedText() { return useDefaultText.isEnabled() ? Esp2DSettings.this.text : text; }
        public boolean vertical() { return position.getValue().startsWith("Left") || position.getValue().startsWith("Right"); }
    }

    public final class TextStyle {
        public final ModeSetting font;
        public final NumberSetting size;
        public final BooleanSetting shadow;
        public final Paint color;
        private TextStyle(String prefix, BooleanSupplier visible) {
            font = add(new ModeSetting(prefix + " Font", "Minecraft", visible, "Minecraft", "Sans", "Sans Bold"));
            size = add(new NumberSetting(prefix + " Size", 9, 5, 24, .5, visible));
            shadow = add(new BooleanSetting(prefix + " Shadow", true, visible));
            color = new Paint(prefix + " Color", visible);
        }
    }

    public final class Paint {
        public final ModeSetting mode;
        public final ColorSetting solid;
        public final Gradient gradient;
        public final NumberSetting rainbowSpeed, rainbowSaturation;
        private Paint(String prefix, BooleanSupplier visible) {
            mode = add(new ModeSetting(prefix + " Mode", "Static", visible, "Static", "Team", "Global Gradient", "Custom Gradient", "Rainbow"));
            solid = add(new ColorSetting(prefix + " Static", 0xFFFFFFFF, visible));
            gradient = new Gradient(prefix + " Gradient", () -> visible.getAsBoolean() && mode.is("Custom Gradient"));
            rainbowSpeed = add(new NumberSetting(prefix + " Rainbow Speed", .2, 0, 3, .01, () -> visible.getAsBoolean() && mode.is("Rainbow")));
            rainbowSaturation = add(new NumberSetting(prefix + " Rainbow Saturation", .8, 0, 1, .01, () -> visible.getAsBoolean() && mode.is("Rainbow")));
        }
    }

    /** Eight independently positioned stops; unused stops stay saved when reducing the count. */
    public final class Gradient {
        public final String prefix;
        public final NumberSetting count, direction, speed;
        public final List<ColorSetting> colors = new ArrayList<ColorSetting>();
        public final List<NumberSetting> positions = new ArrayList<NumberSetting>();
        private Gradient(String prefix, BooleanSupplier visible) {
            this.prefix = prefix;
            count = add(new NumberSetting(prefix + " Stops", 3, 2, 8, 1, visible));
            direction = add(new NumberSetting(prefix + " Direction", 90, 0, 360, 1, visible));
            speed = add(new NumberSetting(prefix + " Speed", 0, -3, 3, .01, visible));
            int[] defaults = {0xFF3FFF58, 0xFFB22AF5, 0xFFA1EF9E, 0xFFFFC857, 0xFF40DCFF, 0xFFFF609B, 0xFFFFFFFF, 0xFF5C6AFF};
            for (int i = 0; i < 8; i++) {
                final int stop = i;
                BooleanSupplier shown = () -> visible.getAsBoolean() && stop < count.getInt();
                colors.add(add(new ColorSetting(prefix + " Stop " + (i + 1), defaults[i], shown)));
                positions.add(add(new NumberSetting(prefix + " Position " + (i + 1), Math.min(1, i * .5), 0, 1, .01, shown)));
            }
        }
    }
}
