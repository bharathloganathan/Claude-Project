package org.example.docsync.analysis;

import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel.UiControl;
import org.example.docsync.model.SourceModel.UiForm;
import org.example.docsync.model.SourceModel.UiOption;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads form#student-form of index.html and nothing else on the page (plan section 6B, S2): a small scanner,
 * no HTML library. Text outside the form never makes the analysis fail.
 */
public final class HtmlAnalyzer {
    private static final Set<String> ALLOWED = Set.of("label", "input", "select", "option", "button");
    private static final Set<String> EXTRACTED = Set.of("id", "name", "type", "required", "maxlength", "value");
    private static final String FORM_ID = "student-form";
    private static final Pattern COMMENT = Pattern.compile("<!--.*?(?:-->|\\z)", Pattern.DOTALL);
    private static final Pattern FORM_START = Pattern.compile("<form\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_END = Pattern.compile("</form", Pattern.CASE_INSENSITIVE);

    // unquoted holds the names of the attributes whose value was written without quotes.
    private record Token(boolean end, String name, Map<String, String> attributes, Set<String> unquoted, String text) {
    }

    private final RootPaths roots;
    private final ProjectFiles files;
    private String file;

    public HtmlAnalyzer(RootPaths roots, ProjectFiles files) {
        this.roots = roots;
        this.files = files;
    }

    public UiForm analyze() {
        file = roots.display(RootPaths.Name.FRONTEND_HTML);
        String html = COMMENT.matcher(files.readText(RootPaths.Name.FRONTEND_HTML)).replaceAll(" ");
        int bodyStart = -1;
        Matcher form = FORM_START.matcher(html);
        while (form.find()) {
            List<Token> tag = new ArrayList<>();
            try {
                startTag(form.group(), 0, tag);
            } catch (UnsupportedConstructException e) {
                continue; // a form tag that is not readable cannot be form#student-form
            }
            if (FORM_ID.equals(tag.get(0).attributes().get("id"))) {
                if (bodyStart >= 0) {
                    throw unsupported("form#" + FORM_ID + " must exist exactly once but was found more than once");
                }
                bodyStart = form.end();
            }
        }
        if (bodyStart < 0) {
            throw unsupported("form#" + FORM_ID + " must exist exactly once but was not found");
        }
        Matcher close = FORM_END.matcher(html);
        if (!close.find(bodyStart)) {
            throw unsupported("form#" + FORM_ID + " is not closed");
        }
        return readForm(scan(html.substring(bodyStart, close.start())));
    }

    private UiForm readForm(List<Token> tokens) {
        List<UiControl> controls = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        Token select = null;
        List<UiOption> options = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.text() != null) {
                continue;
            }
            String name = token.name();
            if (token.end()) {
                if (name.equals("select") && select != null) {
                    controls.add(control(select, "select", "select", options));
                    select = null;
                    options = new ArrayList<>();
                }
                continue;
            }
            if (!ALLOWED.contains(name)) {
                throw unsupported("element <" + name + "> is not allowed inside form#" + FORM_ID);
            }
            switch (name) {
                case "input" -> controls.add(control(token, "input", clean(token.attributes().getOrDefault("type", "text")), List.of()));
                case "select" -> {
                    select = token;
                    options = new ArrayList<>();
                }
                case "option" -> {
                    quoted(token);
                    String label = following(tokens, i);
                    options.add(new UiOption(clean(token.attributes().getOrDefault("value", label)), label));
                }
                case "button" -> buttons.add(following(tokens, i));
                default -> {
                }
            }
        }
        return finish(controls, buttons);
    }

    private UiForm finish(List<UiControl> controls, List<String> buttons) {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (UiControl control : controls) {
            if (!ids.add(control.id())) {
                throw unsupported("duplicate control id " + control.id());
            }
            if (!names.add(control.name())) {
                throw unsupported("duplicate control name " + control.name());
            }
        }
        if (buttons.size() != 1) {
            throw unsupported("form#" + FORM_ID + " must contain exactly one button but has " + buttons.size());
        }
        return new UiForm(controls, buttons.get(0));
    }

    private UiControl control(Token token, String tag, String type, List<UiOption> options) {
        quoted(token);
        String id = clean(token.attributes().getOrDefault("id", ""));
        String name = clean(token.attributes().getOrDefault("name", ""));
        if (id.isEmpty() || name.isEmpty()) {
            throw unsupported("<" + tag + "> needs an id and a name attribute");
        }
        String maxLength = token.attributes().containsKey("maxlength") ? clean(token.attributes().get("maxlength")) : null;
        return new UiControl(id, name, tag, type, required(token, id), maxLength, List.copyOf(options));
    }

    // An unquoted value is tolerated for an unknown attribute but not for one the engine extracts.
    private void quoted(Token token) {
        for (String attribute : token.unquoted()) {
            if (EXTRACTED.contains(attribute)) {
                throw unsupported("attribute " + attribute + " of <" + token.name() + "> needs a quoted value");
            }
        }
    }

    private boolean required(Token token, String id) {
        String value = token.attributes().get("required");
        if (value == null) {
            return false;
        }
        if (value.isEmpty() || value.equals("required")) {
            return true;
        }
        throw unsupported("control " + id + " has an unsupported required attribute value \"" + value + "\"");
    }

    private static String clean(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    private static String following(List<Token> tokens, int index) {
        if (index + 1 < tokens.size() && tokens.get(index + 1).text() != null) {
            return clean(tokens.get(index + 1).text());
        }
        return "";
    }

    // ---- scanner ----

    private List<Token> scan(String html) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = html.length();
        while (i < n) {
            char c = html.charAt(i);
            boolean tag = c == '<' && i + 1 < n && (Character.isLetter(html.charAt(i + 1)) || html.charAt(i + 1) == '/' || html.charAt(i + 1) == '!');
            if (!tag) {
                int next = i + 1;
                while (next < n && html.charAt(next) != '<') {
                    next++;
                }
                tokens.add(new Token(false, null, Map.of(), Set.of(), html.substring(i, next)));
                i = next;
            } else if (html.charAt(i + 1) == '!') {
                int close = html.indexOf('>', i);
                if (close < 0) {
                    throw unsupported("a declaration is not terminated");
                }
                i = close + 1;
            } else if (html.charAt(i + 1) == '/') {
                int close = html.indexOf('>', i);
                if (close < 0) {
                    throw unsupported("an end tag is not terminated");
                }
                tokens.add(new Token(true, html.substring(i + 2, close).strip().toLowerCase(Locale.ROOT), Map.of(), Set.of(), null));
                i = close + 1;
            } else {
                i = startTag(html, i, tokens);
            }
        }
        return tokens;
    }

    private int startTag(String html, int from, List<Token> tokens) {
        int n = html.length();
        int i = from + 1;
        int nameStart = i;
        while (i < n && !Character.isWhitespace(html.charAt(i)) && html.charAt(i) != '>' && html.charAt(i) != '/') {
            i++;
        }
        String name = html.substring(nameStart, i).toLowerCase(Locale.ROOT);
        Map<String, String> attributes = new LinkedHashMap<>();
        Set<String> unquoted = new HashSet<>();
        while (true) {
            while (i < n && (Character.isWhitespace(html.charAt(i)) || html.charAt(i) == '/')) {
                i++;
            }
            if (i >= n || html.charAt(i) == '<') {
                throw unsupported("the tag <" + name + "> is not terminated");
            }
            if (html.charAt(i) == '>') {
                i++;
                break;
            }
            int attributeStart = i;
            while (i < n && !Character.isWhitespace(html.charAt(i)) && "=>/<".indexOf(html.charAt(i)) < 0) {
                i++;
            }
            String attribute = html.substring(attributeStart, i).toLowerCase(Locale.ROOT);
            if (attribute.isEmpty()) {
                throw unsupported("the tag <" + name + "> is not well formed");
            }
            while (i < n && Character.isWhitespace(html.charAt(i))) {
                i++;
            }
            String value = "";
            if (i < n && html.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(html.charAt(i))) {
                    i++;
                }
                if (i < n && (html.charAt(i) == '"' || html.charAt(i) == '\'')) {
                    char quote = html.charAt(i);
                    int close = html.indexOf(quote, i + 1);
                    if (close < 0) {
                        throw unsupported("attribute " + attribute + " of <" + name + "> is not terminated");
                    }
                    value = html.substring(i + 1, close);
                    i = close + 1;
                } else {
                    int valueStart = i;
                    while (i < n && !Character.isWhitespace(html.charAt(i)) && html.charAt(i) != '>') {
                        i++;
                    }
                    value = html.substring(valueStart, i);
                    unquoted.add(attribute);
                }
            }
            attributes.putIfAbsent(attribute, value);
        }
        tokens.add(new Token(false, name, attributes, unquoted, null));
        return i;
    }

    private UnsupportedConstructException unsupported(String construct) {
        return new UnsupportedConstructException(file + ": " + construct);
    }
}
