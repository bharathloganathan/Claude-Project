package org.example.docsync.analysis;

import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel.FrontendRule;
import org.example.docsync.model.SourceModel.PayloadEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lexes only {@code const validationRules = {...}} and the first {@code new URLSearchParams({...})} of app.js
 * (plan section 6B, S3). Everything else in the file is skipped.
 */
public final class JsAnalyzer {
    private static final Set<String> RULE_PROPERTIES = Set.of("required", "minLength", "pattern", "message");

    public record Result(List<FrontendRule> rules, List<PayloadEntry> payload) {
    }

    private final RootPaths roots;
    private final ProjectFiles files;
    private String file;
    private String text;
    private int pos;

    public JsAnalyzer(RootPaths roots, ProjectFiles files) {
        this.roots = roots;
        this.files = files;
    }

    public Result analyze() {
        file = roots.display(RootPaths.Name.FRONTEND_JS);
        text = files.readText(RootPaths.Name.FRONTEND_JS);
        List<FrontendRule> rules = rules();
        List<PayloadEntry> payload = payload();
        return new Result(rules, payload);
    }

    // ---- const validationRules = { key: { property: value, ... }, ... } ----

    private List<FrontendRule> rules() {
        int at = text.indexOf("const validationRules");
        if (at < 0) {
            throw unsupported("const validationRules is missing");
        }
        pos = at + "const validationRules".length();
        skipSpace();
        expect('=');
        skipSpace();
        expect('{');
        List<FrontendRule> rules = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        while (!consumeCloser()) {
            String key = identifier();
            if (!keys.add(key)) {
                throw unsupported("validationRules key " + key + " is duplicated");
            }
            skipSpace();
            expect(':');
            skipSpace();
            rules.add(rule(key));
            skipSpace();
            consumeComma();
        }
        return rules;
    }

    private FrontendRule rule(String key) {
        expect('{');
        boolean required = false;
        Integer minLength = null;
        String pattern = null;
        String message = null;
        while (!consumeCloser()) {
            String property = identifier();
            if (!RULE_PROPERTIES.contains(property)) {
                throw unsupported("validationRules." + key + " uses the unsupported property " + property);
            }
            skipSpace();
            expect(':');
            skipSpace();
            Object value = value(key + "." + property);
            switch (property) {
                case "required" -> required = typed(value, Boolean.class, key, property);
                case "minLength" -> minLength = typed(value, Integer.class, key, property);
                case "pattern" -> pattern = typed(value, Regex.class, key, property).source();
                default -> message = typed(value, String.class, key, property);
            }
            skipSpace();
            consumeComma();
        }
        return new FrontendRule(key, required, minLength, pattern, message);
    }

    private record Regex(String source) {
    }

    private <T> T typed(Object value, Class<T> type, String key, String property) {
        if (!type.isInstance(value)) {
            throw unsupported("validationRules." + key + "." + property + " has a value of the wrong kind");
        }
        return type.cast(value);
    }

    private Object value(String where) {
        if (pos >= text.length()) {
            throw unsupported("validationRules ends unexpectedly at " + where);
        }
        char c = text.charAt(pos);
        if (c == '`') {
            throw unsupported("a template literal is not supported in a validation rule (" + where + ")");
        }
        if (c == '\'') {
            return string();
        }
        if (c == '/') {
            return regex();
        }
        if (Character.isDigit(c)) {
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos < text.length() && (text.charAt(pos) == '.' || Character.isLetter(text.charAt(pos)))) {
                throw unsupported("validationRules " + where + " must be an integer");
            }
            try {
                return Integer.parseInt(text.substring(start, pos));
            } catch (NumberFormatException e) {
                throw unsupported("validationRules " + where + " is not a valid integer");
            }
        }
        if (word("true")) {
            return Boolean.TRUE;
        }
        if (word("false")) {
            return Boolean.FALSE;
        }
        throw unsupported("validationRules " + where + " has an unsupported value");
    }

    private boolean word(String word) {
        int end = pos + word.length();
        if (text.startsWith(word, pos) && (end >= text.length() || !Character.isJavaIdentifierPart(text.charAt(end)))) {
            pos = end;
            return true;
        }
        return false;
    }

    private String string() {
        pos++;
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '\'') {
                return out.toString();
            }
            if (c == '\n') {
                break;
            }
            if (c == '\\') {
                if (pos >= text.length() || (text.charAt(pos) != '\'' && text.charAt(pos) != '\\')) {
                    throw unsupported("a string in validationRules uses an unsupported escape sequence");
                }
                c = text.charAt(pos++);
            }
            out.append(c);
        }
        throw unsupported("a string in validationRules is not terminated");
    }

    private Regex regex() {
        int start = ++pos;
        boolean inClass = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\n') {
                break;
            }
            if (c == '\\') {
                pos += 2;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                String source = text.substring(start, pos);
                pos++;
                if (pos < text.length() && Character.isLetter(text.charAt(pos))) {
                    throw unsupported("a regular expression with flags is not supported in a validation rule");
                }
                return new Regex(source);
            }
            pos++;
        }
        throw unsupported("a regular expression in validationRules is not terminated");
    }

    // ---- new URLSearchParams({ key: formData.field, ... }) ----

    private List<PayloadEntry> payload() {
        int at = text.indexOf("new URLSearchParams(");
        if (at < 0) {
            throw unsupported("new URLSearchParams({...}) is missing");
        }
        pos = at + "new URLSearchParams(".length();
        skipSpace();
        expect('{');
        List<PayloadEntry> entries = new ArrayList<>();
        Set<String> apiFields = new HashSet<>();
        Set<String> uiFields = new HashSet<>();
        while (!consumeCloser()) {
            String apiField = identifier();
            if (!apiFields.add(apiField)) {
                throw unsupported("payload key " + apiField + " is duplicated");
            }
            skipSpace();
            expect(':');
            skipSpace();
            if (!text.startsWith("formData.", pos)) {
                throw unsupported("the payload value of " + apiField + " must be formData.<key>");
            }
            pos += "formData.".length();
            String uiField = identifier();
            if (!uiFields.add(uiField)) {
                throw unsupported("formData." + uiField + " is read by more than one payload key");
            }
            entries.add(new PayloadEntry(apiField, uiField));
            skipSpace();
            consumeComma();
        }
        return entries;
    }

    // ---- lexer helpers ----

    private String identifier() {
        skipSpace();
        int start = pos;
        while (pos < text.length() && Character.isJavaIdentifierPart(text.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw unsupported("an identifier was expected near offset " + pos);
        }
        return text.substring(start, pos);
    }

    private boolean consumeCloser() {
        skipSpace();
        if (pos < text.length() && text.charAt(pos) == '}') {
            pos++;
            return true;
        }
        if (pos >= text.length()) {
            throw unsupported("an object literal is not closed");
        }
        return false;
    }

    private void consumeComma() {
        skipSpace();
        if (pos < text.length() && text.charAt(pos) == ',') {
            pos++;
        } else if (pos >= text.length() || text.charAt(pos) != '}') {
            throw unsupported("a comma or a closing brace was expected near offset " + pos);
        }
    }

    private void expect(char expected) {
        if (pos >= text.length() || text.charAt(pos) != expected) {
            throw unsupported("'" + expected + "' was expected near offset " + pos);
        }
        pos++;
    }

    private void skipSpace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (text.startsWith("//", pos)) {
                int end = text.indexOf('\n', pos);
                pos = end < 0 ? text.length() : end;
            } else if (text.startsWith("/*", pos)) {
                int end = text.indexOf("*/", pos + 2);
                if (end < 0) {
                    throw unsupported("a comment is not terminated");
                }
                pos = end + 2;
            } else {
                return;
            }
        }
    }

    private UnsupportedConstructException unsupported(String construct) {
        return new UnsupportedConstructException(file + ": " + construct);
    }
}
