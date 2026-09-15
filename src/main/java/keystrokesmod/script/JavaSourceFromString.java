package keystrokesmod.script;

import java.net.URI;
import javax.tools.SimpleJavaFileObject;

/** Raven-compatible in-memory Java source used by external script tooling. */
public class JavaSourceFromString extends SimpleJavaFileObject {
    private final String code;
    public final String name;
    public final int extraLines;

    public JavaSourceFromString(String name, String code, int extraLines) {
        super(URI.create("string:///" + name + ".java"), Kind.SOURCE);
        this.name = name;
        this.code = code == null ? "" : code;
        this.extraLines = extraLines;
    }

    public JavaSourceFromString(String name, String code) {
        this(name, code, 0);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
