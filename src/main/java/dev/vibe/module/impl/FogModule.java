/* Copyright (c) 2024. Schizoid. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Ported from ModuleToggleableBlur.kt; modified for Vibe/Forge 1.8.9 on 2026-09-09.
 * See LICENSES/SCHIZOID.md for the pinned source and changes.
 */
package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import org.lwjgl.input.Keyboard;

/** Depth-dependent scene blur and tint, shared by Minecraft and GTA7. */
public final class FogModule extends Module {
    public final BooleanSetting blur = addSetting(new BooleanSetting("Blur", true));
    public final ModeSetting method = addSetting(new ModeSetting("Blur Method", "Kawase", () -> blur.isEnabled(), "Kawase", "Gaussian"));
    public final NumberSetting strength = addSetting(new NumberSetting("Blur Strength", 6, 1, 20, 1, () -> blur.isEnabled()));
    public final NumberSetting start = addSetting(new NumberSetting("Start Distance", 12, 0, 256, 1));
    public final NumberSetting end = addSetting(new NumberSetting("End Distance", 64, 1, 512, 1));
    public final NumberSetting opacity = addSetting(new NumberSetting("Fog Opacity", 100, 0, 100, 1));
    public final ModeSetting tint = addSetting(new ModeSetting("Color Mode", "Scene", "Scene", "Custom", "Rainbow"));
    public final ColorSetting color = addSetting(new ColorSetting("Fog Color", 0xFFB8CBD6, () -> tint.is("Custom")));
    public final NumberSetting tintOpacity = addSetting(new NumberSetting("Color Opacity", 30, 0, 100, 1, () -> !tint.is("Scene")));
    public final NumberSetting saturation = addSetting(new NumberSetting("Rainbow Saturation", 70, 0, 100, 1, () -> tint.is("Rainbow")));
    public final NumberSetting brightness = addSetting(new NumberSetting("Rainbow Brightness", 100, 0, 100, 1, () -> tint.is("Rainbow")));
    public final NumberSetting speed = addSetting(new NumberSetting("Rainbow Speed", 1, 0, 5, .05, () -> tint.is("Rainbow")));
    public final BooleanSetting sky = addSetting(new BooleanSetting("Affect Sky", true));
    public final BooleanSetting vanilla = addSetting(new BooleanSetting("Keep Vanilla Fog", false));

    public FogModule() { super("Fog", "Depth fog with blur, custom colors and animated rainbow", Category.VISUAL, Keyboard.KEY_NONE); }
    public float endDistance() { return Math.max(start.getFloat() + .1f, end.getFloat()); }
}
