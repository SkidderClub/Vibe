package dev.vibe.script;

import java.net.URI;
import java.nio.file.Files;
import java.util.*;
import javax.tools.*;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Raven B4 calls that real scripts use must compile exactly as ScriptRuntime wraps a script. */
public class RavenScriptApiTest {
    static List<String> compileErrors(String name, String body) throws Exception {
        String className = "raven_api_check";
        final String code = ScriptRuntime.header(className, name) + body + "\n}";
        JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreErrors) { return code; }
        };
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> options = Arrays.asList("-d", Files.createTempDirectory("raven-api").toString(),
                "-classpath", System.getProperty("java.class.path"), "--release", "8", "-proc:none");
        ToolProvider.getSystemJavaCompiler().getTask(null, null, diagnostics, options, null, Collections.singletonList(source)).call();
        List<String> errors = new ArrayList<String>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics())
            if (d.getKind() == Diagnostic.Kind.ERROR) errors.add(d.getLineNumber() + ": " + d.getMessage(Locale.ROOT));
        return errors;
    }

    @Test public void ravenB4CallsUsedByCommonScriptsCompile() throws Exception {
        String body = "void check(CPacket packet) {\n"
                + "  String name = getScriptName(); client.setMotion(new Vec3(0.0, 0.0, 0.0)); client.swingReset();\n"
                + "  render.text(name, 1f, 2f, 1f, -1, true); int key = keybinds.getKeycode(\"jump\");\n"
                + "  gl.translate(1.0, 2.0, 3.0); gl.vertex3(1.0, 2.0, 3.0);\n"
                + "  if (packet instanceof C03) { boolean moving = ((C03) packet).moving, rotating = ((C03) packet).rotating; }\n"
                + "}";
        assertEquals(Collections.<String>emptyList(), compileErrors("Check", body));
        // Guards the harness itself: an unknown call must still be reported.
        assertFalse(compileErrors("Check", "void bad() { client.noSuchRavenCall(); }").isEmpty());
    }
}
