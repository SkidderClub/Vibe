package dev.vibe.module.impl;

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
            Arrays.asList("2D (Crosshair)", "3D (World)", "Torus"), Arrays.asList("2D (Crosshair)")));
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
        super("Hitmarker", "2D, 3D and expanding reflective torus hit confirmations", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void mark(EntityLivingBase target) {
        if (!isEnabled() || target == null) {
            return;
        }
        lastHit = System.currentTimeMillis();
        hitTarget = target;
        captureHitPoint(target);
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
    public void clear() { markers.clear(); torusMarkers.clear(); lastHit = lastTorus = 0; hitTarget = null; }

    /** Store the actual attack-ray contact and the camera direction at impact. */
    private void captureHitPoint(EntityLivingBase target) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            lastX = target.posX;
            lastY = target.posY + target.height * 0.5D;
            lastZ = target.posZ;
            return;
        }
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
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
