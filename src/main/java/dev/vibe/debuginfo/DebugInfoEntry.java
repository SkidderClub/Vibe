package dev.vibe.debuginfo;

/** Single formatted telemetry row rendered by {@link dev.vibe.ui.render.DebugInfoRenderer}. */
public final class DebugInfoEntry {

    public final String label;
    public final String value;
    public final int valueColor;

    public DebugInfoEntry(String label, String value, int valueColor) {
        this.label = label;
        this.value = value;
        this.valueColor = valueColor;
    }
}
