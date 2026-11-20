package dev.vibe.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Polls hardware mouse devices on a lightweight daemon thread and accumulates
 * unaccelerated raw deltas for {@link RawMouseHelper}.
 *
 * <p>Discovery and polling are serialized so hardware enumeration never blocks
 * Minecraft's render thread and never races with device polling.
 */
public final class RawMouseInputController {

    private static final long MIN_MANUAL_RESCAN_NANOS = 1_500_000_000L;
    private static final long ZERO_DELAY_PARK_NANOS = 250_000L;

    public static final class ScanResult {
        public final RawMouseProvider.Backend backend;
        public final int deviceCount;
        public final List<String> deviceNames;
        public final boolean manual;

        ScanResult(RawMouseProvider.Backend backend, List<String> deviceNames, boolean manual) {
            this.backend = backend;
            this.deviceNames = Collections.unmodifiableList(new ArrayList<String>(deviceNames));
            this.deviceCount = this.deviceNames.size();
            this.manual = manual;
        }
    }

    private final RawMouseProvider provider;
    private final BooleanSupplier captureSupplier;

    private final Object lifecycleLock = new Object();
    private final Object deltaLock = new Object();

    private final List<RawMouseDevice> devices = new ArrayList<RawMouseDevice>();
    private RawMouseDevice lockedDevice;

    private double pendingX;
    private double pendingY;

    private volatile boolean running;
    private volatile boolean initialized;
    private volatile boolean lockActive;
    private volatile int pollDelayMs = 1;
    private volatile boolean discardNextPoll = true;
    private volatile int activeDeviceCount;
    private volatile String activeDeviceName = "";

    private volatile RawMouseProvider.Backend activeBackend = RawMouseProvider.Backend.AUTO;
    private volatile RawMouseProvider.Backend requestedBackend = RawMouseProvider.Backend.AUTO;
    private volatile boolean rescanRequested;
    private volatile boolean rescanManual;
    private volatile Consumer<ScanResult> rescanCallback;
    private volatile long lastManualScanNanos;

    private boolean wasCapturing;
    private Thread workerThread;

    public RawMouseInputController(RawMouseProvider provider, BooleanSupplier captureSupplier) {
        this.provider = provider == null ? new JInputMouseProvider() : provider;
        this.captureSupplier = captureSupplier == null ? () -> true : captureSupplier;
    }

    /** Starts the background polling thread and schedules initial discovery if needed. */
    public void start(RawMouseProvider.Backend backend) {
        RawMouseProvider.Backend target = backend == null ? RawMouseProvider.Backend.AUTO : backend;
        synchronized (lifecycleLock) {
            requestedBackend = target;
            flush();
            if (!initialized || target != activeBackend) {
                rescanRequested = true;
                rescanManual = false;
            }
            if (running && workerThread != null && workerThread.isAlive()) {
                LockSupport.unpark(workerThread);
                return;
            }
            running = true;
            workerThread = new Thread(this::runLoop, "Vibe-RawMouseInput");
            workerThread.setDaemon(true);
            workerThread.setPriority(Thread.NORM_PRIORITY + 1);
            workerThread.start();
        }
    }

    /** Stops the background polling thread without destroying cached JInput devices. */
    public void stop() {
        Thread thread;
        synchronized (lifecycleLock) {
            running = false;
            thread = workerThread;
            workerThread = null;
        }
        flush();
        if (thread != null) {
            thread.interrupt();
            LockSupport.unpark(thread);
        }
    }

    /** Stops polling and forgets discovered devices. */
    public void shutdown() {
        stop();
        synchronized (lifecycleLock) {
            devices.clear();
            lockedDevice = null;
            activeDeviceCount = 0;
            activeDeviceName = "";
            initialized = false;
        }
    }

    /** Requests a device rescan on the polling thread (or immediately if not running). */
    public boolean requestRescan(RawMouseProvider.Backend backend, boolean manual, Consumer<ScanResult> callback) {
        RawMouseProvider.Backend target = backend == null ? activeBackend : backend;
        long now = System.nanoTime();
        if (manual && initialized && target == activeBackend
                && lastManualScanNanos != 0L && (now - lastManualScanNanos) < MIN_MANUAL_RESCAN_NANOS) {
            return false;
        }
        synchronized (lifecycleLock) {
            requestedBackend = target;
            rescanManual = manual;
            rescanCallback = callback;
            rescanRequested = true;
            if (running && workerThread != null && workerThread.isAlive()) {
                LockSupport.unpark(workerThread);
            }
        }
        return true;
    }

    public void setLockActive(boolean lockActive) {
        boolean previous = this.lockActive;
        this.lockActive = lockActive;
        if (previous && !lockActive) {
            synchronized (lifecycleLock) {
                lockedDevice = null;
                updateActiveSummaryLocked();
            }
        }
    }

    public boolean isLockActive() {
        return lockActive;
    }

    public void setPollDelayMs(int pollDelayMs) {
        this.pollDelayMs = Math.max(0, Math.min(50, pollDelayMs));
    }

    public int getPollDelayMs() {
        return pollDelayMs;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean hasActiveDevices() {
        return activeDeviceCount > 0;
    }

    public int getActiveDeviceCount() {
        return activeDeviceCount;
    }

    public String getActiveDeviceName() {
        return activeDeviceName;
    }

    public RawMouseProvider.Backend getActiveBackend() {
        return activeBackend;
    }

    /** Clears pending deltas and discards the next poll cycle to prevent cursor-grab snaps. */
    public void flush() {
        synchronized (deltaLock) {
            pendingX = 0.0D;
            pendingY = 0.0D;
        }
        discardNextPoll = true;
    }

    /**
     * Atomically extracts whole-unit accumulated deltas (preserving any fractional
     * remainder) into {@code outXY}.
     *
     * @param outXY length >= 2 array receiving {@code [dx, dy]} in hardware space
     * @return {@code true} if raw input is ready and wrote deltas, {@code false} if no raw device is active
     */
    public boolean consumeDelta(int[] outXY) {
        if (outXY == null || outXY.length < 2 || !hasActiveDevices()) {
            return false;
        }
        synchronized (deltaLock) {
            int dx = (int) pendingX;
            int dy = (int) pendingY;
            pendingX -= dx;
            pendingY -= dy;
            outXY[0] = dx;
            outXY[1] = dy;
        }
        return true;
    }

    /** Executes a synchronous device scan. Exposed package-private for deterministic unit tests. */
    public ScanResult scanNow(RawMouseProvider.Backend backend, boolean forceRefresh, boolean manual) {
        RawMouseProvider.Backend target = backend == null ? RawMouseProvider.Backend.AUTO : backend;
        List<RawMouseDevice> discovered;
        try {
            discovered = provider.discover(target, forceRefresh);
        } catch (Throwable ignored) {
            discovered = Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        synchronized (lifecycleLock) {
            devices.clear();
            lockedDevice = null;
            if (discovered != null) {
                for (RawMouseDevice device : discovered) {
                    if (device != null) {
                        devices.add(device);
                        names.add(device.getName());
                    }
                }
            }
            activeBackend = target;
            initialized = true;
            if (manual) {
                lastManualScanNanos = System.nanoTime();
            }
            updateActiveSummaryLocked();
        }
        flush();
        return new ScanResult(target, names, manual);
    }

    /**
     * Runs a single poll pass over discovered devices. Exposed for deterministic unit testing
     * without spawning background threads.
     */
    public void pollOnce() {
        boolean capturing = safeCaptureState();
        boolean discard = discardNextPoll || !capturing || !wasCapturing;
        discardNextPoll = false;
        wasCapturing = capturing;

        double frameDx = 0.0D;
        double frameDy = 0.0D;

        synchronized (lifecycleLock) {
            if (devices.isEmpty()) {
                if (activeDeviceCount != 0) {
                    updateActiveSummaryLocked();
                }
                if (!capturing) {
                    clearPendingDeltas();
                }
                return;
            }

            if (lockActive && lockedDevice != null) {
                if (!lockedDevice.poll()) {
                    devices.remove(lockedDevice);
                    lockedDevice = null;
                    updateActiveSummaryLocked();
                } else {
                    float dx = sanitize(lockedDevice.getDeltaX());
                    float dy = sanitize(lockedDevice.getDeltaY());
                    if (!discard) {
                        frameDx += dx;
                        frameDy += dy;
                    }
                    // Keep other devices drained so their ring buffers do not overflow if we unlock later.
                    for (RawMouseDevice other : devices) {
                        if (other != lockedDevice) {
                            other.poll();
                        }
                    }
                }
            }

            if (!lockActive || lockedDevice == null) {
                Iterator<RawMouseDevice> iterator = devices.iterator();
                boolean removed = false;
                while (iterator.hasNext()) {
                    RawMouseDevice device = iterator.next();
                    if (!device.poll()) {
                        iterator.remove();
                        removed = true;
                        continue;
                    }
                    float dx = sanitize(device.getDeltaX());
                    float dy = sanitize(device.getDeltaY());
                    if (!discard && lockActive && (dx != 0.0F || dy != 0.0F)) {
                        lockedDevice = device;
                        updateActiveSummaryLocked();
                        frameDx += dx;
                        frameDy += dy;
                        // Drain remaining devices for this pass and stop summing others.
                        while (iterator.hasNext()) {
                            RawMouseDevice remaining = iterator.next();
                            if (!remaining.poll()) {
                                iterator.remove();
                                removed = true;
                            }
                        }
                        break;
                    }
                    if (!discard) {
                        frameDx += dx;
                        frameDy += dy;
                    }
                }
                if (removed) {
                    updateActiveSummaryLocked();
                }
            }
        }

        if (!capturing) {
            clearPendingDeltas();
            return;
        }
        if (!discard && (frameDx != 0.0D || frameDy != 0.0D)) {
            synchronized (deltaLock) {
                pendingX += frameDx;
                pendingY += frameDy;
            }
        }
    }

    private void runLoop() {
        while (running) {
            if (rescanRequested) {
                RawMouseProvider.Backend target;
                boolean manual;
                Consumer<ScanResult> callback;
                synchronized (lifecycleLock) {
                    rescanRequested = false;
                    target = requestedBackend;
                    manual = rescanManual;
                    callback = rescanCallback;
                    rescanCallback = null;
                }
                ScanResult result = scanNow(target, initialized, manual);
                if (callback != null) {
                    try {
                        callback.accept(result);
                    } catch (Throwable ignored) {
                    }
                }
            }

            pollOnce();

            int delay = pollDelayMs;
            if (delay <= 0) {
                LockSupport.parkNanos(ZERO_DELAY_PARK_NANOS);
            } else {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                    // Woken up by stop() or requestRescan().
                }
            }
        }
    }

    private boolean safeCaptureState() {
        try {
            return captureSupplier.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void clearPendingDeltas() {
        synchronized (deltaLock) {
            pendingX = 0.0D;
            pendingY = 0.0D;
        }
    }

    private void updateActiveSummaryLocked() {
        activeDeviceCount = devices.size();
        if (lockedDevice != null) {
            activeDeviceName = lockedDevice.getName();
        } else if (devices.isEmpty()) {
            activeDeviceName = "";
        } else if (devices.size() == 1) {
            activeDeviceName = devices.get(0).getName();
        } else {
            activeDeviceName = devices.size() + " mice";
        }
    }

    private static float sanitize(float value) {
        return (Float.isNaN(value) || Float.isInfinite(value)) ? 0.0F : value;
    }
}
