package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * 1.8.9 port of the supplied Cute Visuals script.  The effect state remains
 * local and is rendered only in the world; it never sends packets.
 */
public final class CuteVisualsModule extends Module {
    public static final int HEART = 0;
    public static final int DOT = 1;
    public static final int BED_BURST = 2;

    private final ModeSetting colorPreset = addSetting(new ModeSetting("Color Preset", "Love",
            "Love", "Red", "Green", "Blue", "Yellow", "Gray", "Purple", "LightBlue", "Custom", "China", "Bavaria", "Catton Candy"));
    private final ColorSetting primaryColor = addSetting(new ColorSetting("Primary Color", 0xFFFF80CC, () -> colorPreset.is("Custom")));
    private final ColorSetting secondaryColor = addSetting(new ColorSetting("Secondary Color", 0xFFFF4D99, () -> colorPreset.is("Custom")));

    public ModeSetting getColorPreset() { return colorPreset; }
    public ColorSetting getPrimaryColor() { return primaryColor; }
    public ColorSetting getSecondaryColor() { return secondaryColor; }
    public double[] palette(double red, double green, double blue, float phase) {
        if (colorPreset.is("Love")) return new double[] {red, green, blue, 1};
        int first, second;
        switch (colorPreset.getValue()) {
            case "Custom": first = primaryColor.getArgb(); second = secondaryColor.getArgb(); break;
            case "China": first = 0xFFFF3333; second = 0xFFFFDE33; break;
            case "Bavaria": first = 0xFF3399FF; second = 0xFFFFFFFF; break;
            case "Catton Candy": first = 0xFFFF99CC; second = 0xFF88D8FF; break;
            default:
                switch (colorPreset.getValue()) {
                    case "Red": first = 0xFFFF4D4D; break;
                    case "Green": first = 0xFF55DD77; break;
                    case "Blue": first = 0xFF4477FF; break;
                    case "Yellow": first = 0xFFFFDD55; break;
                    case "Gray": first = 0xFFBBBBBB; break;
                    case "Purple": first = 0xFFB366FF; break;
                    default: first = 0xFF88D8FF; break;
                }
                second = dev.vibe.ui.RenderUtils.blend(first, 0xFF000000, .24F);
        }
        int color = dev.vibe.ui.RenderUtils.blend(first, second, Math.max(0, Math.min(1, phase)));
        return new double[] {(color >> 16 & 255) / 255.0, (color >> 8 & 255) / 255.0,
                (color & 255) / 255.0, (color >>> 24) / 255.0};
    }

    private final BooleanSetting bedSound = addSetting(new BooleanSetting("Bed Sound", true));
    private final BooleanSetting bedBurst = addSetting(new BooleanSetting("Bed Burst", true));
    private final NumberSetting burstCount = addSetting(new NumberSetting("Bed Burst Count", 20.0D, 5.0D, 40.0D, 1.0D,
            () -> bedBurst.isEnabled()));
    private final NumberSetting burstSize = addSetting(new NumberSetting("Bed Burst Size", 0.20D, 0.05D, 0.60D, 0.01D,
            () -> bedBurst.isEnabled()));
    private final NumberSetting burstSpeed = addSetting(new NumberSetting("Bed Burst Speed", 2.5D, 0.5D, 7.0D, 0.1D,
            () -> bedBurst.isEnabled()));
    private final NumberSetting burstLifetime = addSetting(new NumberSetting("Bed Burst Lifetime (ms)", 1500.0D, 500.0D, 3000.0D, 100.0D,
            () -> bedBurst.isEnabled()));
    private final BooleanSetting rainbow = addSetting(new BooleanSetting("Rainbow", true));
    private final NumberSetting rainbowLineWidth = addSetting(new NumberSetting("Rainbow Line Width", 5.0D, 1.0D, 12.0D, 0.5D,
            () -> rainbow.isEnabled()));
    private final NumberSetting rainbowDuration = addSetting(new NumberSetting("Rainbow Duration (ms)", 3000.0D, 1000.0D, 6000.0D, 200.0D,
            () -> rainbow.isEnabled()));
    private final BooleanSetting onlyMoving = addSetting(new BooleanSetting("Only While Moving", true));
    private final NumberSetting opacity = addSetting(new NumberSetting("Opacity", 85.0D, 20.0D, 100.0D, 5.0D));
    private final BooleanSetting hearts = addSetting(new BooleanSetting("Hearts", true));
    private final NumberSetting heartsRate = addSetting(new NumberSetting("Hearts Spawn Rate (ms)", 200.0D, 50.0D, 500.0D, 10.0D,
            () -> hearts.isEnabled()));
    private final NumberSetting heartsLifetime = addSetting(new NumberSetting("Hearts Lifetime (ms)", 1500.0D, 500.0D, 4000.0D, 100.0D,
            () -> hearts.isEnabled()));
    private final BooleanSetting dots = addSetting(new BooleanSetting("Dots", true));
    private final NumberSetting dotsRate = addSetting(new NumberSetting("Dots Spawn Rate (ms)", 100.0D, 20.0D, 200.0D, 10.0D,
            () -> dots.isEnabled()));
    private final NumberSetting dotsLifetime = addSetting(new NumberSetting("Dots Lifetime (ms)", 1500.0D, 500.0D, 5000.0D, 100.0D,
            () -> dots.isEnabled()));
    private final BooleanSetting pulse = addSetting(new BooleanSetting("Pulse", false));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<Particle>();
    private final List<Rainbow> rainbows = new ArrayList<Rainbow>();
    private int nextParticleIndex;
    private long lastHeart;
    private long lastDot;
    private double lastDotX;
    private double lastDotY;
    private double lastDotZ;
    private boolean hasLastDotPosition;
    private BlockPos watchedBed;
    private long watchedSince;
    private final Queue<Digging> digging = new ConcurrentLinkedQueue<Digging>();

    public CuteVisualsModule() {
        super("Cute Visuals", "Bed-break bursts and heart or dot trails", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void watch(BlockPos pos) {
        if (!isEnabled() || minecraft.theWorld == null || pos == null || minecraft.theWorld.getBlockState(pos).getBlock() != Blocks.bed) return;
        watchedBed = pos;
        watchedSince = System.currentTimeMillis();
    }

    /** Mirrors the reference script's START/STOP/ABORT bed-dig state machine. */
    public void onDigging(C07PacketPlayerDigging packet) {
        if (isEnabled() && packet != null && packet.getPosition() != null) {
            digging.offer(new Digging(packet.getStatus(), packet.getPosition()));
        }
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        Digging action;
        while ((action = digging.poll()) != null) processDigging(action, now);
        if (watchedBed != null) {
            if (minecraft.theWorld.getBlockState(watchedBed).getBlock() != Blocks.bed) {
                onBedBroken(watchedBed, now);
                watchedBed = null;
            } else if (now - watchedSince > 5000L) {
                watchedBed = null;
            }
        }
        if (!hearts.isEnabled()) clearParticles(HEART);
        if (!dots.isEnabled()) clearParticles(DOT);
        boolean canSpawnDots = hasLastDotPosition;
        boolean moving = minecraft.thePlayer.motionX * minecraft.thePlayer.motionX
                + minecraft.thePlayer.motionZ * minecraft.thePlayer.motionZ > 0.0009D;
        if (!hasLastDotPosition) {
            lastDotX = minecraft.thePlayer.posX;
            lastDotY = minecraft.thePlayer.posY;
            lastDotZ = minecraft.thePlayer.posZ;
            hasLastDotPosition = true;
        }
        if ((!onlyMoving.isEnabled() || moving) && hearts.isEnabled() && now - lastHeart >= heartsRate.getInt()) {
            spawnTrail(HEART, now, heartsLifetime.getInt());
            lastHeart = now;
        }
        if (canSpawnDots && (!onlyMoving.isEnabled() || moving) && dots.isEnabled() && now - lastDot >= dotsRate.getInt()) {
            spawnTrail(DOT, now, dotsLifetime.getInt());
            lastDot = now;
        }
        lastDotX = minecraft.thePlayer.posX;
        lastDotY = minecraft.thePlayer.posY;
        lastDotZ = minecraft.thePlayer.posZ;
        for (Iterator<Particle> iterator = particles.iterator(); iterator.hasNext();) {
            Particle particle = iterator.next();
            if (now - particle.born > particle.lifetime) iterator.remove();
        }
        for (Iterator<Rainbow> iterator = rainbows.iterator(); iterator.hasNext();) {
            if (now - iterator.next().born > rainbowDuration.getInt()) iterator.remove();
        }
    }

    private void processDigging(Digging action, long now) {
        if (minecraft.theWorld == null || action == null) return;
        if (action.action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
            if (minecraft.theWorld.getBlockState(action.pos).getBlock() != Blocks.bed) {
                watchedBed = null;
                return;
            }
            if (minecraft.thePlayer.capabilities.isCreativeMode) {
                watchedBed = null;
                onBedBroken(action.pos, now);
            } else {
                watchedBed = action.pos;
                watchedSince = now;
            }
        } else if (action.action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
            if (watchedBed != null) {
                onBedBroken(watchedBed, now);
                watchedBed = null;
            }
        } else if (action.action == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
            watchedBed = null;
        }
    }

    private void spawnTrail(int type, long now, int lifetime) {
        if (type == HEART) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = random.nextDouble() * 1.5D;
            particles.add(new Particle(type, minecraft.thePlayer.posX + Math.cos(angle) * radius,
                    minecraft.thePlayer.posY + .5D + random.nextDouble() * .5D,
                    minecraft.thePlayer.posZ + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D,
                    (float) (.15D * (.6D + random.nextDouble() * .8D)), random.nextInt(3),
                    random.nextDouble() * 360.0D, random.nextDouble() * 30.0D - 15.0D, nextParticleIndex++, now, lifetime));
            trimParticles(type, 50);
        } else {
            particles.add(new Particle(type, lastDotX + (random.nextDouble() - .5D) * .9D,
                    lastDotY + .3D + random.nextDouble() * 1.2D,
                    lastDotZ + (random.nextDouble() - .5D) * .9D,
                    (random.nextDouble() - .5D) * .3D, (.3D + random.nextDouble() * .7D) * .3D,
                    (random.nextDouble() - .5D) * .3D, (float) (.04D * (.5D + random.nextDouble())),
                    random.nextInt(4), 0.0D, 0.0D, nextParticleIndex++, now, lifetime));
            trimParticles(type, 100);
        }
    }

    private void onBedBroken(BlockPos pos, long now) {
        if (bedSound.isEnabled()) {
            minecraft.thePlayer.playSound("random.orb", 1.0F, 1.5F);
            minecraft.thePlayer.playSound("random.levelup", .5F, 2.0F);
        }
        if (bedBurst.isEnabled()) {
            for (int index = 0; index < burstCount.getInt(); index++) {
                double theta = random.nextDouble() * Math.PI * 2.0D;
                double phi = random.nextDouble() * Math.PI * .67D - Math.PI / 6.0D;
                double speed = (0.8D + random.nextDouble() * 1.2D) * burstSpeed.getDouble();
                double horizontal = Math.cos(phi);
                particles.add(new Particle(BED_BURST, pos.getX() + .5D, pos.getY() + .45D, pos.getZ() + .5D,
                        horizontal * Math.cos(theta) * speed, Math.sin(phi) * speed + 1.0D, horizontal * Math.sin(theta) * speed,
                        (float) (burstSize.getDouble() * (.6D + random.nextDouble() * .8D)), bedVariant(),
                        0.0D, 0.0D, nextParticleIndex++, now, burstLifetime.getInt()));
            }
            trimParticles(BED_BURST, 200);
        }
        if (rainbow.isEnabled()) {
            float yaw = (float) Math.toDegrees(Math.atan2(minecraft.thePlayer.posX - pos.getX() - .5D,
                    minecraft.thePlayer.posZ - pos.getZ() - .5D));
            rainbows.add(new Rainbow(pos.getX() + .5D, pos.getY() + .55D, pos.getZ() + .5D, yaw, now));
            while (rainbows.size() > 5) rainbows.remove(0);
        }
    }

    private int bedVariant() { int roll = random.nextInt(5); return roll < 2 ? 0 : roll - 1; }
    private void trimParticles(int type, int maximum) {
        int count = 0;
        for (Particle particle : particles) if (particle.type == type) count++;
        while (count > maximum) {
            for (Iterator<Particle> iterator = particles.iterator(); iterator.hasNext();) {
                if (iterator.next().type == type) { iterator.remove(); count--; break; }
            }
        }
    }
    private void clearParticles(int type) {
        for (Iterator<Particle> iterator = particles.iterator(); iterator.hasNext();) {
            if (iterator.next().type == type) iterator.remove();
        }
    }
    private void clear() { particles.clear(); rainbows.clear(); watchedBed = null; digging.clear(); lastHeart = 0L; lastDot = 0L; nextParticleIndex = 0; hasLastDotPosition = false; }
    @Override protected void onDisable() { clear(); }

    public List<Particle> getParticles() { return particles; }
    public List<Rainbow> getRainbows() { return rainbows; }
    public float getOpacity() { return opacity.getFloat() / 100.0F; }
    public boolean isPulse() { return pulse.isEnabled(); }
    public NumberSetting getRainbowLineWidth() { return rainbowLineWidth; }
    public int getRainbowDuration() { return rainbowDuration.getInt(); }
    public int getHeartLifetime() { return heartsLifetime.getInt(); }
    public int getDotLifetime() { return dotsLifetime.getInt(); }
    public int getBurstLifetime() { return burstLifetime.getInt(); }
    public BooleanSetting getBedSound() { return bedSound; }
    public BooleanSetting getBedBurst() { return bedBurst; }
    public BooleanSetting getRainbow() { return rainbow; }
    public BooleanSetting getOnlyMoving() { return onlyMoving; }
    public BooleanSetting getHearts() { return hearts; }
    public BooleanSetting getDots() { return dots; }
    public BooleanSetting getPulse() { return pulse; }

    public static final class Particle {
        public final int type, variant, index; public final double x, y, z, velocityX, velocityY, velocityZ, rotationY, rotationZ; public final float size; public final long born, lifetime;
        private Particle(int type, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float size, int variant, double rotationY, double rotationZ, int index, long born, long lifetime) {
            this.type=type; this.x=x; this.y=y; this.z=z; this.velocityX=velocityX; this.velocityY=velocityY; this.velocityZ=velocityZ; this.size=size; this.variant=variant; this.rotationY=rotationY; this.rotationZ=rotationZ; this.index=index; this.born=born; this.lifetime=lifetime;
        }
    }
    public static final class Rainbow {
        public final double x, y, z; public final float yaw; public final long born;
        private Rainbow(double x, double y, double z, float yaw, long born) { this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.born=born; }
    }
    private static final class Digging {
        private final C07PacketPlayerDigging.Action action;
        private final BlockPos pos;
        private Digging(C07PacketPlayerDigging.Action action, BlockPos pos) { this.action = action; this.pos = pos; }
    }
}
