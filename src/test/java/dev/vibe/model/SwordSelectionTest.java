package dev.vibe.model;

import dev.vibe.module.impl.visual.CustomModelRendererModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.Setting;
import java.lang.reflect.Field;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.Test;
import static org.junit.Assert.*;

public class SwordSelectionTest {
    @Test public void eachSwordHasItsOwnKnifeWithGlobalAndVanillaFallbacks() throws Exception {
        Minecraft previous=Minecraft.getMinecraft();
        try {
            Field registered=field(net.minecraft.init.Bootstrap.class,"alreadyRegistered");
            if(!registered.getBoolean(null)){registered.setBoolean(null,true);net.minecraft.block.Block.registerBlocks();net.minecraft.item.Item.registerItems();}
            Minecraft minecraft=allocate(Minecraft.class);field(Minecraft.class,"theMinecraft").set(null,minecraft);
            field(Minecraft.class,"mcDataDir").set(minecraft,Files.createTempDirectory("vibe-sword-mappings").toFile());
            CustomModelRendererModule module=new CustomModelRendererModule();
            ((BooleanSetting)setting(module,"Per Sword Models")).setEnabled(true);
            ((ModeSetting)setting(module,"Wooden Sword")).setValue("Vanilla");
            ModeSetting diamond=(ModeSetting)setting(module,"Diamond Sword");
            String knife=diamond.getModes().get(2);diamond.setValue(knife);
            assertNull(module.swordSelection(new ItemStack(Items.wooden_sword)));
            assertEquals(knife,module.swordSelection(new ItemStack(Items.diamond_sword)));
            assertEquals(module.getSwordModel().getValue(),module.swordSelection(new ItemStack(Items.iron_sword)));
            ((BooleanSetting)setting(module,"Per Sword Models")).setEnabled(false);
            assertEquals(module.getSwordModel().getValue(),module.swordSelection(new ItemStack(Items.wooden_sword)));
            assertFalse(module.getInventorySwords().isEnabled()); assertFalse(module.getHideCape().isEnabled());
        } finally {field(Minecraft.class,"theMinecraft").set(null,previous);}
    }
    private static Setting<?> setting(CustomModelRendererModule module,String name){for(Setting<?> s:module.getSettings())if(s.getRawName().equals(name))return s;throw new AssertionError(name);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static <T>T allocate(Class<T> type)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");return type.cast(u.getMethod("allocateInstance",Class.class).invoke(field(u,"theUnsafe").get(null),type));}
}
