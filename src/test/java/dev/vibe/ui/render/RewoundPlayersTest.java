package dev.vibe.ui.render;

import com.mojang.authlib.GameProfile;
import dev.vibe.core.TabListTransformer;
import dev.vibe.inventory.AutomationFixture;
import dev.vibe.module.impl.client.QolModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.util.*;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.*;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class RewoundPlayersTest {
    @Test public void rawStarPrefixFiltersOnlyTheChosenViewsAndLeavesCombatIntact() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            QolModule qol=new QolModule();f.base.modules.registerDynamic(qol);
            Info real=new Info("Human","§c* §e[120] Human"),bot=new Info("Bot","§cR Bot");
            List<NetworkPlayerInfo> original=Arrays.asList(bot,real);
            assertSame(original,RewoundPlayers.tabEntries(original));
            qol.getFeatures().toggle(QolModule.REAL_REWOUND_PLAYERS);qol.setEnabled(true);
            PathTestFixture.set(net.minecraft.entity.player.EntityPlayer.class, f.player, "gameProfile", new GameProfile(UUID.randomUUID(), "Self"));
            Map<UUID,NetworkPlayerInfo> map=new HashMap<>();map.put(real.getGameProfile().getId(),real);map.put(bot.getGameProfile().getId(),bot);
            PathTestFixture.set(NetHandlerPlayClient.class,f.queue,"playerInfoMap",map);
            Scoreboard board = new Scoreboard();
            PathTestFixture.set(net.minecraft.world.World.class, f.base.world, "worldScoreboard", board);
            net.minecraft.scoreboard.ScoreObjective sidebar = board.addScoreObjective("game", net.minecraft.scoreboard.IScoreObjectiveCriteria.DUMMY);
            board.setObjectiveInDisplaySlot(1, sidebar);
            sidebar.setDisplayName("§eSKYWARS");
            assertSame(original, RewoundPlayers.tabEntries(original));
            sidebar.setDisplayName("§eTHE HYPIXEL PIT");
            f.player.ticksExisted++;
            assertTrue(qol.getRealPlayerViews().isVisible());assertEquals(Collections.singletonList(real),RewoundPlayers.tabEntries(original));assertEquals(2,original.size());
            board.setObjectiveInDisplaySlot(1, null);
            assertEquals(Collections.singletonList(real), RewoundPlayers.tabEntries(original));
            board.setObjectiveInDisplaySlot(1, sidebar);
            assertFalse(RewoundPlayers.starred("Bot *"));assertFalse(RewoundPlayers.starred(null));assertTrue(RewoundPlayers.starred(" §c* §fRankedName"));
            int existing=f.base.world.loadedEntityList.size();
            PathTestFixture.Player human=f.base.addPlayer(101,"Human",2,64,2),npc=f.base.addPlayer(102,"Bot",3,64,3);
            PathTestFixture.set(Entity.class,human,"entityUniqueID",real.getGameProfile().getId());PathTestFixture.set(Entity.class,npc,"entityUniqueID",bot.getGameProfile().getId());
            assertTrue(RewoundPlayers.showEsp(human));assertFalse(RewoundPlayers.showEsp(npc));
            f.base.filters.getIgnoreTeammates().setEnabled(false);assertTrue("Bot remains a combat candidate",f.base.filters.canTarget(npc));
            assertEquals(2,map.size());assertEquals(existing+2,f.base.world.loadedEntityList.size());
            qol.getRealPlayerViews().toggle("ESP");assertTrue(RewoundPlayers.showEsp(npc));assertEquals(1,RewoundPlayers.tabEntries(original).size());
            sidebar.setDisplayName("BED WARS");
            f.player.ticksExisted++;
            assertSame(original, RewoundPlayers.tabEntries(original));
            qol.getRealPlayerModes().toggle("Bedwars");
            assertEquals(Collections.singletonList(real), RewoundPlayers.tabEntries(original));
            qol.getRealPlayerViews().toggle("Tab");assertSame(original,RewoundPlayers.tabEntries(original));qol.setEnabled(false);
        }
    }
    @Test public void vanillaTabHookIsInstalledOnceBeforeTheEntryCap() throws Exception {
        byte[] original;try(InputStream in=GuiPlayerTabOverlay.class.getResourceAsStream("GuiPlayerTabOverlay.class");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);original=out.toByteArray();
        }
        TabListTransformer transformer=new TabListTransformer();String name=GuiPlayerTabOverlay.class.getName();
        byte[] transformed=transformer.transform(name,name,original);assertNotSame(original,transformed);
        assertArrayEquals(transformed,transformer.transform(name,name,transformed));
        ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);int hooks=0;
        for(Object value:node.methods){boolean hooked=false;for(AbstractInsnNode instruction:((MethodNode)value).instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("dev/vibe/ui/render/RewoundPlayers")){hooks++;hooked=true;}
            if(call.name.equals("subList"))assertTrue("Filter before vanilla's 80-entry cap",hooked);
        }}assertEquals(1,hooks);
    }
    public static class Info extends NetworkPlayerInfo {
        private final IChatComponent display;
        ScorePlayerTeam team;
        Info(String name,String display){super(new GameProfile(UUID.randomUUID(),name));this.display=display==null?null:new ChatComponentText(display);}
        @Override public IChatComponent getDisplayName(){return display;}
        @Override public ScorePlayerTeam getPlayerTeam(){return team;}
    }
    @Test public void scoreboardPrefixesUseTheSameStarRuleAsDisplayNames(){
        Info player=new Info("Ranked",null);player.team=new ScorePlayerTeam(new net.minecraft.scoreboard.Scoreboard(),"real");
        player.team.setNamePrefix("§c* §e[120] ");assertTrue(RewoundPlayers.real(player));
        player.team.setNamePrefix("§cR ");assertFalse(RewoundPlayers.real(player));
    }
}
