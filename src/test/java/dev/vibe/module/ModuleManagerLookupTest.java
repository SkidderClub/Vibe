package dev.vibe.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** getModule(Class) is cached; it must still answer exactly like a scan of the current list. */
public class ModuleManagerLookupTest {

    private static class ScriptBase extends Module {
        ScriptBase(String name) { super(name, "", Category.SCRIPTS, 0); }
    }
    private static final class First extends ScriptBase { First() { super("First"); } }
    private static final class Second extends ScriptBase { Second() { super("Second"); } }
    private static final class Third extends ScriptBase { Third() { super("Third"); } }

    @Test public void cachedLookupFollowsListOrderAndEveryChange() throws Exception {
        First first = new First();
        Second second = new Second();
        Third third = new Third();
        // Tests elsewhere build the registry without its constructor and swap the list reflectively.
        ModuleManager manager = allocate();
        setModules(manager, first, second);

        assertSame(first, manager.getModule(ScriptBase.class));
        assertSame(second, manager.getModule(Second.class));
        assertNull(manager.getModule(Third.class));
        assertSame("repeated lookups use the cache", first, manager.getModule(ScriptBase.class));

        manager.registerDynamic(third);
        assertSame(third, manager.getModule(Third.class));

        manager.unregisterDynamic(first);
        assertNull(manager.getModule(First.class));
        assertSame(second, manager.getModule(ScriptBase.class));

        setModules(manager, third);
        assertSame(third, manager.getModule(ScriptBase.class));
        assertNull(manager.getModule(Second.class));
    }

    private static ModuleManager allocate() throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        return (ModuleManager) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, ModuleManager.class);
    }

    private static void setModules(ModuleManager manager, Module... modules) throws Exception {
        Field field = ModuleManager.class.getDeclaredField("modules");
        field.setAccessible(true);
        field.set(manager, new ArrayList<Module>(Arrays.asList(modules)));
    }
}
