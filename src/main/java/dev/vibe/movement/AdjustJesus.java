package dev.vibe.movement;
import dev.vibe.features.FeatureModule;
import dev.vibe.module.impl.movement.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.block.BlockLiquid;
import net.minecraft.util.*;
import java.util.*;
/** Adjust's Rewound liquid collision surface and five-tick hover sequence. */
public final class AdjustJesus {
    private static Object world,owner;private static int ticks;private static double hover;
    private AdjustJesus(){}
    public static void reset(){world=owner=null;ticks=0;hover=0;}
    public static boolean active(){JesusModule j=FeatureModule.module(JesusModule.class);Minecraft mc=Minecraft.getMinecraft();return j!=null&&j.isEnabled()&&j.mode.is("RewoundAdjust")&&mc.thePlayer!=null&&mc.theWorld!=null&&!mc.thePlayer.isSpectator()&&!mc.thePlayer.isRiding();}
    public static boolean standing(){
        if(!active())return false;EntityPlayerSP p=Minecraft.getMinecraft().thePlayer;
        FlyModule flight=FeatureModule.module(FlyModule.class);
        return liquidBelow(p)&&!p.isInWater()&&!p.isInLava()&&(flight==null||!flight.isEnabled());
    }
    private static boolean liquidBelow(EntityPlayerSP p){AxisAlignedBB b=p.getEntityBoundingBox().contract(.305,0,.305).offset(0,-.1,0);return p.worldObj.getBlockState(new BlockPos(b.minX,b.minY,b.minZ)).getBlock() instanceof BlockLiquid;}
    public static void beforeWalking(){
        Minecraft mc=Minecraft.getMinecraft();if(owner!=mc.thePlayer||world!=mc.theWorld){owner=mc.thePlayer;world=mc.theWorld;ticks=0;}hover=0;
        if(!standing()){ticks=0;return;}
        int stage=ticks++%5;float base=stage==0||stage==2?.000014F:stage==1||stage==3?.000024F:.000017F;
        hover=base+(.00000014F+java.util.concurrent.ThreadLocalRandom.current().nextFloat()*(.00000043F-.00000014F));
    }
    public static double walkingY(double y){return active()?y+hover:y;}
    public static boolean cancelJump(Object entity){return entity==Minecraft.getMinecraft().thePlayer&&standing();}
    public static float stepHeight(float height){return standing()?Math.min(height,.6F):height;}
    public static List<AxisAlignedBB> collisions(List<AxisAlignedBB> original,Object entity,Object query){
        Minecraft mc=Minecraft.getMinecraft();if(entity!=mc.thePlayer||!active()||!(query instanceof AxisAlignedBB))return original;
        AxisAlignedBB b=(AxisAlignedBB)query;
        if((b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ)>4096)return original;
        List<AxisAlignedBB> result=original;
        for(int x=MathHelper.floor_double(b.minX);x<MathHelper.floor_double(b.maxX+1);x++)for(int y=MathHelper.floor_double(b.minY)-1;y<MathHelper.floor_double(b.maxY+1);y++)for(int z=MathHelper.floor_double(b.minZ);z<MathHelper.floor_double(b.maxZ+1);z++){
            BlockPos pos=new BlockPos(x,y,z);if(mc.thePlayer.posY<y+1||!mc.theWorld.isBlockLoaded(pos)||!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockLiquid))continue;
            AxisAlignedBB surface=new AxisAlignedBB(x,y,z,x+1,y+1,z+1);if(surface.intersectsWith(b)){if(result==original)result=new ArrayList<>(original);result.add(surface);}
        }
        return result;
    }
}
