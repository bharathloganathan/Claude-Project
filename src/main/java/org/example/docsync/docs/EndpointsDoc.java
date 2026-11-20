package org.example.docsync.docs;

import org.example.docsync.model.SourceModel;

import java.util.LinkedHashMap;
import java.util.List;

public final class EndpointsDoc {
    public static LinkedHashMap<String, String> blocks(SourceModel model) {
        List<List<String>> routes = model.routes().stream()
            .map(r -> List.of(r.method(), r.path(), r.summary(), r.requestType(), r.responseType()))
            .toList();
        List<List<String>> codes = model.errorCodes().stream()
            .map(c -> List.of(c.name(), String.valueOf(c.httpStatus())))
            .toList();
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("api-routes", DocCatalog.table(List.of("Method", "Path", "Summary", "Request", "Response"), routes));
        blocks.put("api-errors", "Error responses use the body {\"error\":\"<message>\",\"code\":\"<CODE>\"}.\n\n"
            + DocCatalog.table(List.of("Code", "HTTP status"), codes));
        return blocks;
    }
}
