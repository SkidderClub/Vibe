package dev.vibe.inventory;

import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.IChatComponent;

/** Exports exactly the chest data received by the client, including complete item tags. */
public final class ChestSnapshot {
    private ChestSnapshot() { }
    public static NBTTagCompound capture(ContainerChest container) {
        IInventory inventory = container.getLowerChestInventory();
        NBTTagCompound root = new NBTTagCompound();
        if (inventory instanceof net.minecraft.tileentity.TileEntity)
            ((net.minecraft.tileentity.TileEntity)inventory).writeToNBT(root);
        if (!root.hasKey("id")) root.setString("id", "minecraft:chest");
        root.setString("CustomName", inventory.getName());
        root.setString("Title", IChatComponent.Serializer.componentToJson(inventory.getDisplayName()));
        root.setInteger("Size", inventory.getSizeInventory());
        root.setInteger("WindowId", container.windowId);
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null) continue;
            NBTTagCompound item = stack.writeToNBT(new NBTTagCompound());
            item.setByte("Slot", (byte)slot); items.appendTag(item);
        }
        root.setTag("Items", items);
        NBTTagList fields = new NBTTagList();
        for (int i = 0; i < inventory.getFieldCount(); i++) {
            NBTTagCompound field = new NBTTagCompound();
            field.setInteger("Id", i); field.setInteger("Value", inventory.getField(i)); fields.appendTag(field);
        }
        root.setTag("Fields", fields);
        return root;
    }
}
