package dev.vibe.ui.render;

import dev.vibe.module.Category;
import dev.vibe.module.impl.visual.ItemGlowModule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemGlowTest {
    @Test public void fadeIsBoundedAndCanBeDisabled() {
        ItemGlowModule glow = new ItemGlowModule();
        assertEquals(Category.VISUAL, glow.getCategory());
        assertFalse(glow.isEnabled()); assertTrue(glow.phone.isEnabled());
        glow.minimumGlow.setValue(.25D); glow.fadeSpeed.setValue(1D);
        assertEquals(1, glow.fadeAt(.25), .0001);
        assertEquals(.25, glow.fadeAt(.75), .0001);
        for (int i = 0; i < 100; i++) assertTrue(glow.fadeAt(i / 23D) >= .25 && glow.fadeAt(i / 23D) <= 1);
        glow.fade.setEnabled(false); assertEquals(1, glow.fadeAt(.75), 0);
        assertFalse(glow.minimumGlow.isVisible()); assertFalse(glow.fadeSpeed.isVisible());
    }
    @Test public void paletteHonorsStaticGradientAndAlpha() {
        ItemGlowModule glow = new ItemGlowModule(); glow.colorSpeed.setValue(0D);
        glow.color.setValue(0xCC3399FF); glow.secondaryColor.setValue(0x6655FF99);
        glow.colorMode.setValue("Static"); assertEquals(0xCC3399FF, glow.colorAt(10, .75F));
        assertFalse(glow.secondaryColor.isVisible());
        glow.colorMode.setValue("Gradient");
        assertEquals(0xCC3399FF, glow.colorAt(100, 0)); assertEquals(0x6655FF99, glow.colorAt(100, 1));
        glow.colorMode.setValue("Rainbow"); glow.colorSpeed.setValue(1D);
        assertNotEquals(glow.colorAt(0, 0), glow.colorAt(2, 0));
    }
    @Test public void qualityBoundsTheBlurAndHooksCoverBothRenderers() throws Exception {
        ItemGlowModule glow = new ItemGlowModule();
        assertEquals(3, glow.downsample()); assertEquals(960, glow.blurLimit());
        glow.quality.setValue("Performance"); assertEquals(4, glow.downsample());
        glow.quality.setValue("High"); assertEquals(2, glow.downsample());
        assertHook("dev/vibe/ui/render/AnimationItemRenderer", "renderItemInFirstPerson", "held");
        assertHook("dev/vibe/phone/PhoneCanvas", "draw", "phone");
    }
    private static void assertHook(String type, String method, String hook) throws Exception {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        try (java.io.InputStream input = ItemGlowTest.class.getResourceAsStream("/" + type + ".class")) {
            new org.objectweb.asm.ClassReader(input).accept(node, 0);
        }
        int count = 0;
        for (Object candidate : node.methods) {
            org.objectweb.asm.tree.MethodNode function = (org.objectweb.asm.tree.MethodNode) candidate;
            if (!function.name.equals(method)) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : function.instructions.toArray())
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                    org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                    if (call.owner.equals("dev/vibe/ui/render/ItemGlowRenderer") && call.name.equals(hook)) count++;
                }
        }
        assertEquals(type + " must use the shared single-draw glow path", 1, count);
    }
}
