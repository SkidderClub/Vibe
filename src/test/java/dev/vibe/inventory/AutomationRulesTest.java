package dev.vibe.inventory;

import dev.vibe.command.CommandManager;
import dev.vibe.inventory.InventoryLayout.Type;
import dev.vibe.inventory.CraftingPlanner.*;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.world.AutoRespawnModule;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomationRulesTest {
    @Test public void cuboidSupportsReversedCornersAllAxesInversionAndDisabledFallback() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            TargetsModule t=f.filters;
            t.setCorner(1,new BlockPos(5,70,9));t.setCorner(2,new BlockPos(-2,64,3));t.targetArea.setEnabled(true);
            assertFalse(t.shouldDisplayTargetArea());t.displayTargetArea.setEnabled(true);assertTrue(t.shouldDisplayTargetArea());
            net.minecraft.util.AxisAlignedBB bounds=t.getTargetAreaBounds();
            assertEquals(-2,bounds.minX,0);assertEquals(64,bounds.minY,0);assertEquals(3,bounds.minZ,0);
            assertEquals(6,bounds.maxX,0);assertEquals(71,bounds.maxY,0);assertEquals(10,bounds.maxZ,0);
            assertTrue(t.inTargetArea(-2,64,3));assertTrue(t.inTargetArea(5.99,70.99,9.99));
            assertFalse(t.inTargetArea(6,65,5));assertFalse(t.inTargetArea(0,63.99,5));assertFalse(t.inTargetArea(0,65,10));
            PathTestFixture.Player enemy=f.addPlayer(7,"Enemy",0,65,5);
            assertTrue(t.canTarget(enemy));enemy.posY=71;assertFalse(t.canTarget(enemy));
            assertTrue("ESP filtering stays independent of attack boundaries",t.canVisualize(enemy));
            t.invertArea.setEnabled(true);assertTrue(t.canTarget(enemy));assertFalse(t.inTargetArea(0,65,5));
            t.targetArea.setEnabled(false);assertTrue(t.inTargetArea(0,65,5));assertTrue(t.canTarget(enemy));
            assertFalse(t.shouldDisplayTargetArea());
            CommandManager c=new CommandManager();assertTrue(c.getSuggestions(".pos").contains(".pos1"));assertTrue(c.getSuggestions(".pos").contains(".pos2"));
            c.execute(".pos1 -10 20 30");assertEquals(-10,t.pos1X.getInt());assertEquals(20,t.pos1Y.getInt());assertEquals(30,t.pos1Z.getInt());
            t.pos1X.setValue("-12345");assertEquals(-12345,t.pos1X.getInt());
            t.pos1X.setValue("bad input");assertEquals(-12345,t.pos1X.getInt());
            t.pos1X.setValue("Infinity");assertEquals(-12345,t.pos1X.getInt());
            t.pos1X.setValue("30000001");assertEquals(-12345,t.pos1X.getInt());
        }
    }
    @Test public void respawnWaitsForDelayAndSendsOnlyOnceForTheSameDeath() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoRespawnModule m=new AutoRespawnModule();m.delay.setRange(300,300);m.setEnabled(true);
            f.mc.currentScreen=new GuiGameOver();f.health(0);
            m.tick(1000);m.tick(1299);assertEquals(0,f.player.respawns);
            m.tick(1300);assertEquals(1,f.player.respawns);assertNull(f.mc.currentScreen);
            m.tick(1301);m.tick(1700);assertEquals(1,f.player.respawns);
            f.health(20);m.tick(1800);f.health(0);f.mc.currentScreen=new GuiGameOver();
            m.tick(2000);m.tick(2300);assertEquals(2,f.player.respawns);
        }
    }
    @Test public void eightSoupAssignmentsPersistAndEquipmentRemainsUnique() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            InventoryLayout layout=new InventoryLayout();layout.set(0,Type.SWORD);
            for(int i=1;i<=8;i++)layout.set(i,Type.SOUP);
            InventoryLayout copy=InventoryLayout.decode(layout.encode());assertEquals(9,copy.assignments().size());
            for(int i=1;i<=8;i++)assertEquals(Type.SOUP,copy.get(i));
            copy.set(9,Type.SWORD);assertNull(copy.get(0));assertEquals(Type.SWORD,copy.get(9));
            copy.set(10,Type.HEAD);copy.set(11,Type.HEAD);assertEquals(Type.HEAD,copy.get(10));
            assertTrue(InventoryLayout.matches(Type.SOUP,new ItemStack(Items.mushroom_stew)));
            assertTrue(InventoryLayout.matches(Type.HEAD,new ItemStack(Items.skull,1,3)));
            assertFalse(InventoryLayout.matches(Type.FOOD,new ItemStack(Items.mushroom_stew)));
        }
    }
    @Test public void crafterPlansSticksThenSwordAndEveryShapeMatchesVanillaRecipes() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            CraftingPlanner p=new CraftingPlanner();ItemStack[] inv=new ItemStack[40];boolean[] usable=new boolean[40];Arrays.fill(usable,true);
            Set<String> all=new HashSet<String>(Arrays.asList("Sword","Helmet","Chestplate","Leggings","Boots"));
            inv[0]=new ItemStack(Items.diamond,32);inv[1]=new ItemStack(Blocks.planks,4);
            Recipe sticks=p.next(inv,usable,all,"Diamond");assertEquals(Items.stick,sticks.output.getItem());verifyRecipe(sticks,f);
            inv[2]=new ItemStack(Items.stick,4);Recipe sword=p.next(inv,usable,all,"Diamond");assertEquals(Items.diamond_sword,sword.output.getItem());verifyRecipe(sword,f);
            inv[3]=new ItemStack(Items.diamond_sword);
            for(String part:Arrays.asList("Helmet","Chestplate","Leggings","Boots")) {
                Recipe recipe=p.next(inv,usable,Collections.singleton(part),"Diamond");assertNotNull(part,recipe);verifyRecipe(recipe,f);
            }
            Recipe armor=p.next(inv,usable,all,"Diamond");assertEquals(Items.diamond_chestplate,armor.output.getItem());
            inv[4]=new ItemStack(Items.diamond_chestplate);armor=p.next(inv,usable,all,"Diamond");assertEquals(Items.diamond_leggings,armor.output.getItem());
        }
    }
    @Test public void crafterCountsUnwornSuperiorArmorAndPicksGreatestArmorGain() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            CraftingPlanner p=new CraftingPlanner();ItemStack[] inv=new ItemStack[40];boolean[] usable=new boolean[40];Arrays.fill(usable,true);
            inv[0]=new ItemStack(Items.iron_ingot,32);inv[1]=new ItemStack(Items.diamond_chestplate);
            assertNull(p.next(inv,usable,Collections.singleton("Chestplate"),"Iron"));
            inv[0]=new ItemStack(Items.diamond,32);inv[1]=new ItemStack(Items.iron_chestplate);
            Set<String> armor=new HashSet<String>(Arrays.asList("Helmet","Chestplate","Leggings","Boots"));
            assertEquals(Items.diamond_leggings,p.next(inv,usable,armor,"Diamond").output.getItem());
            inv[2]=new ItemStack(Items.diamond_leggings);inv[3]=new ItemStack(Items.diamond_boots);inv[4]=new ItemStack(Items.diamond_helmet);
            assertEquals(Items.diamond_chestplate,p.next(inv,usable,armor,"Diamond").output.getItem());
            inv[1]=new ItemStack(Items.diamond_chestplate);assertNull(p.next(inv,usable,armor,"Diamond"));
            Arrays.fill(usable,false);assertNull(p.next(inv,usable,armor,"Diamond"));
        }
    }
    @Test public void crafterMakesPlanksForMissingStickButDoesNotWasteSwordMaterials() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            CraftingPlanner p=new CraftingPlanner();ItemStack[] inv=new ItemStack[40];boolean[] usable=new boolean[40];Arrays.fill(usable,true);
            inv[0]=new ItemStack(Items.diamond,8);inv[1]=new ItemStack(Blocks.log,1,2);
            Set<String> all=new HashSet<String>(Arrays.asList("Sword","Chestplate"));
            Recipe r=p.next(inv,usable,all,"Diamond");assertEquals(Item.getItemFromBlock(Blocks.planks),r.output.getItem());assertEquals(2,r.output.getMetadata());verifyRecipe(r,f);
            inv[1]=null;assertNull("Sword resources stay reserved while its stick is missing",p.next(inv,usable,all,"Diamond"));
        }
    }
    private static void verifyRecipe(Recipe recipe,AutomationFixture f) {
        Container container=new Container(){@Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}};
        InventoryCrafting grid=new InventoryCrafting(container,3,3);
        for(int i=0;i<9;i++)if(recipe.grid[i]!=null) {
            Ingredient item=recipe.grid[i];grid.setInventorySlotContents(i,new ItemStack(item.item,1,Math.max(0,item.metadata)));
        }
        ItemStack actual=CraftingManager.getInstance().findMatchingRecipe(grid,f.mc.theWorld);
        assertTrue("Recipe must be a real 1.8.9 recipe: "+recipe.name,recipe.matchesOutput(actual));
    }
}
