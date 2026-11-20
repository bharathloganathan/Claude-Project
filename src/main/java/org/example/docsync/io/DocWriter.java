package org.example.docsync.io;

import org.example.docsync.error.RootException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Set;

public final class DocWriter {
    private static final Set<RootPaths.Name> ALLOWED = EnumSet.of(RootPaths.Name.DOC_ENDPOINTS,
        RootPaths.Name.DOC_CONTRACT, RootPaths.Name.DOC_SERVICE, RootPaths.Name.DOC_UI);

    private final AtomicWriter writer;

    public DocWriter(AtomicWriter writer) {
        this.writer = writer;
    }

    public void write(LinkedHashMap<RootPaths.Name, String> docs) {
        for (RootPaths.Name name : docs.keySet()) {
            if (!ALLOWED.contains(name)) {
                throw new RootException("Only catalog documents may be written as documents, not " + name + ".");
            }
        }
        writer.writeAll(docs);
    }
}
