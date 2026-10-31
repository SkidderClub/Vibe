package dev.vibe.module.impl.world;

import dev.vibe.inventory.InventoryLayout;
import dev.vibe.inventory.InventoryLayout.Type;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.junit.*;
import static org.junit.Assert.*;

public class InventoryManagerPlanTest {
    private Minecraft previous;
    private InventoryManagerModule module;
    private Slots inventory;
    @Before public void setup() throws Exception {
        Field registered = field(net.minecraft.init.Bootstrap.class, "alreadyRegistered");
        if (!registered.getBoolean(null)) {
            registered.setBoolean(null, true); net.minecraft.block.Block.registerBlocks(); net.minecraft.item.Item.registerItems();
        }
        previous = Minecraft.getMinecraft(); field(Minecraft.class, "theMinecraft").set(null, allocate(Minecraft.class));
        module = new InventoryManagerModule(); inventory = new Slots();
    }
    @After public void close() throws Exception { field(Minecraft.class, "theMinecraft").set(null, previous); }

    @Test public void plansSwordBeforeArmorBeforeSuppliesRegardlessOfAssignmentOrder() throws Exception {
        InventoryLayout layout = new InventoryLayout(); layout.set(8, Type.FOOD); layout.set(0, Type.SWORD);
        inventory.items.setInventorySlotContents(10, new ItemStack(Items.apple));
        inventory.items.setInventorySlotContents(11, new ItemStack(Items.diamond_helmet));
        inventory.items.setInventorySlotContents(12, new ItemStack(Items.diamond_sword));
        Object sword = plan("arrangeOne", layout); assertEquals(12, integer(sword,"slot")); assertEquals(0, integer(sword,"hotbarButton"));
        inventory.items.setInventorySlotContents(36, inventory.items.getStackInSlot(12)); inventory.items.setInventorySlotContents(12, null);
        Object armor = plan("arrangeOne", layout); assertEquals(11, integer(armor,"slot")); assertEquals(5, integer(armor,"target"));
        inventory.items.setInventorySlotContents(5, inventory.items.getStackInSlot(11)); inventory.items.setInventorySlotContents(11, null);
        assertEquals(10, integer(plan("arrangeOne", layout),"slot"));
    }
    @Test public void duplicateTiesKeepOneCopyAndEquippedArmorCounts() throws Exception {
        InventoryLayout layout = new InventoryLayout(); layout.set(0, Type.SWORD);
        inventory.items.setInventorySlotContents(36, new ItemStack(Items.diamond_sword));
        inventory.items.setInventorySlotContents(10, new ItemStack(Items.diamond_sword));
        inventory.items.setInventorySlotContents(11, new ItemStack(Items.iron_sword));
        assertEquals(10, integer(plan("dropOneDuplicate",layout),"slot")); inventory.items.setInventorySlotContents(10,null);
        assertEquals(11, integer(plan("dropOneDuplicate",layout),"slot")); inventory.items.setInventorySlotContents(11,null);
        assertNull(plan("dropOneDuplicate",layout));
        inventory.items.setInventorySlotContents(5,new ItemStack(Items.diamond_helmet));
        inventory.items.setInventorySlotContents(12,new ItemStack(Items.iron_helmet));
        assertEquals(12,integer(plan("dropOneDuplicate",layout),"slot"));
    }
    @Test public void toolsAreRankedByMaterialAndIgnoredSlotsRemainProtected() throws Exception {
        InventoryLayout layout = new InventoryLayout();
        inventory.items.setInventorySlotContents(10,new ItemStack(Items.wooden_pickaxe));
        inventory.items.setInventorySlotContents(11,new ItemStack(Items.diamond_pickaxe));
        assertEquals(10,integer(plan("dropOneDuplicate",layout),"slot"));
        layout.set(10,Type.IGNORE); assertNull(plan("dropOneDuplicate",layout));
    }
    @Test public void chestTitleCheckIsOnlyACaseInsensitiveSubstringCheck() throws Exception {
        ChestStealerModule stealer=new ChestStealerModule();
        net.minecraft.inventory.ContainerChest chest=allocate(net.minecraft.inventory.ContainerChest.class);
        Method check=ChestStealerModule.class.getDeclaredMethod("hasVanillaChestTitle",net.minecraft.inventory.ContainerChest.class);check.setAccessible(true);
        for(String title:new String[]{"Chest","Large Chest","Team CHEST","Rewards chest 1"}) {
            field(net.minecraft.inventory.ContainerChest.class,"lowerChestInventory").set(chest,new InventoryBasic(title,true,27));
            assertTrue(title,(Boolean)check.invoke(stealer,chest));
        }
        field(net.minecraft.inventory.ContainerChest.class,"lowerChestInventory").set(chest,new InventoryBasic("Shop",true,27));
        assertFalse((Boolean)check.invoke(stealer,chest));
    }
    @Test public void repeatedSoupSlotsFillWithoutStealingOrDroppingOtherReservedStacks() throws Exception {
        InventoryLayout layout=new InventoryLayout();layout.set(0,Type.SWORD);
        inventory.items.setInventorySlotContents(36,new ItemStack(Items.diamond_sword));
        for(int i=1;i<=8;i++){layout.set(i,Type.SOUP);inventory.items.setInventorySlotContents(8+i,new ItemStack(Items.mushroom_stew));}
        for(int i=1;i<=8;i++){
            Object action=plan("arrangeOne",layout);assertNotNull(action);
            int from=integer(action,"slot"),button=integer(action,"hotbarButton");assertEquals(i,button);
            inventory.items.setInventorySlotContents(36+button,inventory.items.getStackInSlot(from));inventory.items.setInventorySlotContents(from,null);
        }
        assertNull(plan("arrangeOne",layout));assertNull(plan("dropOneDuplicate",layout));
        ((dev.vibe.setting.NumberSetting)field(InventoryManagerModule.class,"maxFoodSlots").get(module)).setValue(0D);
        assertNull("Explicit layout assignments take priority over the generic food cap",plan("dropExcess",layout));
        assertEquals(Items.diamond_sword,inventory.items.getStackInSlot(36).getItem());
    }
    private Object plan(String name, InventoryLayout layout) throws Exception {
        Method method=InventoryManagerModule.class.getDeclaredMethod(name,Container.class,InventoryLayout.class); method.setAccessible(true);
        return method.invoke(module,inventory,layout);
    }
    private static int integer(Object object,String name)throws Exception{return field(object.getClass(),name).getInt(object);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static <T>T allocate(Class<T> type)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");return type.cast(u.getMethod("allocateInstance",Class.class).invoke(field(u,"theUnsafe").get(null),type));}
    private static final class Slots extends Container {
        final InventoryBasic items=new InventoryBasic("test",false,45);
        Slots(){for(int index=0;index<45;index++)addSlotToContainer(new Slot(items,index,0,0));}
        @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}
    }
}
