package dev.vibe.combat;

import dev.vibe.module.impl.combat.*;
import dev.vibe.inventory.AutomationFixture;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.target.TargetManager;
import java.nio.file.Files;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import static org.junit.Assert.*;

public class PriorityTargetTest {
    @Test public void markedPlayerBeatsLowerFovAndBowsExistingTargetButStillNeedsToBeEligible()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            TargetManager targets=new TargetManager(Files.createTempDirectory("priority-roster-").toFile());
            PathTestFixture.set(dev.vibe.Vibe.class,f.base.vibe,"targetManager",targets);
            f.base.world.loadedEntityList.clear();
            PathTestFixture.set(net.minecraft.client.network.NetHandlerPlayClient.class,f.queue,"playerInfoMap",new java.util.HashMap<>());
            PathTestFixture.Player centered=f.base.addPlayer(101,"Centered",.5,64,2),marked=f.base.addPlayer(102,"Marked",1.9,64,2.7);
            for(PathTestFixture.Player player:new PathTestFixture.Player[]{centered,marked}){
                PathTestFixture.set(EntityPlayerSP.class,player,"mc",f.mc);
                PathTestFixture.set(net.minecraft.entity.player.EntityPlayer.class,player,"gameProfile",new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),player.getName()));
                player.setEntityBoundingBox(new AxisAlignedBB(player.posX-.3,64,player.posZ-.3,player.posX+.3,65.8,player.posZ+.3));
            }
            f.base.filters.getIgnoreTeammates().setEnabled(false);f.player.rotationYaw=0;
            AimAssistModule aim=new AimAssistModule();KillAuraModule aura=new KillAuraModule();BowAimbotModule bow=new BowAimbotModule();
            for(Setting<?> setting:aura.getSettings())if(setting.getRawName().equals("Target Mode"))((ModeSetting)setting).setValue("Yaw");
            assertSame(centered,select(aim,"findBestTarget"));assertSame(centered,select(aura,"chooseTarget"));
            assertTrue(targets.add("Marked"));
            assertSame(marked,select(aim,"findBestTarget"));assertSame(marked,select(aura,"chooseTarget"));
            PathTestFixture.set(BowAimbotModule.class,bow,"target",centered);assertTrue(bow.compareTargets(marked,centered)<0);
            marked.isDead=true;assertSame(centered,select(aim,"findBestTarget"));assertSame(centered,select(aura,"chooseTarget"));
            marked.isDead=false;targets.remove("Marked");assertSame(centered,select(aim,"findBestTarget"));assertSame(centered,select(aura,"chooseTarget"));
        }
    }
    private static EntityLivingBase select(Object module,String method)throws Exception{java.lang.reflect.Method m=module.getClass().getDeclaredMethod(method);m.setAccessible(true);return (EntityLivingBase)m.invoke(module);}
}
