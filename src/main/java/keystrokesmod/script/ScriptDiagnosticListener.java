package keystrokesmod.script;

import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/** Collects compiler diagnostics for Raven-compatible editor integrations. */
public class ScriptDiagnosticListener implements DiagnosticListener<JavaFileObject> {
    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        String message = diagnostic.getMessage(Locale.ROOT);
        if (message == null || message.isEmpty()) return;
        System.err.println("[Vibe Scripts] " + diagnostic.getKind() + " line " + diagnostic.getLineNumber() + ": " + message);
    }
}
