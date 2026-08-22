package com.neurontracer.http;

import java.util.Map;

/**
 * Just enough JSON to write the responses this server sends. The server never parses
 * JSON, so there is no reader here and no need for a JSON library.
 */
public final class Json {

    private Json() {
    }

    /** Serialises a map of {@code String}, {@code Number}, {@code Boolean}, nested {@code Map} or {@code null} values. */
    public static String write(Map<String, ?> object) {
        StringBuilder out = new StringBuilder();
        writeValue(out, object);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case Map<?, ?> map -> writeObject(out, map);
            case Number number -> out.append(number);
            case Boolean bool -> out.append(bool);
            case CharSequence text -> writeString(out, text);
            default -> writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeString(StringBuilder out, CharSequence text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
