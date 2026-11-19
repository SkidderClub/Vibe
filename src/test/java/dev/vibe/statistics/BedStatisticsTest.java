package dev.vibe.statistics;

import dev.vibe.Vibe;
import dev.vibe.inventory.AutomationFixture;
import dev.vibe.path.PathTestFixture;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class BedStatisticsTest {
    @Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();
    private final BlockPos foot=new BlockPos(2,64,2),head=foot.north();
    private static IBlockState bed(BlockBed.EnumPartType part){return Blocks.bed.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.NORTH).withProperty(BlockBed.PART,part);}
    private StatisticsService statistics(AutomationFixture f)throws Exception{StatisticsService stats=new StatisticsService(f.mc,temporary.newFolder().toPath());PathTestFixture.set(Vibe.class,f.base.vibe,"statistics",stats);stats.tick();return stats;}
    private void place(AutomationFixture f){f.base.block(2,64,2,bed(BlockBed.EnumPartType.FOOT));f.base.block(2,64,1,bed(BlockBed.EnumPartType.HEAD));}
    private static void remove(AutomationFixture f,BlockPos pos){f.base.world.blocks.remove(pos);}
    private static void packet(StatisticsService stats,BlockPos pos,C07PacketPlayerDigging.Action action){stats.recordDigging(new C07PacketPlayerDigging(action,pos,EnumFacing.UP));}

    @Test public void bothHalvesAndLatePacketsProduceOneBedAndFreshBedsAtTheSameLocationStillCount()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            StatisticsService stats=statistics(f);place(f);stats.watchBlockBreak(foot);stats.watchBlockBreak(head);
            Object first=BlockBreakHooks.begin(foot);remove(f,foot);assertTrue(BlockBreakHooks.finish(true,first));
            Thread.sleep(1100);stats.tick(); // A lagging orphan half is still the same bed.
            Object second=BlockBreakHooks.begin(head);remove(f,head);BlockBreakHooks.finish(true,second);
            packet(stats,foot,C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK);packet(stats,head,C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK);stats.tick();
            assertEquals(1,stats.snapshot(StatisticsService.Scope.GLOBAL).bedsBroken);assertEquals(1,stats.snapshot(StatisticsService.Scope.GLOBAL).blocksBroken);
            place(f);Object next=BlockBreakHooks.begin(head);remove(f,head);remove(f,foot);BlockBreakHooks.finish(true,next);stats.tick();
            assertEquals(2,stats.snapshot(StatisticsService.Scope.ACCOUNT).bedsBroken);
        }
    }
    @Test public void worldSamplerAlsoDeduplicatesBothHalvesAndAnAirClickCannotEraseTheOriginalBed()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            StatisticsService stats=statistics(f);place(f);stats.watchBlockBreak(foot);stats.watchBlockBreak(head);remove(f,foot);remove(f,head);
            stats.watchBlockBreak(foot);stats.tick();assertEquals(1,stats.snapshot(StatisticsService.Scope.GLOBAL).bedsBroken);
        }
    }
    @Test public void failedBreakAbortAndBareStopRequestsDoNotCountBedsOrBlocks()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            StatisticsService stats=statistics(f);place(f);Object action=BlockBreakHooks.begin(foot);assertFalse(BlockBreakHooks.finish(false,action));
            packet(stats,foot,C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK);stats.tick();assertEquals(0,stats.snapshot(StatisticsService.Scope.GLOBAL).blocksBroken);
            stats.watchBlockBreak(foot);packet(stats,foot,C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK);stats.tick();remove(f,foot);stats.tick();
            assertEquals(0,stats.snapshot(StatisticsService.Scope.GLOBAL).bedsBroken);
        }
    }
    @Test public void ordinaryBlocksAndInstantBedBreaksCountWithoutAnyInitialInteractEvent()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            StatisticsService stats=statistics(f);place(f);Object action=BlockBreakHooks.begin(head);remove(f,foot);remove(f,head);BlockBreakHooks.finish(true,action);
            f.base.block(2,64,2,Blocks.stone.getDefaultState());Object block=BlockBreakHooks.begin(foot);remove(f,foot);BlockBreakHooks.finish(true,block);
            assertEquals(1,stats.snapshot(StatisticsService.Scope.GLOBAL).bedsBroken);assertEquals(2,stats.snapshot(StatisticsService.Scope.GLOBAL).blocksBroken);
        }
    }
}
