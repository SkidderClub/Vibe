package dev.vibe.features;
import dev.vibe.hitmarker.*;
import dev.vibe.ui.render.HitParticleRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
public final class MaterialDebris {
    public final HitParticles particles=new HitParticles();private final HitParticleRenderer renderer=new HitParticleRenderer();
    public void emit(HitParticleSettings settings,BlockPos pos,IBlockState state,Vec3 point,double strength){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||state==null||state.getBlock().getMaterial().isLiquid()||!state.getBlock().getMaterial().isSolid())return;
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(state);int tint=0xFF000000|state.getBlock().colorMultiplier(mc.theWorld,pos,0);
        particles.emit(mc.theWorld,settings,point,new Vec3(0,0,0),System.nanoTime()/1000000,sprite,tint,Math.max(.3,Math.min(3,strength)));
    }
    public void tick(HitParticleSettings s){particles.tick(Minecraft.getMinecraft().theWorld,s);}
    public void render(HitParticleSettings settings,float partial){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||!particles.isWorld(mc.theWorld)||mc.getRenderManager()==null)return;renderer.render(particles.particles(),settings,mc.getRenderManager().viewerPosX,mc.getRenderManager().viewerPosY,mc.getRenderManager().viewerPosZ,partial);}
    public void clear(){particles.clear();}
}
