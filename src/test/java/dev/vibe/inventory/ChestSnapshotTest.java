package dev.vibe.inventory;

import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.enchantment.Enchantment;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChestSnapshotTest {
    @Test public void exportRoundTripsEveryItemTagTitleAndSlotWithoutIncludingPlayerItems() throws Exception {
        try (AutomationFixture f = new AutomationFixture()) {
            InventoryBasic inventory = new InventoryBasic("§dQuick Buy", true, 54);
            ItemStack sword = new ItemStack(Items.diamond_sword, 1, 19);
            sword.addEnchantment(Enchantment.sharpness, 5); sword.setStackDisplayName("Shop sword");
            sword.getTagCompound().setString("ServerShopData", "Opaque tags must survive");
            inventory.setInventorySlotContents(53, sword);
            inventory.setInventorySlotContents(0, new ItemStack(Items.apple, 17));
            f.player.inventory.mainInventory[0] = new ItemStack(Items.diamond);
            ContainerChest chest = new ContainerChest(f.player.inventory, inventory, f.player); chest.windowId = 42;
            NBTTagCompound snapshot = (NBTTagCompound)JsonToNBT.getTagFromJson(ChestSnapshot.capture(chest).toString());
            assertEquals("§dQuick Buy", snapshot.getString("CustomName")); assertEquals(54, snapshot.getInteger("Size"));
            assertEquals("§dQuick Buy", net.minecraft.util.IChatComponent.Serializer.jsonToComponent(snapshot.getString("Title")).getUnformattedText());
            assertEquals(42, snapshot.getInteger("WindowId"));
            NBTTagList list = snapshot.getTagList("Items", 10); assertEquals(2, list.tagCount());
            assertEquals(53, list.getCompoundTagAt(1).getByte("Slot"));
            ItemStack restored = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(1));
            assertTrue(ItemStack.areItemStacksEqual(sword, restored));
            assertFalse(sword.getTagCompound().hasKey("Slot")); assertSame(sword, inventory.getStackInSlot(53));
        }
    }
}
