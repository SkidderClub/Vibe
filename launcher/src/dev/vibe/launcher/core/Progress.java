package dev.vibe.launcher.core;

/** Receives progress from long-running tasks. A fraction below zero means "indeterminate". */
public interface Progress {
    void update(String message, String detail, double fraction);

    Progress NONE = new Progress() {
        @Override public void update(String message, String detail, double fraction) { }
    };

    /** Maps the sub-task's 0..1 onto {@code [from, to]} of this progress. */
    default Progress range(final double from, final double to) {
        final Progress parent = this;
        return new Progress() {
            @Override public void update(String message, String detail, double fraction) {
                parent.update(message, detail, fraction < 0 ? -1 : from + (to - from) * Math.max(0, Math.min(1, fraction)));
            }
        };
    }

    /** Converts byte counts from {@link Http} into progress updates. */
    default Http.Progress bytes(final String message) {
        final Progress parent = this;
        return new Http.Progress() {
            @Override public void transferred(long bytes, long total) {
                String detail = total > 0 ? Text.bytes(bytes) + " / " + Text.bytes(total) : Text.bytes(bytes);
                parent.update(message, detail, total > 0 ? (double) bytes / total : -1);
            }
        };
    }
}
