package org.example.docsync.docs;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task E2-6 (FR-E2-03, FR-E2-22; AC-E2-02): the student service document blocks. */
class ServiceDocTest {

    private static final String SERVICE_PATH = "src/main/java/org/example/service/StudentService.java";

    private static SourceModel analyzeRealProject() {
        RootPaths roots = RootPaths.of(".");
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    @Test
    void blocksAreTheMethodsAndDigestInCatalogOrder() {
        assertEquals(List.of("service-methods", "service-digest"),
            List.copyOf(ServiceDoc.blocks(analyzeRealProject(), SERVICE_PATH).keySet()));
    }

    @Test
    void realMethodsBlockHasTheSevenMethodsInSourceOrder() {
        String expected = String.join("\n",
            "| Method | Parameters | Returns |",
            "|---|---|---|",
            "| createStudent | String name, String email, String department | Student |",
            "| createStudent | String name, String email, String department, String phone | Student |",
            "| getStudents | none | List<Student> |",
            "| getStudent | String id | Optional<Student> |",
            "| deleteStudent | String id | boolean |",
            "| validateStudentInput | String name, String email, String department | void |",
            "| validateStudentInput | String name, String email, String department, String phone | void |");
        assertEquals(expected, ServiceDoc.blocks(analyzeRealProject(), SERVICE_PATH).get("service-methods"));
    }

    @Test
    void realDigestBlockIsTheSingleDocumentedLineWithPathAndLowercaseHexDigest() {
        RootPaths roots = RootPaths.of(".");
        SourceModel model = new JavaAnalyzer(roots, new ProjectFiles(roots)).analyze();
        String path = roots.display(RootPaths.Name.SERVICE_SOURCE);
        assertEquals(SERVICE_PATH, path);
        String block = ServiceDoc.blocks(model, path).get("service-digest");
        assertFalse(block.contains("\n"));
        assertTrue(model.serviceDigest().matches("[0-9a-f]{64}"));
        assertEquals("Source: `" + SERVICE_PATH + "` sha256: `" + model.serviceDigest() + "`", block);
    }

    @Test
    void aDifferentDigestChangesOnlyTheDigestBlock() {
        SourceModel real = analyzeRealProject();
        String other = "0".repeat(64);
        SourceModel changed = new SourceModel(real.routes(), real.errorCodes(), real.studentFields(),
            real.formParameters(), real.serviceMethods(), other);
        LinkedHashMap<String, String> before = ServiceDoc.blocks(real, SERVICE_PATH);
        LinkedHashMap<String, String> after = ServiceDoc.blocks(changed, SERVICE_PATH);
        assertEquals(before.get("service-methods"), after.get("service-methods"));
        assertNotEquals(before.get("service-digest"), after.get("service-digest"));
        assertEquals("Source: `" + SERVICE_PATH + "` sha256: `" + other + "`", after.get("service-digest"));
    }

    @Test
    void generatingTwiceGivesEqualBlocks() {
        assertEquals(ServiceDoc.blocks(analyzeRealProject(), SERVICE_PATH), ServiceDoc.blocks(analyzeRealProject(), SERVICE_PATH));
    }
}
