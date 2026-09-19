package dev.vibe.module.impl;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.stats.StatBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import org.junit.*;
import static org.junit.Assert.*;

public class PickenSwitchTest {
    @BeforeClass public static void bootstrap()throws Exception{
        Field f=net.minecraft.init.Bootstrap.class.getDeclaredField("alreadyRegistered");f.setAccessible(true);
        if(!f.getBoolean(null)){f.setBoolean(null,true);net.minecraft.block.Block.registerBlocks();Item.registerItems();}
    }
    private ItemStack sword(){return new ItemStack(new ItemSword(Item.ToolMaterial.EMERALD));}
    private ItemStack enchanted(int knock,int fire){ItemStack s=new ItemStack(new ItemSword(Item.ToolMaterial.EMERALD));if(knock>0)s.addEnchantment(Enchantment.knockback,knock);if(fire>0)s.addEnchantment(Enchantment.fireAspect,fire);return s;}
    @Test public void picksImprovedEnchantmentsEvenWhenTheUtilityStackHasLowerDamage(){
        PickenSwitchModule m=new PickenSwitchModule();ItemStack[] bar=new ItemStack[9];bar[0]=sword();bar[2]=enchanted(1,0);bar[5]=enchanted(2,2);
        assertEquals(5,m.chooseSlot(bar,0,false));assertEquals(8,PickenSwitchModule.baseDamage(bar[0]),0);
        assertEquals(8,PickenSwitchModule.baseDamage(bar[5]),0);
        bar[5]=enchanted(0,2);assertEquals(2,m.chooseSlot(bar,0,true));
        bar[0].addEnchantment(Enchantment.knockback,2);bar[0].addEnchantment(Enchantment.fireAspect,2);
        assertEquals(-1,m.chooseSlot(bar,0,false));
        ItemStack[] inferiorOnly=new ItemStack[9];inferiorOnly[0]=sword();inferiorOnly[4]=new ItemStack(new Item());inferiorOnly[4].addEnchantment(Enchantment.knockback,3);
        // The vanilla hit method calculates base damage from the equipped
        // attributes before it reads held-item enchantments. Picken's hook
        // therefore deliberately permits this utility item at that point.
        assertEquals(4,m.chooseSlot(inferiorOnly,0,false));
    }
    @Test public void vanillaAttackUsesCachedDamageAndCurrentHeldEnchantments(){
        TestWorld world=new TestWorld();Player attacker=new Player(world),victim=new Player(world);
        ItemStack weapon=sword(),utility=new ItemStack(new Item());utility.addEnchantment(Enchantment.knockback,2);utility.addEnchantment(Enchantment.fireAspect,2);
        attacker.inventory.mainInventory[0]=weapon;attacker.inventory.mainInventory[1]=utility;
        // EntityLivingBase.onUpdate applies these equipment attributes once per server tick.
        attacker.getAttributeMap().applyAttributeModifiers(weapon.getAttributeModifiers());
        attacker.inventory.currentItem=1;
        attacker.attackTargetEntityWithCurrentItem(victim);
        assertEquals(8,victim.damage,0);assertEquals(8,victim.fireSeconds);assertEquals(1,victim.motionZ,.00001);
        // Once equipment attributes update, the same inferior item returns to its own base damage.
        attacker.getAttributeMap().removeAttributeModifiers(weapon.getAttributeModifiers());
        victim.damage=0;attacker.attackTargetEntityWithCurrentItem(victim);assertEquals(1,victim.damage,0);
    }
    private static final class Player extends EntityPlayer {
        float damage;int fireSeconds;
        Player(World w){super(w,new GameProfile(UUID.randomUUID(),"Fixture"));}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean attackEntityFrom(DamageSource source,float amount){damage=amount;return true;}
        @Override public void setFire(int seconds){fireSeconds=seconds;}
        @Override public void addStat(StatBase stat,int amount){ }
    }
    private static final class TestWorld extends World {
        TestWorld(){super(new SaveHandlerMP(),new WorldInfo(new WorldSettings(0,WorldSettings.GameType.SURVIVAL,false,false,WorldType.DEFAULT),"Test"),new WorldProviderSurface(),new net.minecraft.profiler.Profiler(),true);provider.registerWorld(this);chunkProvider=createChunkProvider();}
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return new net.minecraft.client.multiplayer.ChunkProviderClient(this);}
        @Override protected int getRenderDistanceChunks(){return 0;}
    }
}
