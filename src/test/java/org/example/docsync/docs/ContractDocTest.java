package org.example.docsync.docs;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.Field;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task E2-6 (FR-E2-03, FR-E2-22; AC-E2-02): the student contract document blocks. */
class ContractDocTest {

    private static SourceModel analyzeRealProject() {
        RootPaths roots = RootPaths.of(".");
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    @Test
    void blocksAreTheFieldsAndCreateParamsInCatalogOrder() {
        assertEquals(List.of("contract-student-fields", "contract-create-params"),
            List.copyOf(ContractDoc.blocks(analyzeRealProject()).keySet()));
    }

    @Test
    void realFieldsBlockHasTheSixRecordComponentsInOrder() {
        String expected = String.join("\n",
            "| Field | Type |",
            "|---|---|",
            "| id | String |",
            "| name | String |",
            "| email | String |",
            "| department | String |",
            "| phone | String |",
            "| status | String |");
        assertEquals(expected, ContractDoc.blocks(analyzeRealProject()).get("contract-student-fields"));
    }

    @Test
    void realCreateParamsBlockHasTheFourParametersWithPositionsOneToFour() {
        String expected = String.join("\n",
            "| Parameter | Position |",
            "|---|---|",
            "| name | 1 |",
            "| email | 2 |",
            "| department | 3 |",
            "| phone | 4 |");
        assertEquals(expected, ContractDoc.blocks(analyzeRealProject()).get("contract-create-params"));
    }

    @Test
    void anAdditionalFieldAndParameterInTheModelAppearAsLastRows() {
        SourceModel real = analyzeRealProject();
        List<Field> fields = new ArrayList<>(real.studentFields());
        fields.add(new Field("grade", "int"));
        List<String> parameters = new ArrayList<>(real.formParameters());
        parameters.add("grade");
        SourceModel model = new SourceModel(real.routes(), real.errorCodes(), fields, parameters,
            real.serviceMethods(), real.serviceDigest());
        var blocks = ContractDoc.blocks(model);
        assertTrue(blocks.get("contract-student-fields").endsWith("| status | String |\n| grade | int |"));
        assertTrue(blocks.get("contract-create-params").endsWith("| phone | 4 |\n| grade | 5 |"));
    }

    @Test
    void generatingTwiceGivesEqualBlocks() {
        assertEquals(ContractDoc.blocks(analyzeRealProject()), ContractDoc.blocks(analyzeRealProject()));
    }
}
