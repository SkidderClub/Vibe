package dev.vibe.module.impl.visual;
import dev.vibe.features.*;
import dev.vibe.hitmarker.*;
import dev.vibe.module.Category;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
public final class BlockDebrisModule extends FeatureModule {
    public final HitParticleSettings particles=new HitParticleSettings(this::addSetting,()->true);private final MaterialDebris debris=new MaterialDebris();
    public BlockDebrisModule(){super("Block Debris","Bouncy material-textured fragments from actual block destruction",Category.VISUAL);particles.bounce.setValue(.7);particles.lift.setValue(2.0);particles.count.setValue(16.0);}
    @Override public void tick(){if(playing())debris.tick(particles);else debris.clear();}
    @Override public void render(float partial){debris.render(particles,partial);}
    @Override protected void onDisable(){debris.clear();}
    public static boolean destroyHook(Object position,Object blockState){BlockDebrisModule m=module(BlockDebrisModule.class);if(m==null||!m.isEnabled()||!m.playing()||!(position instanceof BlockPos)||!(blockState instanceof IBlockState))return false;BlockPos p=(BlockPos)position;if(m.mc.thePlayer.getDistanceSqToCenter(p)>1024)return false;
        m.debris.emit(m.particles,p,(IBlockState)blockState,new Vec3(p.getX()+.5,p.getY()+.7,p.getZ()+.5),1.5);return true;
    }
}
