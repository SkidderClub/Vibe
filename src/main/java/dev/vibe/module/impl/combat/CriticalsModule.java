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
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.util.BlockPos;
import java.lang.reflect.Field;

/** Jump input, legacy ground flags and Exire's Updated NCP pre-network mode. */
public final class CriticalsModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","NoGround","NoGround","Jump","Rewound"));
    private volatile boolean spoof;
    private boolean prime=true;
    private int attackTicks;
    private String previousMode;
    private EntityPlayerSP rewoundPlayer;
    private net.minecraft.world.World rewoundWorld;
    private int groundTicks;
    private boolean forcePosition;
    private double walkingOffset;
    private static Field ground;
    public CriticalsModule(){super("Criticals","Controls NoGround, Jump and Rewound critical-hit modes",Category.COMBAT);}
    private boolean allowed(){FlyModule fly=module(FlyModule.class);return controlling()&&mc.thePlayer.capabilities!=null&&!mc.thePlayer.capabilities.isFlying
            &&(fly==null||!fly.isEnabled())&&!mc.thePlayer.isInWater()&&!mc.thePlayer.isInLava()&&!mc.thePlayer.isRiding()
            &&MovementForecast.loaded(mc.thePlayer.getEntityBoundingBox())&&!mc.thePlayer.isOnLadder()&&!dev.vibe.combat.CombatRangeSupport.isInWeb(mc.thePlayer)
            &&!mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness)&&!mc.thePlayer.isUsingItem()&&!ScaffoldModule.isActive()&&!dev.vibe.module.impl.world.AutoWaterModule.reserving();}
    @Override public void tick(){if(!mode.getValue().equals(previousMode)){previousMode=mode.getValue();prime=mode.is("NoGround");attackTicks=0;resetRewound();}if(attackTicks>0)attackTicks--;spoof=mode.is("NoGround")&&allowed();if(!playing()){attackTicks=0;prime=mode.is("NoGround");}if(!mode.is("Rewound")||!playing()||rewoundPlayer!=mc.thePlayer)resetRewound();}
    @Override public void input(MovementInput input){if(mode.is("Rewound")||!allowed()||input.sneak||!mc.thePlayer.onGround)return;
        boolean wants=mode.is("NoGround")?prime:attackTicks>0||combatTarget()!=null;
        if(!wants||!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,mc.thePlayer.getEntityBoundingBox().offset(0,.42,0)).isEmpty())return;
        input.jump=true;prime=false;
    }
    private EntityLivingBase combatTarget(){AIAuraModule ai=module(AIAuraModule.class);KillAuraModule aura=module(KillAuraModule.class);EntityLivingBase target=ai!=null&&ai.isEnabled()?ai.getTarget():null;if(target==null&&aura!=null&&aura.isEnabled())target=aura.getTarget();return target!=null&&target.isEntityAlive()&&target.worldObj==mc.theWorld&&dev.vibe.combat.CombatRangeSupport.distanceToBox(target)<=3?target:null;}
    public void outbound(Packet<?> packet){if(!isEnabled()||!mode.is("NoGround")||!spoof||!(packet instanceof C03PacketPlayer))return;
        try{if(ground==null){ground=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(C03PacketPlayer.class,"onGround","field_149474_g");ground.setAccessible(true);}ground.setBoolean(packet,false);}catch(IllegalAccessException failure){throw new IllegalStateException("Criticals movement ground field",failure);}
    }
    public static void attackHook(Object target){CriticalsModule module=module(CriticalsModule.class);if(module!=null&&module.isEnabled()&&module.mode.is("Jump")&&target instanceof EntityLivingBase&&((EntityLivingBase)target).isEntityAlive()&&module.allowed())module.attackTicks=4;}
    /** UpdatedNcpCriticals: counts native PRE_UPDATE events, independent of attacks. */
    public void rewoundPreUpdate(Object entity){
        if(!rewoundReady(entity)){resetRewound();return;}
        if(rewoundPlayer!=mc.thePlayer||rewoundWorld!=mc.theWorld){resetRewound();rewoundPlayer=mc.thePlayer;rewoundWorld=mc.theWorld;}
        groundTicks=mc.thePlayer.onGround?groundTicks+1:0;
    }
    private boolean rewoundReady(Object entity){return isEnabled()&&mode.is("Rewound")&&entity==mc.thePlayer&&playing()&&!mc.thePlayer.isSpectator()&&mc.thePlayer.worldObj==mc.theWorld;}
    public void rewoundBeforeWalking(Object entity){
        endRewoundWalking();
        if(!rewoundReady(entity)||rewoundPlayer!=mc.thePlayer||rewoundWorld!=mc.theWorld||groundTicks<3)return;
        BlockPos under=new BlockPos(mc.thePlayer.posX,mc.thePlayer.posY-1,mc.thePlayer.posZ);
        if(!mc.theWorld.isBlockLoaded(under))return;
        Block block=mc.theWorld.getBlockState(under).getBlock();
        if(block instanceof BlockAir||block instanceof BlockSlab||block instanceof BlockStairs)return;
        walkingOffset=mc.thePlayer.ticksExisted%2==0?1.7E-14:1E-17;
        forcePosition=true;
    }
    public boolean rewoundGround(Object entity,boolean vanilla){return rewoundReady(entity)&&forcePosition?false:vanilla;}
    public double rewoundWalkingY(double vanilla){return isEnabled()&&mode.is("Rewound")&&forcePosition?vanilla+walkingOffset:vanilla;}
    public int rewoundPositionTicks(int vanilla){return isEnabled()&&mode.is("Rewound")&&forcePosition?Math.max(20,vanilla):vanilla;}
    public void endRewoundWalking(){forcePosition=false;walkingOffset=0;}
    private void resetRewound(){rewoundPlayer=null;rewoundWorld=null;groundTicks=0;endRewoundWalking();}
    @Override protected void onEnable(){prime=true;previousMode=mode.getValue();attackTicks=0;spoof=false;resetRewound();}
    @Override protected void onDisable(){attackTicks=0;spoof=false;prime=false;resetRewound();}
}
