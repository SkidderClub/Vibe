package keystrokesmod.script;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Compatibility loader retained for Raven scripts that instantiate it. Vibe's
 * manager already compiles into an isolated loader; this class deliberately
 * delegates to that loader without imposing a second, incomplete whitelist.
 */
public class SecureClassLoader extends URLClassLoader {
    public SecureClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }
}
