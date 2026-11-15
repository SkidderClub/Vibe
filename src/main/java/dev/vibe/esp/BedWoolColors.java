package dev.vibe.esp;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/** Shared BedESP/BedAura nearest-wool ordering, including deterministic ties. */
public final class BedWoolColors {
    public static final int[] COLORS={0xFFF9F9F9,0xFFF9801D,0xFFC74EBD,0xFF3AB3DA,0xFFFED83D,0xFF80C71F,0xFFF38BAA,0xFF474F52,0xFF9D9D97,0xFF169C9C,0xFF8932B8,0xFF3C44AA,0xFF835432,0xFF5E7C16,0xFFB02E26,0xFF1D1D21};
    private static final List<int[]> OFFSETS=new ArrayList<>();
    static{for(int x=-6;x<=6;x++)for(int y=-3;y<=3;y++)for(int z=-6;z<=6;z++)OFFSETS.add(new int[]{x,y,z,x*x+y*y+z*z});OFFSETS.sort(Comparator.comparingInt(p->p[3]));}
    public static int nearest(World world,BlockPos origin){
        for(int[] d:OFFSETS){BlockPos p=origin.add(d[0],d[1],d[2]);if(!world.isBlockLoaded(p))continue;IBlockState s=world.getBlockState(p);if(s.getBlock()==Blocks.wool)return Blocks.wool.getMetaFromState(s)&15;}return -1;
    }
    public static int fromName(String formatted,String name){
        if(formatted==null)return -1;int end=formatted.lastIndexOf(name);if(end<0)end=formatted.length();int color=-1;
        for(int i=0;i+1<end;i++)if(formatted.charAt(i)=='§'){char c=Character.toLowerCase(formatted.charAt(++i));int value="0123456789abcdef".indexOf(c);if(value>=0)color=value;else if(c=='r')color=-1;}
        return color<0?-1:new int[]{15,11,13,9,14,10,1,8,7,11,5,3,14,6,4,0}[color];
    }
}
