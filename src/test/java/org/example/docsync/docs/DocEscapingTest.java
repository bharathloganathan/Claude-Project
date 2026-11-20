package org.example.docsync.docs;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.docs.DocCatalog.Entry;
import org.example.docsync.error.MarkerIntegrityException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.Route;
import org.example.docsync.model.SourceModel.ServiceMethod;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task E2-6 (FR-E2-03, FR-E2-22; AC-E2-02; DR-13): the one escape helper, marker safety and the catalog. */
class DocEscapingTest {

    private static final String SERVICE_PATH = "src/main/java/org/example/service/StudentService.java";

    private static SourceModel analyzeRealProject() {
        RootPaths roots = RootPaths.of(".");
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi();
    }

    private static SourceModel withRoutes(SourceModel base, List<Route> routes) {
        return new SourceModel(routes, base.errorCodes(), base.studentFields(), base.formParameters(),
            base.serviceMethods(), base.serviceDigest(), base.uiForm(), base.frontendRules(), base.payload(), base.backendRules());
    }

    private static int unescapedPipes(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '|' && (i == 0 || line.charAt(i - 1) != '\\')) {
                count++;
            }
        }
        return count;
    }

    private static String rowContaining(String block, String text) {
        return List.of(block.split("\n")).stream().filter(line -> line.contains(text)).findFirst().orElseThrow();
    }

    // ---- escape ----

    @Test
    void escapeTurnsPipeIntoBackslashPipe() {
        assertEquals("a\\|b", DocCatalog.escape("a|b"));
    }

    @Test
    void escapeTurnsBacktickIntoBackslashBacktick() {
        assertEquals("a\\`b", DocCatalog.escape("a`b"));
    }

    @Test
    void escapeHandlesBothAndLeavesPlainTextUnchanged() {
        assertEquals("\\|\\`\\|", DocCatalog.escape("|`|"));
        assertEquals("List<Student> {id}", DocCatalog.escape("List<Student> {id}"));
    }

    @Test
    void aRouteSummaryWithAPipeIsEscapedAndTheRowKeepsItsColumnCount() {
        SourceModel real = analyzeRealProject();
        List<Route> routes = new ArrayList<>(real.routes());
        routes.add(new Route("GET", "/x", "left|right", "none", "Student"));
        String row = rowContaining(DocCatalog.generate(withRoutes(real, routes), SERVICE_PATH)
            .get(Name.DOC_ENDPOINTS).get("api-routes"), "/x");
        assertTrue(row.contains("left\\|right"));
        assertEquals(6, unescapedPipes(row));
    }

    @Test
    void aServiceParameterWithAPipeIsEscapedAndTheRowKeepsItsColumnCount() {
        SourceModel real = analyzeRealProject();
        List<ServiceMethod> methods = new ArrayList<>(real.serviceMethods());
        methods.add(new ServiceMethod("odd", List.of("A|B first", "String `second`"), "void"));
        SourceModel model = new SourceModel(real.routes(), real.errorCodes(), real.studentFields(),
            real.formParameters(), methods, real.serviceDigest(), real.uiForm(), real.frontendRules(), real.payload(),
            real.backendRules());
        String row = rowContaining(DocCatalog.generate(model, SERVICE_PATH).get(Name.DOC_SERVICE).get("service-methods"), "| odd |");
        assertTrue(row.contains("A\\|B first, String \\`second\\`"));
        assertEquals(4, unescapedPipes(row));
    }

    // ---- marker safety (DR-13) ----

    @Test
    void generateRejectsABodyContainingABeginMarkerText() {
        SourceModel real = analyzeRealProject();
        List<Route> routes = List.of(new Route("GET", "/x", "docsync:begin api-routes", "none", "Student"));
        MarkerIntegrityException e = assertThrows(MarkerIntegrityException.class,
            () -> DocCatalog.generate(withRoutes(real, routes), SERVICE_PATH));
        assertTrue(e.getMessage().contains("api-routes"));
    }

    @Test
    void generateRejectsABodyContainingAnEndMarkerText() {
        SourceModel real = analyzeRealProject();
        List<Route> routes = List.of(new Route("GET", "/x", "x docsync:end y", "none", "Student"));
        assertThrows(MarkerIntegrityException.class, () -> DocCatalog.generate(withRoutes(real, routes), SERVICE_PATH));
    }

    // ---- catalog ----

    @Test
    void entriesAreTheFourDocumentsWithTitlesAndBlockIdsInOrder() {
        List<Entry> entries = DocCatalog.entries();
        assertEquals(List.of(
            new Entry(Name.DOC_ENDPOINTS, "API Endpoints", List.of("api-routes", "api-errors")),
            new Entry(Name.DOC_CONTRACT, "Student Contract", List.of("contract-student-fields", "contract-create-params")),
            new Entry(Name.DOC_SERVICE, "Student Service", List.of("service-methods", "service-digest")),
            new Entry(Name.DOC_UI, "Student UI", List.of("ui-controls", "ui-rules", "ui-field-mapping", "ui-drift"))), entries);
    }

    @Test
    void aModelWithoutTheUiAnalysisIsRejectedInsteadOfSkippingTheUiDocument() {
        SourceModel real = analyzeRealProject();
        SourceModel e2Only = new SourceModel(real.routes(), real.errorCodes(), real.studentFields(), real.formParameters(),
            real.serviceMethods(), real.serviceDigest());

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DocCatalog.generate(e2Only, SERVICE_PATH));

        assertTrue(e.getMessage().contains("analyzeWithUi"), e.getMessage());
    }

    @Test
    void blockIdsAreUniqueAcrossTheCatalog() {
        Set<String> seen = new HashSet<>();
        for (Entry entry : DocCatalog.entries()) {
            for (String id : entry.blockIds()) {
                assertTrue(seen.add(id), "duplicate block id " + id);
            }
        }
        assertEquals(10, seen.size());
    }

    @Test
    void blockIdsOfADocumentAreItsIdsInOrder() {
        assertEquals(List.of("api-routes", "api-errors"), List.copyOf(DocCatalog.blockIds(Name.DOC_ENDPOINTS)));
        assertEquals(List.of("contract-student-fields", "contract-create-params"), List.copyOf(DocCatalog.blockIds(Name.DOC_CONTRACT)));
        assertEquals(List.of("service-methods", "service-digest"), List.copyOf(DocCatalog.blockIds(Name.DOC_SERVICE)));
        assertEquals(List.of("ui-controls", "ui-rules", "ui-field-mapping", "ui-drift"), List.copyOf(DocCatalog.blockIds(Name.DOC_UI)));
    }

    @Test
    void blockIdsOfADocumentOutsideTheCatalogAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DocCatalog.blockIds(Name.REPORT_VERIFY));
        assertThrows(IllegalArgumentException.class, () -> DocCatalog.blockIds(Name.REPORT_SYNC));
    }

    @Test
    void generateReturnsExactlyTheCatalogBlockIdsPerDocumentInOrder() {
        LinkedHashMap<Name, LinkedHashMap<String, String>> generated = DocCatalog.generate(analyzeRealProject(), SERVICE_PATH);
        List<Entry> entries = DocCatalog.entries();
        assertEquals(entries.stream().map(Entry::name).toList(), List.copyOf(generated.keySet()));
        for (Entry entry : entries) {
            assertEquals(entry.blockIds(), List.copyOf(generated.get(entry.name()).keySet()));
        }
    }

    @Test
    void generatedBodiesAreLfTextWithoutTrailingNewlineAndAreDeterministic() {
        SourceModel model = analyzeRealProject();
        LinkedHashMap<Name, LinkedHashMap<String, String>> first = DocCatalog.generate(model, SERVICE_PATH);
        assertEquals(first, DocCatalog.generate(model, SERVICE_PATH));
        for (LinkedHashMap<String, String> blocks : first.values()) {
            for (String body : blocks.values()) {
                assertFalse(body.isEmpty());
                assertFalse(body.contains("\r"));
                assertFalse(body.endsWith("\n"));
            }
        }
    }
}
