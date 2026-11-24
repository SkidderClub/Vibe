package dev.vibe.script;

import dev.vibe.path.PathTestFixture;
import java.io.File;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import keystrokesmod.script.model.Entity;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.item.EntityItem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class ScriptWorldLifecycleTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void wrapperCacheAllowsDiscardedWorldsToBeCollected() throws Exception {
        ReferenceQueue<WorldClient> collected = new ReferenceQueue<>();
        WeakReference<WorldClient> oldWorld = wrapEntityInDiscardedWorld(collected);

        for (int attempt = 0; attempt < 5; attempt++) {
            System.gc();
            if (collected.remove(100) == oldWorld) return;
        }
        fail("The entity wrapper cache retained a discarded world");
    }

    private static WeakReference<WorldClient> wrapEntityInDiscardedWorld(ReferenceQueue<WorldClient> collected)
            throws Exception {
        WorldClient world = PathTestFixture.allocate(WorldClient.class);
        EntityItem item = PathTestFixture.allocate(EntityItem.class);
        item.worldObj = world;
        item.setEntityId(42);
        Entity wrapper = Entity.convert(item);
        assertSame(wrapper, Entity.convert(item));

        EntityItem other = PathTestFixture.allocate(EntityItem.class);
        other.setEntityId(42);
        assertSame(other, Entity.convert(other).entity);
        item.setEntityId(43);
        assertSame(wrapper, Entity.convert(item));
        assertEquals(43, wrapper.entityId);
        return new WeakReference<>(world, collected);
    }

    @Test public void onlyAnEnabledSpawnCallbackNeedsAnEntityWrapper() throws Exception {
        try (PathTestFixture fixture = new PathTestFixture()) {
            ScriptRuntime runtime = new ScriptRuntime(temporary.newFolder());
            CountingItem item = PathTestFixture.allocate(CountingItem.class);
            item.setEntityId(42);
            runtime.worldJoin(item);
            assertEquals(0, item.idReads);

            Path source = new File(runtime.getDirectory(), "WorldProbe.java").toPath();
            Files.write(source, ("void onLoad() { modules.registerButton(\"Joined\", false); }\n"
                    + "void onWorldJoin(Entity entity) { modules.setButton(scriptName, \"Joined\", entity.entityId == 42); }\n")
                    .getBytes(StandardCharsets.UTF_8));
            try {
                runtime.reload();
                ScriptModule script = runtime.getModule("WorldProbe");
                assertNotNull(runtime.getDiagnostics().toString(), script);
                runtime.worldJoin(item);
                assertEquals(0, item.idReads);
                script.setEnabled(true);

                runtime.worldJoin(item);
                assertTrue(script.getButton("Joined"));
                assertTrue(item.idReads > 0);
            } finally {
                Files.deleteIfExists(source);
                runtime.reload();
            }
        }
    }

    private static final class CountingItem extends EntityItem {
        private int idReads;

        private CountingItem() { super(null); }

        @Override public int getEntityId() {
            idReads++;
            return super.getEntityId();
        }
    }
}
