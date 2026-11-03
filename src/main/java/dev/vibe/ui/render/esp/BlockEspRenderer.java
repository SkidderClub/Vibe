package dev.vibe.ui.render.esp;
import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.BlockESPModule;
import dev.vibe.ui.effect.EffectState;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.block.Block;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.*;
public final class BlockEspRenderer {
    private static final int[] FACES={0,0,0,1,0,0,1,0,1,0,0,1, 0,1,0,0,1,1,1,1,1,1,1,0, 0,0,0,0,1,0,1,1,0,1,0,0, 0,0,1,1,0,1,1,1,1,0,1,1, 0,0,0,0,0,1,0,1,1,0,1,0, 1,0,0,1,1,0,1,1,1,1,0,1};
    public void render(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();BlockESPModule m=Vibe.getInstance().getModuleManager().getModule(BlockESPModule.class);
        if(m==null||!m.isEnabled()||mc.theWorld==null||mc.thePlayer==null||m.matches().isEmpty())return;
        try(EffectState state=new EffectState()){
            GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            if(m.throughWalls.isEnabled())GL11.glDisable(GL11.GL_DEPTH_TEST);else GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);int count=0;
            Map<Block,BlockESPModule.Style> styles=m.styles();for(Map.Entry<BlockPos,Block> match:m.matches().entrySet()){
                BlockPos p=match.getKey();if(p.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>m.range.getDouble()*m.range.getDouble()||!mc.theWorld.isBlockLoaded(p)||mc.theWorld.getBlockState(p).getBlock()!=match.getValue())continue;
                BlockESPModule.Style s=styles.get(match.getValue());if(s==null||!s.enabled)continue;if(count++>=m.renderLimit.getInt())break;
                double x=p.getX()-mc.getRenderManager().viewerPosX,y=p.getY()-mc.getRenderManager().viewerPosY,z=p.getZ()-mc.getRenderManager().viewerPosZ;
                GL11.glPushMatrix();GL11.glTranslated(x,y,z);
                if((s.fill>>>24)>0){color(s.fill);GL11.glBegin(GL11.GL_QUADS);
                    for(int i=0;i<FACES.length;i+=3)GL11.glVertex3i(FACES[i],FACES[i+1],FACES[i+2]);GL11.glEnd();}
                if((s.outline>>>24)>0){color(s.outline);GL11.glLineWidth(s.width);GL11.glBegin(GL11.GL_LINES);
                    for(int a=0;a<2;a++)for(int b=0;b<2;b++){GL11.glVertex3d(0,a,b);GL11.glVertex3d(1,a,b);GL11.glVertex3d(a,0,b);GL11.glVertex3d(a,1,b);GL11.glVertex3d(a,b,0);GL11.glVertex3d(a,b,1);}GL11.glEnd();}
                GL11.glPopMatrix();
            }
        }
    }
    private static void color(int c){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);}
}
