package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.aiaura.AimModel;
import dev.vibe.aiaura.MouseMath;
import dev.vibe.chat.ClientChat;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * AIAura V2: Nyx 26.2's AI Aura on Killaura's ray check and vanilla click path.
 *
 * <p>The aim is silent (Rotation: Silent), as in Nyx with its Movement on Silent: MoveFix sends it and leaves
 * your camera free, holds your keys steady under it and, once nobody is left, turns it back to your camera
 * smoothly in whole mouse counts. Rotation: Camera is Nyx's Change look: the aim is your camera, and all of
 * your own mouse movement is in it.
 *
 * <p>With every aura off it learns from your own fighting ({@link AimModel}): where on them your
 * crosshair landed, how far your aim runs out past them between two hits, and your clicking - the gaps
 * between your real mouse clicks, timed to the nanosecond.
 *
 * <p>On, once it has some two minutes of your fighting, the aim is yours ({@link #yours}): each tick it
 * makes the turn you made from nearest where it now is against them - how far off, how they are going
 * round, how it has been turning. Until then, or with Aim on Hand, it is a hand's, not a servo's
 * ({@link #turn}): it acts on what it saw Tracking delay ago,
 * in separate corrections a few times a second rather than steering all the time, and each lands a
 * little off. After a hit that landed it moves on to another of your spots and may run out past them as
 * yours does, before they can be hit again.
 *
 * <p>The clicking is a 1.8.9 player's, not Nyx's: 1.8.9 has no attack bar to wait for. It clicks
 * the whole time someone is in reach - and from Pre-Swing further out - in runs of your own rhythm
 * replayed in order; a click lands if the aim is on them and swings at the air if not. Clicks are
 * kept on deadlines, so a CPS between two ticks is not rounded to them. Hit Timing is how often a click
 * that was coming anyway is made the tick they can be hit again. Vanilla 1.8.9's miss delay is left
 * as it is: it only holds while the button stays down, so clicking - yours or this - never meets it.
 *
 * <p>Nyx's AI Aura also ran on some Kill Aura settings: Stabilization, Wander smoothness, Delay
 * randomness. They stay at Nyx's defaults here; the learned model is what shapes the aim.
 */
public final class AIAuraV2Module extends KillAuraModule {
    /** Learned spots are taken whole: across and up as shares of half width and height, no shift on a hit. */
    private static final float[] LEARNED = {1f, 1f, 1f, 0f};
    /** Nyx's Medium Aim randomization, which the aim wanders by until it has your hits. */
    private static final float[] UNLEARNED = {0.5f, 0.3f, 1f, 0.3f};
    private static final int MEMORY = 30;
    /** Aim Yours: how much more often it picks those of your turns that brought you back onto them. */
    static float PULL = Float.parseFloat(System.getProperty("vibe.aiaura.pull", "1"));
    /** Nyx's Kill Aura defaults, fixed: jump pitch hold, wander glide, how much waits vary. */
    private static final float STABILIZATION = 0.8F, WANDER_SMOOTH = 0.5F, DELAY_RANDOM = 45F;
    /** Up close: within this many blocks of them, flat, the hand stops chasing them round ({@link #turn}). */
    private static final double CLOSE = 1.5D;

    private final NumberSetting attackRange = addSetting(new NumberSetting("Attack Range", 3.0D, 1.0D, 6.0D, 0.05D));
    private final NumberSetting rotationRange = addSetting(new NumberSetting("Rotation Range", 6.0D, 3.0D, 15.0D, 0.1D));
    private final NumberSetting preSwing = addSetting(new NumberSetting("Pre-Swing", 1.0D, 0.0D, 2.0D, 0.1D));
    /** Yours: your own turning, tick by tick, once it has enough of it; Hand: the hand model, always. */
    final ModeSetting aim = addSetting(new ModeSetting("Aim", "Yours", "Yours", "Hand"));
    /**
     * Silent: MoveFix's rotation, your camera left free - Nyx's Silent, its keys held steady and its turn back
     * smooth and in whole mouse counts. Camera: Nyx's Change look - the aim turns your camera and all of your
     * own mouse stays in it.
     */
    final ModeSetting look = addSetting(new ModeSetting("Rotation", "Silent", "Silent", "Camera"));
    final ModeSetting clicking = addSetting(new ModeSetting("Clicking", "Learned", "Learned", "CPS"));
    final RangeSetting cps = addSetting(new RangeSetting("CPS", 10.0D, 14.0D, 1.0D, 20.0D, 0.5D,
            () -> clicking.is("CPS") || !AimModel.rhythm()));
    private final BooleanSetting blockHit = addSetting(new BooleanSetting("Block Hit", false));
    /** Each time they can be hit again, the chance is drawn afresh anywhere between the two. */
    private final RangeSetting hitTiming = addSetting(new RangeSetting("Hit Timing (%)", 0.0D, 5.0D, 0.0D, 100.0D, 1.0D));
    final BooleanSetting learn = addSetting(new BooleanSetting("Learn", true));
    final NumberSetting reaction = addSetting(new NumberSetting("Reaction (ms)", 175.0D, 0.0D, 500.0D, 5.0D));
    private final NumberSetting trackingDelay = addSetting(new NumberSetting("Tracking Delay (ms)", 50.0D, 0.0D, 400.0D, 5.0D));
    private final NumberSetting missRate = addSetting(new NumberSetting("Miss Rate (%)", 25.0D, 0.0D, 50.0D, 1.0D));
    private final NumberSetting fov = addSetting(new NumberSetting("Field Of View", 170.0D, 30.0D, 360.0D, 5.0D));
    final BooleanSetting recordFights = addSetting(new BooleanSetting("Record Fights", false));
    final BooleanSetting learnRecordings = addSetting(new BooleanSetting("Learn From Recordings", false).onEnable(new Runnable() {
        @Override public void run() { learnFromRecordings(); }
    }));
    final BooleanSetting resetLearned = addSetting(new BooleanSetting("Reset Learned", false).onEnable(new Runnable() {
        @Override public void run() { forget(); }
    }));

    private final Random rng = new Random();
    /** Those worth aiming at, by entity id: from when the aim may go for each (Reaction). */
    private final Map<Integer, Long> noticed = new HashMap<Integer, Long>();
    private final Set<Integer> seen = new HashSet<Integer>();
    /** On someone: the aim is in use, and a new target is a switch, not a first aim. */
    private boolean engaged, missing;
    private long overAt, overFrom, overUntil, lastAsked;
    /** When the next click is due, kept fractional; NaN while it is not clicking. And the CPS the hand is at, under CPS. */
    private double nextClick = Double.NaN, handCps;
    /** On your ticks: ticks to the next swing, -1 while it is not clicking. */
    private int ticksLeft = -1;
    /** The aim as it turns, and as it is sent: the same, in whole mouse counts. And the view a tick ago. */
    private float currentYaw, currentPitch, lastYaw, lastPitch, viewYaw, viewPitch;
    /** Where on them the wandering spot is, -1 to 1, and where the aim is as it glides after it. */
    private float strayX, strayY, glideX, glideY;
    /** How far past them the aim runs out after this swing. */
    private float overYaw, overPitch;
    /**
     * The hand ({@link #turn}): the corrections it is making - each {yaw, pitch, ticks it takes, ticks done} -
     * and in how many ticks it looks again. Where its spot was, tick by tick, newest last: it acts on what it
     * saw Tracking delay (lag, in ticks) ago. Near enough its spot it only drifts (resting, for the record).
     * And how much of their going round, as it saw it, the hand keeps up with as it goes.
     */
    private final java.util.List<float[]> moves = new java.util.ArrayList<float[]>();
    private final ArrayDeque<float[]> sight = new ArrayDeque<float[]>();
    /** The last three ticks of the aim as sent against them ({@link AimModel#seen}), oldest first: what Aim Yours goes by. */
    private final ArrayDeque<float[]> sent = new ArrayDeque<float[]>();
    private int planIn, lag;
    private float keep = 0.6F;
    /** What the hand makes of their going round, from the last few ticks; and its last step, which the next carries on from. */
    private float paceYaw, stepWasYaw, stepWasPitch;
    /** Near enough, it leaves the mouse be until it looks again - keeping up with them included. */
    private boolean idle;
    private boolean resting;
    private double steadyAngle = Double.NaN;
    /** Their hurt time a tick ago; this tick they were hit afresh. And a Block Hit tap, until when. */
    private int hurtWas;
    private boolean struck, reopened;
    private long blockUntil;
    /** Your own hurt time a tick ago. After a switch, the aim stays put until then: the new one has to be seen first. */
    private int ownHurtWas;
    private long holdUntil;
    /** Pre-Swing's edge is not at the same distance for every target, and the hand clicks on for a moment once they are out of it. */
    private float edge = 1F;
    private long linger = 250L, clicksOn;

    public AIAuraV2Module() {
        super("AIAura V2", "Aims where your own hits land, with a hand's lag and inertia, and clicks in your own rhythm");
        replaceSettings(attackRange, rotationRange, preSwing, aim, look, clicking, cps, blockHit, hitTiming, learn, learnRecordings, reaction, trackingDelay, missRate,
                fov, recordFights, resetLearned);
    }

    @Override public String getDescription() {
        ensureLoaded();
        String rhythm = AimModel.clicks() >= 50 ? ", your clicking ~" + Math.round(AimModel.cps()) + " CPS" : "";
        return super.getDescription() + " · learned " + AimModel.steps() + "/" + AimModel.STEPS + " ticks of your aim, " + AimModel.size() + "/"
                + AimModel.ENOUGH + " hits, " + AimModel.clicks() + "/" + AimModel.CLICKS + " clicks, " + AimModel.swings() + "/" + AimModel.SWINGS
                + " swings" + rhythm;
    }

    @Override protected void onEnable() {
        Vibe vibe = Vibe.getInstance();
        if (vibe != null && vibe.getModuleManager() != null) {
            KillAuraModule aura = vibe.getModuleManager().getModule(KillAuraModule.class);
            AIAuraModule ai = vibe.getModuleManager().getModule(AIAuraModule.class);
            if (aura != null && aura != this && aura.isEnabled()) aura.setEnabled(false);
            if (ai != null && ai.isEnabled()) ai.setEnabled(false);
        }
        ensureLoaded();
    }

    @Override protected void onDisable() {
        super.onDisable();
        noticed.clear();
    }

    private void forget() {
        resetLearned.setEnabled(false);
        if (isConfigLoading()) return;
        ensureLoaded();
        AimModel.clear();
        watched.clear();
        lastStruck = -1;
        ClientChat.send("AIAura V2: forgot every learned hit, turn, overshoot, click and tick of your aim.");
    }

    /** Every tick of your own fighting in Record Fights' files, learned as your aim (a file learned twice counts twice). */
    private void learnFromRecordings() {
        learnRecordings.setEnabled(false);
        if (isConfigLoading()) return;
        ensureLoaded();
        File[] files = new File(minecraft.mcDataDir, "vibe/aiaura-v2/traces").listFiles();
        int learned = 0, read = 0, swingsWere = AimModel.swings();
        if (files != null) for (File file : files) {
            if (!file.getName().endsWith(".csv")) continue;
            try {
                learned += AimModel.learnFrom(file);
                read++;
            } catch (java.io.IOException | RuntimeException unreadable) {
                // a file being written or cut off: the others still count
            }
        }
        AimModel.save();
        ClientChat.send("AIAura V2: learned " + learned + " ticks of your aim and " + (AimModel.swings() - swingsWere) + " of your swings from "
                + read + " recordings - " + AimModel.steps() + " of " + AimModel.STEPS + " ticks, " + AimModel.swings() + " of " + AimModel.SWINGS
                + " swings it wants.");
    }

    // ---------------------------------------------------------------- who

    @Override protected double selectionRange() { return Math.max(rotationRange.getDouble(), attackRange.getDouble()); }
    /** Pre-Swing: a 1.8.9 player is clicking before they are in reach, not from the tick they are. */
    @Override protected double clickRange() {
        double range = attackRange.getDouble() + preSwing.getDouble() * edge;
        long now = now();
        if (target != null && distanceTo(target) <= range) clicksOn = now + linger;
        return now < clicksOn ? Math.max(range, selectionRange()) : range;
    }
    @Override protected double attackReach() { return attackRange.getDouble(); }
    @Override public double getReachRadius() { return attackRange.getDouble(); }
    /** Those it can hit before those it can only aim at, then the nearest - whoever it is on stays until another is half a block nearer. */
    @Override protected double score(EntityLivingBase entity) {
        double d = distanceTo(entity);
        return (d <= attackRange.getDouble() ? 0 : 1000) + d - (entity == target ? 0.5D : 0.0D);
    }

    /** The nearest point of them - or, with a block in the way of that, the nearest that can be seen (Augustus's SmartAim). */
    @Override protected Vec3 aimPoint(EntityLivingBase entity) {
        Vec3 eye = eyes(), best = bestHitVec(eye, entity);
        if (seen(eye, best)) return best;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double least = Double.MAX_VALUE, in = 0.05D;
        // ponytail: a 3 x 5 x 3 grid, and only once the nearest point is covered; finer if slivers of them go unseen
        for (int x = 0; x < 3; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 3; z++) {
            Vec3 spot = new Vec3(box.minX + in + (box.maxX - box.minX - 2 * in) * x / 2, box.minY + in + (box.maxY - box.minY - 2 * in) * y / 4,
                    box.minZ + in + (box.maxZ - box.minZ - 2 * in) * z / 2);
            double d = eye.squareDistanceTo(spot);
            if (d < least && seen(eye, spot)) {
                least = d;
                best = spot;
            }
        }
        return best;
    }

    private boolean seen(Vec3 eye, Vec3 spot) {
        return minecraft.theWorld.rayTraceBlocks(eye, spot, false, false, false) == null;
    }

    @Override protected EntityLivingBase chooseTarget() {
        seen.clear();
        EntityLivingBase best = super.chooseTarget();
        noticed.keySet().retainAll(seen);                // whoever left starts over when they come back
        return best;
    }

    @Override protected boolean validTarget(EntityLivingBase entity) {
        if (!super.validTarget(entity)) return false;
        float field = fov.getFloat();
        if (field < 360F && offView(minecraft.thePlayer, entity) > field / 2F) return false;
        long now = now();
        Long aimFrom = noticed.get(entity.getEntityId());
        if (aimFrom == null) noticed.put(entity.getEntityId(), aimFrom = now + after(reaction.getFloat()));
        seen.add(entity.getEntityId());
        return engaged || now >= aimFrom;                // Reaction: a first aim waits; from one target to the next it is already moving
    }

    @Override protected void targetChanged(EntityLivingBase previous, EntityLivingBase next) {
        steadyAngle = Double.NaN;
        overYaw = overPitch = 0F;
        missing = false;
        sight.clear();
        sent.clear();
        moves.clear();
        planIn = 0;
        paceYaw = stepWasYaw = stepWasPitch = 0F;
        idle = false;
        lag = Math.round(after(trackingDelay.getFloat()) / 50F);
        hurtWas = next == null ? 0 : next.hurtTime;
        blockUntil = 0L;
        clicksOn = 0L;
        resting = true;
        edge = 0.6F + rng.nextFloat() * 0.8F;
        linger = after(250F);
        // from one target to the next is not instant either: the aim rests where it was for most of a reaction
        if (previous != null && engaged) holdUntil = now() + after(reaction.getFloat() * 0.8F);
    }

    @Override protected void resetPolicy() {
        engaged = false;
        nextClick = Double.NaN;
        ticksLeft = -1;
        if (!isEnabled()) noticed.clear();
    }

    // ---------------------------------------------------------------- the look

    @Override protected boolean turnsCamera() { return look.is("Camera"); }

    @Override protected void advanceRotation(EntityLivingBase entity) {
        long now = now();
        EntityPlayerSP player = minecraft.thePlayer;
        float sensitivity = minecraft.gameSettings.mouseSensitivity, gcd = MouseMath.step(sensitivity);
        // what your hand has turned the view by since the last tick: under Camera the view is the aim, so all of it
        boolean camera = turnsCamera();
        float dYaw = player.rotationYaw - (camera ? lastYaw : viewYaw), dPitch = player.rotationPitch - (camera ? lastPitch : viewPitch);
        viewYaw = player.rotationYaw;
        viewPitch = player.rotationPitch;
        if (!engaged) {                                  // taking the aim up from nothing: from wherever it is, gathering pace
            currentYaw = lastYaw = rotation.getYaw();
            currentPitch = lastPitch = rotation.getPitch();
            strayX = glideX = nudged(0F, 0.5F);          // and not for the same spot as last time
            strayY = glideY = nudged(0F, 0.5F);
            moves.clear();
            sight.clear();
            sent.clear();
            planIn = 0;
            engaged = true;
            if (AimModel.ready()) relearn();
            else actionBar("AIAura V2: " + AimModel.size() + " of " + AimModel.ENOUGH + " hits learned - wandering at random until then");
        } else if (camera || Math.hypot(dYaw, dPitch) < 2.0D) {    // your hand's own movements stay in the aim; silent, only small ones
            currentYaw += dYaw;
            lastYaw += dYaw;
            currentPitch = pitch(currentPitch + dPitch);
            lastPitch = pitch(lastPitch + dPitch);
        }
        struck = entity.hurtTime > hurtWas;
        reopened = hurtWas > 1 && entity.hurtTime <= 1;
        hurtWas = entity.hurtTime;
        if (struck) landed(entity, now);
        if (player.hurtTime > ownHurtWas) {              // being hit knocks the hand off them a little
            moves.add(new float[]{(float) rng.nextGaussian() * 2.5F, (float) rng.nextGaussian() * 1.5F, 3F, 0F});
            strayX = nudged(strayX, 0.4F);
            strayY = nudged(strayY, 0.4F);
        }
        ownHurtWas = player.hurtTime;
        AxisAlignedBB body = entity.getEntityBoundingBox();
        Vec3 from = eyes();
        sent.addLast(AimModel.seen(lastYaw, lastPitch, from.xCoord, from.yCoord, from.zCoord, (body.minX + body.maxX) / 2,
                (body.minY + body.maxY) / 2, (body.minZ + body.maxZ) / 2, (body.maxX - body.minX) / 2 + entity.getCollisionBorderSize()));
        if (sent.size() > 3) sent.removeFirst();
        Vec3 eye = eyes(), nearest = aimPoint(entity), projectile = ProjectileAuraModule.rotationAimPoint(this, entity);
        Vec3 at = projectile != null ? projectile : hittable(entity, wander(entity, steady(player, entity, nearest)), nearest);
        double dx = at.xCoord - eye.xCoord, dy = at.yCoord - eye.yCoord, dz = at.zCoord - eye.zCoord;
        float toYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float toPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        // the overshoot: out past them a moment after the swing - or, the one it misses, until that swing is made
        if (now - overAt > 2000L) missing = false;
        long since = now - overAt;
        boolean out = (overYaw != 0F || overPitch != 0F) && (missing || since >= overFrom && since < overUntil);
        if (out) {
            toYaw += overYaw;
            toPitch = pitch(toPitch + overPitch);
        }
        sight.addLast(new float[]{toYaw, toPitch});
        if (sight.size() > 20) sight.removeFirst();
        if (now < holdUntil) {                           // a switch: the new one has to be seen first
            moves.clear();
            send(gcd, sensitivity);
            return;
        }
        if (aim.is("Yours") && AimModel.cloned() && projectile == null) yours();
        else turn(entity, eye, projectile != null, gcd);
        send(gcd, sensitivity);
    }

    /**
     * Aim Yours: the turn you made, in one of your own fights, from nearest where the aim now is against them
     * ({@link AimModel#act}) - so it is off them, follows them, rests and flicks as much and as often as you do.
     */
    private void yours() {
        if (sent.size() < 3) return;                    // its first ticks on someone: still, until it has seen them move
        float[][] log = sent.toArray(new float[0][]);
        float[] turn = AimModel.act(AimModel.state(log[2], log[1], log[0]), PULL);
        currentYaw = lastYaw + turn[0];
        currentPitch = pitch(lastPitch + turn[1]);
        resting = turn[0] == 0F && turn[1] == 0F;
    }

    /** A hit on them has landed: the next is for another of your spots, and until they can be hit again the aim may run out past them. */
    private void landed(EntityLivingBase entity, long now) {
        if (AimModel.ready()) relearn();
        else {                                           // the swing knocks the aim aside a little, as it does a hand's
            strayX = nudged(strayX, UNLEARNED[3]);
            strayY = nudged(strayY, UNLEARNED[3]);
        }
        overshoot(entity, now, 450L);
    }

    /** As sent: every step a whole number of mouse counts. */
    private void send(float gcd, float sensitivity) {
        lastYaw = MouseMath.angle(lastYaw, Math.round((currentYaw - lastYaw) / gcd), sensitivity);
        lastPitch = MouseMath.pitch(lastPitch, Math.round((currentPitch - lastPitch) / gcd), sensitivity);
        rotation.reset(lastYaw, lastPitch);
    }

    /**
     * One tick of the hand. It keeps up with them going round - most of it, not all, as it saw it a tick or so
     * ago - the way you follow someone strafing past. For the rest it does not steer all the time: a few times
     * a second, as yours does, it looks -
     * at where its spot was Tracking delay ago, and a little at where that was going - and against where
     * the corrections already under way will leave it, plans one more and carries it out over a few ticks,
     * slow at either end and fast in the middle, a longer way taking longer. Each lands a little short or
     * long and not quite level. Near enough - well on them - it leaves the mouse be, or drifts a little.
     * Its height it leaves alone while that is still on their body, and once not only brings it back onto
     * them: it does not bob with their jumps, or yours.
     * A throw's aim point is not seen late: that is ProjectileAura's to say.
     */
    private void turn(Entity entity, Vec3 eye, boolean throwing, float gcd) {
        float[][] seen = sight.toArray(new float[0][]);
        int at = throwing ? seen.length - 1 : Math.max(0, seen.length - 1 - lag), before = Math.max(0, at - 2);
        float predict = throwing ? 0F : 0.3F + rng.nextFloat() * 0.3F, ahead = (throwing ? 0 : lag) * predict / Math.max(1, at - before);
        float goYaw = seen[at][0] + MathHelper.wrapAngleTo180_float(seen[at][0] - seen[before][0]) * ahead;
        float goPitch = seen[at][1] + (seen[at][1] - seen[before][1]) * ahead;
        float leftYaw = 0F, leftPitch = 0F;
        for (float[] move : moves) {
            float undone = 1F - minJerk(move[3] / move[2]);
            leftYaw += move[0] * undone;
            leftPitch += move[1] * undone;
        }
        // Up close - almost inside one another, where they fill the view and go round you fastest - a hand does not chase
        // them: while the aim (where the moves under way leave it) is on them it leaves the mouse be, keeping up with them
        // included; off them, it comes back only just onto them and slower than from further off. Yours turned a tenth of
        // a degree a tick there, mostly, where the aura chased them round at three and up to forty
        double flat = Math.hypot(entity.posX - eye.xCoord, entity.posZ - eye.zCoord);
        boolean close = !throwing && flat < CLOSE;
        boolean onThem = close && lands(entity, look(currentYaw + leftYaw, currentPitch + leftPitch), 0.0D);
        // Pursuit: what it makes of their going round is smoothed over a few ticks, and too slow to notice is not followed
        int last = seen.length - 1, saw = throwing ? last : Math.max(0, last - lag);
        float seenPace = saw > 0 ? clampF(MathHelper.wrapAngleTo180_float(seen[saw][0] - seen[saw - 1][0]), -30F, 30F) : 0F;
        paceYaw += (seenPace - paceYaw) * 0.4F;
        keep = clampF(keep + (float) rng.nextGaussian() * 0.05F, 0.3F, 0.75F);
        float pursuit = !idle && !onThem && Math.abs(paceYaw) > 0.8F ? paceYaw * keep : 0F;
        float offYaw = MathHelper.wrapAngleTo180_float(goYaw - currentYaw - pursuit - leftYaw), offPitch = goPitch - currentPitch - leftPitch;
        if (!throwing) offPitch = ontoThem(entity, eye, currentPitch + leftPitch, offPitch);
        if (--planIn <= 0) {
            resting = onThem || Math.hypot(offYaw, offPitch) <= nearEnough(entity, eye, throwing);
            planIn = 2 + rng.nextInt(3);                    // the next is planned before this one is done: a hand's moves run into one another
            idle = resting && rng.nextFloat() < (onThem ? 0.8F : 0.5F);   // near enough: half the time it leaves the mouse be; up close, most
            if (!idle) {
                if (resting) {                               // and half the time it drifts a little
                    offYaw = (float) rng.nextGaussian() * 0.7F;
                    offPitch = (float) rng.nextGaussian() * 0.5F;
                } else if (close) {                          // just onto them: short of the spot by a part of their half width
                    float half = (float) Math.toDegrees(Math.atan2((entity.getEntityBoundingBox().maxX - entity.getEntityBoundingBox().minX) / 2,
                            Math.max(0.3D, flat))), size = (float) Math.hypot(offYaw, offPitch), shy = half * (0.4F + rng.nextFloat() * 0.4F);
                    if (size > shy) {
                        offYaw *= (size - shy) / size;
                        offPitch *= (size - shy) / size;
                    }
                }
                float gain = clampF(0.95F + (float) rng.nextGaussian() * 0.15F, 0.5F, 1.4F);
                float tilt = (float) Math.toRadians(rng.nextGaussian() * 10.0D), cos = MathHelper.cos(tilt), sin = MathHelper.sin(tilt);
                float moveYaw = (offYaw * cos - offPitch * sin) * gain, movePitch = (offYaw * sin + offPitch * cos) * gain;
                float size = (float) Math.hypot(moveYaw, movePitch);
                float ticks = clampF(Math.round(2F + (float) (Math.log(1F + size / 6F) / Math.log(2D))), 3F, 7F);
                moves.add(new float[]{moveYaw, movePitch, ticks, 0F});
            }
        }
        float stepYaw = pursuit, stepPitch = pursuit * (float) rng.nextGaussian() * 0.15F;   // a hand going sideways does not hold its height
        for (java.util.Iterator<float[]> it = moves.iterator(); it.hasNext(); ) {
            float[] move = it.next();
            float from = minJerk(move[3] / move[2]);
            move[3]++;
            float to = minJerk(Math.min(1F, move[3] / move[2]));
            stepYaw += move[0] * (to - from);
            stepPitch += move[1] * (to - from);
            if (move[3] >= move[2]) it.remove();
        }
        if (stepYaw != 0F || stepPitch != 0F) {             // a moving hand's pace carries on a little from tick to tick
            stepYaw = stepYaw * 0.75F + stepWasYaw * 0.25F;
            stepPitch = stepPitch * 0.75F + stepWasPitch * 0.25F;
        }
        stepWasYaw = stepYaw;
        stepWasPitch = stepPitch;
        if (Math.abs(stepYaw) + Math.abs(stepPitch) > gcd) {   // and trembles a little
            stepYaw += (float) rng.nextGaussian() * gcd * 0.3F;
            stepPitch += (float) rng.nextGaussian() * gcd * 0.3F;
        }
        // yours topped out at some 20 degrees a tick inside half a block of them, 30 from a block and a half
        float speed = (float) Math.hypot(stepYaw, stepPitch), most = close ? 15F + 15F * clampF((float) (flat - 0.4D) / 1.1F, 0F, 1F) : 30F;
        if (speed > most) {
            stepYaw *= most / speed;
            stepPitch *= most / speed;
        }
        currentYaw += stepYaw;
        currentPitch = pitch(currentPitch + stepPitch);
    }

    /**
     * The height to correct: none while the aim at that pitch is on their body, a little in from its top and
     * bottom, where they are - and, near the edge of reach, near enough your eyes' height that a click still
     * reaches; else as far as brings it back into that, a little way in - not to the spot.
     */
    private float ontoThem(Entity entity, Vec3 eye, float aimPitch, float toSpot) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double flat = Math.max(0.3D, Math.hypot(entity.posX - eye.xCoord, entity.posZ - eye.zCoord));
        double y = eye.yCoord - Math.tan(Math.toRadians(aimPitch)) * flat, tall = box.maxY - box.minY;
        double low = box.minY + tall * 0.35D, high = box.maxY - tall * 0.08D, range = attackRange.getDouble() - 0.1D;
        if (flat < range) {
            double up = Math.sqrt(range * range - flat * flat);
            low = Math.max(low, eye.yCoord - up);
            high = Math.min(high, eye.yCoord + up);
            if (low > high) low = high = clampD(eye.yCoord, box.minY + 0.1D, box.maxY - 0.1D);
        }
        if (y >= low && y <= high) return 0F;
        double back = low + (high - low) * (y < low ? 0.15D + rng.nextDouble() * 0.35D : 0.5D + rng.nextDouble() * 0.35D);
        // ponytail: half the way a correction, the rest the next; a hand brings its height back in small steps
        float onto = ((float) -Math.toDegrees(Math.atan2(back - eye.yCoord, flat)) - aimPitch) * 0.5F;
        return Math.abs(onto) < Math.abs(toSpot) ? onto : toSpot;
    }

    /** Minimum jerk: how much of a move is done this far through it. */
    private static float minJerk(float s) {
        return s * s * s * (10F + s * (-15F + s * 6F));
    }

    /** How near its spot is near enough to leave be, in degrees: a share of their half width - while a throw wants it close. */
    private float nearEnough(Entity entity, Vec3 eye, boolean throwing) {
        if (throwing) return 0.5F;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double flat = Math.max(0.3D, Math.hypot(entity.posX - eye.xCoord, entity.posZ - eye.zCoord));
        float half = (float) Math.toDegrees(Math.atan2((box.maxX - box.minX) / 2, flat));
        return Math.max(0.5F, half * (0.05F + rng.nextFloat() * 0.25F));
    }

    /** Stabilization: while either of you goes up or down, the aim keeps the pitch it has as long as that is still on them and in reach. */
    private Vec3 steady(EntityPlayerSP player, Entity entity, Vec3 at) {
        float hold = STABILIZATION;
        double eye = player.posY + player.getEyeHeight(), flat = Math.hypot(at.xCoord - player.posX, at.zCoord - player.posZ);
        double angle = Math.atan2(at.yCoord - eye, flat);
        if (Double.isNaN(steadyAngle)) {
            steadyAngle = angle;
            return at;
        }
        AxisAlignedBB box = entity.getEntityBoundingBox();
        boolean moving = Math.abs(entity.posY - entity.prevPosY) + Math.abs(player.posY - player.prevPosY) > 0.01D;
        double in = 0.1D, share = moving ? (1F - hold) * (1F - hold) * (1F - hold) : Math.max(0.1F, 1F - hold);
        double range = attackRange.getDouble() - 0.03D, reach = Math.sqrt(Math.abs(range * range - flat * flat));
        double low = clamp(eye - reach, box.minY + in, box.maxY - in), high = clamp(eye + reach, box.minY + in, box.maxY - in);
        double y = clamp(eye + Math.tan(steadyAngle + (angle - steadyAngle) * share) * flat, low, high);
        steadyAngle = Math.atan2(y - eye, flat);
        return new Vec3(at.xCoord, y, at.zCoord);
    }

    /** The aim wanders over them: to where one of your own hits landed, or at random until it has them. */
    private Vec3 wander(Entity entity, Vec3 at) {
        float[] s = AimModel.ready() ? LEARNED : UNLEARNED;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        Vec3 eye = eyes();
        double fx = at.xCoord - eye.xCoord, fz = at.zCoord - eye.zCoord, flat = Math.sqrt(fx * fx + fz * fz);
        if (flat < 1.0E-4D) return at;
        float pull = Math.min(0.5F, 0.06F * s[2]), push = 0.5F * (float) Math.sqrt(2F * pull - pull * pull);
        if (!AimModel.ready()) {                         // your own spots are moved between by landed(), not pushed about
            strayX = nudged(strayX * (1F - pull), push);
            strayY = nudged(strayY * (1F - pull), push);
        }
        glideX += (strayX - glideX) * (1F - WANDER_SMOOTH);
        glideY += (strayY - glideY) * (1F - WANDER_SMOOTH);
        // a share of them, but never a wider angle than that is from three blocks off
        double near = Math.min(1.0D, flat / 3.0D), in = 0.05D;
        double across = glideX * s[0] * (box.maxX - box.minX) / 2 * near, up = glideY * s[1] * (box.maxY - box.minY) / 2 * near;
        return new Vec3(clamp(at.xCoord - fz / flat * across, box.minX + in, box.maxX - in), clamp(at.yCoord + up, box.minY + in, box.maxY - in),
                clamp(at.zCoord + fx / flat * across, box.minZ + in, box.maxZ - in));
    }

    /** In reach, the spot is one a click would land on: the wandering one, else the first of these that is. */
    private Vec3 hittable(Entity entity, Vec3 to, Vec3 at) {
        if (distanceTo(entity) > attackRange.getDouble()) return to;
        Vec3 eye = eyes(), mid = new Vec3(entity.posX, (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2, entity.posZ);
        double in = 0.2D, low = entity.getEntityBoundingBox().minY + in, high = entity.getEntityBoundingBox().maxY - in;
        for (Vec3 spot : new Vec3[]{to, at, new Vec3(mid.xCoord, clamp(eye.yCoord, low, high), mid.zCoord), bestHitVec(eye, entity),
                new Vec3(mid.xCoord, high, mid.zCoord), new Vec3(mid.xCoord, low, mid.zCoord)}) {
            if (lands(entity, spot.subtract(eye).normalize(), 0.03D)) return spot;
        }
        return to;
    }

    private void relearn() {
        float[] hit = AimModel.pick();
        strayX = hit[0];
        strayY = hit[1];
    }

    // ---------------------------------------------------------------- the click

    /**
     * Clicking, while someone is within reach and Pre-Swing: on the rhythm's own deadlines, whatever the aim
     * is doing - a hand does not stop clicking because its crosshair slipped. Killaura asks only while they
     * are that close; asked again after a while, the clicking starts afresh somewhere in its first gap.
     */
    @Override protected boolean clickDue(long now) {
        if (target == null) return false;
        if (onTicks()) {
            // your swings, tick by tick: asked once a tick, it counts down to the next of yours. Afresh, somewhere in its first gap
            // the ticks that went by, asked or not: a tick just past Pre-Swing's edge still counts, as it does for your hand
            if (ticksLeft < 0 || now - lastAsked > 120L) ticksLeft = rng.nextInt(Math.max(1, swingGap()) + 1);
            else ticksLeft -= Math.max(1, Math.round((now - lastAsked) / 50F));
            lastAsked = now;
            if (minecraft.thePlayer.isUsingItem()) {     // clicks made with the sword up are lost, as vanilla loses them
                if (ticksLeft <= 0) ticksLeft = Math.max(1, swingGap());
                return false;
            }
            if (reopened && ticksLeft == 1 && distanceTo(target) <= attackRange.getDouble() && aimedAt(target)) {   // Hit Timing, a tick early at most
                double chance = hitTiming.getMin() + rng.nextDouble() * (Math.max(hitTiming.getMin(), hitTiming.getMax()) - hitTiming.getMin());
                if (rng.nextDouble() * 100.0D < chance) ticksLeft = 0;
            }
            return ticksLeft <= 0;
        }
        if (Double.isNaN(nextClick) || now - lastAsked > 120L) nextClick = now + rng.nextDouble() * gap();
        lastAsked = now;
        if (now - nextClick > 150.0D) nextClick = now;   // a stalled tick is not made up for with a burst
        if (minecraft.thePlayer.isUsingItem()) {         // clicks made with the sword up are lost, as vanilla loses them
            while (now >= nextClick) nextClick += gap();
            return false;
        }
        // Hit Timing: the tick they can be hit again, a click that was coming before the next tick anyway is made in
        // this one - as often as a chance drawn anywhere in the range says. Never more than a tick early
        if (reopened && nextClick - now < 50.0D && distanceTo(target) <= attackRange.getDouble() && aimedAt(target)) {
            double chance = hitTiming.getMin() + rng.nextDouble() * (Math.max(hitTiming.getMin(), hitTiming.getMax()) - hitTiming.getMin());
            if (rng.nextDouble() * 100.0D < chance) nextClick = Math.min(nextClick, now);
        }
        return now >= nextClick;
    }

    /**
     * Block Hit: after a fresh hit on them, more often than not, the sword goes up for a moment - a tap,
     * down again long before they can be hit again, so it costs no hit of your own.
     */
    @Override protected Boolean ownBlock(long now) {
        if (!blockHit.isEnabled() || target == null) return null;
        if (struck && distanceTo(target) <= attackRange.getDouble() && rng.nextFloat() < 0.6F) blockUntil = now + 60L + rng.nextInt(90);
        struck = false;
        return now < blockUntil;
    }

    /** Every click whose deadline has passed this tick - two can, as two of yours can between ticks. */
    @Override protected int pollClicks(long now) {
        int count = 0;
        if (onTicks()) {                                 // this tick's swing, and one more for each 0 of yours after it
            int next;
            for (count = 1; (next = swingGap()) == 0 && count < 4; ) count++;
            ticksLeft = Math.max(1, next);
        } else while (now >= nextClick && count < 4) {
            count++;
            nextClick += gap();
        }
        EntityLivingBase entity = target;
        polled += count;
        if (count == 0 || entity == null || distanceTo(entity) > attackRange.getDouble()) return count;
        if (missing && entity.hurtTime <= 1) missing = false;   // that was the swing it was not back for
        return count;
    }

    /**
     * Clicking Learned, with enough of your swings: on your own ticks. The server sees a click only in the tick it
     * is sent in, so that is what is played back - your ticks between swings, in your order, two in a tick where
     * you had two - and not milliseconds, which land on the ticks one way for your hand and another for this.
     */
    private boolean onTicks() {
        return clicking.is("Learned") && AimModel.swingRhythm();
    }

    private int swingGap() {
        int ticks = AimModel.nextSwing();
        return ticks < 0 ? 2 : ticks;
    }

    /** The gap to the next click: your own clicking, played back, once it has enough of it; else a hand at the CPS range. */
    private double gap() {
        if (clicking.is("Learned") && AimModel.rhythm()) {
            float learned = AimModel.nextGap();      // a little off each time, so no run of yours lands on the ticks the same way twice
            if (learned > 0F) return clampD(learned * Math.exp(rng.nextGaussian() * 0.08D), 5.0D, 1200.0D);   // a double click of yours stays one
        }
        // A hand at the CPS range - Learned, at the pace you have clicked at so far - drifting about it rather than
        // drawing every click afresh. Its gaps are a hand's: never much under what its pace allows, now and then far over
        double low = cps.getMin(), high = Math.max(low, cps.getMax());
        if (handCps < low || handCps > high) handCps = low + rng.nextDouble() * (high - low);
        handCps = clampD(handCps + rng.nextGaussian() * Math.max(0.25D, (high - low) * 0.15D), low, high);
        double pace = clicking.is("Learned") && AimModel.clicks() >= 50 ? AimModel.cps() * handCps * 2.0D / (low + high) : handCps;
        double gap = 1000.0D / pace * (0.4D + 0.6D * Math.exp(rng.nextGaussian() - 0.5D));
        return clampD(gap, 25.0D, 1200.0D);
    }

    /**
     * After each hit that landed: whether the aim now runs out past them, and how far - what yours did in one of
     * the stretches between two of your own hits made in the {@link #state} this is. Miss rate is how
     * often it is not back for the next click.
     */
    private void overshoot(EntityLivingBase entity, long now, long gap) {
        EntityPlayerSP player = minecraft.thePlayer;
        missing = rng.nextFloat() * 100F < missRate.getFloat();
        float[] out = AimModel.overshoot(state(player, entity), missing);
        overYaw = out[0];
        overPitch = out[1];
        missing &= overYaw != 0F || overPitch != 0F;
        overAt = now;
        // ponytail: rotations are sent once a tick, so the window ends no sooner than 75 ms - the next tick's aim always sees it
        overFrom = (long) (rng.nextFloat() * 0.15F * gap);
        overUntil = Math.max(75L, (long) ((0.3F + rng.nextFloat() * 0.3F) * gap));
        // going sideways, they are overshot the way they are going
        double dx = entity.posX - player.posX, dz = entity.posZ - player.posZ;
        double round = dx * (entity.posZ - entity.prevPosZ) - dz * (entity.posX - entity.prevPosX);
        if (Math.abs(round) > 0.03D * Math.sqrt(dx * dx + dz * dz)) overYaw = Math.abs(overYaw) * Math.signum((float) round);
        lag = Math.round(after(trackingDelay.getFloat()) / 50F);   // and the next stretch is followed a little later or sooner
    }

    private boolean aimedAt(Entity entity) {
        return lands(entity, look(rotation.getYaw(), rotation.getPitch()), 0.0D);
    }

    /** Whether a click with the aim that way lands on them: their hitbox inside reach, with no block in front. */
    private boolean lands(Entity entity, Vec3 look, double inside) {
        Vec3 eye = eyes();
        AxisAlignedBB box = hitbox(entity);
        if (box.isVecInside(eye)) return true;
        double reach = attackRange.getDouble() - inside;
        MovingObjectPosition hit = box.calculateIntercept(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        return hit != null && minecraft.theWorld.rayTraceBlocks(eye, hit.hitVec, false, false, false) == null;
    }

    /** So many milliseconds, give or take: no two waits come out the same. */
    private long after(float ms) {
        double f = Math.exp(DELAY_RANDOM / 100.0D * 0.6D * rng.nextGaussian());
        return (long) (ms * Math.max(0.5D, Math.min(2.5D, f)));
    }

    private float nudged(float value, float by) {
        return clampF(value + (float) rng.nextGaussian() * by, -1F, 1F);
    }

    // ---------------------------------------------------------------- the lesson (only with every aura off)

    /** Your aim over the last ticks: yaw, pitch, the eye, the corner of whoever the crosshair was nearest, their id. */
    private static final ArrayDeque<double[]> watched = new ArrayDeque<double[]>();
    /** Whom your last own hit was on, or -1 once your aim has been let go of since; and what it was made in. */
    private static int lastStruck = -1, struckIn;
    /** Someone was near your crosshair last tick: clicks then are a fight's. */
    private static boolean dirty, fighting;
    private static long savedAt, lastClickNanos, pressedAt;
    /** Your aim against whoever the crosshair was nearest, the last four ticks, newest first; and whom. */
    private static final ArrayDeque<float[]> own = new ArrayDeque<float[]>();
    private static int ownOn = -1;

    /** A tick of your own aim in a fight - within six blocks of them, and you clicked in the last second - kept for Aim Yours. */
    private static void aimed(EntityPlayerSP player, Entity near, long now) {
        if (near == null || near.getEntityId() != ownOn || reach(player.getPositionEyes(1.0F), near) > 6.0D) own.clear();
        if (near == null) return;
        ownOn = near.getEntityId();
        AxisAlignedBB body = near.getEntityBoundingBox();
        Vec3 from = player.getPositionEyes(1.0F);
        own.addFirst(AimModel.seen(player.rotationYaw, player.rotationPitch, from.xCoord, from.yCoord, from.zCoord,
                (body.minX + body.maxX) / 2, (body.minY + body.maxY) / 2, (body.minZ + body.maxZ) / 2, (body.maxX - body.minX) / 2 + near.getCollisionBorderSize()));
        if (own.size() > 4) own.removeLast();
        if (own.size() == 4 && now - pressedAt <= 1000L) {
            float[][] ticks = own.toArray(new float[0][]);
            AimModel.step(AimModel.sample(ticks[0], ticks[1], ticks[2], ticks[3]));
            dirty = true;
        }
    }

    private static AIAuraV2Module instance() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(AIAuraV2Module.class);
    }

    static void ensureLoaded() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!AimModel.loaded() && mc != null && mc.mcDataDir != null) AimModel.load(new File(mc.mcDataDir, "vibe/aiaura-v2/aim.bin"));
    }

    /** Learn is on and the aim is your own: no aura, aim assist, scaffold or free camera has it. */
    private static boolean learning(Minecraft mc, AIAuraV2Module module) {
        if (module == null || !module.learn.isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return false;
        KillAuraModule aura = KillAuraModule.activeController();
        AimAssistModule assist = Vibe.getInstance().getModuleManager().getModule(AimAssistModule.class);
        return (aura == null || !aura.isEnabled()) && (assist == null || !assist.isEnabled()) && !ScaffoldModule.isActive()
                && !FreecamModule.cameraActiveHook();
    }

    /** End of each client tick: your aim is kept for a second and a half against whoever the crosshair is nearest. */
    public static void learnTick() {
        Minecraft mc = Minecraft.getMinecraft();
        AIAuraV2Module module = instance();
        Entity near = null;
        if (learning(mc, module)) {
            EntityPlayerSP player = mc.thePlayer;
            Vec3 eye = player.getPositionEyes(1.0F);
            double least = Double.MAX_VALUE;
            for (Object value : mc.theWorld.loadedEntityList) {
                if (!(value instanceof EntityLivingBase) || value == player) continue;
                EntityLivingBase entity = (EntityLivingBase) value;
                if (!entity.isEntityAlive() || reach(eye, entity) > 8.0D) continue;
                double off = offView(player, entity);
                if (off < least) {
                    least = off;
                    near = entity;
                }
            }
        }
        long now = now();
        fighting = near != null;
        if (near == null) {
            watched.clear();
            lastStruck = -1;
            lastClickNanos = 0L;
        } else {
            note(mc.thePlayer, near);
        }
        aimed(mc.thePlayer, near, now);
        swungTick(near);
        // ponytail: written at most every 5 s while you fight and once you stop; a crash loses those seconds, not the file
        if (dirty && (near == null || now - savedAt > 5000L)) {
            AimModel.save();
            dirty = false;
            savedAt = now;
        }
        record(mc, module, near);
    }

    /** Presses of your own button, clicks of the aura's, attacks by either, and swings and attacks sent, since the last tick; and the record itself. */
    private static int pressed, polled, attacked, swung, hit, tickNo, fightNo, rows;
    private static boolean recording;
    private static java.io.PrintWriter trace;

    /**
     * Record Fights: every tick of a fight as a server sees it - yours with every aura off (Learn on), or
     * the aura's - to vibe/aiaura-v2/traces: the rotation sent, where the two of you are, the swings and the
     * attacks. Side by side, the two show where the aura is not you.
     * ponytail: one file a game session and 200 000 ticks of it at most - under three hours of fighting, some 30 MB
     */
    /** Every packet that goes out: the swings and attacks the server sees, whoever made them. */
    public static void sent(Object packet) {
        if (packet instanceof net.minecraft.network.play.client.C0APacketAnimation) {
            swung++;
            ownSwung++;
        }
        else if (packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
                && ((net.minecraft.network.play.client.C02PacketUseEntity) packet).getAction() == net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK) hit++;
    }

    private static void record(Minecraft mc, AIAuraV2Module module, Entity near) {
        int presses = pressed, clicks = polled, swings = swung, attacks = hit;
        pressed = polled = attacked = swung = hit = 0;
        tickNo++;
        boolean aura = module != null && mc.thePlayer != null && module.isEnabled() && module.target != null && KillAuraModule.activeController() == module;
        if (module == null || !module.recordFights.isEnabled() || near == null && !aura || rows >= 200000) {
            if (recording && trace != null) trace.flush();
            recording = false;
            return;
        }
        try {
            if (trace == null) {
                File dir = new File(mc.mcDataDir, "vibe/aiaura-v2/traces");
                dir.mkdirs();
                trace = new java.io.PrintWriter(new File(dir, new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".csv"));
                trace.println("tick,ms,yaw,pitch,sx,sy,sz,ex,ey,ez,dist,swings,attacks,hurt,resting,ownHurt,ground,id,fight,aura,presses,clicks");
            }
        } catch (java.io.IOException e) {
            rows = Integer.MAX_VALUE;                    // nowhere to write: no record this session, and no more tries
            return;
        }
        if (!recording) fightNo++;
        recording = true;
        rows++;
        EntityPlayerSP player = mc.thePlayer;
        Entity entity = aura ? module.target : near;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        // the rotation the server is sent: the aura's, or MoveFix's turning back from it, or your own
        dev.vibe.module.impl.movement.MoveFixModule fix = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.movement.MoveFixModule.class);
        float yaw = fix != null ? fix.getRotationYaw() : player.rotationYaw, pitch = fix != null ? fix.getRotationPitch() : player.rotationPitch;
        trace.println(tickNo + "," + now() + "," + yaw + "," + pitch
                + "," + player.posX + "," + player.posY + "," + player.posZ + "," + entity.posX + "," + box.minY + "," + entity.posZ
                + "," + reach(player.getPositionEyes(1.0F), entity) + "," + swings + "," + attacks
                + "," + (entity instanceof EntityLivingBase ? ((EntityLivingBase) entity).hurtTime : 0) + "," + (aura && module.resting ? 1 : 0)
                + "," + player.hurtTime + "," + (player.onGround ? 1 : 0) + "," + entity.getEntityId() + "," + fightNo + "," + (aura ? 1 : 0)
                + "," + presses + "," + clicks);
    }

    /**
     * Each press of your real left button (Forge's MouseEvent, stamped by LWJGL when it happened - not when
     * the tick got to it). In a fight, the gap since your last press is your clicking; a second or more
     * is the end of a burst. AutoClicker's clicks are not yours, so nothing is kept while it is on.
     */
    /** Swings sent since the last tick, for the learning; and ticks since your last in this fight, -1 with none. */
    private static int ownSwung, sinceSwing = -1;

    /** Your swings in a fight, tick by tick, as the server gets them - not AutoClicker's, nor any aura's. */
    private static void swungTick(Entity near) {
        int count = ownSwung;
        ownSwung = 0;
        AutoClickerModule clicker = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(AutoClickerModule.class);
        if (near == null || clicker != null && clicker.isEnabled()) {
            sinceSwing = -1;
            return;
        }
        if (sinceSwing >= 0) sinceSwing++;
        if (count == 0) return;
        AimModel.swing(sinceSwing >= 0 && sinceSwing <= 20 ? sinceSwing : -1);
        for (int i = 1; i < count; i++) AimModel.swing(0);
        sinceSwing = 0;
        dirty = true;
    }

    public static void learnClick(long nanos) {
        pressed++;
        pressedAt = now();
        Minecraft mc = Minecraft.getMinecraft();
        AutoClickerModule clicker = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(AutoClickerModule.class);
        if (!fighting || !learning(mc, instance()) || clicker != null && clicker.isEnabled()) {
            lastClickNanos = 0L;
            return;
        }
        ensureLoaded();
        if (lastClickNanos != 0L && nanos > lastClickNanos) {
            double gap = (nanos - lastClickNanos) / 1.0E6D;
            AimModel.click(gap < 1000.0D ? (float) gap : 0F);
            dirty = true;
        }
        lastClickNanos = nanos;
    }

    private static void note(EntityPlayerSP player, Entity entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        watched.addLast(new double[]{player.rotationYaw, player.rotationPitch, player.posX, player.posY + player.getEyeHeight(), player.posZ,
                box.minX, box.minY, box.minZ, entity.getEntityId()});
        if (watched.size() > MEMORY) watched.removeFirst();
    }

    /**
     * At every hit of your own: where on them your crosshair landed, across and up from the nearest point of
     * them. The ticks before were turning for that spot: each is kept as how far off it the aim was and how
     * far toward it you turned. And when your last hit was on them too, how far out past them the aim got in
     * between: your overshoot, or that there was none.
     */
    public static void learnAttack(Entity entity) {
        attacked++;
        Minecraft mc = Minecraft.getMinecraft();
        if (!learning(mc, instance())) return;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || hit.entityHit != entity || hit.hitVec == null) return;
        ensureLoaded();
        EntityPlayerSP player = mc.thePlayer;
        Vec3 eye = player.getPositionEyes(1.0F), at = bestHitVec(eye, entity), on = hit.hitVec;
        double fx = at.xCoord - eye.xCoord, fz = at.zCoord - eye.zCoord, flat = Math.sqrt(fx * fx + fz * fz);
        if (flat < 0.1D) return;                         // standing in them, there is no across
        AxisAlignedBB box = entity.getEntityBoundingBox();
        boolean ready = AimModel.ready();
        AimModel.add((float) (((on.zCoord - at.zCoord) * fx - (on.xCoord - at.xCoord) * fz) / flat / ((box.maxX - box.minX) / 2)),
                (float) ((on.yCoord - at.yCoord) / ((box.maxY - box.minY) / 2)));
        boolean between = lastStruck == entity.getEntityId() && watched.size() < MEMORY;   // every tick since your last hit, and that on them
        lastStruck = entity.getEntityId();
        note(player, entity);
        float outYaw = 0F, outPitch = 0F, wasYaw = 0F;
        double[] last = null;
        for (double[] t : watched) {
            if (t[8] != entity.getEntityId()) {
                between = false;
                last = null;
                continue;
            }
            double dx = t[5] + on.xCoord - box.minX - t[2], dy = t[6] + on.yCoord - box.minY - t[3], dz = t[7] + on.zCoord - box.minZ - t[4];
            float spotYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            float spotPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            if (last != null) {
                // as turn() uses it: what was left before they moved, and the turn less what keeping up with them took
                float offYaw = MathHelper.wrapAngleTo180_float(wasYaw - (float) last[0]), offPitch = spotPitch - (float) last[1];
                float off = (float) Math.hypot(offYaw, offPitch);
                float turnYaw = (float) (t[0] - last[0]) - MathHelper.wrapAngleTo180_float(spotYaw - wasYaw), turnPitch = (float) (t[1] - last[1]);
                float toward = off < 1.0E-3F ? 0F : (turnYaw * offYaw + turnPitch * offPitch) / off;
                if (toward > 0F) AimModel.pace(off, toward, Math.abs(turnPitch * offYaw - turnYaw * offPitch) / off);
            }
            wasYaw = spotYaw;
            float awayYaw = MathHelper.wrapAngleTo180_float((float) t[0] - spotYaw), awayPitch = (float) t[1] - spotPitch;
            AxisAlignedBB then = box.offset(t[5] - box.minX, t[6] - box.minY, t[7] - box.minZ);
            Vec3 from = new Vec3(t[2], t[3], t[4]), look = look((float) t[0], (float) t[1]);
            if (Math.hypot(awayYaw, awayPitch) > Math.hypot(outYaw, outPitch) && !then.isVecInside(from)
                    && then.calculateIntercept(from, from.addVector(look.xCoord * 16, look.yCoord * 16, look.zCoord * 16)) == null) {
                outYaw = awayYaw;
                outPitch = awayPitch;
            }
            last = t;
        }
        if (between && Math.hypot(outYaw, outPitch) <= 30.0D) AimModel.overshot(outYaw, outPitch, struckIn);   // further out is looking away
        struckIn = state(player, entity);
        watched.clear();
        dirty = true;
        if (!ready) actionBar(AimModel.ready() ? "AIAura V2: it has your hits" : "AIAura V2: learning your hits, " + AimModel.size() + " of " + AimModel.ENOUGH);
    }

    // ---------------------------------------------------------------- geometry

    /** What a stretch between two hits is made in: going sideways to one another (1) or not, you in the air (2) or on the ground. */
    private static int state(EntityPlayerSP player, Entity entity) {
        double dx = entity.posX - player.posX, dz = entity.posZ - player.posZ;
        double vx = entity.posX - entity.prevPosX - (player.posX - player.prevPosX), vz = entity.posZ - entity.prevPosZ - (player.posZ - player.prevPosZ);
        return (Math.abs(dx * vz - dz * vx) > 0.05D * Math.sqrt(dx * dx + dz * dz) ? 1 : 0) | (player.onGround ? 0 : 2);
    }

    /** The point of their hitbox nearest the eye, kept a little inside its faces: what your hits are measured from. */
    private static Vec3 bestHitVec(Vec3 eye, Entity entity) {
        AxisAlignedBB box = hitbox(entity);
        double in = 0.085D;
        return new Vec3(clampD(eye.xCoord, box.minX + in, box.maxX - in), clampD(eye.yCoord, box.minY + in, box.maxY - in),
                clampD(eye.zCoord, box.minZ + in, box.maxZ - in));
    }

    private static AxisAlignedBB hitbox(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    /** Eye to the nearest point of their hitbox. */
    private static double reach(Vec3 eye, Entity entity) {
        AxisAlignedBB box = hitbox(entity);
        return eye.distanceTo(new Vec3(clampD(eye.xCoord, box.minX, box.maxX), clampD(eye.yCoord, box.minY, box.maxY), clampD(eye.zCoord, box.minZ, box.maxZ)));
    }

    /** How far off your own view they are, in degrees. */
    private static double offView(EntityPlayerSP player, Entity entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double dx = (box.minX + box.maxX) / 2 - player.posX, dy = (box.minY + box.maxY) / 2 - (player.posY + player.getEyeHeight()),
                dz = (box.minZ + box.maxZ) / 2 - player.posZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return Math.hypot(MathHelper.wrapAngleTo180_double(yaw - player.rotationYaw), pitch - player.rotationPitch);
    }

    /** Entity.getVectorForRotation, which 1.8.9 keeps protected. */
    private static Vec3 look(float yaw, float pitch) {
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI), sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F), sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    private static void actionBar(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.ingameGUI != null) mc.ingameGUI.setRecordPlaying(text, false);
    }

    private static long now() { return System.nanoTime() / 1000000L; }
    private static float pitch(float value) { return clampF(value, -90F, 90F); }
    private static float clampF(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static double clampD(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double sq(double value) { return value * value; }
}
