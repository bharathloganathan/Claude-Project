package org.example.docsync.docs;

import org.example.docsync.engine.DriftDetector;
import org.example.docsync.error.MarkerIntegrityException;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DocCatalog {
    public record Entry(RootPaths.Name name, String title, List<String> blockIds) {
    }

    private static final List<Entry> ENTRIES = List.of(
        new Entry(RootPaths.Name.DOC_ENDPOINTS, "API Endpoints", List.of("api-routes", "api-errors")),
        new Entry(RootPaths.Name.DOC_CONTRACT, "Student Contract", List.of("contract-student-fields", "contract-create-params")),
        new Entry(RootPaths.Name.DOC_SERVICE, "Student Service", List.of("service-methods", "service-digest")),
        new Entry(RootPaths.Name.DOC_UI, "Student UI", List.of("ui-controls", "ui-rules", "ui-field-mapping", "ui-drift")));

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Set<String> blockIds(RootPaths.Name name) {
        for (Entry entry : ENTRIES) {
            if (entry.name() == name) {
                return new LinkedHashSet<>(entry.blockIds());
            }
        }
        throw new IllegalArgumentException(name + " is not a catalog document.");
    }

    public static LinkedHashMap<RootPaths.Name, LinkedHashMap<String, String>> generate(SourceModel model, String serviceSourcePath) {
        LinkedHashMap<RootPaths.Name, LinkedHashMap<String, String>> documents = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (entry.name() == RootPaths.Name.DOC_UI && model.uiForm() == null) {
                throw new IllegalStateException("The model carries no UI analysis; build it with JavaAnalyzer.analyzeWithUi().");
            }
            LinkedHashMap<String, String> blocks = switch (entry.name()) {
                case DOC_ENDPOINTS -> EndpointsDoc.blocks(model);
                case DOC_CONTRACT -> ContractDoc.blocks(model);
                case DOC_SERVICE -> ServiceDoc.blocks(model, serviceSourcePath);
                case DOC_UI -> UiDoc.blocks(model, DriftDetector.detect(model));
                default -> throw new IllegalStateException(entry.name() + " has no generator.");
            };
            for (var block : blocks.entrySet()) {
                if (block.getValue().contains("docsync:begin") || block.getValue().contains("docsync:end")) {
                    throw new MarkerIntegrityException("Generated body for block '" + block.getKey()
                        + "' contains marker syntax. Manual text must not contain literal docsync markers.");
                }
            }
            documents.put(entry.name(), blocks);
        }
        return documents;
    }

    public static String escape(String cell) {
        return cell.replace("|", "\\|").replace("`", "\\`");
    }

    static String table(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        appendRow(out, header);
        out.append("\n|").append("---|".repeat(header.size()));
        for (List<String> row : rows) {
            out.append('\n');
            appendRow(out, row);
        }
        return out.toString();
    }

    private static void appendRow(StringBuilder out, List<String> cells) {
        out.append('|');
        for (String cell : cells) {
            out.append(' ').append(escape(cell)).append(" |");
        }
    }
}
