package dev.vibe.companion;

import dev.vibe.inventory.AutomationFixture;
import dev.vibe.module.impl.meme.GirlfriendModule;
import dev.vibe.path.PathTestFixture;
import java.util.Random;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.init.Blocks;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionHitTest {
    @Test public void localClicksUseNativeKnockbackWithoutAnAttackPacket()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.player.width=.6F;f.player.height=1.8F;PathTestFixture.set(net.minecraft.entity.player.EntityPlayer.class,f.player,"eyeHeight",1.62F);
            GirlfriendModule module=new GirlfriendModule();f.base.modules.registerDynamic(module);module.setEnabled(true);
            CompanionFollower follower=module.follower();follower.tick(f.base.world,f.player,2,6,1,.95);follower.x=.5;follower.y=64;follower.z=2.5;
            EntityOtherPlayerMP npc=PathTestFixture.allocate(EntityOtherPlayerMP.class);npc.width=.56F;npc.height=1.71F;npc.worldObj=f.base.world;npc.setPosition(.5,64,2.5);
            PathTestFixture.set(Entity.class,npc,"rand",new Random(4));ServersideAttributeMap attributes=new ServersideAttributeMap();attributes.registerAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0);PathTestFixture.set(EntityLivingBase.class,npc,"attributeMap",attributes);
            PathTestFixture.set(GirlfriendModule.class,module,"companion",npc);PathTestFixture.set(GirlfriendModule.class,module,"companionWorld",f.base.world);
            assertTrue(GirlfriendModule.hitHook());assertEquals(.4,follower.motionZ(),.0001);assertEquals(.4,follower.motionY(),.0001);
            assertFalse(f.queue.packets.stream().anyMatch(p->p instanceof net.minecraft.network.play.client.C02PacketUseEntity||p instanceof net.minecraft.network.play.client.C07PacketPlayerDigging));
            f.base.block(0,65,1,Blocks.stone.getDefaultState());assertFalse("Hit went through a block",GirlfriendModule.hitHook());
        }
    }
}
