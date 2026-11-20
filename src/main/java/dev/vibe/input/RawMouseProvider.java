package dev.vibe.input;

import java.util.List;

/** Discovers hardware mouse devices for {@link RawMouseInputController}. */
public interface RawMouseProvider {

    enum Backend {
        AUTO("Auto"),
        WINDOWS_RAW("Windows Raw"),
        DEFAULT("Default");

        private final String label;

        Backend(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static Backend fromLabel(String value) {
            if (value != null) {
                for (Backend backend : values()) {
                    if (backend.label.equalsIgnoreCase(value) || backend.name().equalsIgnoreCase(value)) {
                        return backend;
                    }
                }
            }
            return AUTO;
        }
    }

    /**
     * Discovers available mouse devices for the requested backend.
     *
     * @param backend backend selection mode
     * @param forceRefresh when {@code true}, requests fresh enumeration instead of cached results
     * @return discovered mice (empty if none are available or the backend is unsupported)
     */
    List<RawMouseDevice> discover(Backend backend, boolean forceRefresh);
}
