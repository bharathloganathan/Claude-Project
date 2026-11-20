package org.example.docsync.docs;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.engine.DriftDetector;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.example.docsync.error.MarkerIntegrityException;
import org.example.docsync.model.Findings.DriftFinding;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.FrontendRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E3-4 (FR-E3-01, FR-E3-04, FR-E3-07, AC-E3-01 to AC-E3-03, S7): the golden layout of the four UI blocks.
 * The real sources are read read-only through the project directory (the surefire working directory).
 */
class UiDocTest {

    private static final String ARROW = String.valueOf((char) 0x2192);
    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private static SourceModel realModel() {
        RootPaths roots = RootPaths.of(".");
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi();
    }

    private static LinkedHashMap<String, String> blocks(SourceModel model) {
        return UiDoc.blocks(model, DriftDetector.detect(model));
    }

    @Test
    void theBlocksAreTheCatalogIdsInOrder() {
        assertEquals(DocCatalog.entries().get(3).blockIds(), List.copyOf(blocks(realModel()).keySet()));
        assertEquals(Name.DOC_UI, DocCatalog.entries().get(3).name());
    }

    @Test
    void theControlsBlockListsTheFourControlsTheDepartmentOptionsAndTheSubmitButton() {
        assertEquals("| Id | Name | Type | Required | Max length |\n|---|---|---|---|---|\n"
            + "| studentName | studentName | text | yes | none |\n"
            + "| studentEmail | studentEmail | email | yes | none |\n"
            + "| studentDepartment | studentDepartment | select | yes | none |\n"
            + "| studentPhone | studentPhone | tel | no | 20 |\n\n"
            + "Options of studentDepartment:\n\n| Value | Label |\n|---|---|\n"
            + "| \"\" | Select department |\n| Computer Science | Computer Science |\n| Mathematics | Mathematics |\n\n"
            + "Submit button: Add student", blocks(realModel()).get("ui-controls"));
    }

    @Test
    void theRulesBlockListsTheFourValidationRules() {
        assertEquals("| UI field | Required | Min length | Pattern |\n|---|---|---|---|\n"
            + "| studentName | yes | 2 | none |\n"
            + "| studentEmail | yes | none | " + EMAIL_REGEX + " |\n"
            + "| studentDepartment | yes | none | none |\n"
            + "| studentPhone | no | none | ^(?=.{7,20}$)\\+?[0-9 -]+$ |", blocks(realModel()).get("ui-rules"));
    }

    @Test
    void theMappingBlockMapsEachUiFieldToItsApiField() {
        assertEquals("| UI field | API field | Mapping |\n|---|---|---|\n"
            + "| studentName | name | studentName" + ARROW + "name |\n"
            + "| studentEmail | email | studentEmail" + ARROW + "email |\n"
            + "| studentDepartment | department | studentDepartment" + ARROW + "department |\n"
            + "| studentPhone | phone | studentPhone" + ARROW + "phone |", blocks(realModel()).get("ui-field-mapping"));
    }

    @Test
    void theDriftBlockListsTheTwoFindingsOrSaysThereIsNone() {
        assertEquals("Report only: drift never changes the exit code.\n\n"
            + "| UI field | Property | Frontend | Backend |\n|---|---|---|---|\n"
            + "| studentName | minLength | 2 | none |\n"
            + "| studentEmail | pattern | " + EMAIL_REGEX + " | contains(\"@\") |", blocks(realModel()).get("ui-drift"));
        assertEquals("No drift found.", UiDoc.blocks(realModel(), List.<DriftFinding>of()).get("ui-drift"));
    }

    @Test
    void aPipeInAPatternIsEscapedAndTheRowKeepsItsColumnCount() {
        SourceModel real = realModel();
        List<FrontendRule> rules = new ArrayList<>(real.frontendRules());
        rules.set(0, new FrontendRule("studentName", true, 2, "^a|b$", "m"));
        SourceModel model = real.withUi(real.uiForm(), rules, real.payload(), real.backendRules());

        String row = List.of(blocks(model).get("ui-rules").split("\n")).stream().filter(l -> l.startsWith("| studentName")).findFirst().orElseThrow();

        assertTrue(row.contains("^a\\|b$"), row);
        long unescaped = java.util.stream.IntStream.range(0, row.length())
            .filter(i -> row.charAt(i) == '|' && (i == 0 || row.charAt(i - 1) != '\\')).count();
        assertEquals(5, unescaped);
    }

    @Test
    void theBodiesAreLfTextWithoutTrailingNewlineDeterministicAndFreeOfMarkerSyntax() {
        SourceModel model = realModel();
        LinkedHashMap<String, String> first = blocks(model);

        assertEquals(first, blocks(model));
        for (String body : first.values()) {
            assertFalse(body.isEmpty());
            assertFalse(body.contains("\r"));
            assertFalse(body.endsWith("\n"));
            assertFalse(body.contains("docsync:begin") || body.contains("docsync:end"));
        }
    }

    @Test
    void theCatalogGeneratesTheUiDocumentFromTheFullModelAndRejectsTheE2Model() {
        SourceModel full = realModel();
        SourceModel e2 = new SourceModel(full.routes(), full.errorCodes(), full.studentFields(), full.formParameters(),
            full.serviceMethods(), full.serviceDigest());

        assertTrue(DocCatalog.generate(full, "x").containsKey(Name.DOC_UI));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DocCatalog.generate(e2, "x"));
        assertTrue(e.getMessage().contains("analyzeWithUi"), e.getMessage());
    }

    @Test
    void markerTextInUiContentIsRejectedWithTheBlockId() {
        SourceModel real = realModel();
        SourceModel.UiForm form = new SourceModel.UiForm(List.of(
            new SourceModel.UiControl("docsync:begin x", "n", "input", "text", true, null, List.of())), "Go");
        SourceModel model = real.withUi(form, real.frontendRules(), real.payload(), real.backendRules());

        MarkerIntegrityException e = assertThrows(MarkerIntegrityException.class, () -> DocCatalog.generate(model, "x"));

        assertTrue(e.getMessage().contains("ui-controls"), e.getMessage());
    }

    @Test
    void aBacktickInTheSubmitTextIsEscapedAndNewlinesCannotBreakTheLayout() {
        SourceModel real = realModel();
        SourceModel.UiForm form = new SourceModel.UiForm(real.uiForm().controls(), "Add `student` | now");
        SourceModel model = real.withUi(form, real.frontendRules(), real.payload(), real.backendRules());

        String block = blocks(model).get("ui-controls");

        assertTrue(block.endsWith("Submit button: Add \\`student\\` \\| now"), block);
    }
}
