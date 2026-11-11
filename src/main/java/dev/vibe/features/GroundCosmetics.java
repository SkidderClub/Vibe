package dev.vibe.features;
import dev.vibe.hitmarker.*;
import dev.vibe.setting.*;
import dev.vibe.ui.effect.EffectState;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
public final class GroundCosmetics {
    public final NumberSetting lifetime,limit;public final ColorSetting footprintColor;public final HitParticleSettings dust;
    private final Deque<Print> prints=new ArrayDeque<>();private final MaterialDebris debris=new MaterialDebris();private Object world;private Vec3 last;private boolean ground,left;private double fallSpeed;
    public GroundCosmetics(Function<Setting<?>,Setting<?>> add,java.util.function.BooleanSupplier footprints,java.util.function.BooleanSupplier landing){
        lifetime=new NumberSetting("Footprint Lifetime (ms)",3500,500,10000,100,footprints);limit=new NumberSetting("Footprint Maximum",96,16,192,8,footprints);footprintColor=new ColorSetting("Footprint Color",0xAA94DFFF,footprints);add.apply(lifetime);add.apply(limit);add.apply(footprintColor);dust=new HitParticleSettings(add,landing);dust.bounce.setValue(.7);dust.lift.setValue(1.4);dust.speed.setValue(1.6);
    }
    public void tick(boolean enabled,boolean footprints,boolean landing){Minecraft mc=Minecraft.getMinecraft();if(!enabled||mc.thePlayer==null||mc.theWorld==null){clear();return;}if(world!=mc.theWorld){clear();world=mc.theWorld;ground=mc.thePlayer.onGround;}long now=System.nanoTime()/1000000;Vec3 at=mc.thePlayer.getPositionVector();
        if(footprints&&mc.thePlayer.onGround&&(last==null||last.squareDistanceTo(at)>.16)){
            double yaw=Math.toRadians(mc.thePlayer.rotationYaw),side=(left?1:-1)*.13,x=at.xCoord+Math.cos(yaw)*side,z=at.zCoord+Math.sin(yaw)*side;left=!left;
            AxisAlignedBB feet=new AxisAlignedBB(x-.08,at.yCoord-.5,z-.08,x+.08,at.yCoord+.06,z+.08);double top=-Double.MAX_VALUE;
            for(AxisAlignedBB b:mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,feet))if(b.maxY<=at.yCoord+.06)top=Math.max(top,b.maxY);
            if(top!=-Double.MAX_VALUE)prints.addLast(new Print(x,top+.006,z,mc.thePlayer.rotationYaw,now));last=at;
        }if(!footprints)prints.clear();if(!mc.thePlayer.onGround)fallSpeed=Math.max(fallSpeed,-mc.thePlayer.motionY);
        if(landing&&!ground&&mc.thePlayer.onGround&&fallSpeed>.18){BlockPos pos=new BlockPos(at.xCoord,at.yCoord-.02,at.zCoord);debris.emit(dust,pos,mc.theWorld.getBlockState(pos),at.addVector(0,.15,0),fallSpeed*2);fallSpeed=0;}if(mc.thePlayer.onGround)fallSpeed=0;ground=mc.thePlayer.onGround;
        while(!prints.isEmpty()&&(now-prints.peekFirst().born>lifetime.getInt()||prints.size()>limit.getInt()))prints.removeFirst();if(landing)debris.tick(dust);else debris.clear();
    }
    public void render(float partial){Minecraft mc=Minecraft.getMinecraft();if(world!=mc.theWorld||mc.theWorld==null)return;debris.render(dust,partial);if(prints.isEmpty())return;long now=System.nanoTime()/1000000;
        try(EffectState state=new EffectState()){GL11.glPushMatrix();try{GL11.glTranslated(-mc.getRenderManager().viewerPosX,-mc.getRenderManager().viewerPosY,-mc.getRenderManager().viewerPosZ);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);GL11.glBegin(GL11.GL_QUADS);
            for(Print p:prints){float alpha=Math.max(0,1-(now-p.born)/(float)lifetime.getInt());int c=footprintColor.getArgb();WorldDraw.color((c&0xFFFFFF)|((int)((c>>>24)*alpha*alpha)<<24));double yaw=Math.toRadians(p.yaw),co=Math.cos(yaw),si=Math.sin(yaw);
                for(double[] r:new double[][]{{-.055,.02,.055,.14},{-.04,-.11,.04,-.015}})for(double[] v:new double[][]{{r[0],r[1]},{r[2],r[1]},{r[2],r[3]},{r[0],r[3]}})GL11.glVertex3d(p.x+v[0]*co-v[1]*si,p.y,p.z+v[0]*si+v[1]*co);
            }GL11.glEnd();}finally{GL11.glPopMatrix();}}
    }
    public void clear(){prints.clear();debris.clear();world=null;last=null;ground=false;fallSpeed=0;}
    private static final class Print{final double x,y,z;final float yaw;final long born;Print(double x,double y,double z,float yaw,long born){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.born=born;}}
}
