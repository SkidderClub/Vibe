package dev.vibe.extension;

/** API version 1. Implement this in an extension JAR; Vibe constructs it once at startup. */
public interface VibeExtension {
    void initialize(ExtensionContext context) throws Exception;
}
