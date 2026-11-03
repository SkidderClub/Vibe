package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.combat.*;
import dev.vibe.module.Category;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;

/** Aims while the user draws a bow. Vanilla still owns bow use, release and arrow spread. */
public final class BowAimbotModule extends dev.vibe.module.Module {
    public final NumberSetting range=addSetting(new NumberSetting("Range",64,8,96,2));
    public final NumberSetting fov=addSetting(new NumberSetting("FOV",100,10,360,5));
    public final NumberSetting yawSpeed=addSetting(new NumberSetting("Horizontal Rotation Speed",35,1,180,1));
    public final NumberSetting pitchSpeed=addSetting(new NumberSetting("Vertical Rotation Speed",25,1,180,1));
    public final BooleanSetting prediction=addSetting(new BooleanSetting("Movement Prediction",true));
    private EntityLivingBase target;private Vec3 relative;private ArrowAimSolver.Shot solution;
    public BowAimbotModule(){super("Bow Aimbot","Predict arrow drop and target movement; find an unobstructed body aimpoint",Category.COMBAT,Keyboard.KEY_NONE);}
    public EntityLivingBase getTarget(){return target;}public ArrowAimSolver.Shot getSolution(){return solution;}
    public static boolean drawing(){
        Vibe v=Vibe.getInstance();Minecraft mc=Minecraft.getMinecraft();if(v==null||v.getModuleManager()==null||mc.thePlayer==null||mc.theWorld==null||mc.thePlayer.inventory==null||mc.currentScreen!=null||!mc.inGameHasFocus)return false;
        BowAimbotModule m=v.getModuleManager().getModule(BowAimbotModule.class);ItemStack stack=mc.thePlayer.getHeldItem();
        return m!=null&&m.isEnabled()&&stack!=null&&stack.getItem() instanceof ItemBow&&mc.thePlayer.isUsingItem()
                &&!dev.vibe.module.impl.world.ScaffoldModule.isActive()&&!dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook();
    }
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();MoveFixModule fix=Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if(!drawing()||fix==null){target=null;relative=null;solution=null;if(fix!=null)fix.clearFakeRotation(getId());return;}
        double draw=mc.thePlayer.getItemInUseDuration()/20D,pull=Math.min(1,(draw*draw+2*draw)/3),speed=pull*3;
        if(speed<.3){fix.setFakeRotation(getId(),mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);return;}
        TargetsModule filters=Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);List<EntityLivingBase> candidates=new ArrayList<>();
        for(Entity e:mc.theWorld.loadedEntityList)if(e instanceof EntityLivingBase&&e!=mc.thePlayer&&e.isEntityAlive()&&mc.thePlayer.getDistanceSqToEntity(e)<=range.getDouble()*range.getDouble()&&(filters==null||filters.canTarget((EntityLivingBase)e))){
            float yaw=(float)Math.toDegrees(Math.atan2(e.posZ-mc.thePlayer.posZ,e.posX-mc.thePlayer.posX))-90;if(Math.abs(RotationMath.difference(yaw,mc.thePlayer.rotationYaw))<=fov.getFloat()/2)candidates.add((EntityLivingBase)e);
        }
        candidates.sort(Comparator.comparingDouble(e->e==target?-1:mc.thePlayer.getDistanceSqToEntity(e)));ArrowAimSolver.Shot found=null;EntityLivingBase chosen=null;Vec3 chosenRelative=null;
        for(int i=0;i<Math.min(3,candidates.size())&&found==null;i++){
            EntityLivingBase e=candidates.get(i);AxisAlignedBB box=e.getEntityBoundingBox();Vec3[] movement=ArrowAimSolver.forecast(mc.theWorld,e,prediction.isEnabled());List<Vec3> points=new ArrayList<>();
            if(e==target&&relative!=null)points.add(relative);
            for(double y:new double[]{.5,.85,.97,.65,.35,.1,.03})for(double x:new double[]{.5,.06,.94})for(double z:new double[]{.5,.06,.94})points.add(new Vec3(x,y,z));
            for(Vec3 point:points){Vec3 aim=new Vec3(box.minX+(box.maxX-box.minX)*point.xCoord,box.minY+(box.maxY-box.minY)*point.yCoord,box.minZ+(box.maxZ-box.minZ)*point.zCoord);
                Vec3 origin=origin(mc.thePlayer.rotationYaw);ArrowAimSolver.Shot shot=ArrowAimSolver.solve(origin,aim,movement,speed);
                if(shot==null)continue;shot=ArrowAimSolver.solve(origin(shot.yaw),aim,movement,speed);
                if(shot!=null&&ArrowAimSolver.hits(mc.theWorld,box,movement,shot)){found=shot;chosen=e;chosenRelative=point;break;}
            }
        }
        target=chosen;relative=chosenRelative;solution=found;
        if(found!=null){mc.thePlayer.rotationYaw+=RotationMath.clamp(RotationMath.difference(found.yaw,mc.thePlayer.rotationYaw),-yawSpeed.getFloat(),yawSpeed.getFloat());mc.thePlayer.rotationPitch+=RotationMath.clamp(found.pitch-mc.thePlayer.rotationPitch,-pitchSpeed.getFloat(),pitchSpeed.getFloat());}
        fix.setFakeRotation(getId(),mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);
    }
    private Vec3 origin(float yaw){Minecraft mc=Minecraft.getMinecraft();double angle=Math.toRadians(yaw);return mc.thePlayer.getPositionEyes(1).addVector(-Math.cos(angle)*.16,-.10000000149,-Math.sin(angle)*.16);}
    @Override protected void onDisable(){target=null;relative=null;solution=null;}
}
