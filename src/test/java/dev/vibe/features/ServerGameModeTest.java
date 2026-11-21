package dev.vibe.features;

import dev.vibe.path.PathTestFixture;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.world.World;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerGameModeTest {
    @Test public void autoUsesOnlyTheTeamSignalForTheCurrentGame() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            PathTestFixture.Player other = f.addPlayer(2, "Other", 1, 64, .5);
            f.player.inventory = new InventoryPlayer(f.player);
            other.inventory = new InventoryPlayer(other);
            Scoreboard board = new Scoreboard();
            PathTestFixture.set(World.class, f.world, "worldScoreboard", board);
            ScoreObjective sidebar = board.addScoreObjective("game", IScoreObjectiveCriteria.DUMMY);
            board.setObjectiveInDisplaySlot(1, sidebar);
            ScorePlayerTeam own = board.createTeam("own");
            ScorePlayerTeam theirs = board.createTeam("theirs");
            board.addPlayerToTeam(f.player.getName(), own.getRegisteredName());
            board.addPlayerToTeam(other.getName(), theirs.getRegisteredName());
            f.filters.getTeammateDetection().setValue("Auto");

            sidebar.setDisplayName("§eBED WARS");
            ItemStack selfArmor = new ItemStack(Items.leather_chestplate);
            ItemStack otherArmor = new ItemStack(Items.leather_chestplate);
            f.player.inventory.armorInventory[2] = selfArmor;
            other.inventory.armorInventory[2] = otherArmor;
            assertFalse(f.filters.isTeammate(other));
            ((ItemArmor) selfArmor.getItem()).setColor(selfArmor, 0x33AA44);
            ((ItemArmor) otherArmor.getItem()).setColor(otherArmor, 0x33AA44);
            assertTrue(f.filters.isTeammate(other));
            ((ItemArmor) otherArmor.getItem()).setColor(otherArmor, 0xFF0000);
            assertFalse(f.filters.isTeammate(other));

            own.setNamePrefix("§a");
            theirs.setNamePrefix("§a");
            for (String title : new String[]{"§eSKYWARS", "THE HYPIXEL PIT", "BLITZ SG", "SURVIVALGAMES"}) {
                sidebar.setDisplayName(title);
                f.player.ticksExisted++;
                assertFalse(title, f.filters.isTeammate(other));
            }
            sidebar.setDisplayName("§eSKYWARS");
            own.setNamePrefix("§a[K] ");
            theirs.setNamePrefix("§c[K] §a");
            f.player.ticksExisted++;
            assertEquals(ServerGameMode.SKYWARS_TEAMS, ServerGameMode.current());
            assertTrue(f.filters.isTeammate(other));
            theirs.setNamePrefix("§c[K] ");
            assertFalse(f.filters.isTeammate(other));
        }
    }
}
