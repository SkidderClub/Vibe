package dev.vibe.ui.render;

import dev.vibe.module.impl.visual.ChunkAnimationModule;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import org.junit.*;
import static org.junit.Assert.*;

public class ChunkAnimationTest {
    private ChunkAnimationModule module;
    @Before public void setup() { module = new ChunkAnimationModule(); }
    @After public void cleanup() { ChunkAnimationHooks.bind(null); }
    @Test public void easingStartsAtFullOffsetAndSettlesWithoutOvershooting() {
        assertEquals(1,ChunkAnimationHooks.remaining(99,100,100),0);
        assertEquals(1,ChunkAnimationHooks.remaining(100,100,100),0);
        assertEquals(.125F,ChunkAnimationHooks.remaining(150,100,100),0);
        assertEquals(0,ChunkAnimationHooks.remaining(200,100,100),0);
        assertEquals(0,ChunkAnimationHooks.remaining(300,100,100),0);
        float previous=1;
        for(int i=0;i<=100;i++){float value=ChunkAnimationHooks.remaining(100+i,100,100);assertTrue(value<=previous);assertTrue(value>=0);previous=value;}
    }
    @Test public void onlyFirstReadyMeshStartsAnAnimation() {
        State state=new State();ChunkAnimationHooks.beginFrame(1,true,module);
        ChunkAnimationHooks.compiled(state,CompiledChunk.DUMMY);assertEquals(0,state.start);
        ChunkAnimationHooks.compiled(state,new Geometry());long born=state.start;assertTrue(born>0);
        ChunkAnimationHooks.compiled(state,new Geometry());assertEquals(born,state.start);
        state.start=-1;ChunkAnimationHooks.compiled(state,new Geometry());assertEquals(-1,state.start);
    }
    @Test public void emptyAndDisabledSectionsDoNotAnimateWhenBlocksLaterChange() {
        State empty=new State();ChunkAnimationHooks.beginFrame(1,true,module);
        ChunkAnimationHooks.compiled(empty,new CompiledChunk());assertEquals(-1,empty.start);
        ChunkAnimationHooks.compiled(empty,new Geometry());assertEquals(-1,empty.start);
        State disabled=new State();ChunkAnimationHooks.beginFrame(2,false,module);ChunkAnimationHooks.compiled(disabled,new Geometry());
        ChunkAnimationHooks.beginFrame(3,true,module);ChunkAnimationHooks.compiled(disabled,new Geometry());assertEquals(-1,disabled.start);
    }
    @Test public void recyclingResetsOnlyWhenTheChunkPositionChanges() {
        State state=new State();state.start=-1;
        ChunkAnimationHooks.resetPosition(state,new BlockPos(0,0,0),new BlockPos(0,0,0));assertEquals(-1,state.start);
        ChunkAnimationHooks.resetPosition(state,new BlockPos(0,0,0),new BlockPos(16,0,0));assertEquals(0,state.start);
        state.start=100;ChunkAnimationHooks.discarded(state);assertEquals(-1,state.start);
    }
    @Test public void layersShareOneFrameTimeAndCompletedChunksCannotRestart() {
        module.duration.setValue(600D);ChunkAnimationHooks.beginFrame(1000000000L,true,module);
        State state=new State();state.start=1000000000L;
        ChunkAnimationHooks.beginFrame(1300000000L,true,module);
        assertEquals(.125F,ChunkAnimationHooks.remaining(state),0);assertEquals(.125F,ChunkAnimationHooks.remaining(state),0);
        ChunkAnimationHooks.beginFrame(1600000000L,true,module);assertEquals(0,ChunkAnimationHooks.remaining(state),0);assertEquals(-1,state.start);
        // Completed fast path has no GL calls and works without a graphics context.
        for(int i=0;i<10000;i++)ChunkAnimationHooks.apply(state);
        ChunkAnimationHooks.compiled(state,new Geometry());assertEquals(-1,state.start);
    }
    @Test public void reenablingCannotResumeOldAnimations() {
        ChunkAnimationHooks.beginFrame(100,true,module);State state=new State();state.start=100;
        ChunkAnimationHooks.beginFrame(200,false,module);ChunkAnimationHooks.beginFrame(300,true,module);
        assertEquals(0,ChunkAnimationHooks.remaining(state),0);assertEquals(-1,state.start);
    }
    static final class State implements ChunkAnimationState {
        long start;
        public long vibe$getAnimationStart(){return start;}
        public void vibe$setAnimationStart(long value){start=value;}
    }
    static final class Geometry extends CompiledChunk { Geometry(){setLayerUsed(EnumWorldBlockLayer.SOLID);} }
}
