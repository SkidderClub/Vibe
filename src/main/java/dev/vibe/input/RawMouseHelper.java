package dev.vibe.input;

import java.util.function.BooleanSupplier;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;

/**
 * Drop-in {@link MouseHelper} replacement that feeds unaccelerated hardware
 * deltas from {@link RawMouseInputController} into Minecraft's camera loop.
 *
 * <p>Hardware Y is positive downward while Minecraft/LWJGL {@code deltaY} is
 * positive upward, so vertical deltas are negated when applied.
 */
public final class RawMouseHelper extends MouseHelper {

    private final MouseHelper delegate;
    private final RawMouseInputController controller;
    private final BooleanSupplier fallbackSupplier;
    private final Runnable lwjglDrain;
    private final int[] scratch = new int[2];

    public RawMouseHelper(MouseHelper delegate, RawMouseInputController controller, BooleanSupplier fallbackSupplier) {
        this(delegate, controller, fallbackSupplier, RawMouseHelper::drainLwjglMouse);
    }

    RawMouseHelper(MouseHelper delegate, RawMouseInputController controller,
            BooleanSupplier fallbackSupplier, Runnable lwjglDrain) {
        this.delegate = delegate;
        this.controller = controller;
        this.fallbackSupplier = fallbackSupplier == null ? () -> true : fallbackSupplier;
        this.lwjglDrain = lwjglDrain == null ? () -> { } : lwjglDrain;
    }

    public MouseHelper getDelegate() {
        return delegate;
    }

    @Override
    public void grabMouseCursor() {
        if (delegate != null) {
            delegate.grabMouseCursor();
        } else {
            super.grabMouseCursor();
        }
        controller.flush();
        lwjglDrain.run();
        this.deltaX = 0;
        this.deltaY = 0;
    }

    @Override
    public void ungrabMouseCursor() {
        if (delegate != null) {
            delegate.ungrabMouseCursor();
        } else {
            super.ungrabMouseCursor();
        }
        controller.flush();
        this.deltaX = 0;
        this.deltaY = 0;
    }

    @Override
    public void mouseXYChange() {
        if (controller.consumeDelta(scratch)) {
            // Keep LWJGL's internal cursor delta drained so disabling RawMouseInput never snaps.
            lwjglDrain.run();
            this.deltaX = scratch[0];
            this.deltaY = -scratch[1];
            return;
        }
        if (fallbackSupplier.getAsBoolean()) {
            if (delegate != null) {
                delegate.mouseXYChange();
                this.deltaX = delegate.deltaX;
                this.deltaY = delegate.deltaY;
            } else {
                super.mouseXYChange();
            }
            return;
        }
        lwjglDrain.run();
        this.deltaX = 0;
        this.deltaY = 0;
    }

    private static void drainLwjglMouse() {
        try {
            if (Mouse.isCreated()) {
                Mouse.getDX();
                Mouse.getDY();
            }
        } catch (Throwable ignored) {
        }
    }
}
