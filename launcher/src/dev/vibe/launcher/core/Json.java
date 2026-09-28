package dev.vibe.launcher.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal JSON reader for GitHub, Adoptium, Mojang and mcmod.info documents.
 * Objects become {@link LinkedHashMap}s, arrays {@link ArrayList}s, numbers
 * {@link Double}s. Values of "redacted" keys are never materialised as strings;
 * they are replaced by {@code Boolean.TRUE} when non-empty so secrets such as
 * refresh tokens stay out of the heap.
 */
public final class Json {
    private final char[] text;
    private final Set<String> redacted;
    private int position;
    private int depth;

    private Json(char[] text, Set<String> redacted) {
        this.text = text;
        this.redacted = redacted;
    }

    public static Object parse(String text) throws IOException {
        return parse(text.toCharArray(), Collections.<String>emptySet());
    }

    public static Object parse(char[] text, Set<String> redactedKeys) throws IOException {
        Json parser = new Json(text, redactedKeys);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.position != text.length) throw parser.error("Unexpected trailing data");
        return value;
    }

    // ---- typed accessors -------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    public static Map<String, Object> object(Map<String, Object> parent, String key) { return object(parent.get(key)); }
    public static List<Object> array(Map<String, Object> parent, String key) { return array(parent.get(key)); }

    public static String string(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value instanceof String ? (String) value : value instanceof Double ? number(parent, key, 0) + "" : "";
    }

    public static long number(Map<String, Object> parent, String key, long fallback) {
        Object value = parent.get(key);
        if (value instanceof Double) return ((Double) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong(((String) value).trim()); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> parent, String key) { return Boolean.TRUE.equals(parent.get(key)); }

    // ---- parser ----------------------------------------------------------

    private Object value() throws IOException {
        if (position >= text.length) throw error("Unexpected end of JSON");
        char c = text[position];
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("Unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object() throws IOException {
        enter();
        position++;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek('}')) { position++; depth--; return result; }
        while (true) {
            skipWhitespace();
            if (!peek('"')) throw error("Expected a key");
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            if (redacted.contains(key) && peek('"')) result.put(key, skipString() ? Boolean.TRUE : Boolean.FALSE);
            else result.put(key, value());
            skipWhitespace();
            if (peek(',')) { position++; continue; }
            expect('}');
            depth--;
            return result;
        }
    }

    private List<Object> array() throws IOException {
        enter();
        position++;
        List<Object> result = new ArrayList<Object>();
        skipWhitespace();
        if (peek(']')) { position++; depth--; return result; }
        while (true) {
            skipWhitespace();
            result.add(value());
            skipWhitespace();
            if (peek(',')) { position++; continue; }
            expect(']');
            depth--;
            return result;
        }
    }

    private String string() throws IOException {
        position++;
        StringBuilder builder = new StringBuilder();
        while (position < text.length) {
            char c = text[position++];
            if (c == '"') return builder.toString();
            if (c != '\\') { builder.append(c); continue; }
            if (position >= text.length) break;
            char escape = text[position++];
            switch (escape) {
                case '"': case '\\': case '/': builder.append(escape); break;
                case 'b': builder.append('\b'); break;
                case 'f': builder.append('\f'); break;
                case 'n': builder.append('\n'); break;
                case 'r': builder.append('\r'); break;
                case 't': builder.append('\t'); break;
                case 'u':
                    if (position + 4 > text.length) throw error("Bad unicode escape");
                    builder.append((char) Integer.parseInt(new String(text, position, 4), 16));
                    position += 4;
                    break;
                default: throw error("Bad escape");
            }
        }
        throw error("Unterminated string");
    }

    /** Skips a string without copying it. Returns whether it had any content. */
    private boolean skipString() throws IOException {
        position++;
        int start = position;
        while (position < text.length) {
            char c = text[position++];
            if (c == '"') return position - 1 > start;
            if (c == '\\') position++;
        }
        throw error("Unterminated string");
    }

    private Double number() throws IOException {
        int start = position;
        while (position < text.length && "+-0123456789.eE".indexOf(text[position]) >= 0) position++;
        try { return Double.valueOf(new String(text, start, position - start)); }
        catch (NumberFormatException error) { throw error("Bad number"); }
    }

    private void literal(String word) throws IOException {
        if (position + word.length() > text.length || !new String(text, position, word.length()).equals(word)) throw error("Bad literal");
        position += word.length();
    }

    private void enter() throws IOException { if (++depth > 256) throw error("JSON nesting is too deep"); }
    private boolean peek(char c) { return position < text.length && text[position] == c; }
    private void expect(char c) throws IOException { if (!peek(c)) throw error("Expected '" + c + "'"); position++; }
    private void skipWhitespace() { while (position < text.length && Character.isWhitespace(text[position])) position++; }
    private IOException error(String message) { return new IOException(message + " at offset " + position + "."); }
}
