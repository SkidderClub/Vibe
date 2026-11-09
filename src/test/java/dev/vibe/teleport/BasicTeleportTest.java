package dev.vibe.teleport;

import dev.vibe.inventory.AutomationFixture;
import dev.vibe.module.impl.movement.TeleportModule;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BasicTeleportTest {
    @Test public void destinationUsesRealSupportHeightAndRequiresHeadroom()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.player.width=.6F;f.player.height=1.8F;BlockPos target=new BlockPos(3,64,3);
            f.base.block(3,64,3,Blocks.stone.getDefaultState());assertEquals(65,TeleportDestination.above(f.base.world,f.player,target).yCoord,0);
            f.base.block(3,64,3,Blocks.stone_slab.getDefaultState());assertEquals(64.5,TeleportDestination.above(f.base.world,f.player,target).yCoord,0);
            f.base.block(3,66,3,Blocks.stone.getDefaultState());assertNull(TeleportDestination.above(f.base.world,f.player,target));
            f.base.block(3,66,3,Blocks.air.getDefaultState());f.base.block(3,64,3,Blocks.cactus.getDefaultState());assertNull(TeleportDestination.above(f.base.world,f.player,target));
            f.base.world.loaded=false;assertNull(TeleportDestination.above(f.base.world,f.player,target));
        }
    }
    @Test public void boundActionCanBeUsedRepeatedlyWithoutStayingToggledOn()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.player.width=.6F;f.player.height=1.8F;f.player.rotationYaw=f.player.rotationPitch=0;
            dev.vibe.path.PathTestFixture.set(net.minecraft.entity.player.EntityPlayer.class,f.player,"eyeHeight",1.62F);
            f.base.block(0,65,5,Blocks.stone.getDefaultState());TeleportModule module=new TeleportModule();f.base.modules.registerDynamic(module);
            assertNotNull(TeleportDestination.look(f.base.world,f.player.getPositionEyes(1),new Vec3(0,0,1),64));
            module.toggle();assertFalse(module.isEnabled());assertEquals(.5,f.player.posX,0);assertEquals(f.player.messages.toString(),66,f.player.posY,0);assertEquals(5.5,f.player.posZ,0);assertEquals(2,module.portals().size());assertEquals(0,f.player.fallDistance,0);
            assertTrue(f.player.messages.get(0).getFormattedText().contains(dev.vibe.chat.ClientChat.PREFIX));
            assertFalse(module.activate());assertEquals(2,module.portals().size());
            module.clear();f.base.block(0,67,10,Blocks.stone.getDefaultState());module.toggle();assertFalse(module.isEnabled());assertEquals(10.5,f.player.posZ,0);assertEquals(68,f.player.posY,0);
        }
    }
    @Test public void lookDoesNotPassThroughTheFirstSolidBlock()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.base.block(0,65,3,Blocks.stone.getDefaultState());f.base.block(0,65,6,Blocks.stone.getDefaultState());
            MovingObjectPosition hit=TeleportDestination.look(f.base.world,new Vec3(.5,65.6,.5),new Vec3(0,0,1),20);assertNotNull(hit);assertEquals(new BlockPos(0,65,3),hit.getBlockPos());
            f.base.world.loaded=false;assertNull(TeleportDestination.look(f.base.world,new Vec3(.5,65.6,.5),new Vec3(0,0,1),20));
        }
    }
    @Test public void distantTeleportHasNoInteractionReachCapAndReportsBoundedMoves()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.player.width=.6F;f.player.height=1.8F;f.player.rotationYaw=f.player.rotationPitch=0;
            dev.vibe.path.PathTestFixture.set(net.minecraft.entity.player.EntityPlayer.class,f.player,"eyeHeight",1.62F);
            f.base.block(0,65,25,Blocks.stone.getDefaultState());TeleportModule module=new TeleportModule();f.base.modules.registerDynamic(module);
            assertEquals("Vanilla",module.mode.getValue());assertTrue(module.activate());assertEquals(25.5,f.player.posZ,0);
            double x=.5,y=64,z=.5;int count=0;
            for(net.minecraft.network.Packet<?> packet:f.queue.packets)if(packet instanceof net.minecraft.network.play.client.C03PacketPlayer){
                net.minecraft.network.play.client.C03PacketPlayer move=(net.minecraft.network.play.client.C03PacketPlayer)packet;
                double dx=move.getPositionX()-x,dy=move.getPositionY()-y,dz=move.getPositionZ()-z;assertTrue(dx*dx+dy*dy+dz*dz<=16.0001);
                x=move.getPositionX();y=move.getPositionY();z=move.getPositionZ();count++;
            }
            assertTrue(count>1);assertEquals(f.player.posZ,z,0);assertEquals(f.player.posY,y,0);
        }
    }
    @Test public void renamedModuleMigratesOldProfilesWithoutTriggeringTeleport()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            TeleportModule module=new TeleportModule();f.base.modules.registerDynamic(module);
            java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("vibe-teleport-profile-");dev.vibe.config.VibeConfig config=new dev.vibe.config.VibeConfig(dir.toFile());dev.vibe.path.PathTestFixture.set(dev.vibe.Vibe.class,f.base.vibe,"config",config);
            com.google.gson.JsonObject root=new com.google.gson.JsonParser().parse("{\"modules\":[{\"id\":\"basicteleport\",\"key\":37,\"enabled\":true,\"settings\":{\"Range\":28}}]}").getAsJsonObject();
            assertTrue(config.loadOnline("legacy-teleport",root,f.base.modules,true,true));assertEquals(37,module.getKey());assertEquals(28,module.range.getInt());assertFalse(module.isEnabled());assertEquals(.5,f.player.posZ,0);
        }
    }
    @Test public void bundledAtlasDecodesWithTransparencyAndEveryRegionFits()throws Exception{
        java.awt.image.BufferedImage image=PortalSprite.read();assertEquals(989,image.getWidth());assertEquals(1024,image.getHeight());assertTrue(image.getColorModel().hasAlpha());
        for(int[] region:PortalSprite.REGIONS){assertTrue(region[0]>=0&&region[1]>=0&&region[0]+region[2]<=image.getWidth()&&region[1]+region[3]<=image.getHeight());int visible=0;for(int x=region[0];x<region[0]+region[2];x++)for(int y=region[1];y<region[1]+region[3];y++)if(image.getRGB(x,y)>>>24>0)visible++;assertTrue(visible>100);}
    }
}
