package org.example.docsync.docs;

import org.example.api.ErrorCode;
import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.ErrorCodeInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task E2-6 (FR-E2-03, FR-E2-22, FR-E1-11; AC-E1-10, AC-E2-02): the endpoints document blocks. */
class EndpointsDocTest {

    private static SourceModel analyzeRealProject() {
        RootPaths roots = RootPaths.of(".");
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    private static SourceModel withErrorCodes(SourceModel base, List<ErrorCodeInfo> codes) {
        return new SourceModel(base.routes(), codes, base.studentFields(), base.formParameters(),
            base.serviceMethods(), base.serviceDigest());
    }

    @Test
    void blocksAreTheApiRoutesAndApiErrorsInCatalogOrder() {
        assertEquals(List.of("api-routes", "api-errors"), List.copyOf(EndpointsDoc.blocks(analyzeRealProject()).keySet()));
    }

    @Test
    void realRoutesBlockHasExactlyTheFourRoutesInSourceOrder() {
        String expected = String.join("\n",
            "| Method | Path | Summary | Request | Response |",
            "|---|---|---|---|---|",
            "| GET | /students | List all students | none | Student[] |",
            "| POST | /students | Create a student | StudentRequest | Student |",
            "| GET | /students/{id} | Fetch a student by id | none | Student |",
            "| DELETE | /students/{id} | Delete a student | none | DeleteResponse |");
        assertEquals(expected, EndpointsDoc.blocks(analyzeRealProject()).get("api-routes"));
    }

    @Test
    void realErrorsBlockHasTheErrorBodySentenceAndTheCodeTable() {
        String expected = String.join("\n",
            "Error responses use the body {\"error\":\"<message>\",\"code\":\"<CODE>\"}.",
            "",
            "| Code | HTTP status |",
            "|---|---|",
            "| VALIDATION_ERROR | 400 |",
            "| NOT_FOUND | 404 |",
            "| METHOD_NOT_ALLOWED | 405 |",
            "| INTERNAL_SERVER_ERROR | 500 |");
        assertEquals(expected, EndpointsDoc.blocks(analyzeRealProject()).get("api-errors"));
    }

    // AC-E1-10 part 1: a minimum baseline, not an exhaustive list of codes.
    @Test
    void baselineRequiredErrorCodesAreStillPresentInTheErrorCodeEnum() {
        Set<String> present = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());
        for (String required : List.of("VALIDATION_ERROR", "NOT_FOUND", "METHOD_NOT_ALLOWED", "INTERNAL_SERVER_ERROR")) {
            assertTrue(present.contains(required), "ErrorCode lost the required constant " + required);
        }
    }

    // AC-E1-10 part 2: no hard-coded list; every constant of the real enum must be documented with its status.
    @Test
    void everyConstantOfTheRealErrorCodeEnumHasARowWithItsStatus() {
        String block = EndpointsDoc.blocks(analyzeRealProject()).get("api-errors");
        List<String> lines = List.of(block.split("\n"));
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(lines.contains("| " + code.name() + " | " + code.status() + " |"),
                "api-errors has no row for " + code.name() + " with status " + code.status());
        }
        long rows = lines.stream().filter(line -> line.matches("\\| [A-Z_]+ \\| [0-9]+ \\|")).count();
        assertEquals(ErrorCode.values().length, rows);
    }

    @Test
    void anAdditionalErrorCodeInTheModelAutomaticallyGetsARow() {
        SourceModel real = analyzeRealProject();
        List<ErrorCodeInfo> codes = new ArrayList<>(real.errorCodes());
        codes.add(new ErrorCodeInfo("CONFLICT", 409));
        String block = EndpointsDoc.blocks(withErrorCodes(real, codes)).get("api-errors");
        assertTrue(List.of(block.split("\n")).contains("| CONFLICT | 409 |"));
        assertTrue(block.endsWith("| INTERNAL_SERVER_ERROR | 500 |\n| CONFLICT | 409 |"));
    }

    @Test
    void generatingTwiceGivesEqualBlocks() {
        SourceModel model = analyzeRealProject();
        LinkedHashMap<String, String> first = EndpointsDoc.blocks(model);
        assertEquals(first, EndpointsDoc.blocks(model));
        assertEquals(first, EndpointsDoc.blocks(analyzeRealProject()));
    }
}
