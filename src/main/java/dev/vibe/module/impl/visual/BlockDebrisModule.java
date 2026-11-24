package dev.vibe.module.impl.visual;
import dev.vibe.features.*;
import dev.vibe.hitmarker.*;
import dev.vibe.module.Category;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import net.minecraft.world.World;
public final class BlockDebrisModule extends FeatureModule {
    private static final int MAX_PENDING = 128;
    public final HitParticleSettings particles=new HitParticleSettings(this::addSetting,()->true);
    private final MaterialDebris debris=new MaterialDebris();
    private final Deque<BreakEffect> pending = new ArrayDeque<>();
    private World world;
    public BlockDebrisModule(){super("Block Debris","Bouncy material-textured fragments from actual block destruction",Category.VISUAL);particles.bounce.setValue(.7);particles.lift.setValue(2.0);particles.count.setValue(16.0);}
    @Override public void tick() {
        if (!playing() || world != mc.theWorld) {
            clear();
            return;
        }
        debris.tick(particles);
        for (int remaining = pending.size(); remaining > 0; remaining--) {
            BreakEffect effect = pending.removeFirst();
            if (!world.isBlockLoaded(effect.pos)) continue;
            // Destruction effects can arrive before the block update. The
            // collision check would reject particles inside the original block.
            if (world.getBlockState(effect.pos).getBlock() == effect.state.getBlock()) {
                if (--effect.retries > 0) pending.addLast(effect);
                continue;
            }
            BlockPos p = effect.pos;
            debris.emit(particles, p, effect.state, new Vec3(p.getX()+.5, p.getY()+.7, p.getZ()+.5), 1.5);
        }
    }

    @Override public void render(float partial){debris.render(particles,partial);}
    @Override protected void onDisable(){clear();}

    private void clear() {
        pending.clear();
        debris.clear();
        world = null;
    }

    public static boolean destroyHook(Object position, Object blockState) {
        BlockDebrisModule module = module(BlockDebrisModule.class);
        if (module == null || !module.isEnabled() || !module.playing()
                || !(position instanceof BlockPos) || !(blockState instanceof IBlockState)) return false;
        BlockPos pos = (BlockPos) position;
        IBlockState state = (IBlockState) blockState;
        Material material = state.getBlock().getMaterial();
        if (material == Material.air || material.isLiquid() || module.mc.thePlayer.getDistanceSqToCenter(pos) > 1024) return false;
        if (module.world != module.mc.theWorld) {
            module.clear();
            module.world = module.mc.theWorld;
        }
        if (module.pending.size() >= MAX_PENDING) return false;
        module.pending.addLast(new BreakEffect(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), state));
        return true;
    }

    private static final class BreakEffect {
        final BlockPos pos;
        final IBlockState state;
        int retries = 4;

        BreakEffect(BlockPos pos, IBlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }
}
