package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.setting.*;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Client-observed health deltas. Hurt animations/attacks never invent an amount of damage. */
public final class DamageESPModule extends Module {
    public final MultiSelectSetting entities = addSetting(new MultiSelectSetting("Entities",
            Arrays.asList("Players", "Mobs", "Self"), Arrays.asList("Players", "Mobs")));
    public final NumberSetting range = addSetting(new NumberSetting("Range", 48, 4, 128, 1));
    public final BooleanSetting healing = addSetting(new BooleanSetting("Show Healing", false));
    public final BooleanSetting absorption = addSetting(new BooleanSetting("Include Absorption", true));
    public final ModeSetting units = addSetting(new ModeSetting("Units", "Health", "Health", "Hearts"));
    public final NumberSetting decimals = addSetting(new NumberSetting("Decimals", 1, 0, 2, 1));
    public final BooleanSetting signs = addSetting(new BooleanSetting("Show Signs", true));
    public final ModeSetting font = addSetting(new ModeSetting("Font", "Bold", "Smooth", "Bold", "Monospace", "Serif", "Minecraft"));
    public final NumberSetting scale = addSetting(new NumberSetting("Scale", 1, .5, 3, .05));
    public final BooleanSetting distanceScale = addSetting(new BooleanSetting("Distance Scaling", false));
    public final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", false));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color Mode", "Custom", "Custom", "Gradient", "Rainbow"));
    public final ColorSetting damageColor = addSetting(new ColorSetting("Damage Color", 0xFFFF707D, () -> !colorMode.is("Rainbow")));
    public final ColorSetting healColor = addSetting(new ColorSetting("Healing Color", 0xFF77E8B1, () -> healing.isEnabled()));
    public final ColorSetting endColor = addSetting(new ColorSetting("End Color", 0xFFFFCF88, () -> colorMode.is("Gradient")));
    public final NumberSetting rainbowSpeed = addSetting(new NumberSetting("Rainbow Speed", .25, .05, 2, .05, () -> colorMode.is("Rainbow")));
    public final BooleanSetting glow = addSetting(new BooleanSetting("Glow", true));
    public final BooleanSetting glowMatchesText = addSetting(new BooleanSetting("Glow Matches Text", true, () -> glow.isEnabled()));
    public final ColorSetting glowColor = addSetting(new ColorSetting("Glow Color", 0xFFFF587A, () -> glow.isEnabled() && !glowMatchesText.isEnabled()));
    public final NumberSetting glowRadius = addSetting(new NumberSetting("Glow Radius", 1.2, .5, 4, .1, () -> glow.isEnabled()));
    public final NumberSetting glowStrength = addSetting(new NumberSetting("Glow Strength", .35, .05, 1, .05, () -> glow.isEnabled()));
    public final BooleanSetting outline = addSetting(new BooleanSetting("Outline", true));
    public final ColorSetting outlineColor = addSetting(new ColorSetting("Outline Color", 0xC0201828, () -> outline.isEnabled()));
    public final NumberSetting outlineWidth = addSetting(new NumberSetting("Outline Width", .6, .3, 2, .1, () -> outline.isEnabled()));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", false));
    public final NumberSetting lifetime = addSetting(new NumberSetting("Lifetime (ms)", 1400, 300, 4000, 50));
    public final NumberSetting riseSpeed = addSetting(new NumberSetting("Rise Speed", 1.5, 0, 6, .1));
    public final ModeSetting easing = addSetting(new ModeSetting("Rise Easing", "Cubic Out", "Linear", "Cubic Out", "Exponential Out"));
    public final ModeSetting fade = addSetting(new ModeSetting("Fade", "Smooth", "Smooth", "Linear", "Late", "None"));
    public final NumberSetting fadeIn = addSetting(new NumberSetting("Fade In (ms)", 80, 0, 300, 10));
    public final BooleanSetting pop = addSetting(new BooleanSetting("Pop In", true));
    public final NumberSetting spread = addSetting(new NumberSetting("Horizontal Spread", .3, 0, 1, .05));
    public final NumberSetting drift = addSetting(new NumberSetting("Horizontal Drift", .15, 0, 1, .05));
    public final NumberSetting height = addSetting(new NumberSetting("Height Offset", .1, -.5, 1.5, .05));
    public final NumberSetting limit = addSetting(new NumberSetting("Max Numbers", 64, 8, 160, 8));
    private final IdentityHashMap<EntityLivingBase, Sample> samples = new IdentityHashMap<>();
    private final List<Marker> markers = new ArrayList<>();
    private final List<Marker> markerView = Collections.unmodifiableList(markers);
    private final Random random = new Random();
    private final DecimalFormat format = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private World world;
    private boolean countedAbsorption;
    private int generation;

    public DamageESPModule() { super("DamageESP", "Floating numbers from observed damage and healing", Category.VISUAL, Keyboard.KEY_NONE); }
    @Override protected void onDisable() { clear(); }
    public void clear() { world = null; samples.clear(); markers.clear(); }
    public List<Marker> markers() { return markerView; }
    public void tick() { tick(System.nanoTime() / 1000000L); }
    public void tick(long now) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || mc == null || mc.theWorld == null || mc.thePlayer == null) { clear(); return; }
        if (world != mc.theWorld) { clear(); world = mc.theWorld; countedAbsorption = absorption.isEnabled(); }
        if (countedAbsorption != absorption.isEnabled()) { samples.clear(); countedAbsorption = absorption.isEnabled(); }
        markers.removeIf(marker -> now - marker.born >= lifetime.getDouble());
        while (markers.size() > limit.getInt()) markers.remove(0);
        generation++;
        double x = FreecamModule.terrainXHook(mc.thePlayer, mc.thePlayer.posX);
        double y = FreecamModule.terrainYHook(mc.thePlayer, mc.thePlayer.posY);
        double z = FreecamModule.terrainZHook(mc.thePlayer, mc.thePlayer.posZ);
        int observed = 0;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity instanceof EntityArmorStand) continue;
            boolean self = entity == mc.thePlayer;
            if (!(self ? entities.isSelected("Self") : entity instanceof EntityPlayer ? entities.isSelected("Players") : entities.isSelected("Mobs"))) continue;
            double dx = entity.posX - x, dy = entity.posY - y, dz = entity.posZ - z;
            if (dx * dx + dy * dy + dz * dz > range.getDouble() * range.getDouble()) continue;
            if (++observed > 1024) break;
            EntityLivingBase living = (EntityLivingBase) entity;
            float hp = living.getHealth() + (countedAbsorption ? living.getAbsorptionAmount() : 0);
            if (!Float.isFinite(hp) || hp < 0) continue;
            Sample sample = samples.get(living);
            if (sample == null) { sample = new Sample(hp); samples.put(living, sample); }
            else {
                float delta = sample.health - hp;
                if (Math.abs(delta) > .001F && (delta > 0 || healing.isEnabled())) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = random.nextDouble() * spread.getDouble();
                    while (markers.size() >= limit.getInt()) markers.remove(0);
                    markers.add(new Marker(Math.abs(delta), delta < 0, living.posX + Math.cos(angle) * radius,
                            living.posY + living.height + height.getDouble(), living.posZ + Math.sin(angle) * radius,
                            Math.cos(angle), Math.sin(angle), now));
                }
                sample.health = hp;
            }
            sample.generation = generation;
        }
        samples.values().removeIf(sample -> sample.generation != generation);
    }
    public String text(Marker marker) {
        int key = decimals.getInt() | (units.is("Hearts") ? 4 : 0) | (signs.isEnabled() ? 8 : 0);
        if (marker.text == null || marker.textKey != key) {
            format.setMinimumFractionDigits(decimals.getInt()); format.setMaximumFractionDigits(decimals.getInt());
            marker.text = (signs.isEnabled() ? marker.heal ? "+" : "-" : "")
                    + format.format(marker.amount * (units.is("Hearts") ? .5 : 1));
            marker.textKey = key;
        }
        return marker.text;
    }
    public double progress(Marker marker, long now) { return Math.max(0, Math.min(1, (now - marker.born) / lifetime.getDouble())); }
    public double rise(double t) {
        double seconds = lifetime.getDouble() * .001, speed = riseSpeed.getDouble();
        if (easing.is("Cubic Out")) return speed * seconds / 3 * (1 - Math.pow(1 - t, 3));
        if (easing.is("Exponential Out")) return speed * seconds / 4 * (1 - Math.exp(-4 * t));
        return speed * seconds * t;
    }
    public double opacity(double t) {
        double in = fadeIn.getDouble() == 0 ? 1 : Math.min(1, t * lifetime.getDouble() / fadeIn.getDouble());
        double out = fade.is("None") ? 1 : fade.is("Late") ? 1 - smooth((t - .65) / .35)
                : fade.is("Linear") ? 1 - t : 1 - smooth(t);
        return Math.max(0, Math.min(1, in * out));
    }
    public int color(Marker marker, double t, long now) {
        if (marker.heal) return healColor.getArgb();
        if (colorMode.is("Rainbow")) return Color.HSBtoRGB((float) ((now * .001 * rainbowSpeed.getDouble()) % 1), .55F, 1F);
        return colorMode.is("Gradient") ? blend(damageColor.getArgb(), endColor.getArgb(), t) : damageColor.getArgb();
    }
    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static int blend(int a, int b, double t) {
        int color = 0; for (int shift = 0; shift <= 24; shift += 8)
            color |= (int) Math.round((a >>> shift & 255) * (1 - t) + (b >>> shift & 255) * t) << shift;
        return color;
    }
    private static final class Sample {
        float health; int generation;
        Sample(float health) { this.health = health; }
    }
    public static final class Marker {
        public final float amount; public final boolean heal;
        public final double x, y, z, driftX, driftZ; public final long born;
        private String text; private int textKey;
        Marker(float amount, boolean heal, double x, double y, double z, double driftX, double driftZ, long born) {
            this.amount = amount; this.heal = heal; this.x = x; this.y = y; this.z = z;
            this.driftX = driftX; this.driftZ = driftZ; this.born = born;
        }
    }
}
