package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.combat.CriticalsModule;
import dev.vibe.module.impl.movement.StepModule;
import dev.vibe.module.impl.movement.FlyModule;
import dev.vibe.module.impl.movement.PhaseModule;
import dev.vibe.module.impl.movement.AntiVoidModule;
import dev.vibe.module.impl.movement.NoFallModule;
import dev.vibe.module.impl.movement.NoSlowModule;
import dev.vibe.module.impl.movement.SpeedModule;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/** Main-thread adapters for the three native phases used by the imported modes. */
public final class RewoundHooks {
    // The native walking scope ends before Netty necessarily writes the packet.
    private static final Set<Packet<?>> CRITICALS_GROUND = Collections.newSetFromMap(
            Collections.synchronizedMap(new WeakHashMap<>()));

    private RewoundHooks() { }
    private static <T extends Module> T module(Class<T> type) {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(type);
    }

    public static void preUpdate(Object entity) {
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        final FlyModule fly = module(FlyModule.class);
        if (fly != null) fly.rewoundPreUpdate();
        PhaseModule phase=module(PhaseModule.class);if(phase!=null)phase.preUpdate(entity);
        StepModule step = module(StepModule.class);
        if (step != null) step.rewoundPreUpdate(entity);
        AntiVoidModule antiVoid = module(AntiVoidModule.class);
        if (antiVoid != null) antiVoid.preUpdate(entity);
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) criticals.rewoundPreUpdate(entity);
    }
    public static float preStepHeight(Object entity, float vanilla) {
        StepModule step = module(StepModule.class);
        float value=step == null ? vanilla : step.rewoundPreStep(entity, vanilla);
        return entity==Minecraft.getMinecraft().thePlayer?AdjustJesus.stepHeight(value):value;
    }
    public static float stepHeight(Object entity, float vanilla) {
        StepModule step = module(StepModule.class);
        float value=step == null ? vanilla : step.rewoundStepHeight(entity, vanilla);
        return entity==Minecraft.getMinecraft().thePlayer?AdjustJesus.stepHeight(value):value;
    }
    public static void postStep(Object entity) {
        StepModule step = module(StepModule.class);
        if (step != null) step.rewoundPostStep(entity);
    }
    public static void confirmAdjustStep(Object entity) {
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        StepModule step = module(StepModule.class);
        if (step != null) step.adjustPostStep(entity);
    }

    public static double[] move(Object entity, double x, double y, double z) {
        if (entity != Minecraft.getMinecraft().thePlayer) return null;
        final FlyModule fly = module(FlyModule.class);
        SpeedModule speed = module(SpeedModule.class);
        if (fly != null && fly.isEnabled() && speed != null) speed.maintainRewound();
        final double[] flight = fly == null ? null : fly.rewoundMove(entity);
        if (flight != null) return flight;
        double[] result=speed==null?null:speed.rewoundMove(entity,x,y,z);
        dev.vibe.module.impl.movement.JesusModule jesus=module(dev.vibe.module.impl.movement.JesusModule.class);
        double[] water=jesus==null?null:jesus.move(entity,result==null?x:result[0],result==null?y:result[1],result==null?z:result[2]);
        return water==null?result:water;
    }
    public static void input(net.minecraft.util.MovementInput input) {
        SpeedModule speed = module(SpeedModule.class);
        if (speed != null && speed.rewoundControlsJump() || dev.vibe.module.impl.world.ScaffoldModule.ownsNovolineJump()) input.jump = false;
    }
    public static void afterMove(Object entity, double[] move) {
        if (move == null) return;
        SpeedModule speed = module(SpeedModule.class);
        if (speed != null) speed.rewoundAfterMove(entity, move);
    }
    public static void beforeWalking(Object entity) {
        dev.vibe.module.impl.movement.LongJumpModule longJump=module(dev.vibe.module.impl.movement.LongJumpModule.class);if(longJump!=null)longJump.diabloBeforeWalking(entity);
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        PhaseModule phase=module(PhaseModule.class);if(phase!=null)phase.beforeWalking(entity);
        final FlyModule fly = module(FlyModule.class);
        if (fly != null) {
            if (fly.isEnabled()) {
                SpeedModule speed = module(SpeedModule.class);
                if (speed != null) speed.maintainRewound();
            }
            fly.rewoundBeforeWalking(entity);
        }
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) criticals.rewoundBeforeWalking(entity);
        dev.vibe.module.impl.movement.JesusModule jesus=module(dev.vibe.module.impl.movement.JesusModule.class);
        if(jesus!=null)jesus.novolinePreUpdate(entity);
        AdjustJesus.beforeWalking();
        KillAuraModule aura = KillAuraModule.activeController();
        if(aura!=null)aura.novolineBeforeWalking(entity);
        NoFallModule fall = module(NoFallModule.class);
        if (fall != null && (criticals == null || !criticals.ownsWalkingGround(entity))) fall.beforeWalking(entity);
        NoSlowModule slow = module(NoSlowModule.class);
        if (slow != null) slow.beforeWalking(entity);
    }
    public static boolean ground(Object entity, boolean vanilla) {
        PhaseModule phase = module(PhaseModule.class);
        if (phase != null) vanilla = phase.adjustGround(entity, vanilla);
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) vanilla = criticals.rewoundGround(entity, vanilla);
        NoFallModule fall = module(NoFallModule.class);
        if (fall != null && (criticals == null || !criticals.ownsWalkingGround(entity))) vanilla = fall.walkingGround(entity, vanilla);
        dev.vibe.module.impl.world.ScaffoldModule scaffold=module(dev.vibe.module.impl.world.ScaffoldModule.class);
        return scaffold==null?vanilla:scaffold.novolineGround(entity,vanilla);
    }
    public static double walkingY(double vanilla) {
        CriticalsModule criticals = module(CriticalsModule.class);
        return AdjustJesus.walkingY(criticals == null ? vanilla : criticals.rewoundWalkingY(vanilla));
    }
    public static int positionTicks(int vanilla) {
        CriticalsModule criticals = module(CriticalsModule.class);
        return criticals == null ? vanilla : criticals.rewoundPositionTicks(vanilla);
    }

    public static void trackWalkingPacket(Object packet, Object entity) {
        if (!(packet instanceof C03PacketPlayer)) return;
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null && criticals.ownsWalkingGround(entity)) CRITICALS_GROUND.add((Packet<?>) packet);
    }

    public static boolean takeCriticalsGround(Packet<?> packet) {
        return packet instanceof C03PacketPlayer && CRITICALS_GROUND.remove(packet);
    }

    public static void afterWalking(Object entity) {
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        try {
            final FlyModule fly = module(FlyModule.class);
            if (fly != null) fly.rewoundAfterWalking();
            CriticalsModule criticals = module(CriticalsModule.class);
            if (criticals != null) criticals.adjustAfterWalking();
            dev.vibe.module.impl.movement.LongJumpModule longJump=module(dev.vibe.module.impl.movement.LongJumpModule.class);if(longJump!=null)longJump.diabloAfterWalking(entity);
            NoSlowModule slow = module(NoSlowModule.class);
            if (slow != null) slow.afterWalking(entity);
            dev.vibe.module.impl.world.ScaffoldModule scaffold=module(dev.vibe.module.impl.world.ScaffoldModule.class);
            if(scaffold!=null)scaffold.novolineAfterWalking();
            KillAuraModule aura = KillAuraModule.activeController();
            if (aura != null) {aura.rewoundAfterWalking(entity);aura.novolineAfterWalking(entity);}
        } finally { endWalking(); }
    }
    public static void endWalking() {
        final FlyModule fly = module(FlyModule.class);
        if (fly != null) fly.endRewoundWalking();
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) criticals.endRewoundWalking();
        NoSlowModule slow = module(NoSlowModule.class);
        if (slow != null) slow.endWalking();
        NoFallModule fall = module(NoFallModule.class);
        if (fall != null) fall.afterWalking();
    }
    /** Inserted after PacketThreadUtil, so the packet and state change exactly once on the client thread. */
    public static void correction(Object packet) {
        if (packet instanceof S08PacketPlayerPosLook) {
            StepModule step = module(StepModule.class);
            if (step != null) step.adjustCorrection();
        }
        final FlyModule fly = module(FlyModule.class);
        if (fly != null && packet instanceof S08PacketPlayerPosLook) fly.rewoundCorrection();
        PhaseModule phase=module(PhaseModule.class);if(phase!=null&&packet instanceof S08PacketPlayerPosLook)phase.correction();
        dev.vibe.module.impl.client.FlagDetectorModule flags=module(dev.vibe.module.impl.client.FlagDetectorModule.class);
        if(flags!=null&&packet instanceof S08PacketPlayerPosLook)flags.observe((S08PacketPlayerPosLook)packet);
        dev.vibe.module.impl.world.ScaffoldModule scaffold = module(dev.vibe.module.impl.world.ScaffoldModule.class);
        if (scaffold != null && packet instanceof S08PacketPlayerPosLook) scaffold.onTowerCorrection((S08PacketPlayerPosLook) packet);
        KillAuraModule aura=KillAuraModule.activeController();if(aura!=null&&packet instanceof S08PacketPlayerPosLook)aura.novolineCorrection();
        SpeedModule speed = module(SpeedModule.class);
        if (speed != null && packet instanceof S08PacketPlayerPosLook) speed.rewoundCorrection((S08PacketPlayerPosLook) packet);
    }
}
