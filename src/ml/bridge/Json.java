package ml.bridge;

import java.util.*;

final class Json {
    private Json() {}
    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return quote((String)value);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            ((Map<?,?>)value).forEach((k,v) -> j.add(quote(k.toString()) + ":" + encode(v)));
            return j.toString();
        }
        if (value instanceof Iterable) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            for (Object o : (Iterable<?>)value) j.add(encode(o));
            return j.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value " + value.getClass());
    }
    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 32 || Character.isSurrogate(c)) {
                        b.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
                    } else b.append(c);
            }
        }
        return b.append('"').toString();
    }
    static Map<String,Object> obj(Object... pairs) {
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String)pairs[i], pairs[i+1]);
        return m;
    }
}
