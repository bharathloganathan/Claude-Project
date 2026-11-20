package org.example.docsync.docs;

import org.example.docsync.model.SourceModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.IntStream;

public final class ContractDoc {
    public static LinkedHashMap<String, String> blocks(SourceModel model) {
        List<List<String>> fields = model.studentFields().stream()
            .map(f -> List.of(f.name(), f.type()))
            .toList();
        List<List<String>> parameters = IntStream.range(0, model.formParameters().size())
            .mapToObj(i -> List.of(model.formParameters().get(i), String.valueOf(i + 1)))
            .toList();
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("contract-student-fields", DocCatalog.table(List.of("Field", "Type"), fields));
        blocks.put("contract-create-params", DocCatalog.table(List.of("Parameter", "Position"), parameters));
        return blocks;
    }
}
