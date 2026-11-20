package org.example.docsync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self check on a copy of the real project sources (task E2-9, AC-E2-01, AC-E2-07): sync and verify run
 * only on the temp copy, and the real docs directory and the real sources are read-only inputs whose
 * names, sizes and content hashes are compared before and after. This source file is ASCII only.
 */
class ProjectCopySelfCheckTest {

    @TempDir
    Path copy;

    @Test
    void aCopyOfTheRealProjectSyncsAndVerifiesAndTheRealRootIsUntouched() throws IOException {
        Path realDocs = Path.of("docs");
        Path realSources = Path.of("src", "main", "java");
        Path realFrontend = Path.of("frontend");
        boolean docsExisted = Files.exists(realDocs);
        Map<String, String> docsBefore = EndToEndTest.snapshot(realDocs);
        Map<String, String> sourcesBefore = EndToEndTest.snapshot(realSources);
        Map<String, String> frontendBefore = EndToEndTest.snapshot(realFrontend);
        assertTrue(sourcesBefore.size() > 10, "the real sources must be found: " + sourcesBefore.size());

        EndToEndTest.copyProject(copy);
        assertEquals(sourcesBefore, EndToEndTest.snapshot(copy.resolve(realSources)));
        assertEquals(frontendBefore, EndToEndTest.snapshot(copy.resolve(realFrontend)));

        EndToEndTest.Result sync = EndToEndTest.inProcess("sync", "--root", copy.toString());
        assertEquals(0, sync.exit(), sync.err());
        assertTrue(sync.out().contains("sync: created docs/api/endpoints.md"), sync.out());
        assertEquals(0, EndToEndTest.inProcess("verify", "--root", copy.toString()).exit());
        EndToEndTest.Result second = EndToEndTest.inProcess("sync", "--root", copy.toString());
        assertEquals(0, second.exit(), second.err());
        for (String doc : new String[] {"docs/api/endpoints.md", "docs/api/student-contract.md", "docs/backend/student-service.md",
            "docs/ui/student-ui.md"}) {
            assertTrue(Files.isRegularFile(copy.resolve(doc)), doc);
            assertTrue(second.out().contains("sync: unchanged " + doc), second.out());
        }

        assertEquals(sourcesBefore, EndToEndTest.snapshot(copy.resolve(realSources)), "the copied sources must not change");
        assertEquals(frontendBefore, EndToEndTest.snapshot(copy.resolve(realFrontend)), "the copied frontend must not change");
        assertEquals(docsExisted, Files.exists(realDocs), "the real docs directory must not appear or disappear");
        assertEquals(docsBefore, EndToEndTest.snapshot(realDocs), "the real docs directory must not change");
        assertEquals(sourcesBefore, EndToEndTest.snapshot(realSources), "the real sources must not change");
        assertEquals(frontendBefore, EndToEndTest.snapshot(realFrontend), "the real frontend must not change");
    }
}
