package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Client feedback for successful local attacks. */
public final class HitmarkerModule extends Module {

    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("2D (Crosshair)", "3D (World)", "Torus", "Hologram"), Arrays.asList("2D (Crosshair)")));
    public final NumberSetting hologramLifetime=addSetting(new NumberSetting("Hologram Lifetime",1000,100,5000,50,()->modes.isSelected("Hologram")));
    public final NumberSetting hologramDelay=addSetting(new NumberSetting("Hologram Delay",100,0,1000,10,()->modes.isSelected("Hologram")));
    public final ColorSetting hologramColor=addSetting(new ColorSetting("Hologram Color",0xFF65D8FF,()->modes.isSelected("Hologram")));
    public final BooleanSetting hologramColorFade=addSetting(new BooleanSetting("Hologram Color Fade",false,()->modes.isSelected("Hologram")));
    public final ColorSetting hologramEndColor=addSetting(new ColorSetting("Hologram End Color",0xFFB493FF,()->modes.isSelected("Hologram")&&hologramColorFade.isEnabled()));
    public final NumberSetting hologramTransparency=addSetting(new NumberSetting("Hologram Transparency",45,0,100,1,()->modes.isSelected("Hologram")));
    public final BooleanSetting hologramFade=addSetting(new BooleanSetting("Hologram Fade",true,()->modes.isSelected("Hologram")));
    public final dev.vibe.setting.ModeSetting hologramEasing=addSetting(new dev.vibe.setting.ModeSetting("Hologram Fade Easing","Ease Out",()->modes.isSelected("Hologram")&&hologramFade.isEnabled(),"Linear","Ease In","Ease Out"));
    public final BooleanSetting hologramGlow=addSetting(new BooleanSetting("Hologram Glow",true,()->modes.isSelected("Hologram")));
    public final NumberSetting hologramGlowStrength=addSetting(new NumberSetting("Hologram Glow Strength",1,.1,3,.1,()->modes.isSelected("Hologram")&&hologramGlow.isEnabled()));
    public final BooleanSetting hologramSkin=addSetting(new BooleanSetting("Hologram Skin",false,()->modes.isSelected("Hologram")));
    public final NumberSetting hologramSkinOpacity=addSetting(new NumberSetting("Hologram Skin Opacity",65,0,100,1,()->modes.isSelected("Hologram")&&hologramSkin.isEnabled()));
    public final BooleanSetting hologramBrightness=addSetting(new BooleanSetting("Hologram Skin Brightness Only",false,()->modes.isSelected("Hologram")&&hologramSkin.isEnabled()));
    public final BooleanSetting hologramThroughWalls=addSetting(new BooleanSetting("Hologram Through Walls",false,()->modes.isSelected("Hologram")));
    private final java.util.Map<net.minecraft.entity.player.EntityPlayer,dev.vibe.ui.render.HologramPose> hologramPoses=new java.util.IdentityHashMap<>();
    private final java.util.Map<net.minecraft.entity.player.EntityPlayer,Long> pendingHolograms=new java.util.IdentityHashMap<>();
    private final List<dev.vibe.ui.render.HologramPose> holograms=new ArrayList<>();
    private long lastHologram;
    public final NumberSetting torusLifetime = addSetting(new NumberSetting("Torus Lifetime", 1000, 100, 5000, 50, () -> modes.isSelected("Torus")));
    public final NumberSetting torusDelay = addSetting(new NumberSetting("Torus Delay (ms)", 100, 0, 1000, 1, () -> modes.isSelected("Torus")));
    public final NumberSetting torusNoise = addSetting(new NumberSetting("Torus Noise", 50, 0, 100, 1, () -> modes.isSelected("Torus")));
    public final NumberSetting torusScale = addSetting(new NumberSetting("Torus Scale", 1, .1, 3, .05, () -> modes.isSelected("Torus")));
    public final BooleanSetting torusDepth = addSetting(new BooleanSetting("Torus Depth", false, () -> modes.isSelected("Torus")));
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFFFFFFFF));
    private final NumberSetting duration = addSetting(new NumberSetting("Duration", 240.0D, 60.0D, 1500.0D, 10.0D));
    private final BooleanSetting fade = addSetting(new BooleanSetting("Fade", true));
    private final dev.vibe.setting.ModeSetting fadeEasing = addSetting(new dev.vibe.setting.ModeSetting("Fade Easing", "Ease Out",
            () -> fade.isEnabled(), "Linear", "Ease In", "Ease Out"));
    private final dev.vibe.setting.ModeSetting worldAnimation = addSetting(new dev.vibe.setting.ModeSetting("3D Animation", "Static",
            () -> modes.isSelected("3D (World)"), "Static", "Expand"));
    private final NumberSetting size2d = addSetting(new NumberSetting("2D Size", 8.0D, 2.0D, 32.0D, 1.0D,
            () -> modes.isSelected("2D (Crosshair)")));
    private final NumberSetting width2d = addSetting(new NumberSetting("2D Line Width", 1.5D, 1.0D, 5.0D, 0.5D,
            () -> modes.isSelected("2D (Crosshair)")));
    private final NumberSetting size3d = addSetting(new NumberSetting("3D Size", 0.16D, 0.04D, 0.65D, 0.01D,
            () -> modes.isSelected("3D (World)")));
    private final NumberSetting width3d = addSetting(new NumberSetting("3D Line Width", 2.0D, 1.0D, 5.0D, 0.5D,
            () -> modes.isSelected("3D (World)")));
    private final NumberSetting gap3d = addSetting(new NumberSetting("3D Centre Gap", 0.07D, 0.01D, 0.30D, 0.01D,
            () -> modes.isSelected("3D (World)")));
    private final BooleanSetting outline3d = addSetting(new BooleanSetting("3D Outline", true,
            () -> modes.isSelected("3D (World)")));
    private final ColorSetting outlineColor3d = addSetting(new ColorSetting("3D Outline Color", 0xC8000000,
            () -> modes.isSelected("3D (World)") && outline3d.isEnabled()));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("3D Through Walls", true,
            () -> modes.isSelected("3D (World)")));

    private long lastHit;
    private long lastTorus;
    private EntityLivingBase hitTarget;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double lastLookX;
    private double lastLookY;
    private double lastLookZ = 1.0D;
    private final List<Marker> markers = new ArrayList<Marker>();
    private final List<Marker> torusMarkers = new ArrayList<Marker>();

    public HitmarkerModule() {
        super("Hitmarker", "2D, 3D, torus and frozen player hologram hit confirmations", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void mark(EntityLivingBase target) {
        if (!isEnabled() || target == null) {
            return;
        }
        lastHit = System.currentTimeMillis();
        hitTarget = target;
        captureHitPoint(target);
        if(modes.isSelected("Hologram")&&target instanceof net.minecraft.entity.player.EntityPlayer&&(lastHologram==0||lastHit-lastHologram>=hologramDelay.getInt())){
            pendingHolograms.entrySet().removeIf(entry->lastHit-entry.getValue()>500);
            if(pendingHolograms.size()>=32)pendingHolograms.remove(pendingHolograms.keySet().iterator().next());
            lastHologram=lastHit;net.minecraft.entity.player.EntityPlayer player=(net.minecraft.entity.player.EntityPlayer)target;
            dev.vibe.ui.render.HologramPose pose=hologramPoses.get(player);
            if(pose!=null&&lastHit-pose.created<=250)addHologram(pose.at(lastHit));else pendingHolograms.put(player,lastHit);
        }
        boolean spawnTorus = modes.isSelected("Torus") && (lastTorus == 0 || lastHit - lastTorus >= torusDelay.getInt());
        if (spawnTorus) lastTorus = lastHit;
        Marker marker = new Marker(lastX, lastY, lastZ, lastLookX, lastLookY, lastLookZ, lastHit, spawnTorus);
        markers.add(marker);
        if (spawnTorus) {
            torusMarkers.add(marker);
            while (torusMarkers.size() > 18) torusMarkers.remove(0);
        }
        while (markers.size() > 18) markers.remove(0);
    }

    public float progress() {
        if (!isEnabled() || lastHit == 0L) {
            return 0.0F;
        }
        return Math.max(0.0F, 1.0F - (System.currentTimeMillis() - lastHit) / duration.getFloat());
    }

    public EntityLivingBase getHitTarget() { return hitTarget; }
    public double getLastX() { return lastX; }
    public double getLastY() { return lastY; }
    public double getLastZ() { return lastZ; }
    public double getLastLookX() { return lastLookX; }
    public double getLastLookY() { return lastLookY; }
    public double getLastLookZ() { return lastLookZ; }
    public MultiSelectSetting getModes() { return modes; }
    public ColorSetting getColor() { return color; }
    public BooleanSetting getFade() { return fade; }
    public dev.vibe.setting.ModeSetting getFadeEasing() { return fadeEasing; }
    public dev.vibe.setting.ModeSetting getWorldAnimation() { return worldAnimation; }
    public NumberSetting getSize2d() { return size2d; }
    public NumberSetting getWidth2d() { return width2d; }
    public NumberSetting getSize3d() { return size3d; }
    public NumberSetting getWidth3d() { return width3d; }
    public NumberSetting getGap3d() { return gap3d; }
    public BooleanSetting getOutline3d() { return outline3d; }
    public ColorSetting getOutlineColor3d() { return outlineColor3d; }
    public BooleanSetting getThroughWalls() { return throughWalls; }

    /** Snapshot list lets several world hitmarkers overlap naturally. */
    public List<Marker> getMarkers() {
        long now = System.currentTimeMillis();
        Iterator<Marker> iterator = markers.iterator();
        long lifetime = duration.getInt();
        while (iterator.hasNext()) if (now - iterator.next().created >= lifetime) iterator.remove();
        return Collections.unmodifiableList(new ArrayList<Marker>(markers));
    }

    /** Ordinary hitmarkers must not evict a longer-lived torus during its delay. */
    public List<Marker> getTorusMarkers() {
        long now = System.currentTimeMillis();
        Iterator<Marker> iterator = torusMarkers.iterator();
        while (iterator.hasNext()) if (now - iterator.next().created >= torusLifetime.getInt()) iterator.remove();
        return Collections.unmodifiableList(new ArrayList<Marker>(torusMarkers));
    }

    public float progress(Marker marker) {
        if (marker == null || !isEnabled()) return 0.0F;
        float raw = Math.max(0.0F, 1.0F - (System.currentTimeMillis() - marker.created) / duration.getFloat());
        if (!fade.isEnabled() || fadeEasing.is("Linear")) return raw;
        return fadeEasing.is("Ease In") ? raw * raw : 1.0F - (1.0F - raw) * (1.0F - raw);
    }

    public static final class Marker {
        public final double x, y, z, lookX, lookY, lookZ;
        public final long created;
        public final boolean torus;
        private Marker(double x, double y, double z, double lookX, double lookY, double lookZ, long created, boolean torus) {
            this.torus = torus;
            this.x = x; this.y = y; this.z = z; this.lookX = lookX; this.lookY = lookY; this.lookZ = lookZ; this.created = created;
        }
    }

    @Override protected void onDisable() { clear(); }
    public void clear() { markers.clear(); torusMarkers.clear(); hologramPoses.clear();pendingHolograms.clear();holograms.clear();lastHologram=0;lastHit = lastTorus = 0; hitTarget = null; }
    public void captureHologram(net.minecraft.entity.player.EntityPlayer player,dev.vibe.ui.render.HologramPose pose){
        if(!isEnabled()||!modes.isSelected("Hologram"))return;
        hologramPoses.entrySet().removeIf(entry->pose.created-entry.getValue().created>500);
        if(hologramPoses.size()>=64&&!hologramPoses.containsKey(player))hologramPoses.remove(hologramPoses.keySet().iterator().next());
        hologramPoses.put(player,pose);Long pending=pendingHolograms.remove(player);if(pending!=null&&pose.created-pending<=500)addHologram(pose.at(pending));
        pendingHolograms.entrySet().removeIf(entry->pose.created-entry.getValue()>500);
    }
    private void addHologram(dev.vibe.ui.render.HologramPose pose){holograms.add(pose);while(holograms.size()>12)holograms.remove(0);}
    public List<dev.vibe.ui.render.HologramPose> getHolograms(){long now=System.currentTimeMillis();holograms.removeIf(pose->now-pose.created>=hologramLifetime.getInt());return new ArrayList<>(holograms);}
    public float hologramOpacity(dev.vibe.ui.render.HologramPose pose){
        float p=Math.max(0,Math.min(1,(System.currentTimeMillis()-pose.created)/(float)hologramLifetime.getInt()));
        float fade=1;if(hologramFade.isEnabled())fade=hologramEasing.is("Ease In")?1-p*p:hologramEasing.is("Ease Out")?(1-p)*(1-p):1-p;
        return (1-hologramTransparency.getFloat()/100)*(hologramTint(pose)>>>24)/255F*fade;
    }
    public int hologramTint(dev.vibe.ui.render.HologramPose pose){
        int from=hologramColor.getArgb();if(!hologramColorFade.isEnabled())return from;
        int to=hologramEndColor.getArgb(),color=0;float age=Math.max(0,Math.min(1,(System.currentTimeMillis()-pose.created)/(float)hologramLifetime.getInt()));
        for(int shift=0;shift<=24;shift+=8){int a=from>>>shift&255,b=to>>>shift&255;color|=Math.round(a+(b-a)*age)<<shift;}return color;
    }

    /** Store the actual attack-ray contact and the camera direction at impact. */
    private void captureHitPoint(EntityLivingBase target) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            lastX = target.posX;
            lastY = target.posY + target.height * 0.5D;
            lastZ = target.posZ;
            // A world marker can outlive a disconnect by a few render
            // frames. Keep a valid plane orientation for that short window.
            lastLookX = 0.0D;
            lastLookY = 0.0D;
            lastLookZ = 1.0D;
            return;
        }
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        double length = Math.sqrt(look.xCoord * look.xCoord + look.yCoord * look.yCoord + look.zCoord * look.zCoord);
        if (length < 0.0001D) {
            double centreX = target.posX - eyes.xCoord;
            double centreY = target.posY + target.height * 0.5D - eyes.yCoord;
            double centreZ = target.posZ - eyes.zCoord;
            length = Math.sqrt(centreX * centreX + centreY * centreY + centreZ * centreZ);
            look = length < 0.0001D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(centreX / length, centreY / length, centreZ / length);
        }
        lastLookX = look.xCoord;
        lastLookY = look.yCoord;
        lastLookZ = look.zCoord;
        AxisAlignedBB box = target.getEntityBoundingBox().expand(target.getCollisionBorderSize(), target.getCollisionBorderSize(), target.getCollisionBorderSize());
        Vec3 end = eyes.addVector(look.xCoord * 6.0D, look.yCoord * 6.0D, look.zCoord * 6.0D);
        MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
        Vec3 point = intercept == null ? closestPoint(box, eyes) : intercept.hitVec;
        lastX = point.xCoord;
        lastY = point.yCoord;
        lastZ = point.zCoord;
    }

    private Vec3 closestPoint(AxisAlignedBB box, Vec3 point) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.xCoord));
        double y = Math.max(box.minY, Math.min(box.maxY, point.yCoord));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.zCoord));
        return new Vec3(x, y, z);
    }
}
