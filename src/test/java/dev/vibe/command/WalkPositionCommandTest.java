package dev.vibe.command;
import dev.vibe.module.impl.movement.AutoWalkModule;
import dev.vibe.path.PathTestFixture;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class WalkPositionCommandTest {
    @Test public void posSetsCoordinatesAtomicallyUsesCurrentBlockAndKeepsAreaCommandsSeparate()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            AutoWalkModule walk=new AutoWalkModule();f.modules.registerDynamic(walk);CommandManager commands=new CommandManager();
            assertTrue(commands.execute(".pos 7 64 -3"));assertEquals(new BlockPos(7,64,-3),walk.getPosition());assertFalse(walk.isEnabled());assertEquals("Player",walk.pathMode.getValue());
            for(String bad:new String[]{".pos 1 2",".pos 1.5 64 3",".pos 30000001 64 3",".pos NaN 64 3",".pos 2147483648 64 3"}){commands.execute(bad);assertEquals(new BlockPos(7,64,-3),walk.getPosition());}
            f.player.setPosition(-2.2,65,3.9);commands.execute(".pos");BlockPos here=f.player.getPosition();assertEquals(here,walk.getPosition());
            assertTrue(commands.getSuggestions(".po").contains(".pos"));assertTrue(commands.getSuggestions(".pos ").contains(".pos "+here.getX()));assertTrue(commands.getSuggestions(".pos "+here.getX()+" ").contains(".pos "+here.getX()+" "+here.getY()));
            commands.execute(".pos1 1 2 3");assertEquals(1,f.filters.pos1X.getInt());assertEquals(here,walk.getPosition());
            assertTrue("Must use Vibe's normal local chat formatting",f.player.localMessages.get(0).startsWith("[Vibe] "));
        }
    }
}
