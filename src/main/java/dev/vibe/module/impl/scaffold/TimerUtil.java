/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;

public class TimerUtil {
    private long lastMS = System.currentTimeMillis();

    public boolean hasTimeReached(long ms, boolean autoReset) {
        if (System.currentTimeMillis() >= this.lastMS + ms) {
            if (autoReset) {
                this.reset();
            }
            return true;
        }
        return false;
    }

    public boolean hasTimeReached(long ms) {
        return this.hasTimeReached(ms, false);
    }

    public void reset() {
        this.lastMS = System.currentTimeMillis();
    }

    public long getTime() {
        return System.currentTimeMillis() - this.lastMS;
    }
}

