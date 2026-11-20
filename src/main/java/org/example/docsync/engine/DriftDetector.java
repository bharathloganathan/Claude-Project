package org.example.docsync.engine;

import org.example.docsync.model.Findings.DriftFinding;
import org.example.docsync.model.RuleSpec;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.FrontendRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares the frontend and backend rules of every mapped field (plan section 6B, S5): required, minLength and
 * pattern, in that order, one finding per difference. Message text, select options, maxlength and type never count.
 */
public final class DriftDetector {
    private static final String NONE = "none";

    private DriftDetector() {
    }

    public static List<DriftFinding> detect(SourceModel model) {
        List<DriftFinding> findings = new ArrayList<>();
        if (model.uiForm() == null) {
            return findings;
        }
        for (String apiField : model.formParameters()) {
            String uiField = model.payload().stream().filter(p -> p.apiField().equals(apiField)).findFirst().orElseThrow().uiField();
            FrontendRule frontend = model.frontendRules().stream().filter(r -> r.key().equals(uiField)).findFirst().orElseThrow();
            RuleSpec backend = model.backendRules().stream().filter(r -> r.apiField().equals(apiField)).findFirst().orElseThrow();
            if (frontend.required() != backend.required()) {
                findings.add(new DriftFinding(uiField, "required", String.valueOf(frontend.required()), String.valueOf(backend.required())));
            }
            if (frontend.minLength() != null) {
                findings.add(new DriftFinding(uiField, "minLength", String.valueOf(frontend.minLength()), NONE));
            }
            if (!samePattern(frontend.pattern(), backend)) {
                findings.add(new DriftFinding(uiField, "pattern", frontend.pattern() == null ? NONE : frontend.pattern(), describe(backend)));
            }
        }
        return findings;
    }

    // The backend has no minimum-length rule (S4 rejects a length check), so any frontend minLength differs.
    private static boolean samePattern(String frontend, RuleSpec backend) {
        return switch (backend.kind()) {
            case NONE -> frontend == null;
            case CONTAINS -> false;
            case REGEX -> frontend != null && normalize(frontend).equals(backend.text());
        };
    }

    private static String describe(RuleSpec backend) {
        return switch (backend.kind()) {
            case NONE -> NONE;
            case CONTAINS -> "contains(\"" + backend.text() + "\")";
            case REGEX -> backend.text();
        };
    }

    // Exactly one leading ^ and one trailing (unescaped) $ are removed before the text is compared.
    private static String normalize(String regex) {
        String text = regex.startsWith("^") ? regex.substring(1) : regex;
        return text.endsWith("$") && !text.endsWith("\\$") ? text.substring(0, text.length() - 1) : text;
    }
}
