package org.example.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict RFC 8259 parser used only by tests to prove that produced text is valid JSON.
 * Objects become LinkedHashMap, arrays ArrayList, numbers BigDecimal, and null becomes Java null.
 * Any invalid input throws IllegalArgumentException that names the position.
 */
final class JsonChecker {
    private final String text;
    private int pos;

    private JsonChecker(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null");
        }
        JsonChecker checker = new JsonChecker(text);
        checker.skipWhitespace();
        Object value = checker.readValue();
        checker.skipWhitespace();
        if (checker.pos != text.length()) {
            throw checker.fail("trailing characters after the JSON value");
        }
        return value;
    }

    private IllegalArgumentException fail(String message) {
        return new IllegalArgumentException("Invalid JSON: " + message + " at position " + pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object readValue() {
        if (pos >= text.length()) {
            throw fail("unexpected end of text, a value is required");
        }
        char c = text.charAt(pos);
        if (c == '{') {
            return readObject();
        }
        if (c == '[') {
            return readArray();
        }
        if (c == '"') {
            return readString();
        }
        if (c == '-' || isDigit(c)) {
            return readNumber();
        }
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw fail("unexpected character '" + c + "'");
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (pos < text.length() && text.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) != '"') {
                throw fail("an object key must be a double-quoted string");
            }
            String key = readString();
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) != ':') {
                throw fail("expected ':' after the object key");
            }
            pos++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            if (pos >= text.length()) {
                throw fail("unterminated object");
            }
            char c = text.charAt(pos++);
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                pos--;
                throw fail("expected ',' or '}' in an object");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (pos < text.length() && text.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            if (pos >= text.length()) {
                throw fail("unterminated array");
            }
            char c = text.charAt(pos++);
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                pos--;
                throw fail("expected ',' or ']' in an array");
            }
        }
    }

    private String readString() {
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw fail("unterminated string");
            }
            char c = text.charAt(pos);
            if (c == '"') {
                pos++;
                return out.toString();
            }
            if (c < 0x20) {
                throw fail("raw control character in a string");
            }
            if (c != '\\') {
                out.append(c);
                pos++;
                continue;
            }
            pos++;
            if (pos >= text.length()) {
                throw fail("unterminated escape");
            }
            char e = text.charAt(pos);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 >= text.length()) {
                        throw fail("incomplete \\u escape");
                    }
                    int code = 0;
                    for (int i = 1; i <= 4; i++) {
                        int digit = hexValue(text.charAt(pos + i));
                        if (digit < 0) {
                            pos += i;
                            throw fail("bad hex digit in a \\u escape");
                        }
                        code = code * 16 + digit;
                    }
                    out.append((char) code);
                    pos += 4;
                }
                default -> throw fail("invalid escape '\\" + e + "'");
            }
            pos++;
        }
    }

    private BigDecimal readNumber() {
        int start = pos;
        if (text.charAt(pos) == '-') {
            pos++;
        }
        if (pos >= text.length() || !isDigit(text.charAt(pos))) {
            throw fail("a digit is required in a number");
        }
        if (text.charAt(pos) == '0') {
            pos++;
            if (pos < text.length() && isDigit(text.charAt(pos))) {
                throw fail("leading zero in a number");
            }
        } else {
            skipDigits();
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            if (pos >= text.length() || !isDigit(text.charAt(pos))) {
                throw fail("a digit is required after the decimal point");
            }
            skipDigits();
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            if (pos >= text.length() || !isDigit(text.charAt(pos))) {
                throw fail("a digit is required in the exponent");
            }
            skipDigits();
        }
        return new BigDecimal(text.substring(start, pos));
    }

    private void skipDigits() {
        while (pos < text.length() && isDigit(text.charAt(pos))) {
            pos++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }
}
