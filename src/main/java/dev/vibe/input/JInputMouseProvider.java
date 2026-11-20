package dev.vibe.input;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;
import net.java.games.input.RawInputEnvironmentPlugin;

/**
 * Discovers raw hardware mice using Minecraft 1.8.9's bundled JInput runtime.
 *
 * <p>On Windows, {@link RawInputEnvironmentPlugin} reads {@code WM_INPUT} /
 * {@code GetRawInputData} directly via {@code jinput-raw}, avoiding the slow
 * DirectInput gamepad enumeration that often stalls startup on modern systems.
 */
public final class JInputMouseProvider implements RawMouseProvider {

    /**
     * JInput's {@code RawDevice} defaults to a 32-event ring buffer, which drops
     * motion packets on 1000Hz-8000Hz mice during short pauses. Expanding it
     * prevents silent packet loss.
     */
    static final int EVENT_QUEUE_SIZE = 2048;

    private final String osName;

    public JInputMouseProvider() {
        this(System.getProperty("os.name", ""));
    }

    JInputMouseProvider(String osName) {
        this.osName = osName == null ? "" : osName;
    }

    @Override
    public List<RawMouseDevice> discover(Backend backend, boolean forceRefresh) {
        Backend selected = backend == null ? Backend.AUTO : backend;
        if (selected == Backend.WINDOWS_RAW) {
            return collectMice(createWindowsRawEnvironment());
        }
        if (selected == Backend.DEFAULT) {
            return collectMice(createDefaultEnvironment(forceRefresh));
        }

        // AUTO: prefer pure Windows Raw Input on Windows, then fall back to platform defaults.
        if (isWindows()) {
            List<RawMouseDevice> rawMice = collectMice(createWindowsRawEnvironment());
            if (!rawMice.isEmpty()) {
                return rawMice;
            }
        }
        return collectMice(createDefaultEnvironment(forceRefresh));
    }

    private boolean isWindows() {
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private ControllerEnvironment createWindowsRawEnvironment() {
        try {
            RawInputEnvironmentPlugin plugin = new RawInputEnvironmentPlugin();
            return plugin.isSupported() ? plugin : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ControllerEnvironment createDefaultEnvironment(boolean forceRefresh) {
        if (forceRefresh) {
            ControllerEnvironment fresh = createFreshDefaultEnvironment();
            if (fresh != null) {
                return fresh;
            }
        }
        try {
            return ControllerEnvironment.getDefaultEnvironment();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * {@link ControllerEnvironment#getDefaultEnvironment()} caches its controller
     * array forever in JInput 2.0.5. Constructing a fresh instance lets manual
     * rescans pick up newly plugged mice.
     */
    private ControllerEnvironment createFreshDefaultEnvironment() {
        try {
            Class<?> type = Class.forName("net.java.games.input.DefaultControllerEnvironment");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            if (instance instanceof ControllerEnvironment) {
                return (ControllerEnvironment) instance;
            }
        } catch (Throwable ignored) {
            // Fall back to the cached default environment below.
        }
        return null;
    }

    private List<RawMouseDevice> collectMice(ControllerEnvironment environment) {
        if (environment == null) {
            return Collections.emptyList();
        }
        Controller[] controllers;
        try {
            controllers = environment.getControllers();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        if (controllers == null || controllers.length == 0) {
            return Collections.emptyList();
        }
        List<RawMouseDevice> mice = new ArrayList<RawMouseDevice>();
        for (Controller controller : controllers) {
            if (controller == null || controller.getType() != Controller.Type.MOUSE || !(controller instanceof Mouse)) {
                continue;
            }
            Mouse mouse = (Mouse) controller;
            Component xAxis = mouse.getX();
            Component yAxis = mouse.getY();
            if (xAxis == null || yAxis == null) {
                continue;
            }
            JInputMouseDevice device = new JInputMouseDevice(mouse, xAxis, yAxis);
            device.setEventQueueSize(EVENT_QUEUE_SIZE);
            // Drain any stale startup delta already sitting in the driver queue.
            device.poll();
            mice.add(device);
        }
        return mice;
    }

    private static final class JInputMouseDevice implements RawMouseDevice {
        private final Mouse mouse;
        private final Component xAxis;
        private final Component yAxis;
        private final String name;

        JInputMouseDevice(Mouse mouse, Component xAxis, Component yAxis) {
            this.mouse = mouse;
            this.xAxis = xAxis;
            this.yAxis = yAxis;
            String rawName = mouse.getName();
            this.name = (rawName == null || rawName.trim().isEmpty()) ? "Mouse" : rawName.trim();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean poll() {
            try {
                return mouse.poll();
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public float getDeltaX() {
            try {
                return xAxis.getPollData();
            } catch (Throwable ignored) {
                return 0.0F;
            }
        }

        @Override
        public float getDeltaY() {
            try {
                return yAxis.getPollData();
            } catch (Throwable ignored) {
                return 0.0F;
            }
        }

        @Override
        public void setEventQueueSize(int size) {
            try {
                mouse.setEventQueueSize(size);
            } catch (Throwable ignored) {
                // Not all JInput plugins honor custom queue sizes.
            }
        }
    }
}
