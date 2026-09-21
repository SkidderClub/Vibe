package dev.vibe.module.impl;

import java.io.IOException;
import net.minecraft.client.Minecraft;

/** Access bridge for the same {@code mc.runTick()} calls used by Gothaj. */
final class CombatTickRunner {
    private CombatTickRunner() { }
    static void runQuietly() {
        try { Minecraft.getMinecraft().runTick(); } catch (IOException ignored) { }
    }
    static void runOrThrow() {
        try { Minecraft.getMinecraft().runTick(); } catch (IOException exception) { throw new RuntimeException(exception); }
    }
}
