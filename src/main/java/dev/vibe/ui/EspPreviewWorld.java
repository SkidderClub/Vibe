package dev.vibe.ui;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;

/** Isolated, unsaved world for real player previews even from the main menu. */
final class EspPreviewWorld extends World {
    EspPreviewWorld() {
        super(new SaveHandlerMP(),new WorldInfo(new WorldSettings(0,WorldSettings.GameType.CREATIVE,false,false,WorldType.DEFAULT),"ESP preview"),new WorldProviderSurface(),new Profiler(),true);
        provider.registerWorld(this);chunkProvider=createChunkProvider();
    }
    @Override protected IChunkProvider createChunkProvider(){return new ChunkProviderClient(this);}
    @Override protected int getRenderDistanceChunks(){return 0;}
}
