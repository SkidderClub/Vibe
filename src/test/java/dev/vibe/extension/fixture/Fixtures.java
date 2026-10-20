package dev.vibe.extension.fixture;

import dev.vibe.extension.ExtensionContext;
import dev.vibe.extension.VibeExtension;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.NumberSetting;

/** These class files are copied into actual fixture JARs and loaded through a separate classloader. */
public final class Fixtures {
    public static final class FixtureModule extends Module {
        public int ticks, activities, stops;
        public final NumberSetting amount = addSetting(new NumberSetting("Amount", 3, 0, 10, 1));
        FixtureModule(String name) { super(name, "Fixture", Category.MEME, 0); }
    }
    public static final class Good implements VibeExtension {
        public void initialize(ExtensionContext context) {
            FixtureModule module = context.registerModule(new FixtureModule("Fixture"));
            context.registerService(CharSequence.class, new StringBuilder("Fixture service"));
            context.registerEvents(this);
            context.onTickStart(() -> module.ticks++);
            context.onActivity((activity, position, target) -> module.activities++);
            context.onShutdown(() -> module.stops++);
        }
    }
    public static final class Bad implements VibeExtension {
        public void initialize(ExtensionContext context) {
            context.registerModule(new FixtureModule("Rollback"));
            context.registerService(Runnable.class, () -> { });
            context.registerEvents(this);
            context.onTickStart(() -> { throw new AssertionError("Rolled back callback ran"); });
            context.onActivity((activity, position, target) -> { throw new AssertionError("Rolled back activity ran"); });
            throw new IllegalStateException("Fixture initialization failed");
        }
    }
    public static final class Collision implements VibeExtension {
        public void initialize(ExtensionContext context) { context.registerModule(new FixtureModule("Fixture")); }
    }
    public static final class TickFailure implements VibeExtension {
        public void initialize(ExtensionContext context) {
            FixtureModule module = context.registerModule(new FixtureModule("Tick failure"));
            context.onTickStart(() -> { module.ticks++; throw new IllegalStateException("Tick fixture failed"); });
        }
    }
    public static final class DisableModule extends Module {
        public DisableModule() { super("Broken disable", "Fixture", Category.MEME, 0); }
        @Override protected void onDisable() { throw new AssertionError("Disable fixture failed"); }
    }
    public static final class DisableFailure implements VibeExtension {
        public void initialize(ExtensionContext context) {
            context.registerModule(new DisableModule()).setEnabled(true);
            throw new AssertionError("Rollback fixture failed");
        }
    }
}
