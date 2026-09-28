package dev.vibe.launcher.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * One shared 60 fps timer for small UI transitions. It only runs while a
 * transition is in progress, so an idle launcher does not repaint at all.
 */
final class Anim {
    /** A value that eases towards its target and repaints its owner while moving. */
    static final class Value {
        private final Component owner;
        private final double speed;
        double value, target;

        Value(Component owner, double initial, double speed) {
            this.owner = owner;
            this.value = initial;
            this.target = initial;
            this.speed = speed;
        }

        void to(double next) {
            if (next == target) return;
            target = next;
            if (!Anim.enabled) { value = next; owner.repaint(); return; }
            start(this);
        }

        void set(double next) { value = next; target = next; owner.repaint(); }

        boolean step(double seconds) {
            double delta = target - value;
            if (Math.abs(delta) < 0.002) { value = target; owner.repaint(); return false; }
            value += delta * Math.min(1, seconds * speed);
            owner.repaint();
            return true;
        }
    }

    static volatile boolean enabled = true;
    private static final List<Value> ACTIVE = new ArrayList<Value>();
    private static long last;
    private static final Timer TIMER = new Timer(16, event -> tick());

    private Anim() { }

    private static void start(Value value) {
        if (!ACTIVE.contains(value)) ACTIVE.add(value);
        if (!TIMER.isRunning()) {
            last = System.nanoTime();
            TIMER.start();
        }
    }

    private static void tick() {
        long now = System.nanoTime();
        double seconds = Math.min(0.1, (now - last) / 1e9);
        last = now;
        for (Value value : new ArrayList<Value>(ACTIVE)) if (!value.step(seconds)) ACTIVE.remove(value);
        if (ACTIVE.isEmpty()) TIMER.stop();
    }
}
