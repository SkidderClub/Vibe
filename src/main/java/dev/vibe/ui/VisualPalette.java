package dev.vibe.ui;

/** Shared Cute Visuals and fog presets. Love keeps each effect's original rainbow. */
public final class VisualPalette {
    private VisualPalette() { }
    public static int primary(String preset, int custom) {
        switch (preset) {
            case "Custom": return custom;
            case "Red": return 0xFFFF4D4D;
            case "Green": return 0xFF55DD77;
            case "Blue": return 0xFF4477FF;
            case "Yellow": return 0xFFFFDD55;
            case "Gray": return 0xFFBBBBBB;
            case "Purple": return 0xFFB366FF;
            case "China": return 0xFFFF3333;
            case "Bavaria": return 0xFF3399FF;
            case "Catton Candy": return 0xFFFF99CC;
            default: return 0xFF88D8FF;
        }
    }
    public static int secondary(String preset, int first, int custom) {
        switch (preset) {
            case "Custom": return custom;
            case "China": return 0xFFFFDE33;
            case "Bavaria": return 0xFFFFFFFF;
            case "Catton Candy": return 0xFF88D8FF;
            default: return RenderUtils.blend(first, 0xFF000000, .24F);
        }
    }
}
