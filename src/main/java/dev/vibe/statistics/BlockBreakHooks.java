package dev.vibe.statistics;

import dev.vibe.Vibe;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;

/** Capture the original block before vanilla removes it, for manual, automated and instant breaks. */
public final class BlockBreakHooks {
    private BlockBreakHooks() { }
    public static Object begin(Object position){
        Vibe vibe=Vibe.getInstance();Minecraft mc=Minecraft.getMinecraft();
        if(vibe==null||vibe.getStatistics()==null||mc.theWorld==null||mc.thePlayer==null||!(position instanceof BlockPos))return null;
        return new Break(vibe.getStatistics(),mc.theWorld,(BlockPos)position);
    }
    public static boolean finish(boolean destroyed,Object token){
        if(destroyed&&token instanceof Break){Break action=(Break)token;
            if(Minecraft.getMinecraft().theWorld==action.world)action.statistics.recordBlockDestroyed(action.position,action.original,action.paired);}
        return destroyed;
    }
    private static final class Break {
        final StatisticsService statistics;final World world;final BlockPos position;final IBlockState original;final boolean paired;
        Break(StatisticsService statistics,World world,BlockPos position){this.statistics=statistics;this.world=world;this.position=position;original=world.getBlockState(position);
            BlockPos other=StatisticsService.otherBedHalf(position,original);paired=other!=null&&world.getBlockState(other).getBlock() instanceof net.minecraft.block.BlockBed;}
    }
}
