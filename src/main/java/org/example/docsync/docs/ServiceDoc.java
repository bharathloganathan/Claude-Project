package org.example.docsync.docs;

import org.example.docsync.model.SourceModel;

import java.util.LinkedHashMap;
import java.util.List;

public final class ServiceDoc {
    public static LinkedHashMap<String, String> blocks(SourceModel model, String serviceSourcePath) {
        List<List<String>> methods = model.serviceMethods().stream()
            .map(m -> List.of(m.name(), m.parameters().isEmpty() ? "none" : String.join(", ", m.parameters()), m.returnType()))
            .toList();
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("service-methods", DocCatalog.table(List.of("Method", "Parameters", "Returns"), methods));
        blocks.put("service-digest", "Source: `" + serviceSourcePath + "` sha256: `" + model.serviceDigest() + "`");
        return blocks;
    }
}
