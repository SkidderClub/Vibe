package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.combat.CriticalsModule;
import dev.vibe.module.impl.movement.StepModule;
import dev.vibe.module.impl.movement.AntiVoidModule;
import dev.vibe.module.impl.movement.NoFallModule;
import dev.vibe.module.impl.movement.NoSlowModule;
import dev.vibe.module.impl.movement.SpeedModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/** Main-thread adapters for the three native phases used by the imported modes. */
public final class RewoundHooks {
    private RewoundHooks() { }
    private static <T extends Module> T module(Class<T> type) {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(type);
    }

    public static void preUpdate(Object entity) {
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        StepModule step = module(StepModule.class);
        if (step != null) step.rewoundPreUpdate(entity);
        AntiVoidModule antiVoid = module(AntiVoidModule.class);
        if (antiVoid != null) antiVoid.preUpdate(entity);
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) criticals.rewoundPreUpdate(entity);
    }
    public static float preStepHeight(Object entity, float vanilla) {
        StepModule step = module(StepModule.class);
        return step == null ? vanilla : step.rewoundPreStep(entity, vanilla);
    }
    public static float stepHeight(Object entity, float vanilla) {
        StepModule step = module(StepModule.class);
        return step == null ? vanilla : step.rewoundStepHeight(entity, vanilla);
    }
    public static void postStep(Object entity) {
        StepModule step = module(StepModule.class);
        if (step != null) step.rewoundPostStep(entity);
    }

    public static double[] move(Object entity, double x, double y, double z) {
        if (entity != Minecraft.getMinecraft().thePlayer) return null;
        SpeedModule speed = module(SpeedModule.class);
        return speed == null ? null : speed.rewoundMove(entity, x, y, z);
    }
    public static void input(net.minecraft.util.MovementInput input) {
        SpeedModule speed = module(SpeedModule.class);
        if (speed != null && speed.rewoundControlsJump()) input.jump = false;
    }
    public static void afterMove(Object entity, double[] move) {
        if (move == null) return;
        SpeedModule speed = module(SpeedModule.class);
        if (speed != null) speed.rewoundAfterMove(entity, move);
    }
    public static void beforeWalking(Object entity) {
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) criticals.rewoundBeforeWalking(entity);
        NoFallModule fall = module(NoFallModule.class);
        if (fall != null) fall.beforeWalking(entity);
        NoSlowModule slow = module(NoSlowModule.class);
        if (slow != null) slow.beforeWalking(entity);
    }
    public static boolean ground(Object entity, boolean vanilla) {
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) vanilla = criticals.rewoundGround(entity, vanilla);
        NoFallModule fall = module(NoFallModule.class);
        return fall == null ? vanilla : fall.walkingGround(entity, vanilla);
    }
    public static double walkingY(double vanilla) {
        CriticalsModule criticals = module(CriticalsModule.class);
        return criticals == null ? vanilla : criticals.rewoundWalkingY(vanilla);
    }
    public static int positionTicks(int vanilla) {
        CriticalsModule criticals = module(CriticalsModule.class);
        return criticals == null ? vanilla : criticals.rewoundPositionTicks(vanilla);
    }
    public static void afterWalking(Object entity) {
        if (entity != Minecraft.getMinecraft().thePlayer) return;
        try {
            NoSlowModule slow = module(NoSlowModule.class);
            if (slow != null) slow.afterWalking(entity);
            KillAuraModule aura = KillAuraModule.activeController();
            if (aura != null) aura.rewoundAfterWalking(entity);
        } finally { endWalking(); }
    }
    public static void endWalking() {
        CriticalsModule criticals = module(CriticalsModule.class);
        if (criticals != null) criticals.endRewoundWalking();
        NoSlowModule slow = module(NoSlowModule.class);
        if (slow != null) slow.endWalking();
        NoFallModule fall = module(NoFallModule.class);
        if (fall != null) fall.afterWalking();
    }
    /** Inserted after PacketThreadUtil, so the packet and state change exactly once on the client thread. */
    public static void correction(Object packet) {
        dev.vibe.module.impl.world.ScaffoldModule scaffold = module(dev.vibe.module.impl.world.ScaffoldModule.class);
        if (scaffold != null && packet instanceof S08PacketPlayerPosLook) scaffold.onTowerCorrection((S08PacketPlayerPosLook) packet);
        SpeedModule speed = module(SpeedModule.class);
        if (speed != null && packet instanceof S08PacketPlayerPosLook) speed.rewoundCorrection((S08PacketPlayerPosLook) packet);
    }
}
