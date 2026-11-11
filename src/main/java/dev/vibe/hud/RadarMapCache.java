package dev.vibe.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** A loaded-chunk-only height map. Work is spread over 32 ticks and old pixels remain visible while it rebuilds. */
final class RadarMapCache {
    static final int SIZE=128;private DynamicTexture texture;private World world;private int column=SIZE,buildX,buildZ,buildStride=1;int x,z,stride=1;private int[] building=new int[SIZE*SIZE];private long refresh;
    boolean ready;
    void tick(double diameter){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null)return;if(world!=mc.theWorld){world=mc.theWorld;column=SIZE;ready=false;refresh=0;}
        int requestedStride=Math.max(1,(int)Math.ceil(diameter/SIZE));
        int centerX=((int)Math.floor(mc.thePlayer.posX/4))*4,centerZ=((int)Math.floor(mc.thePlayer.posZ/4))*4;long now=System.nanoTime()/1000000;
        if(column>=SIZE){if(ready&&stride==requestedStride&&Math.abs(centerX-x)<4&&Math.abs(centerZ-z)<4&&now<refresh)return;buildX=centerX;buildZ=centerZ;buildStride=requestedStride;column=0;}
        for(int limit=Math.min(SIZE,column+4);column<limit;column++)for(int row=0;row<SIZE;row++){
            BlockPos p=new BlockPos(buildX+(column-SIZE/2)*buildStride,0,buildZ+(row-SIZE/2)*buildStride);int c=0xFF222831;
            if(world.isBlockLoaded(p)){int h=world.getHeight(p).getY()-1;p=new BlockPos(p.getX(),h,p.getZ());IBlockState s=world.getBlockState(p);c=s.getBlock().getMapColor(s).colorValue;int tint=s.getBlock().colorMultiplier(world,p,0);if(tint!=0xFFFFFF)c=tint;
                double light=.82+Math.sin(h*.38)*.08;c=0xFF000000|((int)((c>>16&255)*light)<<16)|((int)((c>>8&255)*light)<<8)|(int)((c&255)*light);
            }building[column+row*SIZE]=c;
        }
        if(column>=SIZE){if(texture==null){texture=new DynamicTexture(SIZE,SIZE);texture.setBlurMipmap(true,false);}System.arraycopy(building,0,texture.getTextureData(),0,building.length);texture.updateDynamicTexture();x=buildX;z=buildZ;stride=buildStride;ready=true;refresh=now+2000;}
    }
    int texture(){return texture==null?0:texture.getGlTextureId();}
    void suspend(){if(Minecraft.getMinecraft().theWorld==null){world=null;ready=false;column=SIZE;}}
}
