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
    private final ModeSetting editProfile=addSetting(new ModeSetting("Edit Appearance","Players","Players","Friends","Targets"));
    private final Esp2DSettings twoD = new Esp2DSettings(setting -> addSetting(setting), () -> visibleFor(0,"2D"));
    private final Esp2DSettings friends2D = new Esp2DSettings(setting -> addSetting(setting), () -> visibleFor(1,"2D"),"Friends ");
    private final Esp2DSettings targets2D = new Esp2DSettings(setting -> addSetting(setting), () -> visibleFor(2,"2D"),"Targets ");
    private final SkeletalSettings skeletal = new SkeletalSettings("",0), friendsSkeletal=new SkeletalSettings("Friends ",1), targetsSkeletal=new SkeletalSettings("Targets ",2);
    private final ChamsSettings invisibleChams = new ChamsSettings("Invisible", 0xBFFF5B6E);
    private final ChamsSettings visibleChams = new ChamsSettings("Visible", 0xBF2DE2C2);
    private final ChamsSettings friendHidden=new ChamsSettings("Friends ",1,"Invisible",0xBFFF5B6E),friendVisible=new ChamsSettings("Friends ",1,"Visible",0xBF5BE8A6);
    private final ChamsSettings targetHidden=new ChamsSettings("Targets ",2,"Invisible",0xBFFF5B6E),targetVisible=new ChamsSettings("Targets ",2,"Visible",0xBFFF5B6E);
    private final ColorSetting outlineColor = addSetting(new ColorSetting("Outline Color", 0xFF2DE2C2, () -> visibleFor(0,"3D")));
    private final ColorSetting fillColor = addSetting(new ColorSetting("Fill Color", 0x342DE2C2, () -> visibleFor(0,"3D")));
    private final ModeSetting playerColorMode = addSetting(new ModeSetting("Player Color Mode", "Color", () -> visibleFor(0,"3D"), "Color", "Team Color", "Rainbow"));
    private final BooleanSetting colorFade = addSetting(new BooleanSetting("Color Fade", false, () -> visibleFor(0,"3D")));
    private final ColorSetting fadeStart = addSetting(new ColorSetting("Fade Start", 0xFF2DE2C2, () -> visibleFor(0,"3D") && colorFade.isEnabled()));
    private final ColorSetting fadeEnd = addSetting(new ColorSetting("Fade End", 0xFFA855F7, () -> visibleFor(0,"3D") && colorFade.isEnabled()));
    private final NumberSetting fadeSpeed = addSetting(new NumberSetting("Fade Speed", 1.0D, 0.1D, 4.0D, 0.1D, () -> visibleFor(0,"3D") && colorFade.isEnabled()));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 1.5D, 1.0D, 5.0D, 0.5D, () -> visibleFor(0,"3D")));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true, () -> visibleFor(0,"3D")));
    // Retained legacy keys for profile/API compatibility; all 2D drawing reads get2D().
    private final BooleanSetting names = addSetting(new BooleanSetting("Names", true, () -> false));
    private final BooleanSetting health = addSetting(new BooleanSetting("Health Bar", true, () -> false));
    private final NumberSetting healthBarWidth = addSetting(new NumberSetting("Health Bar Width", 1.0D, 1.0D, 5.0D, 1.0D, () -> false));
    private final BooleanSetting healthFade = addSetting(new BooleanSetting("Health Fade", true, () -> false));
    private final ColorSetting healthStart = addSetting(new ColorSetting("Health Start Color", 0xFF2DE2C2, () -> false));
    private final ColorSetting healthEnd = addSetting(new ColorSetting("Health End Color", 0xFFFF5B6E, () -> false));
    private final BooleanSetting depthBackplate = addSetting(new BooleanSetting("Depth Backplate", false, () -> false));
    private final BooleanSetting heldItem = addSetting(new BooleanSetting("Held Item", true, () -> false));
    private final BooleanSetting distance = addSetting(new BooleanSetting("Distance", true, () -> false));
    private final ModeSetting namePosition = addSetting(position("Name Position", "Top", () -> false));
    private final ModeSetting healthPosition = addSetting(new ModeSetting("Health Bar Position", "Left", () -> false, "Left", "Right"));
    private final ModeSetting distancePosition = addSetting(position("Distance Position", "Name", () -> false, "Name"));
    private final ModeSetting heldPosition = addSetting(position("Held Item Position", "Bottom", () -> false));

    private final ProfileSettings friends = new ProfileSettings("Friends/Teams", 0xFF5BE8A6, () -> profileSelected(1));
    private final ProfileSettings targets = new ProfileSettings("Targets", 0xFFFF5B6E, () -> profileSelected(2));

    public EspModule() { super("ESP", "2D, 3D, Skeletal and customizable model Chams ESP", Category.VISUAL, Keyboard.KEY_P);
        for(dev.vibe.setting.Setting<?> setting:new java.util.ArrayList<dev.vibe.setting.Setting<?>>(getSettings()))
            if(setting instanceof ColorSetting)((ColorSetting)setting).enableEntityOptions(s->addSetting(s));
    }
    public ModeSetting getEditProfile(){return editProfile;}
    public int editingProfile(){return editProfile.is("Friends")?1:editProfile.is("Targets")?2:0;}
    private boolean profileSelected(int type){return editingProfile()==type;}
    private boolean visibleFor(int type,String mode){return profileSelected(type)&&modes.isSelected(mode)&&(type==0||!profileDefaults(type));}
    public boolean profileDefaults(int type){return type==1?friends.usePlayerDefaults.isEnabled():type==2?targets.usePlayerDefaults.isEnabled():false;}
    public int profileFor(EntityLivingBase entity){
        Vibe vibe=Vibe.getInstance();if(!(entity instanceof EntityPlayer)||vibe==null)return 0;
        if(vibe.getTargetManager()!=null&&vibe.getTargetManager().isTarget((EntityPlayer)entity))return 2;
        return isFriendOrTeam(entity)?1:0;
    }
    public int resolvedProfile(int type){return profileDefaults(type)?0:type;}
    public Esp2DSettings get2D(int type){return type==1?friends2D:type==2?targets2D:twoD;}
    public ChamsSettings getChams(int type,boolean visible){return type==1?(visible?friendVisible:friendHidden):type==2?(visible?targetVisible:targetHidden):(visible?visibleChams:invisibleChams);}
    public int teamColor(EntityLivingBase entity){
        Vibe vibe=Vibe.getInstance();TargetsModule t=vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(TargetsModule.class);
        return t!=null&&entity instanceof EntityPlayer?t.teamColor((EntityPlayer)entity):0;
    }

    private ModeSetting position(String name, String selected, java.util.function.BooleanSupplier visible, String... extra) {
        String[] values = new String[4 + extra.length]; values[0] = "Top"; values[1] = "Bottom"; values[2] = "Left"; values[3] = "Right";
        for (int i = 0; i < extra.length; i++) values[4 + i] = extra[i];
        return new ModeSetting(name, selected, visible, values);
    }

    public Esp2DSettings get2D() { return twoD; }
    public SkeletalSettings getSkeletal() { return skeletal; }
    public SkeletalSettings getSkeletal(int type) { return type==1?friendsSkeletal:type==2?targetsSkeletal:skeletal; }
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
    public NumberSetting getLineWidth(int profile) {return profile==1?friends.lineWidth:profile==2?targets.lineWidth:lineWidth;}
    public BooleanSetting getThroughWalls() { return throughWalls; }
    public BooleanSetting getThroughWalls(int profile) {return profile==1?friends.throughWalls:profile==2?targets.throughWalls:throughWalls;}
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
        Vibe vibe=Vibe.getInstance();if(vibe==null)return false;
        TargetsModule targets=vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(TargetsModule.class);
        return vibe.getFriendManager() != null && vibe.getFriendManager().isFriend(player)
                || targets!=null&&targets.isTeammate(player);
    }

    public Style getStyleFor(EntityLivingBase entity) {
        Style style = getPreviewStyle(profileFor(entity),teamColor(entity),entity.hurtTime>0);
        HypixelModule hypixel = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(HypixelModule.class);
        int override = hypixel == null ? 0 : hypixel.visualColor(entity);
        return override == 0 ? style : style.recolor(override);
    }
    public Style getPreviewStyle(int type) { return getPreviewStyle(type,0,false); }
    public Style getPreviewStyle(int type,int team,boolean hurt) {
        ProfileSettings p=type==1?friends:type==2?targets:null;
        Style style=p==null?baseStyle(team,hurt):p.toStyle(baseStyle(team,hurt),team);
        ColorSetting line=p==null||p.usePlayerDefaults.isEnabled()?outlineColor:p.outline;
        ColorSetting fill=p==null||p.usePlayerDefaults.isEnabled()?fillColor:p.fill;
        if(p!=null&&p.usePlayerDefaults.isEnabled())style=style.recolor(p.overrideColor.resolve(style.outline,team,hurt));
        return style.withColors(line.resolve(style.outline,team,hurt),fill.resolve(style.fill,team,hurt));
    }
    private Style baseStyle(int teamColor,boolean hurt) {
        Style base = new Style(outlineColor.getArgb(), fillColor.getArgb(), healthStart.getArgb(), healthEnd.getArgb(), names.isEnabled(), health.isEnabled(), heldItem.isEnabled(), distance.isEnabled(), depthBackplate.isEnabled(), healthFade.isEnabled(), healthBarWidth.getInt(), namePosition.getValue(), healthPosition.getValue(), distancePosition.getValue(), heldPosition.getValue());
        if (colorFade.isEnabled()) {
            float phase = (float) ((System.currentTimeMillis() % 5000L) / 5000.0D * fadeSpeed.getDouble());
            float blend = (float) ((Math.sin(phase * Math.PI * 2.0D) + 1.0D) * 0.5D);
            base = base.recolor(dev.vibe.ui.RenderUtils.blend(fadeStart.resolve(teamColor,hurt), fadeEnd.resolve(teamColor,hurt), blend));
        }
        if (playerColorMode.is("Rainbow")) return base.recolor(rainbow());
        if (playerColorMode.is("Team Color") && teamColor != 0) return base.recolor(teamColor);
        return base;
    }

    private static int recolor(int source, int rgb) { return (source & 0xFF000000) | (rgb & 0x00FFFFFF); }
    private static int rainbow() { return Color.HSBtoRGB((System.currentTimeMillis() % 4500L) / 4500.0F, 0.86F, 1.0F) | 0xFF000000; }

    /** Original skeletal controls, owned and enabled by ESP. */
    public final class SkeletalSettings {
        private final ColorSetting color;
        private final BooleanSetting rainbow,targets,walls,backplate;
        private final NumberSetting width;
        private SkeletalSettings(String prefix,int type) {
            java.util.function.BooleanSupplier visible=()->visibleFor(type,"Skeletal");
            color=addSetting(new ColorSetting(prefix+"Skeletal Color",0xFF2DE2C2,visible));
            rainbow=addSetting(new BooleanSetting(prefix+"Skeletal Rainbow",false,visible));
            width=addSetting(new NumberSetting(prefix+"Skeletal Line Width",2,1,5,.5,visible));
            targets=addSetting(new BooleanSetting(prefix+"Skeletal Only Targets",false,visible));
            walls=addSetting(new BooleanSetting(prefix+"Skeletal Through Walls",true,visible));
            backplate=addSetting(new BooleanSetting(prefix+"Skeletal Depth Backplate",false,visible));
        }        public boolean isEnabled() { return EspModule.this.isEnabled() && modes.isSelected("Skeletal"); }
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
            this("",0,side,argb);
        }
        private ChamsSettings(String prefix,int profile,String side, int argb) {
            java.util.function.BooleanSupplier selected = () -> visibleFor(profile,"Chams");side=prefix+side;
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
        private final NumberSetting lineWidth;
        private final BooleanSetting throughWalls;
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
            colorMode = addSetting(new ModeSetting(title + " Color Mode", "Color", ()->baseVisible.getAsBoolean()&&modes.isSelected("3D"), "Color", "Team Color", "Rainbow"));
            overrideColor = addSetting(new ColorSetting(title + " Override Color", color, ()->baseVisible.getAsBoolean()&&modes.isSelected("3D")));
            java.util.function.BooleanSupplier custom = new java.util.function.BooleanSupplier() { public boolean getAsBoolean() { return baseVisible.getAsBoolean() && modes.isSelected("3D") && !usePlayerDefaults.isEnabled(); } };
            outline = addSetting(new ColorSetting(title + " Outline", color, custom));
            fill = addSetting(new ColorSetting(title + " Fill", (color & 0x00FFFFFF) | 0x34000000, custom));
            lineWidth=addSetting(new NumberSetting(title+" Line Width",1.5,1,5,.5,custom));
            throughWalls=addSetting(new BooleanSetting(title+" Through Walls",true,custom));
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
        private Style withColors(int line,int background){return new Style(line,background,healthStart,healthEnd,names,health,held,distance,depth,healthFade,healthWidth,namePosition,healthPosition,distancePosition,heldPosition);}
        public int getOutline() { return outline; } public int getFill() { return fill; } public int getHealthStart() { return healthStart; } public int getHealthEnd() { return healthEnd; }
        public boolean hasNames() { return names; } public boolean hasHealth() { return health; } public boolean hasHeld() { return held; } public boolean hasDistance() { return distance; } public boolean hasDepth() { return depth; } public boolean hasHealthFade() { return healthFade; }
        public int getHealthWidth() { return healthWidth; } public String getNamePosition() { return namePosition; } public String getHealthPosition() { return healthPosition; } public String getDistancePosition() { return distancePosition; } public String getHeldPosition() { return heldPosition; }
    }
}
