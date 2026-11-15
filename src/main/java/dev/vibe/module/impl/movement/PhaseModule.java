package dev.vibe.module.impl.movement;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.ModeSetting;
import io.netty.channel.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.block.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Exire's NcpPhase and WatchdogBlinkPhase, with per-module packet/state ownership. */
public final class PhaseModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Watchdog","Watchdog","NCP"));
    private EntityPlayerSP owner;private Object world;private String activeMode;
    private boolean claimed,oldClip,shouldSpeed;private float oldJump;
    private volatile boolean cage,blinking;
    private volatile boolean packetShift;
    private volatile double packetStepX,packetStepZ;
    private int ticks,delay;private long sinkAt;
    private final ConcurrentLinkedDeque<Packet<?>> signals=new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Pending> packets=new ConcurrentLinkedDeque<>();
    private static final java.lang.reflect.Field REPORT_Y=ReflectionHelper.findField(EntityPlayerSP.class,"lastReportedPosY","field_175166_bJ"),REPORT_PITCH=ReflectionHelper.findField(EntityPlayerSP.class,"lastReportedPitch","field_175165_bM");
    private static final java.lang.reflect.Field PX=ReflectionHelper.findField(C03PacketPlayer.class,"x","field_149479_a"),PY=ReflectionHelper.findField(C03PacketPlayer.class,"y","field_149477_b"),PZ=ReflectionHelper.findField(C03PacketPlayer.class,"z","field_149478_c");
    public PhaseModule(){super("Phase","Exire Watchdog cage blink and NCP block phasing",Category.MOVEMENT);}
    @Override protected void onEnable(){initialize();}
    @Override protected void onDisable(){reset(true);}
    private void initialize(){
        reset(true);owner=mc.thePlayer;world=mc.theWorld;activeMode=mode.getValue();sinkAt=System.currentTimeMillis();
        if(!playing()||!mode.is("NCP"))return;
        shouldSpeed=inside(owner.getEntityBoundingBox());
        if(inside(owner.getEntityBoundingBox().expand(.001,0,.001))||owner.isCollidedHorizontally){claim();owner.onGround=false;owner.motionX=owner.motionZ=0;teleport(.006000000238415);}
    }
    private void claim(){if(!claimed){oldClip=owner.noClip;oldJump=owner.jumpMovementFactor;claimed=true;}owner.noClip=true;owner.jumpMovementFactor=0;}
    private void reset(boolean flush){
        blinking=cage=packetShift=false;if(flush)release();else packets.clear();signals.clear();
        if(claimed&&owner!=null){if(owner.noClip&&!FreecamModule.cameraActiveHook())owner.noClip=oldClip;if(owner.jumpMovementFactor==0)owner.jumpMovementFactor=oldJump;}
        claimed=false;owner=null;world=null;activeMode=null;ticks=delay=0;shouldSpeed=false;
    }
    @Override public void tick(){
        if(owner!=mc.thePlayer||world!=mc.theWorld){reset(false);initialize();}
        else if(!mode.getValue().equals(activeMode))initialize();
        for(Packet<?> packet;(packet=signals.poll())!=null;){
            if(packet instanceof S45PacketTitle){S45PacketTitle title=(S45PacketTitle)packet;if(title.getType()==S45PacketTitle.Type.TITLE&&title.getMessage()!=null&&"3".equals(dev.vibe.pit.PitStatus.plain(title.getMessage().getUnformattedText())))cage=true;}
            if(packet instanceof S02PacketChat){S02PacketChat chat=(S02PacketChat)packet;if(chat.getChatComponent()!=null&&dev.vibe.pit.PitStatus.plain(chat.getChatComponent().getUnformattedText()).startsWith("▬▬▬▬▬▬▬▬▬▬▬")){cage=false;blinking=false;release();}}
        }
    }
    public void receive(Packet<?> packet){if(isEnabled()&&mode.is("Watchdog")&&(packet instanceof S45PacketTitle||packet instanceof S02PacketChat))signals.add(packet);}
    public void preUpdate(Object entity){if(entity==mc.thePlayer&&isEnabled()&&playing()&&mode.is("Watchdog"))blinking=cage&&mc.thePlayer.ticksExisted>30;}
    public void correction(){if(isEnabled()&&mode.is("NCP"))shouldSpeed=true;}
    public void beforeWalking(Object entity){
        if(!isEnabled()||entity!=mc.thePlayer||!playing()||mc.thePlayer.isSpectator()||!mode.is("NCP"))return;
        if(owner!=mc.thePlayer||world!=mc.theWorld||!mode.getValue().equals(activeMode))initialize();
        boolean in=inside(owner.getEntityBoundingBox());
        try{
            if(shouldSpeed||in){if(!owner.isSneaking())REPORT_Y.setDouble(owner,0);REPORT_PITCH.setFloat(owner,999);claim();owner.onGround=false;owner.motionX=owner.motionZ=0;
                if(mc.gameSettings.keyBindJump.isKeyDown()&&owner.posY==(int)owner.posY)owner.jump();}
            ticks++;
            if(mc.gameSettings.keyBindSneak.isKeyDown()){
                REPORT_PITCH.setFloat(owner,999);
                if(owner.movementInput.moveForward==0&&owner.movementInput.moveStrafe==0){
                    if(!mc.theWorld.getCollidingBoundingBoxes(owner,owner.getEntityBoundingBox().offset(0,-.001,0)).isEmpty()&&!in){REPORT_Y.setDouble(owner,-99);owner.setPosition(owner.posX,owner.posY-1,owner.posZ);sinkAt=System.currentTimeMillis();owner.motionY=0;}
                    else if(System.currentTimeMillis()-sinkAt>=100&&owner.posY==(int)owner.posY)owner.setPosition(owner.posX,owner.posY-.3,owner.posZ);
                }
            }
            if(inside(owner.getEntityBoundingBox())&&ticks>=3)teleport(shouldSpeed?.617:.031);
            packetShift=!inside(owner.getEntityBoundingBox())&&owner.isCollidedHorizontally;
            double[] shift=step(.2,false);packetStepX=shift[0];packetStepZ=shift[1];
        }catch(IllegalAccessException e){throw new IllegalStateException("Phase reporting fields",e);}
    }
    private boolean inside(AxisAlignedBB box){
        if(mc.theWorld==null)return false;
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(box.minX,box.minY,box.minZ),new BlockPos(box.maxX,box.maxY,box.maxZ))){
            if(!mc.theWorld.isBlockLoaded(pos))continue;net.minecraft.block.state.IBlockState state=mc.theWorld.getBlockState(pos);Block block=state.getBlock();if(block instanceof BlockAir)continue;
            AxisAlignedBB bounds=block instanceof BlockHopper?new AxisAlignedBB(pos,pos.add(1,1,1)):block.getCollisionBoundingBox(mc.theWorld,pos,state);
            if(bounds!=null&&box.intersectsWith(bounds))return true;
        }return false;
    }
    private double[] step(double length,boolean normalize){double f=owner.movementInput.moveForward,s=owner.movementInput.moveStrafe;float yaw=owner.rotationYaw;
        if(normalize&&f!=0){if(s>0)yaw+=f>0?-45:45;else if(s<0)yaw+=f>0?45:-45;s=0;f=Math.signum(f);}
        double c=Math.cos(Math.toRadians(yaw+90)),n=Math.sin(Math.toRadians(yaw+90));return new double[]{f*length*c+s*length*n,f*length*n-s*length*c};}
    private void teleport(double length){double[] d=step(length,true);owner.setPosition(owner.posX+d[0],owner.posY,owner.posZ+d[1]);}
    public void mutate(Packet<?> packet){
        if(!isEnabled()||!mode.is("NCP")||owner==null||owner!=mc.thePlayer||world!=mc.theWorld||!(packet instanceof C03PacketPlayer)||!packetShift)return;
        if(++delay<5)return;delay=0;C03PacketPlayer p=(C03PacketPlayer)packet;
        try{PX.setDouble(p,p.getPositionX()+packetStepX);PY.setDouble(p,p.getPositionY()-1);PZ.setDouble(p,p.getPositionZ()+packetStepZ);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    public boolean outbound(Packet<?> packet,ChannelHandlerContext ctx,ChannelPromise promise){
        if(!isEnabled()||!mode.is("Watchdog")||!blinking||owner!=mc.thePlayer||world!=mc.theWorld||packet instanceof C01PacketChatMessage||packet instanceof C00PacketKeepAlive||packet instanceof C0FPacketConfirmTransaction)return false;
        packets.add(new Pending(packet,ctx));promise.trySuccess();if(!isEnabled()||!blinking)release();return true;
    }
    private void release(){for(Pending item;(item=packets.poll())!=null;){Pending p=item;Runnable write=()->{if(p.ctx.channel().isActive())p.ctx.writeAndFlush(p.packet);};if(p.ctx.executor().inEventLoop())write.run();else p.ctx.executor().execute(write);}}
    public int queuedPackets(){return packets.size();}
    public static boolean cancelPush(Object player){PhaseModule phase=module(PhaseModule.class);return phase!=null&&phase.isEnabled()&&phase.mode.is("NCP")&&player==phase.mc.thePlayer;}
    public static List<AxisAlignedBB> collisions(List<AxisAlignedBB> boxes,Object entity){
        PhaseModule phase=module(PhaseModule.class);if(phase==null||!phase.isEnabled()||phase.mc.thePlayer==null||entity!=phase.mc.thePlayer||phase.mc.theWorld==null)return boxes;
        List<AxisAlignedBB> result=new ArrayList<>(boxes);
        if(phase.mode.is("NCP"))result.removeIf(box->box.maxY>phase.mc.thePlayer.getEntityBoundingBox().minY);
        else if(phase.cage&&phase.mc.thePlayer.ticksExisted>30)result.removeIf(box->{BlockPos p=new BlockPos(box.minX+.001,box.minY+.001,box.minZ+.001);return phase.mc.theWorld.isBlockLoaded(p)&&phase.mc.theWorld.getBlockState(p).getBlock()==Blocks.glass;});
        return result;
    }
    private static final class Pending{final Packet<?> packet;final ChannelHandlerContext ctx;Pending(Packet<?> packet,ChannelHandlerContext ctx){this.packet=packet;this.ctx=ctx;}}
}
