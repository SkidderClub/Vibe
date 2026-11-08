package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.core.ContainerThemeTransformer;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.util.jar.JarFile;
import net.minecraft.client.gui.inventory.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class ContainerThemeTest implements Opcodes {
    @Test public void panelSlicesExcludeProgressBarsAndNativeIcons(){
        assertTrue(ContainerThemes.panelSlice(20,30,176,166,20,30,176,166));
        assertTrue(ContainerThemes.panelSlice(20,30,176,71,20,30,176,168));assertTrue(ContainerThemes.panelSlice(20,101,176,96,20,30,176,168));
        assertFalse(ContainerThemes.panelSlice(99,64,24,17,20,30,176,166));assertFalse(ContainerThemes.panelSlice(20,30,18,18,20,30,176,166));
    }
    @Test public void developmentAndProductionBytecodeKeepNativeClicksAndAddOnlyThePanelAndLabelHooks()throws Exception{
        verify(bytes(GuiContainer.class.getResourceAsStream("GuiContainer.class")),false);
        verifyPotions(bytes(net.minecraft.client.renderer.InventoryEffectRenderer.class.getResourceAsStream("InventoryEffectRenderer.class")),false);
        java.lang.reflect.Method official=dev.vibe.combat.CombatHooksTest.class.getDeclaredMethod("officialJar");official.setAccessible(true);
        try(JarFile jar=(JarFile)official.invoke(new dev.vibe.combat.CombatHooksTest())){assertNotNull(jar.getJarEntry("ayl.class"));verify(bytes(jar.getInputStream(jar.getJarEntry("ayl.class"))),true);verifyPotions(bytes(jar.getInputStream(jar.getJarEntry("ayw.class"))),true);}
    }
    private static void verifyPotions(byte[] raw,boolean obf)throws Exception{
        String name=obf?"ayw":"net.minecraft.client.renderer.InventoryEffectRenderer";ContainerThemeTransformer transformer=new ContainerThemeTransformer();
        byte[] patched=transformer.transform(name,"net.minecraft.client.renderer.InventoryEffectRenderer",raw);
        assertArrayEquals(patched,transformer.transform(name,"net.minecraft.client.renderer.InventoryEffectRenderer",patched));
        ClassNode node=new ClassNode();new ClassReader(patched).accept(node,0);int layout=0,draw=0,forgeIcon=0;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.name.equals("updatePotionLayout"))layout++;if(call.name.equals("potionEffects"))draw++;if(call.name.equals("renderInventoryEffect"))forgeIcon++;
            }
        }
        assertEquals(1,layout);assertEquals(1,draw);assertEquals(1,forgeIcon);
    }
    @Test public void centeredEffectsLayoutIsOptionalAndOnlyAppliesToThemedInventory()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);hud.setEnabled(true);PathTestFixture.set(Vibe.class,f.vibe,"hudManager",new HudManager(new File("build/container-unit-layout")));
            GuiInventory gui=PathTestFixture.allocate(GuiInventory.class);gui.width=960;PathTestFixture.set(GuiContainer.class,gui,"xSize",176);PathTestFixture.set(GuiContainer.class,gui,"guiLeft",452);
            assertTrue(hud.containers.slots.getAlpha()<64);assertTrue(hud.containers.centerInventory.isEnabled());
            ContainerThemes.updatePotionLayout(gui);assertEquals(452,dev.vibe.ui.render.InventoryHighlightRenderer.left(gui));
            hud.getHudElements().toggle(HudManager.CUSTOM_GUIS);ContainerThemes.updatePotionLayout(gui);assertEquals(392,dev.vibe.ui.render.InventoryHighlightRenderer.left(gui));
            hud.containers.centerInventory.setEnabled(false);PathTestFixture.set(GuiContainer.class,gui,"guiLeft",452);ContainerThemes.updatePotionLayout(gui);assertEquals(452,dev.vibe.ui.render.InventoryHighlightRenderer.left(gui));
            hud.containers.centerInventory.setEnabled(true);hud.containers.screens.toggle("Inventory");ContainerThemes.updatePotionLayout(gui);assertEquals(452,dev.vibe.ui.render.InventoryHighlightRenderer.left(gui));
            assertFalse(ContainerThemes.potionEffects(gui));
        }
    }
    private static void verify(byte[] raw,boolean obf)throws Exception{
        ContainerThemeTransformer transformer=new ContainerThemeTransformer();byte[] patched=transformer.transform(obf?"ayl":"net.minecraft.client.gui.inventory.GuiContainer","net.minecraft.client.gui.inventory.GuiContainer",raw);
        assertArrayEquals(patched,transformer.transform(obf?"ayl":"net.minecraft.client.gui.inventory.GuiContainer","net.minecraft.client.gui.inventory.GuiContainer",patched));
        ClassNode before=new ClassNode(),after=new ClassNode();new ClassReader(raw).accept(before,0);new ClassReader(patched).accept(after,0);
        int backdrop=0,foreground=0;for(Object entry:after.methods){MethodNode method=(MethodNode)entry;new Analyzer(new BasicVerifier()).analyze(after.name,method);
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;if(call.owner.equals("dev/vibe/hud/ContainerThemes")){if(call.name.equals("backdrop"))backdrop++;if(call.name.equals("foreground"))foreground++;}}
            if(method.name.equals(obf?"a":"mouseClicked")&&method.desc.equals("(III)V")){
                MethodNode original=null;for(Object previous:before.methods){MethodNode candidate=(MethodNode)previous;if(candidate.name.equals(method.name)&&candidate.desc.equals(method.desc))original=candidate;}
                assertNotNull(original);assertEquals(original.instructions.size(),method.instructions.size());
            }
        }
        assertEquals(1,backdrop);assertEquals(1,foreground);
    }
    @Test public void nativeForegroundDelegatesAndOnlyItsDarkLabelColorChanges()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);hud.setEnabled(true);HudManager manager=new HudManager(new File("build/container-unit-layout"));PathTestFixture.set(Vibe.class,f.vibe,"hudManager",manager);
            NativeInventory gui=PathTestFixture.allocate(NativeInventory.class);
            ContainerThemes.foreground(gui,4,8);assertEquals(0x404040,gui.color);assertEquals(1,gui.calls);
            hud.getHudElements().toggle(HudManager.CUSTOM_GUIS);hud.containers.text.setValue(0xFFE8CA95);
            ContainerThemes.foreground(gui,4,8);assertEquals(0xFFE8CA95,gui.color);assertEquals(2,gui.calls);assertEquals(0x404040,ContainerThemes.fontColor(0x404040));
            hud.containers.screens.toggle("Inventory");ContainerThemes.foreground(gui,4,8);assertEquals(0x404040,gui.color);
        }
    }
    public static class NativeInventory extends GuiInventory{
        int calls,color;private NativeInventory(){super(null);}
        @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){calls++;color=ContainerThemes.fontColor(0x404040);}
    }
    private static byte[] bytes(InputStream in)throws IOException{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=input.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
}
