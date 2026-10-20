package dev.vibe.extension.example;

import dev.vibe.extension.ExtensionContext;
import dev.vibe.extension.VibeExtension;
import dev.vibe.module.impl.movement.TestModule;

/** Minimal extension: a module, settings, a tick hook, normal config and keybind support. */
public final class ExampleExtension implements VibeExtension {
    @Override public void initialize(ExtensionContext context) {
        TestModule test = context.registerModule(new TestModule());
        context.onTickStart(test::tick);
    }
}
