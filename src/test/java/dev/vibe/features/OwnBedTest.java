package dev.vibe.features;
import dev.vibe.esp.BedWoolColors;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.world.BedAuraModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class OwnBedTest {
    @Test public void usesTheNamesActualColorAndTheSameNearestWoolAsBedEsp()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            ColoredPlayer player=PathTestFixture.allocate(ColoredPlayer.class);f.mc.thePlayer=player;
            BlockPos bed=new BlockPos(0,64,0);f.block(0,64,0,Blocks.bed.getDefaultState());f.block(1,64,0,Blocks.wool.getStateFromMeta(14));
            BedAuraModule aura=new BedAuraModule();aura.ownBed.setEnabled(true);java.lang.reflect.Method check=BedAuraModule.class.getDeclaredMethod("isOwnBed",BlockPos.class);check.setAccessible(true);
            assertEquals(14,BedWoolColors.fromName("§a[VIP] §cRedName§r","RedName"));assertTrue((Boolean)check.invoke(aura,bed));
            f.block(1,64,0,Blocks.wool.getStateFromMeta(11));assertFalse((Boolean)check.invoke(aura,bed));
            aura.ownBed.setEnabled(false);f.block(1,64,0,Blocks.wool.getStateFromMeta(14));assertFalse((Boolean)check.invoke(aura,bed));
        }
    }
    public static class ColoredPlayer extends EntityPlayerSP {private ColoredPlayer(){super(null,null,null,null);}@Override public String getName(){return "RedName";}@Override public IChatComponent getDisplayName(){return new ChatComponentText("§a[VIP] §cRedName");}}
}
