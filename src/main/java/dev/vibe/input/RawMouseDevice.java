package dev.vibe.input;

/** Minimal abstraction over a single hardware mouse device for raw delta polling. */
public interface RawMouseDevice {

    /** Human-readable device name reported by the underlying input driver. */
    String getName();

    /**
     * Polls fresh hardware events from the device.
     *
     * @return {@code false} if the device has disconnected or can no longer be polled.
     */
    boolean poll();

    /** Relative horizontal delta from the most recent {@link #poll()} call (positive right). */
    float getDeltaX();

    /** Relative vertical delta from the most recent {@link #poll()} call (positive down). */
    float getDeltaY();

    /** Requests a larger native/driver event queue when supported by the backend. */
    void setEventQueueSize(int size);
}
