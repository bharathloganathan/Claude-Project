package org.example.docsync.docs;

import org.example.docsync.model.Findings.DriftFinding;
import org.example.docsync.model.SourceModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** The four blocks of docs/ui/student-ui.md (plan section 6B, S7). */
public final class UiDoc {
    private static final String ARROW = String.valueOf((char) 0x2192);

    public static LinkedHashMap<String, String> blocks(SourceModel model, List<DriftFinding> drift) {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("ui-controls", controls(model.uiForm()));
        blocks.put("ui-rules", rules(model));
        blocks.put("ui-field-mapping", mapping(model));
        blocks.put("ui-drift", drift(drift));
        return blocks;
    }

    private static String controls(SourceModel.UiForm form) {
        List<List<String>> rows = new ArrayList<>();
        for (SourceModel.UiControl control : form.controls()) {
            rows.add(List.of(control.id(), control.name(), control.type(), control.required() ? "yes" : "no",
                control.maxLength() == null ? "none" : control.maxLength()));
        }
        StringBuilder out = new StringBuilder(DocCatalog.table(List.of("Id", "Name", "Type", "Required", "Max length"), rows));
        for (SourceModel.UiControl control : form.controls()) {
            if (!control.options().isEmpty()) {
                List<List<String>> options = new ArrayList<>();
                for (SourceModel.UiOption option : control.options()) {
                    options.add(List.of(option.value().isEmpty() ? "\"\"" : option.value(), option.label()));
                }
                out.append("\n\nOptions of ").append(control.id()).append(":\n\n")
                    .append(DocCatalog.table(List.of("Value", "Label"), options));
            }
        }
        return out.append("\n\nSubmit button: ").append(DocCatalog.escape(form.submitText())).toString();
    }

    private static String rules(SourceModel model) {
        List<List<String>> rows = new ArrayList<>();
        for (SourceModel.FrontendRule rule : model.frontendRules()) {
            rows.add(List.of(rule.key(), rule.required() ? "yes" : "no",
                rule.minLength() == null ? "none" : String.valueOf(rule.minLength()),
                rule.pattern() == null ? "none" : rule.pattern()));
        }
        return DocCatalog.table(List.of("UI field", "Required", "Min length", "Pattern"), rows);
    }

    private static String mapping(SourceModel model) {
        List<List<String>> rows = new ArrayList<>();
        for (String apiField : model.formParameters()) {
            String uiField = model.payload().stream().filter(p -> p.apiField().equals(apiField)).findFirst().orElseThrow().uiField();
            rows.add(List.of(uiField, apiField, uiField + ARROW + apiField));
        }
        return DocCatalog.table(List.of("UI field", "API field", "Mapping"), rows);
    }

    private static String drift(List<DriftFinding> drift) {
        if (drift.isEmpty()) {
            return "No drift found.";
        }
        List<List<String>> rows = new ArrayList<>();
        for (DriftFinding finding : drift) {
            rows.add(List.of(finding.uiField(), finding.property(), finding.frontend(), finding.backend()));
        }
        return "Report only: drift never changes the exit code.\n\n"
            + DocCatalog.table(List.of("UI field", "Property", "Frontend", "Backend"), rows);
    }
}
