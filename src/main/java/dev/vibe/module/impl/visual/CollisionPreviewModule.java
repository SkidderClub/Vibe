package dev.vibe.module.impl.visual;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import dev.vibe.ui.effect.EffectState;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
public final class CollisionPreviewModule extends FeatureModule {
    public final NumberSetting range=addSetting(new NumberSetting("Range",5,1,12,1));public final BooleanSetting minimal=addSetting(new BooleanSetting("Minimal",true));
    public final WorldStyle style=new WorldStyle(this::addSetting);
    private final List<AxisAlignedBB> boxes=new ArrayList<>();private List<CollisionMesh.Edge> mesh=Collections.emptyList();private Object world;private BlockPos origin;private int cursor,size,previousRange;private boolean previousMinimal;
    public CollisionPreviewModule(){super("Collision Preview","Show actual block collision shapes; Minimal joins coplanar boundaries",Category.VISUAL);}
    @Override public void tick(){if(!playing()){clear();return;}BlockPos center=new BlockPos(mc.thePlayer);int r=range.getInt();if(world!=mc.theWorld){clear();world=mc.theWorld;}
        // The visible mesh and the scan under construction are separate buffers. Finish
        // a bounded scan before following a moving player so rebuilding cannot starve.
        if(origin==null||cursor==0&&(origin.distanceSq(center)>1||r!=previousRange||minimal.isEnabled()!=previousMinimal)){origin=center;previousRange=r;previousMinimal=minimal.isEnabled();boxes.clear();}
        r=previousRange;size=2*r+1;int total=size*size*size;
        for(int work=0;work<512&&cursor<total;work++,cursor++){int index=cursor,x=index%size-r,y=index/size%size-r,z=index/(size*size)-r;if(x*x+y*y+z*z>(r+.8)*(r+.8))continue;BlockPos p=origin.add(x,y,z);if(!mc.theWorld.isBlockLoaded(p))continue;IBlockState state=mc.theWorld.getBlockState(p);state.getBlock().addCollisionBoxesToList(mc.theWorld,p,state,new AxisAlignedBB(p,p.add(1,2,1)),boxes,mc.thePlayer);}
        if(cursor>=total){mesh=CollisionMesh.build(boxes,previousMinimal);cursor=0;boxes.clear();}
    }
    @Override public void render(float partial){if(!playing()||world!=mc.theWorld||mesh.isEmpty())return;try(EffectState state=new EffectState()){GL11.glPushMatrix();try{GL11.glTranslated(-mc.getRenderManager().viewerPosX,-mc.getRenderManager().viewerPosY,-mc.getRenderManager().viewerPosZ);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glDepthMask(false);GL11.glEnable(GL11.GL_LINE_SMOOTH);
            for(int pass=style.glow.isEnabled()?0:1;pass<2;pass++){GL11.glBlendFunc(GL11.GL_SRC_ALPHA,pass==0?GL11.GL_ONE:GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glLineWidth(style.width.getFloat()+(pass==0?3:0));GL11.glBegin(GL11.GL_LINES);for(CollisionMesh.Edge edge:mesh){double d=mc.thePlayer.getPositionVector().distanceTo(edge.a);if(d>range.getDouble()+2)continue;int color=style.color(d,range.getDouble()+1,0);if(pass==0)color=(color&0xFFFFFF)|((color>>>24)/4<<24);WorldDraw.color(color);GL11.glVertex3d(edge.a.xCoord,edge.a.yCoord+.002,edge.a.zCoord);GL11.glVertex3d(edge.b.xCoord,edge.b.yCoord+.002,edge.b.zCoord);}GL11.glEnd();}
        }finally{GL11.glPopMatrix();}}}
    private void clear(){world=null;origin=null;boxes.clear();mesh=Collections.emptyList();cursor=0;}
    @Override protected void onDisable(){clear();}
}
