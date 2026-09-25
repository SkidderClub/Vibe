package dev.vibe.command;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.StringSetting;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleSettingCommandsTest {
    private static final class Example extends Module {
        final BooleanSetting enabled = addSetting(new BooleanSetting("Check Blocks", false));
        final NumberSetting speed = addSetting(new NumberSetting("Speed", 2, 0, 10, 0.5));
        final RangeSetting delay = addSetting(new RangeSetting("Delay", 2, 5, 0, 10, 1));
        final ModeSetting mode = addSetting(new ModeSetting("Style", "Basic", "Basic", "Fast Place"));
        final MultiSelectSetting effects = addSetting(new MultiSelectSetting("Effects", Arrays.asList("Glow", "Soft Blur"), Arrays.asList("Glow")));
        final StringSetting caption = addSetting(new StringSetting("Caption", "hello", 12, null));
        final ColorSetting color = addSetting(new ColorSetting("Color", 0xFFFFFFFF));
        Example() { super("Example", "", Category.CLIENT, 0); }
    }

    @Test public void changesTypedSettingsAndRejectsInvalidValues() {
        Example module = new Example();
        assertTrue(ModuleSettingCommands.apply(module, "check blocks on").changed);
        assertTrue(module.enabled.isEnabled());
        assertFalse(ModuleSettingCommands.apply(module, "check blocks true").changed);
        assertFalse(ModuleSettingCommands.apply(module, "speed nope").changed);
        assertEquals(2, module.speed.getDouble(), 0);
        assertFalse(ModuleSettingCommands.apply(module, "speed 11").changed);
        assertTrue(ModuleSettingCommands.apply(module, "speed 3.2").changed);
        assertEquals(3, module.speed.getDouble(), 0);
        assertFalse(ModuleSettingCommands.apply(module, "style unknown").changed);
        assertTrue(ModuleSettingCommands.apply(module, "style fast place").changed);
        assertEquals("Fast Place", module.mode.getValue());
        assertTrue(ModuleSettingCommands.apply(module, "caption hello world").changed);
        assertEquals("hello world", module.caption.getValue());
        assertFalse(ModuleSettingCommands.apply(module, "caption this text is too long").changed);
        assertEquals("hello world", module.caption.getValue());
        assertTrue(ModuleSettingCommands.apply(module, "color #10203040").changed);
        assertEquals("#10203040", module.color.getHex());
        assertFalse(ModuleSettingCommands.apply(module, "color #oops").changed);
    }

    @Test public void rangeAndMultiSelectCanUseShortAndNamedForms() {
        Example module = new Example();
        assertTrue(ModuleSettingCommands.apply(module, "min 3").changed);
        assertEquals(3, module.delay.getMin(), 0);
        assertTrue(ModuleSettingCommands.apply(module, "delay max 6").changed);
        assertEquals(6, module.delay.getMax(), 0);
        assertFalse(ModuleSettingCommands.apply(module, "delay max infinity").changed);
        assertTrue(ModuleSettingCommands.apply(module, "soft blur").changed);
        assertTrue(module.effects.isSelected("Soft Blur"));
        assertTrue(ModuleSettingCommands.apply(module, "effects soft blur").changed);
        assertFalse(module.effects.isSelected("Soft Blur"));
        assertFalse(ModuleSettingCommands.apply(module, "effects glow true").changed);
    }

    @Test public void ambiguousRangeRequiresItsSettingName() {
        Example module = new Example();
        RangeSetting second = new RangeSetting("Other Delay", 1, 3, 0, 10, 1);
        Module withTwoRanges = new Module("Two Ranges", "", Category.CLIENT, 0) {
            { addSetting(module.delay); addSetting(second); }
        };
        assertFalse(ModuleSettingCommands.apply(withTwoRanges, "min 4").changed);
        assertEquals(2, module.delay.getMin(), 0);
        assertTrue(ModuleSettingCommands.apply(withTwoRanges, "other delay min 4").changed);
        assertEquals(4, second.getMin(), 0);
    }

    @Test public void suggestionsFollowSettingAndValuePosition() {
        Example module = new Example();
        assertTrue(ModuleSettingCommands.suggestions(module, ".example che", "che").contains(".example Check Blocks"));
        assertTrue(ModuleSettingCommands.suggestions(module, ".example Style f", "Style f").contains(".example Style Fast Place"));
        assertTrue(ModuleSettingCommands.suggestions(module, ".example Effects so", "Effects so").contains(".example Effects Soft Blur"));
        assertTrue(ModuleSettingCommands.suggestions(module, ".example Delay ", "Delay ").contains(".example Delay min"));
        assertTrue(ModuleSettingCommands.suggestions(module, ".example Delay max ", "Delay max ").contains(".example Delay max 10"));
        assertTrue(ModuleSettingCommands.suggestions(module, ".example min ", "min ").contains(".example min 10"));
        assertTrue(ModuleSettingCommands.suggestions(module, ".example so", "so").contains(".example Soft Blur"));
    }
}
