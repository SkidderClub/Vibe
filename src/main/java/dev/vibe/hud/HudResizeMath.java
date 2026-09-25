package dev.vibe.hud;

/** Stable scale for a bottom-right resize drag, measured from the drag start. */
final class HudResizeMath {
    private HudResizeMath() { }

    static float fromDrag(float startScale, int startWidth, int startHeight, float deltaX, float deltaY) {
        float width = Math.max(1, startWidth), height = Math.max(1, startHeight);
        float factor = 1.0F + (deltaX * width + deltaY * height) / (width * width + height * height);
        return Math.max(.50F, Math.min(2.00F, startScale * factor));
    }
}
