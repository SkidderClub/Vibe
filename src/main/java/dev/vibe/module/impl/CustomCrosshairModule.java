package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

/** Fully configurable replacement for Minecraft's first-person crosshair. */
public final class CustomCrosshairModule extends Module {

    private final ModeSetting style = addSetting(new ModeSetting("Style", "Cross", "Cross", "Cross Dot", "Dot", "Circle", "Dynamic"));
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFFFFFFFF));
    private final BooleanSetting rainbow = addSetting(new BooleanSetting("Rainbow", false));
    private final NumberSetting rainbowSpeed = addSetting(new NumberSetting("Rainbow Speed", 1.0D, 0.1D, 5.0D, 0.1D,
            () -> rainbow.isEnabled()));
    private final NumberSetting opacity = addSetting(new NumberSetting("Opacity", 255.0D, 20.0D, 255.0D, 1.0D));
    private final NumberSetting length = addSetting(new NumberSetting("Arm Length", 6.0D, 1.0D, 20.0D, 1.0D,
            () -> !style.is("Dot") && !style.is("Circle")));
    private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1.0D, 1.0D, 5.0D, 1.0D));
    private final NumberSetting gap = addSetting(new NumberSetting("Gap", 3.0D, 0.0D, 16.0D, 1.0D,
            () -> !style.is("Dot") && !style.is("Circle")));
    private final BooleanSetting centerDot = addSetting(new BooleanSetting("Center Dot", false,
            () -> !style.is("Dot") && !style.is("Cross Dot")));
    private final NumberSetting circleRadius = addSetting(new NumberSetting("Circle Radius", 7.0D, 2.0D, 32.0D, 1.0D,
            () -> style.is("Circle")));
    private final NumberSetting circleSegments = addSetting(new NumberSetting("Circle Segments", 32.0D, 12.0D, 96.0D, 2.0D,
            () -> style.is("Circle")));
    private final BooleanSetting movementGap = addSetting(new BooleanSetting("Movement Gap", true,
            () -> style.is("Dynamic")));
    private final NumberSetting movementGapAmount = addSetting(new NumberSetting("Movement Gap Amount", 5.0D, 0.0D, 16.0D, 0.5D,
            () -> style.is("Dynamic") && movementGap.isEnabled()));
    private final BooleanSetting rotate = addSetting(new BooleanSetting("Rotate", false));
    private final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rotation Speed", 1.0D, 0.1D, 6.0D, 0.1D,
            () -> rotate.isEnabled()));
    private final NumberSetting horizontalOffset = addSetting(new NumberSetting("Horizontal Offset", 0.0D, -80.0D, 80.0D, 1.0D));
    private final NumberSetting verticalOffset = addSetting(new NumberSetting("Vertical Offset", 0.0D, -80.0D, 80.0D, 1.0D));
    private final BooleanSetting outline = addSetting(new BooleanSetting("Outline", true));
    private final ColorSetting outlineColor = addSetting(new ColorSetting("Outline Color", 0xC0000000,
            () -> outline.isEnabled()));
    private final NumberSetting outlineSize = addSetting(new NumberSetting("Outline Size", 1.0D, 1.0D, 3.0D, 1.0D,
            () -> outline.isEnabled()));
    private final BooleanSetting breakCircle = addSetting(new BooleanSetting("Break Circle", true));
    private final NumberSetting breakCircleRadius = addSetting(new NumberSetting("Break Circle Radius", 12.0D, 4.0D, 40.0D, 1.0D,
            () -> breakCircle.isEnabled()));
    private final NumberSetting breakCircleWidth = addSetting(new NumberSetting("Break Circle Width", 1.5D, 1.0D, 5.0D, 0.5D,
            () -> breakCircle.isEnabled()));
    private final ColorSetting breakCircleColor = addSetting(new ColorSetting("Break Circle Color", 0xFF2DE2C2,
            () -> breakCircle.isEnabled()));

    public CustomCrosshairModule() {
        super("CustomCrosshair", "Custom first-person crosshair", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public ModeSetting getStyle() { return style; }
    public ColorSetting getColor() { return color; }
    public BooleanSetting getRainbow() { return rainbow; }
    public NumberSetting getRainbowSpeed() { return rainbowSpeed; }
    public NumberSetting getOpacity() { return opacity; }
    public NumberSetting getLength() { return length; }
    public NumberSetting getThickness() { return thickness; }
    public NumberSetting getGap() { return gap; }
    public BooleanSetting getCenterDot() { return centerDot; }
    public NumberSetting getCircleRadius() { return circleRadius; }
    public NumberSetting getCircleSegments() { return circleSegments; }
    public BooleanSetting getMovementGap() { return movementGap; }
    public NumberSetting getMovementGapAmount() { return movementGapAmount; }
    public BooleanSetting getRotate() { return rotate; }
    public NumberSetting getRotationSpeed() { return rotationSpeed; }
    public NumberSetting getHorizontalOffset() { return horizontalOffset; }
    public NumberSetting getVerticalOffset() { return verticalOffset; }
    public BooleanSetting getOutline() { return outline; }
    public ColorSetting getOutlineColor() { return outlineColor; }
    public NumberSetting getOutlineSize() { return outlineSize; }
    public BooleanSetting getBreakCircle() { return breakCircle; }
    public NumberSetting getBreakCircleRadius() { return breakCircleRadius; }
    public NumberSetting getBreakCircleWidth() { return breakCircleWidth; }
    public ColorSetting getBreakCircleColor() { return breakCircleColor; }
}
