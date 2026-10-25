package dev.vibe.module.impl.meme;

import dev.vibe.game.hypixel.MurderWeapons;
import dev.vibe.module.Module;
import dev.vibe.module.impl.visual.PathModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.Setting;
import java.util.Collections;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.world.World;
import org.junit.*;
import static org.junit.Assert.*;

/** Real registry variants, scoreboard roles and Path selection without a server or GL window. */
public class MurderMysteryTest {
    private PathTestFixture f;
    private HypixelModule murder;
    private Scoreboard scoreboard;
    private ScoreObjective objective;
    private String role;

    @Before public void setup() throws Exception {
        f = new PathTestFixture();
        f.mc.setServerData(new ServerData("Fixture", "mc.hypixel.net", false));
        f.player.inventory = new InventoryPlayer(f.player);
        murder = new HypixelModule(); f.modules.registerDynamic(murder);
        PathTestFixture.set(Module.class, murder, "enabled", true);
        scoreboard = new Scoreboard();
        objective = scoreboard.addScoreObjective("murder", IScoreObjectiveCriteria.DUMMY);
        objective.setDisplayName("\u00a7e\u00a7lMURDER MYSTERY");
        scoreboard.setObjectiveInDisplaySlot(1, objective);
        PathTestFixture.set(World.class, f.world, "worldScoreboard", scoreboard);
        role("Innocent");
    }

    @After public void cleanup() throws Exception { if (f != null) f.close(); }

    @Test public void all47PicturedSkinsResolveAndMatchIncludingVariantsAndWornTools() {
        assertEquals(47, MurderWeapons.defaults().size());
        assertEquals(MurderWeapons.DEFAULTS, murder.getMurderWeapons().getValue());
        for (String id : MurderWeapons.defaults()) {
            ItemStack item = MurderWeapons.stack(id);
            assertNotNull("Unresolved screenshot skin: " + id, item);
            assertTrue("Undetected screenshot skin: " + id, MurderWeapons.matches(MurderWeapons.defaults(), item));
        }
        assertTrue(MurderWeapons.matches(MurderWeapons.defaults(), new ItemStack(Items.iron_sword, 1, 200)));
        assertTrue(MurderWeapons.matches(MurderWeapons.defaults(), new ItemStack(Items.dye, 1, 4)));
        assertTrue(MurderWeapons.matches(MurderWeapons.defaults(), new ItemStack(Items.dye, 1, 1)));
        assertFalse(MurderWeapons.matches(MurderWeapons.defaults(), new ItemStack(Items.dye, 1, 15)));
        assertFalse(MurderWeapons.matches(MurderWeapons.defaults(), new ItemStack(Items.fish, 1, 0)));
        assertFalse(MurderWeapons.matches(MurderWeapons.defaults(), new ItemStack(Items.apple)));
        ItemStack renamed = new ItemStack(Items.apple); renamed.setStackDisplayName("Iron Sword");
        assertFalse("Display names are not weapon signatures", MurderWeapons.matches(MurderWeapons.defaults(), renamed));
    }

    @Test public void upgradesOnlyTheOldStockListAndKeepsCustomLegacyAndNamespacedEntries() {
        murder.getMurderWeapons().setValue(MurderWeapons.LEGACY_DEFAULTS);
        assertEquals(MurderWeapons.DEFAULTS, murder.getMurderWeapons().getValue());
        murder.getMurderWeapons().setValue("minecraft:iron_sword,dye@4,swordwood");
        assertEquals("minecraft:iron_sword,dye@4,swordwood", murder.getMurderWeapons().getValue());
        assertTrue(MurderWeapons.matches(MurderWeapons.parse(murder.getMurderWeapons().getValue()), new ItemStack(Items.wooden_sword)));
        assertTrue(MurderWeapons.matches(MurderWeapons.parse("minecraft:dye@4"), new ItemStack(Items.dye, 1, 4)));
        assertFalse(MurderWeapons.matches(MurderWeapons.parse("minecraft:dye@4"), new ItemStack(Items.dye, 1, 1)));
        assertTrue(MurderWeapons.matches(MurderWeapons.parse("dye"), new ItemStack(Items.dye, 1, 15)));
    }

    @Test public void pathSwitchesFromMurderersToRememberedBowHoldersWithTheLocalRole() throws Exception {
        PathTestFixture.Player killer = player(2, "Killer", 7, new ItemStack(Items.iron_shovel));
        PathTestFixture.Player archer = player(3, "Archer", 5, new ItemStack(Items.bow));
        player(4, "Bystander", 2, null);
        murder.tick();
        assertFalse(f.module.getTargetMode().getModes().contains("Murder Mystery"));
        for (String mode : f.module.getTargetMode().getModes()) {
            f.module.getTargetMode().setValue(mode);
            assertSame("Automatically override " + mode + " with the murderer", killer, f.module.selectTarget(f.mc));
        }
        assertNotEquals(0, murder.visualColor(killer));
        killer.inventory.mainInventory[0] = null; archer.inventory.mainInventory[0] = null;
        role("Murderer"); murder.tick();
        for (String mode : f.module.getTargetMode().getModes()) {
            f.module.getTargetMode().setValue(mode);
            assertSame("Automatically override " + mode + " with remembered bow holders", archer, f.module.selectTarget(f.mc));
        }
        for (Setting<?> setting : murder.getSettings()) if (setting.getRawName().equals("Detect bow")) ((BooleanSetting) setting).setEnabled(false);
        assertEquals(0, murder.visualColor(archer));
        assertSame("Path bow tracking is independent of bow ESP", archer, f.module.selectTarget(f.mc));
        archer.isDead = true;
        assertNull("Dead bow holders must not be replaced with a random innocent", f.module.selectTarget(f.mc));
    }

    @Test public void eachPathRoleOptionCanBeDisabledAndTurningOffDetectionClearsTheRoute() throws Exception {
        player(2, "Killer", 5, new ItemStack(Items.stick));
        player(3, "Archer", 7, new ItemStack(Items.bow));
        f.module.getTargetMode().setValue("Combat Target"); murder.tick();
        assertTrue(f.module.getMurderLines().isVisible());
        f.module.getMurderLines().toggle(PathModule.TO_MURDERER);
        assertNull(f.module.selectTarget(f.mc));
        role("Murderer"); murder.tick(); assertNotNull(f.module.selectTarget(f.mc));
        f.module.getMurderLines().toggle(PathModule.TO_BOW_HOLDERS);
        assertNull(f.module.selectTarget(f.mc));
        f.module.getMurderLines().toggle(PathModule.TO_BOW_HOLDERS);
        f.module.setEnabled(true); f.finishRoute(); assertFalse(f.module.getRoute().points.isEmpty());
        murder.getModes().setValue(Collections.emptySet()); f.module.tick();
        assertNull(f.module.getTarget()); assertTrue(f.module.getRoute().points.isEmpty());
    }

    @Test public void localInventoryIdentifiesTheMurdererWhenTheRoleLineIsMissing() throws Exception {
        role(null); scoreboard.getValueFromObjective("Innocents Left: 9", objective).setScorePoints(1);
        f.player.inventory.mainInventory[4] = new ItemStack(Items.golden_axe);
        PathTestFixture.Player archer = player(2, "Archer", 5, new ItemStack(Items.bow));
        murder.tick();
        assertTrue(murder.isMurderMysteryPathTarget(archer, true, true));
        assertFalse(murder.isMurderMysteryPathTarget(f.player, true, true));
    }

    @Test public void lobbyOtherGamesAndRoundEndDoNotLeaveRoleVisualsButProxyAddressesWork() throws Exception {
        PathTestFixture.Player killer = player(2, "Killer", 5, new ItemStack(Items.iron_sword));
        role(null); scoreboard.getValueFromObjective("Players: 8/16", objective).setScorePoints(1);
        murder.tick(); assertFalse(murder.isMurderMysteryActive()); assertEquals(0, murder.visualColor(killer));
        role("Innocent"); murder.tick(); assertNotEquals(0, murder.visualColor(killer));
        murder.receiveChat("\u00a7aInnocents win!"); assertFalse(murder.isMurderMysteryActive());
        murder.tick(); objective.setDisplayName("BED WARS"); murder.tick();
        assertFalse(murder.isMurderMysteryActive()); assertEquals(0, murder.visualColor(killer));
        objective.setDisplayName("MURDER MYSTERY"); f.mc.setServerData(new ServerData("Other", "example.net", false)); murder.tick();
        assertTrue(murder.isMurderMysteryActive()); assertNotEquals(0, murder.visualColor(killer));
    }

    @Test public void proxyConnectionDetectsWeaponsAndAlwaysWarnsInLocalChatOncePerPlayer() throws Exception {
        f.mc.setServerData(new ServerData("Proxy", "fixture.eu-fra.liquidproxy.net", false));
        PathTestFixture.Player killer = player(2, "Killer", 5, new ItemStack(Items.iron_sword));
        murder.tick(); murder.tick();
        assertTrue(murder.isMurderMysteryActive());
        assertNotEquals(0, murder.visualColor(killer));
        assertEquals(1, f.player.localMessages.size());
        assertTrue(f.player.localMessages.get(0).contains("Murderer detected: Killer"));
        assertTrue(f.player.localMessages.get(0).contains("Iron Sword"));
        assertTrue("Local warnings must not send chat to the server", f.player.sentMessages.isEmpty());
        player(3, "SecondKiller", 7, new ItemStack(Items.stick)); murder.tick();
        assertEquals(2, f.player.localMessages.size());
    }

    @Test public void usesTheVisibleTeamSidebarAndReconstructsTeamFormattedRoleRows() throws Exception {
        f.mc.setServerData(null);
        PathTestFixture.Player killer = player(2, "Killer", 5, new ItemStack(Items.iron_sword));
        net.minecraft.scoreboard.ScorePlayerTeam team = scoreboard.createTeam("self-team");
        team.setChatFormat(net.minecraft.util.EnumChatFormatting.BLUE);
        scoreboard.addPlayerToTeam(f.player.getName(), team.getRegisteredName());
        scoreboard.setObjectiveInDisplaySlot(3 + team.getChatFormat().getColorIndex(), objective);
        net.minecraft.scoreboard.ScoreObjective other = scoreboard.addScoreObjective("other", IScoreObjectiveCriteria.DUMMY);
        other.setDisplayName("BED WARS"); scoreboard.setObjectiveInDisplaySlot(1, other);
        role(null);
        net.minecraft.scoreboard.ScorePlayerTeam row = scoreboard.createTeam("role-row");
        row.setNamePrefix("\u00a7eRole: "); row.setNameSuffix("\u00a7aInnocent");
        scoreboard.addPlayerToTeam("\u00a70", row.getRegisteredName());
        scoreboard.getValueFromObjective("\u00a70", objective).setScorePoints(2);
        objective.setDisplayName("\u00a7eMURDER\u00a0  MYSTERY");
        murder.tick(); assertTrue(murder.isMurderMysteryActive());
        assertSame(killer, f.module.selectTarget(f.mc));
        assertEquals(1, f.player.localMessages.size());
    }

    @Test public void automaticRoutingWaitsForDetectionAndReturnsToNormalOutsideTheGame() throws Exception {
        PathTestFixture.Player bystander = player(2, "Bystander", 2, null);
        assertSame(bystander, f.module.selectTarget(f.mc));
        murder.tick(); assertNull("Unknown murderer must not route to a random innocent", f.module.selectTarget(f.mc));
        f.module.setEnabled(true); f.module.tick(); assertNull(f.module.getTarget());
        bystander.inventory.mainInventory[0] = new ItemStack(Items.stick);
        murder.tick(); f.forceReplan(); f.finishRoute();
        assertSame(bystander, f.module.getTarget()); assertFalse(f.module.getRoute().points.isEmpty());
        objective.setDisplayName("BED WARS"); murder.tick(); f.module.tick();
        assertSame(bystander, f.module.getTarget());
    }

    @Test public void goldFilterWorksWithoutItemEspAndRespectsToggleDeadDropsAndGameContext() throws Exception {
        f.mc.setServerData(new ServerData("Proxy", "fixture.eu-fra.liquidproxy.net", false));
        Drop gold = PathTestFixture.allocate(Drop.class); gold.stack = new ItemStack(Items.gold_ingot);
        Drop other = PathTestFixture.allocate(Drop.class); other.stack = new ItemStack(Items.iron_ingot);
        murder.tick(); assertTrue(murder.shouldHighlightGold(gold)); assertFalse(murder.shouldHighlightGold(other));
        gold.posX = 100; assertTrue("All loaded gold, not only nearby drops", murder.shouldHighlightGold(gold));
        gold.isDead = true; assertFalse(murder.shouldHighlightGold(gold)); gold.isDead = false;
        murder.getHighlightGold().setEnabled(false); assertFalse(murder.shouldHighlightGold(gold));
        murder.getHighlightGold().setEnabled(true); role(null); murder.tick(); assertFalse(murder.shouldHighlightGold(gold));
    }

    @Test public void closestGoldPathIsOptionalAndIndependentOfGoldEspAndPlayerFilters() throws Exception {
        Drop near = drop(10, 3.5, .5, Items.gold_ingot), far = drop(11, 7.5, .5, Items.gold_ingot);
        drop(12, 1.5, .5, Items.iron_ingot);
        Drop empty = drop(13, 1.6, .5, Items.gold_ingot); empty.stack.stackSize = 0;
        Drop dead = drop(14, 1.7, .5, Items.gold_ingot); dead.isDead = true;
        drop(15, 100, .5, Items.gold_ingot);
        Drop removed = drop(16, 1.8, .5, Items.gold_ingot); f.world.entities.remove(16);
        murder.tick(); f.module.setEnabled(true); f.module.tick();
        assertFalse(murder.getGoldPath().isEnabled()); assertNull(f.module.getGoldTarget());
        murder.getGoldPath().setEnabled(true); murder.getHighlightGold().setEnabled(false); f.filters.getPlayers().setEnabled(false);
        finishGold();
        assertSame(near, f.module.getGoldTarget()); assertTrue(f.module.getGoldRoute().complete);
        assertNotSame(far, f.module.getGoldTarget()); assertNull(f.module.getTarget());
        assertEquals(new net.minecraft.util.BlockPos(3,64,0), f.module.getGoldRoute().end);
    }

    @Test public void goldAndMurdererRoutesWorkTogetherAndBothRespectBlockCollisions() throws Exception {
        PathTestFixture.Player killer = player(2, "Killer", 6.5, new ItemStack(Items.stick));
        Drop gold = drop(10, 6.5, 4.5, Items.gold_ingot);
        for (int z=-2;z<=2;z++) for (int y=64;y<=66;y++) f.block(3,y,z,net.minecraft.init.Blocks.stone.getDefaultState());
        murder.getGoldPath().setEnabled(true); murder.tick(); f.module.setEnabled(true);
        for (int i=0;i<200 && (!f.module.getRoute().complete || !f.module.getGoldRoute().complete);i++) f.module.tick();
        assertSame(killer, f.module.getTarget()); assertSame(gold, f.module.getGoldTarget());
        assertTrue(f.module.getRoute().complete); assertTrue(f.module.getGoldRoute().complete);
        for (dev.vibe.path.PathRoute route : new dev.vibe.path.PathRoute[]{f.module.getRoute(), f.module.getGoldRoute()}) {
            dev.vibe.path.PathTerrain terrain = new dev.vibe.path.PathTerrain(f.world,f.player);
            for (int i=1;i<route.nodes.size();i++) assertTrue(dev.vibe.movement.WalkPathfinder.canStep(terrain,route.nodes.get(i-1),route.nodes.get(i),true,3,true));
        }
    }

    @Test public void collectedGoldRetargetsAndTogglesAndRoundChangesClearItsRoute() throws Exception {
        Drop first = drop(10, 3.5, .5, Items.gold_ingot), second = drop(11, 7.5, .5, Items.gold_ingot);
        murder.getGoldPath().setEnabled(true); murder.tick(); f.module.setEnabled(true); finishGold();
        assertSame(first, f.module.getGoldTarget()); first.isDead = true;
        f.module.tick(); assertSame(second, f.module.getGoldTarget()); finishGold();
        assertEquals(new net.minecraft.util.BlockPos(7,64,0), f.module.getGoldRoute().end);
        murder.getGoldPath().setEnabled(false); f.module.tick();
        assertNull(f.module.getGoldTarget()); assertTrue(f.module.getGoldRoute().points.isEmpty());
        murder.getGoldPath().setEnabled(true); finishGold(); assertSame(second, f.module.getGoldTarget());
        objective.setDisplayName("BED WARS"); murder.tick(); f.module.tick();
        assertNull(f.module.getGoldTarget()); assertTrue(f.module.getGoldRoute().points.isEmpty());
        objective.setDisplayName("MURDER MYSTERY"); murder.tick(); finishGold();
        f.module.setEnabled(false); assertNull(f.module.getGoldTarget()); assertTrue(f.module.getGoldRoute().points.isEmpty());
    }

    @Test public void spectatorGameModeSuppressesLocalWarningsAndPublicAccusations() throws Exception {
        enableAnnouncements();
        f.mc.playerController=new net.minecraft.client.multiplayer.PlayerControllerMP(f.mc,null);
        PathTestFixture.set(net.minecraft.client.multiplayer.PlayerControllerMP.class,f.mc.playerController,"currentGameType",net.minecraft.world.WorldSettings.GameType.SPECTATOR);
        PathTestFixture.Player killer=player(2,"Killer",5,new ItemStack(Items.iron_sword));
        murder.tick();assertTrue(murder.isSpectating());
        assertNotEquals(0,murder.murdererColor(killer));
        assertTrue(f.player.localMessages.isEmpty());assertTrue(f.player.sentMessages.isEmpty());
    }
    @Test public void spectatorSidebarSuppressesAlertsEvenInAdventureGameMode() throws Exception {
        enableAnnouncements();role("Spectator");
        player(2,"Killer",5,new ItemStack(Items.iron_sword));murder.tick();
        assertTrue(murder.isSpectating());assertTrue(f.player.localMessages.isEmpty());assertTrue(f.player.sentMessages.isEmpty());
    }
    @Test public void dyingSuppressesNewAlertsUntilTheRoundResetsEvenIfTheOldRoleLineRemains() throws Exception {
        enableAnnouncements();murder.tick();murder.receiveChat("\u00a7cYou died!");
        player(2,"Killer",5,new ItemStack(Items.iron_sword));murder.tick();murder.tick();
        assertTrue(murder.isSpectating());assertTrue(f.player.localMessages.isEmpty());assertTrue(f.player.sentMessages.isEmpty());
        murder.receiveChat("Game over");role("Innocent");murder.tick();
        assertFalse(murder.isSpectating());assertEquals(1,f.player.localMessages.size());assertEquals(1,f.player.sentMessages.size());
    }
    @Test public void murdererColorOverridesArrowRainbowWhilePreservingVisibilityOpacity() throws Exception {
        PathTestFixture.Player killer=player(2,"Killer",5,new ItemStack(Items.iron_sword));
        PathTestFixture.Player archer=player(3,"Archer",7,new ItemStack(Items.bow));
        int chosen=0x8042A6EF;
        for(Setting<?> setting:murder.getSettings())if(setting.getRawName().equals("Murderer ESP color"))((dev.vibe.setting.ColorSetting)setting).setValue(chosen);
        murder.tick();assertEquals(chosen,murder.murdererColor(killer));assertEquals(0,murder.murdererColor(archer));
        dev.vibe.module.impl.visual.OofArrowsModule arrows=new dev.vibe.module.impl.visual.OofArrowsModule();
        arrows.colorMode.setValue("Rainbow");arrows.distanceFade.setEnabled(false);arrows.visibleOpacity.setValue(.5D);
        int color=dev.vibe.ui.render.OofArrowsRenderer.color(arrows,4567,4,25,true,murder.murdererColor(killer));
        assertEquals(chosen&0xFFFFFF,color&0xFFFFFF);assertEquals(64,color>>>24);
        assertNotEquals(chosen&0xFFFFFF,dev.vibe.ui.render.OofArrowsRenderer.color(arrows,4567,4,25,true,0)&0xFFFFFF);
    }
    private void enableAnnouncements() {
        for(Setting<?> setting:murder.getSettings())if(setting.getRawName().equals("Announce murderer"))((BooleanSetting)setting).setEnabled(true);
    }

    private void finishGold() {
        for (int i=0;i<200 && f.module.getGoldRoute().points.isEmpty();i++) f.module.tick();
    }

    private Drop drop(int id, double x, double z, net.minecraft.item.Item type) throws Exception {
        Drop item = PathTestFixture.allocate(Drop.class); item.stack = new ItemStack(type); item.setEntityId(id); item.worldObj=f.world;
        item.posX=item.lastTickPosX=x; item.posY=item.lastTickPosY=64.2; item.posZ=item.lastTickPosZ=z;
        f.world.entities.put(id,item); f.world.loadedEntityList.add(item); return item;
    }

    private void role(String value) {
        if (role != null) scoreboard.removeObjectiveFromEntity(role, objective);
        role = value == null ? null : "Role: " + value;
        if (role != null) scoreboard.getValueFromObjective(role, objective).setScorePoints(2);
    }

    private PathTestFixture.Player player(int id, String name, double x, ItemStack held) throws Exception {
        PathTestFixture.Player player = f.addPlayer(id, name, x, 64, .5);
        player.inventory = new InventoryPlayer(player); player.inventory.mainInventory[0] = held;
        return player;
    }

    public static final class Drop extends EntityItem {
        ItemStack stack;
        private Drop() { super((World) null); }
        @Override public ItemStack getEntityItem() { return stack; }
    }
}
