package dev.vibe.module.impl.movement;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.movement.RewoundVoid;
import dev.vibe.network.PacketDelayService;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;

/** The NcpAntiVoid branch selected by the supplied Exire config. */
public final class AntiVoidModule extends FeatureModule {
    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Rewound", "Rewound"));
    public final NumberSetting fallDistance = addSetting(new NumberSetting("Fall distance", 6, 1, 10, .25));
    private final RewoundVoid prediction = new RewoundVoid();
    private EntityPlayerSP owner;
    private net.minecraft.world.World ownerWorld;
    private double lastYGround, fallen;

    public AntiVoidModule() { super("AntiVoid", "Exire's void detection and recovery packet", Category.MOVEMENT); }

    public void preUpdate(Object entity) {
        if (!isEnabled() || entity != mc.thePlayer || !playing() || mc.thePlayer.worldObj != mc.theWorld || mc.thePlayer.isSpectator()) { reset(); return; }
        EntityPlayerSP player = mc.thePlayer;
        if (owner != player || ownerWorld != player.worldObj) { reset(); owner = player; ownerWorld = player.worldObj; }
        if (player.onGround) { fallen = 0; lastYGround = player.posY; }
        else fallen = lastYGround - player.posY;
        if (fallen > fallDistance.getDouble() && !player.capabilities.allowFlying && prediction.isInVoid(player)) {
            fallen = 0;
            PacketDelayService.sendUnmodified(player.sendQueue,
                    new C04PacketPlayerPosition(player.posX, player.posY + 100, player.posZ, false));
        }
    }

    @Override public void tick() { if (!playing() || owner != mc.thePlayer || ownerWorld != mc.theWorld) reset(); }
    private void reset() { owner = null; ownerWorld = null; lastYGround = fallen = 0; prediction.clear(); }
    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }
}
