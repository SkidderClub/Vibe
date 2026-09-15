package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.awt.Color;
import java.util.Arrays;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/** Shared 2D appearance, 3D boxes, original pose-based skeletons and model Chams. */
public final class EspModule extends Module {
    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("ESP Modes", Arrays.asList("2D", "3D", "Skeletal", "Chams"), Arrays.asList("3D")));
    private final Esp2DSettings twoD = new Esp2DSettings(setting -> addSetting(setting), () -> modes.isSelected("2D"));
    private final SkeletalSettings skeletal = new SkeletalSettings();
    private final ChamsSettings invisibleChams = new ChamsSettings("Invisible", 0xBFFF5B6E);
    private final ChamsSettings visibleChams = new ChamsSettings("Visible", 0xBF2DE2C2);
    private final ColorSetting outlineColor = addSetting(new ColorSetting("Outline Color", 0xFF2DE2C2, () -> modes.isSelected("3D")));
    private final ColorSetting fillColor = addSetting(new ColorSetting("Fill Color", 0x342DE2C2, () -> modes.isSelected("3D")));
    private final ModeSetting playerColorMode = addSetting(new ModeSetting("Player Color Mode", "Color", () -> modes.isSelected("3D"), "Color", "Team Color", "Rainbow"));
    private final BooleanSetting colorFade = addSetting(new BooleanSetting("Color Fade", false, () -> modes.isSelected("3D")));
    private final ColorSetting fadeStart = addSetting(new ColorSetting("Fade Start", 0xFF2DE2C2, () -> modes.isSelected("3D") && colorFade.isEnabled()));
    private final ColorSetting fadeEnd = addSetting(new ColorSetting("Fade End", 0xFFA855F7, () -> modes.isSelected("3D") && colorFade.isEnabled()));
    private final NumberSetting fadeSpeed = addSetting(new NumberSetting("Fade Speed", 1.0D, 0.1D, 4.0D, 0.1D, () -> modes.isSelected("3D") && colorFade.isEnabled()));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 1.5D, 1.0D, 5.0D, 0.5D, () -> modes.isSelected("3D")));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true, () -> modes.isSelected("3D")));
    // Retained legacy keys for profile/API compatibility; all 2D drawing reads get2D().
    private final BooleanSetting names = addSetting(new BooleanSetting("Names", true, () -> false));
    private final BooleanSetting health = addSetting(new BooleanSetting("Health Bar", true, () -> false));
    private final NumberSetting healthBarWidth = addSetting(new NumberSetting("Health Bar Width", 1.0D, 1.0D, 5.0D, 1.0D, () -> false));
    private final BooleanSetting healthFade = addSetting(new BooleanSetting("Health Fade", true, () -> false));
    private final ColorSetting healthStart = addSetting(new ColorSetting("Health Start Color", 0xFF2DE2C2, () -> false));
    private final ColorSetting healthEnd = addSetting(new ColorSetting("Health End Color", 0xFFFF5B6E, () -> false));
    private final BooleanSetting depthBackplate = addSetting(new BooleanSetting("Depth Backplate", true, () -> false));
    private final BooleanSetting heldItem = addSetting(new BooleanSetting("Held Item", true, () -> false));
    private final BooleanSetting distance = addSetting(new BooleanSetting("Distance", true, () -> false));
    private final ModeSetting namePosition = addSetting(position("Name Position", "Top", () -> false));
    private final ModeSetting healthPosition = addSetting(new ModeSetting("Health Bar Position", "Left", () -> false, "Left", "Right"));
    private final ModeSetting distancePosition = addSetting(position("Distance Position", "Name", () -> false, "Name"));
    private final ModeSetting heldPosition = addSetting(position("Held Item Position", "Bottom", () -> false));

    private final ProfileSettings friends = new ProfileSettings("Friends/Teams", 0xFF5BE8A6, () -> modes.isSelected("3D"));
    private final ProfileSettings targets = new ProfileSettings("Targets", 0xFFFF5B6E, () -> modes.isSelected("3D"));

    public EspModule() { super("ESP", "2D, 3D, Skeletal and customizable model Chams ESP", Category.VISUAL, Keyboard.KEY_P); }

    private ModeSetting position(String name, String selected, java.util.function.BooleanSupplier visible, String... extra) {
        String[] values = new String[4 + extra.length]; values[0] = "Top"; values[1] = "Bottom"; values[2] = "Left"; values[3] = "Right";
        for (int i = 0; i < extra.length; i++) values[4 + i] = extra[i];
        return new ModeSetting(name, selected, visible, values);
    }

    public Esp2DSettings get2D() { return twoD; }
    public SkeletalSettings getSkeletal() { return skeletal; }
    public MultiSelectSetting getModes() { return modes; }
    public ChamsSettings getInvisibleChams() { return invisibleChams; }
    public ChamsSettings getVisibleChams() { return visibleChams; }
    public ColorSetting getOutlineColor() { return outlineColor; }
    public ColorSetting getFillColor() { return fillColor; }
    /** Compatibility alias: this is the Friends/Teams default override colour. */
    public ColorSetting getFriendColor() { return friends.overrideColor; }
    /** Compatibility alias: this is the Targets default override colour. */
    public ColorSetting getTargetColor() { return targets.overrideColor; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public BooleanSetting getThroughWalls() { return throughWalls; }
    public BooleanSetting getNames() { return names; }
    public BooleanSetting getHealth() { return health; }
    public BooleanSetting getDepthBackplate() { return depthBackplate; }
    public NumberSetting getHealthBarWidth() { return healthBarWidth; }
    public BooleanSetting getHealthFade() { return healthFade; }
    public ColorSetting getHealthStart() { return healthStart; }
    public ColorSetting getHealthEnd() { return healthEnd; }
    public BooleanSetting getHeldItem() { return heldItem; }
    public BooleanSetting getDistance() { return distance; }
    public ModeSetting getNamePosition() { return namePosition; }
    public ModeSetting getHealthPosition() { return healthPosition; }
    public ModeSetting getDistancePosition() { return distancePosition; }
    public ModeSetting getHeldPosition() { return heldPosition; }
    public ModeSetting getPlayerColorMode() { return playerColorMode; }
    public BooleanSetting getColorFade() { return colorFade; }
    public ColorSetting getFadeStart() { return fadeStart; }
    public ColorSetting getFadeEnd() { return fadeEnd; }
    public NumberSetting getFadeSpeed() { return fadeSpeed; }
    public ProfileSettings getFriendsProfile() { return friends; }
    public ProfileSettings getTargetsProfile() { return targets; }

    public boolean isFriendOrTeam(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return false;
        EntityPlayer player = (EntityPlayer) entity;
        return Vibe.getInstance().getFriendManager() != null && Vibe.getInstance().getFriendManager().isFriend(player)
                || isScoreboardTeammate(player);
    }

    private boolean isScoreboardTeammate(EntityPlayer player) {
        if (Vibe.getInstance() == null || Vibe.getInstance().getMinecraft().thePlayer == null) return false;
        net.minecraft.scoreboard.Team own = Vibe.getInstance().getMinecraft().thePlayer.getTeam();
        return own != null && player != Vibe.getInstance().getMinecraft().thePlayer && own == player.getTeam();
    }

    public Style getStyleFor(EntityLivingBase entity) {
        ProfileSettings profile = null;
        if (entity instanceof EntityPlayer && Vibe.getInstance().getTargetManager() != null && Vibe.getInstance().getTargetManager().isTarget((EntityPlayer) entity)) profile = targets;
        else if (isFriendOrTeam(entity)) profile = friends;
        int teamColor = entity instanceof EntityPlayer ? teamRgb((EntityPlayer) entity) : 0;
        if (profile == null) return baseStyle(teamColor);
        return profile.toStyle(baseStyle(teamColor), teamColor);
    }

    public Style getPreviewStyle(int type) {
        Style base = baseStyle(0);
        if (type == 1) return friends.toStyle(base, 0);
        if (type == 2) return targets.toStyle(base, 0);
        return base;
    }

    private Style baseStyle(int teamColor) {
        Style base = new Style(outlineColor.getArgb(), fillColor.getArgb(), healthStart.getArgb(), healthEnd.getArgb(), names.isEnabled(), health.isEnabled(), heldItem.isEnabled(), distance.isEnabled(), depthBackplate.isEnabled(), healthFade.isEnabled(), healthBarWidth.getInt(), namePosition.getValue(), healthPosition.getValue(), distancePosition.getValue(), heldPosition.getValue());
        if (colorFade.isEnabled()) {
            float phase = (float) ((System.currentTimeMillis() % 5000L) / 5000.0D * fadeSpeed.getDouble());
            float blend = (float) ((Math.sin(phase * Math.PI * 2.0D) + 1.0D) * 0.5D);
            base = base.recolor(dev.vibe.ui.RenderUtils.blend(fadeStart.getArgb(), fadeEnd.getArgb(), blend));
        }
        if (playerColorMode.is("Rainbow")) return base.recolor(rainbow());
        if (playerColorMode.is("Team Color") && teamColor != 0) return base.recolor(teamColor);
        return base;
    }

    private int teamRgb(EntityPlayer player) {
        if (player != null && player.getTeam() instanceof net.minecraft.scoreboard.ScorePlayerTeam) {
            String prefix = ((net.minecraft.scoreboard.ScorePlayerTeam) player.getTeam()).getColorPrefix();
            for (int i = 0; prefix != null && i + 1 < prefix.length(); i++) {
                if (prefix.charAt(i) == '§') {
                    // Scoreboard prefixes use formatting *characters* (for
                    // example §c), not formatting names.  Resolving by name
                    // therefore silently failed for every standard team.
                    switch (Character.toLowerCase(prefix.charAt(i + 1))) {
                        case '0': return 0xFF000000;
                        case '1': return 0xFF0000AA;
                        case '2': return 0xFF00AA00;
                        case '3': return 0xFF00AAAA;
                        case '4': return 0xFFAA0000;
                        case '5': return 0xFFAA00AA;
                        case '6': return 0xFFFFAA00;
                        case '7': return 0xFFAAAAAA;
                        case '8': return 0xFF555555;
                        case '9': return 0xFF5555FF;
                        case 'a': return 0xFF55FF55;
                        case 'b': return 0xFF55FFFF;
                        case 'c': return 0xFFFF5555;
                        case 'd': return 0xFFFF55FF;
                        case 'e': return 0xFFFFFF55;
                        case 'f': return 0xFFFFFFFF;
                        default: break;
                    }
                }
            }
        }
        return 0;
    }

    private static int recolor(int source, int rgb) { return (source & 0xFF000000) | (rgb & 0x00FFFFFF); }
    private static int rainbow() { return Color.HSBtoRGB((System.currentTimeMillis() % 4500L) / 4500.0F, 0.86F, 1.0F) | 0xFF000000; }

    /** Original skeletal controls, owned and enabled by ESP. */
    public final class SkeletalSettings {
        private final java.util.function.BooleanSupplier visible = () -> modes.isSelected("Skeletal");
        private final ColorSetting color = addSetting(new ColorSetting("Skeletal Color", 0xFF2DE2C2, visible));
        private final BooleanSetting rainbow = addSetting(new BooleanSetting("Skeletal Rainbow", false, visible));
        private final NumberSetting width = addSetting(new NumberSetting("Skeletal Line Width", 2, 1, 5, .5, visible));
        private final BooleanSetting targets = addSetting(new BooleanSetting("Skeletal Only Targets", false, visible));
        private final BooleanSetting walls = addSetting(new BooleanSetting("Skeletal Through Walls", true, visible));
        private final BooleanSetting backplate = addSetting(new BooleanSetting("Skeletal Depth Backplate", true, visible));
        public boolean isEnabled() { return EspModule.this.isEnabled() && modes.isSelected("Skeletal"); }
        public ColorSetting getColor() { return color; }
        public BooleanSetting getRainbow() { return rainbow; }
        public NumberSetting getLineWidth() { return width; }
        public BooleanSetting getOnlyTargets() { return targets; }
        public BooleanSetting getThroughWalls() { return walls; }
        public BooleanSetting getDepthBackplate() { return backplate; }
    }

    /** Invisible means the portions occluded by world geometry, including partial cover. */
    public final class ChamsSettings {
        private final BooleanSetting armor;
        private final BooleanSetting showSkin;
        private final ModeSetting mode;
        private final ColorSetting color;

        private ChamsSettings(String side, int argb) {
            java.util.function.BooleanSupplier selected = () -> modes.isSelected("Chams");
            armor = addSetting(new BooleanSetting(side + " Armor", true, selected));
            showSkin = addSetting(new BooleanSetting(side + " Show Skin", false, selected));
            mode = addSetting(new ModeSetting(side + " Mode", "Flat", selected, "Flat", "Glow", "Metallic"));
            color = addSetting(new ColorSetting(side + " Color", argb, selected));
        }

        public BooleanSetting getArmor() { return armor; }
        public BooleanSetting getShowSkin() { return showSkin; }
        public ModeSetting getMode() { return mode; }
        public ColorSetting getColor() { return color; }
    }

    public final class ProfileSettings {
        private final BooleanSetting usePlayerDefaults;
        private final ModeSetting colorMode;
        private final ColorSetting overrideColor;
        private final ColorSetting outline;
        private final ColorSetting fill;
        private final ColorSetting healthStartColor;
        private final ColorSetting healthEndColor;
        private final BooleanSetting profileNames;
        private final BooleanSetting profileHealth;
        private final BooleanSetting profileHeld;
        private final BooleanSetting profileDistance;
        private final BooleanSetting profileDepth;
        private final BooleanSetting profileHealthFade;
        private final NumberSetting profileHealthWidth;
        private final ModeSetting profileNamePosition;
        private final ModeSetting profileHealthPosition;
        private final ModeSetting profileDistancePosition;
        private final ModeSetting profileHeldPosition;

        private ProfileSettings(final String title, int color, final java.util.function.BooleanSupplier baseVisible) {
            usePlayerDefaults = addSetting(new BooleanSetting(title + " Use Player Defaults", true, baseVisible));
            colorMode = addSetting(new ModeSetting(title + " Color Mode", "Color", baseVisible, "Color", "Team Color", "Rainbow"));
            overrideColor = addSetting(new ColorSetting(title + " Override Color", color, baseVisible));
            java.util.function.BooleanSupplier custom = new java.util.function.BooleanSupplier() { public boolean getAsBoolean() { return baseVisible.getAsBoolean() && !usePlayerDefaults.isEnabled(); } };
            outline = addSetting(new ColorSetting(title + " Outline", color, custom));
            fill = addSetting(new ColorSetting(title + " Fill", (color & 0x00FFFFFF) | 0x34000000, custom));
            healthStartColor = addSetting(new ColorSetting(title + " Health Start", color, () -> false));
            healthEndColor = addSetting(new ColorSetting(title + " Health End", 0xFFFF5B6E, () -> false));
            profileNames = addSetting(new BooleanSetting(title + " Names", true, () -> false));
            profileHealth = addSetting(new BooleanSetting(title + " Health", true, () -> false));
            profileHeld = addSetting(new BooleanSetting(title + " Held Item", true, () -> false));
            profileDistance = addSetting(new BooleanSetting(title + " Distance", true, () -> false));
            profileDepth = addSetting(new BooleanSetting(title + " Backplate", true, () -> false));
            profileHealthFade = addSetting(new BooleanSetting(title + " Health Fade", true, () -> false));
            profileHealthWidth = addSetting(new NumberSetting(title + " Health Width", 1.0D, 1.0D, 5.0D, 1.0D, () -> false));
            profileNamePosition = addSetting(position(title + " Name Position", "Top", () -> false));
            profileHealthPosition = addSetting(new ModeSetting(title + " Health Position", "Left", () -> false, "Left", "Right"));
            profileDistancePosition = addSetting(position(title + " Distance Position", "Name", () -> false, "Name"));
            profileHeldPosition = addSetting(position(title + " Held Position", "Bottom", () -> false));
        }

        public BooleanSetting getUsePlayerDefaults() { return usePlayerDefaults; }
        public ModeSetting getColorMode() { return colorMode; }
        public ColorSetting getOverrideColor() { return overrideColor; }
        public ColorSetting getOutline() { return outline; }
        public ColorSetting getFill() { return fill; }
        public ColorSetting getHealthStart() { return healthStartColor; }
        public ColorSetting getHealthEnd() { return healthEndColor; }
        public BooleanSetting getNames() { return profileNames; }
        public BooleanSetting getHealth() { return profileHealth; }
        public BooleanSetting getHeld() { return profileHeld; }
        public BooleanSetting getDistance() { return profileDistance; }
        public BooleanSetting getDepth() { return profileDepth; }
        public BooleanSetting getHealthFade() { return profileHealthFade; }
        public NumberSetting getHealthWidth() { return profileHealthWidth; }
        public ModeSetting getNamePosition() { return profileNamePosition; }
        public ModeSetting getHealthPosition() { return profileHealthPosition; }
        public ModeSetting getDistancePosition() { return profileDistancePosition; }
        public ModeSetting getHeldPosition() { return profileHeldPosition; }

        private Style toStyle(Style base, int teamRgb) {
            Style result = usePlayerDefaults.isEnabled() ? base : new Style(outline.getArgb(), fill.getArgb(), healthStartColor.getArgb(), healthEndColor.getArgb(), profileNames.isEnabled(), profileHealth.isEnabled(), profileHeld.isEnabled(), profileDistance.isEnabled(), profileDepth.isEnabled(), profileHealthFade.isEnabled(), profileHealthWidth.getInt(), profileNamePosition.getValue(), profileHealthPosition.getValue(), profileDistancePosition.getValue(), profileHeldPosition.getValue());
            int rgb = 0;
            if (colorMode.is("Team Color")) rgb = teamRgb == 0 ? overrideColor.getArgb() : teamRgb;
            else if (colorMode.is("Rainbow")) rgb = rainbow();
            else if (usePlayerDefaults.isEnabled()) rgb = overrideColor.getArgb();
            if (rgb != 0) result = result.recolor(rgb);
            return result;
        }
    }

    /** Immutable resolved appearance consumed by both world and 2D ESP. */
    public static final class Style {
        private final int outline, fill, healthStart, healthEnd;
        private final boolean names, health, held, distance, depth, healthFade;
        private final int healthWidth;
        private final String namePosition, healthPosition, distancePosition, heldPosition;
        private Style(int outline, int fill, int healthStart, int healthEnd, boolean names, boolean health, boolean held, boolean distance, boolean depth, boolean healthFade, int healthWidth, String namePosition, String healthPosition, String distancePosition, String heldPosition) { this.outline = outline; this.fill = fill; this.healthStart = healthStart; this.healthEnd = healthEnd; this.names = names; this.health = health; this.held = held; this.distance = distance; this.depth = depth; this.healthFade = healthFade; this.healthWidth = healthWidth; this.namePosition = namePosition; this.healthPosition = healthPosition; this.distancePosition = distancePosition; this.heldPosition = heldPosition; }
        private Style recolor(int rgb) { return new Style(EspModule.recolor(outline, rgb), EspModule.recolor(fill, rgb), EspModule.recolor(healthStart, rgb), EspModule.recolor(healthEnd, rgb), names, health, held, distance, depth, healthFade, healthWidth, namePosition, healthPosition, distancePosition, heldPosition); }
        public int getOutline() { return outline; } public int getFill() { return fill; } public int getHealthStart() { return healthStart; } public int getHealthEnd() { return healthEnd; }
        public boolean hasNames() { return names; } public boolean hasHealth() { return health; } public boolean hasHeld() { return held; } public boolean hasDistance() { return distance; } public boolean hasDepth() { return depth; } public boolean hasHealthFade() { return healthFade; }
        public int getHealthWidth() { return healthWidth; } public String getNamePosition() { return namePosition; } public String getHealthPosition() { return healthPosition; } public String getDistancePosition() { return distancePosition; } public String getHeldPosition() { return heldPosition; }
    }
}
