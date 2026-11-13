package dev.vibe.module.impl.combat;

import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.module.impl.movement.FlyModule;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.setting.ModeSetting;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovementInput;
import java.lang.reflect.Field;

/** Uses ordinary jump input and the existing outbound movement packet boundary. */
public final class CriticalsModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","NoGround","NoGround","Jump"));
    private volatile boolean spoof;
    private boolean prime=true;
    private int attackTicks;
    private String previousMode;
    private static Field ground;
    public CriticalsModule(){super("Criticals","NoGround spoofs movement ground flags; Jump uses real jumps during attacks",Category.COMBAT);}
    private boolean allowed(){FlyModule fly=module(FlyModule.class);return controlling()&&mc.thePlayer.capabilities!=null&&!mc.thePlayer.capabilities.isFlying
            &&(fly==null||!fly.isEnabled())&&!mc.thePlayer.isInWater()&&!mc.thePlayer.isInLava()&&!mc.thePlayer.isRiding()
            &&MovementForecast.loaded(mc.thePlayer.getEntityBoundingBox())&&!mc.thePlayer.isOnLadder()&&!dev.vibe.combat.CombatRangeSupport.isInWeb(mc.thePlayer)
            &&!mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness)&&!mc.thePlayer.isUsingItem()&&!ScaffoldModule.isActive()&&!dev.vibe.module.impl.world.AutoWaterModule.reserving();}
    @Override public void tick(){if(!mode.getValue().equals(previousMode)){previousMode=mode.getValue();prime=mode.is("NoGround");attackTicks=0;}if(attackTicks>0)attackTicks--;spoof=mode.is("NoGround")&&allowed();if(!playing()){attackTicks=0;prime=mode.is("NoGround");}}
    @Override public void input(MovementInput input){if(!allowed()||input.sneak||!mc.thePlayer.onGround)return;
        boolean wants=mode.is("NoGround")?prime:attackTicks>0||combatTarget()!=null;
        if(!wants||!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,mc.thePlayer.getEntityBoundingBox().offset(0,.42,0)).isEmpty())return;
        input.jump=true;prime=false;
    }
    private EntityLivingBase combatTarget(){AIAuraModule ai=module(AIAuraModule.class);KillAuraModule aura=module(KillAuraModule.class);EntityLivingBase target=ai!=null&&ai.isEnabled()?ai.getTarget():null;if(target==null&&aura!=null&&aura.isEnabled())target=aura.getTarget();return target!=null&&target.isEntityAlive()&&target.worldObj==mc.theWorld&&dev.vibe.combat.CombatRangeSupport.distanceToBox(target)<=3?target:null;}
    public void outbound(Packet<?> packet){if(!isEnabled()||!mode.is("NoGround")||!spoof||!(packet instanceof C03PacketPlayer))return;
        try{if(ground==null){ground=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(C03PacketPlayer.class,"onGround","field_149474_g");ground.setAccessible(true);}ground.setBoolean(packet,false);}catch(IllegalAccessException failure){throw new IllegalStateException("Criticals movement ground field",failure);}
    }
    public static void attackHook(Object target){CriticalsModule module=module(CriticalsModule.class);if(module!=null&&module.isEnabled()&&module.mode.is("Jump")&&target instanceof EntityLivingBase&&((EntityLivingBase)target).isEntityAlive()&&module.allowed())module.attackTicks=4;}
    @Override protected void onEnable(){prime=true;previousMode=null;attackTicks=0;spoof=false;}
    @Override protected void onDisable(){attackTicks=0;spoof=false;prime=false;}
}
