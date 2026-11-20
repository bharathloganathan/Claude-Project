package org.example.api;

import org.example.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonWriterTest {

    private static final List<String> STUDENT_KEYS = List.of("id", "name", "email", "department", "phone", "status");

    private static final Student SAMPLE = new Student("ab12cd34", "Ann Lee", "ann@example.org", "Computer Science", "1234567", "active");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        return (Map<String, Object>) JsonChecker.parse(json);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(String json) {
        return (List<Object>) JsonChecker.parse(json);
    }

    private static String roundTrip(String value) {
        return (String) JsonChecker.parse(JsonWriter.quote(value));
    }

    // ---- quote ----

    @Test
    void quoteEscapesQuoteAndBackslash() {
        assertEquals("\"a\\\"b\\\\c\"", JsonWriter.quote("a\"b\\c"));
    }

    @Test
    void quoteEscapesTheNamedControlCharacters() {
        assertEquals("\"\\n\"", JsonWriter.quote("\n"));
        assertEquals("\"\\t\"", JsonWriter.quote("\t"));
        assertEquals("\"\\r\"", JsonWriter.quote("\r"));
        assertEquals("\"\\b\"", JsonWriter.quote("\b"));
        assertEquals("\"\\f\"", JsonWriter.quote("\f"));
    }

    @Test
    void quoteEscapesOtherControlCharactersAsLowercaseHex() {
        assertEquals("\"\\u0001\"", JsonWriter.quote(String.valueOf((char) 0x01)));
        assertEquals("\"\\u001a\"", JsonWriter.quote(String.valueOf((char) 0x1a)));
        assertEquals("\"\\u001f\"", JsonWriter.quote(String.valueOf((char) 0x1f)));
    }

    @Test
    void quotePassesNonAsciiAndSurrogatePairsThroughUnchanged() {
        String eAcute = "caf\u00e9";
        String japanese = "\u65e5\u672c\u8a9e";
        String emoji = "ok \ud83d\ude00";
        assertEquals("\"" + eAcute + "\"", JsonWriter.quote(eAcute));
        assertEquals("\"" + japanese + "\"", JsonWriter.quote(japanese));
        assertEquals("\"" + emoji + "\"", JsonWriter.quote(emoji));
        assertEquals(eAcute, roundTrip(eAcute));
        assertEquals(japanese, roundTrip(japanese));
        assertEquals(emoji, roundTrip(emoji));
    }

    @Test
    void quoteTreatsNullAsAnEmptyString() {
        assertEquals("\"\"", JsonWriter.quote(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain", "a\"b", "back\\slash", "line\nbreak", "tab\there", "cr\r", "bs\bff\f", "\001", "\037", "\"\\\n"})
    void everyQuotedOutputIsValidJsonAndRoundTrips(String value) {
        assertEquals(value, roundTrip(value));
    }

    @Test
    void everySingleCharacterRoundTrips() {
        for (int c = 0; c <= 0xFFFF; c++) {
            String value = String.valueOf((char) c);
            assertEquals(value, roundTrip(value), "character 0x" + Integer.toHexString(c));
        }
    }

    // ---- student ----

    @Test
    void studentHasExactlyTheSixKeysInOrderWithTheRightValues() {
        Map<String, Object> parsed = object(JsonWriter.student(SAMPLE));
        assertEquals(STUDENT_KEYS, new ArrayList<>(parsed.keySet()));
        assertEquals("ab12cd34", parsed.get("id"));
        assertEquals("Ann Lee", parsed.get("name"));
        assertEquals("ann@example.org", parsed.get("email"));
        assertEquals("Computer Science", parsed.get("department"));
        assertEquals("1234567", parsed.get("phone"));
        assertEquals("active", parsed.get("status"));
    }

    @Test
    void studentExactText() {
        assertEquals("{\"id\":\"ab12cd34\",\"name\":\"Ann Lee\",\"email\":\"ann@example.org\","
            + "\"department\":\"Computer Science\",\"phone\":\"1234567\",\"status\":\"active\"}",
            JsonWriter.student(SAMPLE));
    }

    @Test
    void studentWithSpecialCharactersRoundTrips() {
        String name = "Ann \"Q\" Lee\\Jr\nline2 caf\u00e9 \u65e5\u672c \ud83d\ude00";
        Student student = new Student("id1", name, "a@b", "R&D", "", "active");
        Map<String, Object> parsed = object(JsonWriter.student(student));
        assertEquals(name, parsed.get("name"));
        assertEquals("R&D", parsed.get("department"));
    }

    @Test
    void emptyPhoneIsTheEmptyString() {
        Map<String, Object> parsed = object(JsonWriter.student(new Student("id1", "Ann", "a@b", "CS", "", "active")));
        assertEquals("", parsed.get("phone"));
    }

    // ---- students ----

    @Test
    void listOfTwoParsesToAnArrayOfTwoObjects() {
        Student second = new Student("ef56ab78", "Bob", "bob@example.org", "Mathematics", "", "active");
        List<Object> parsed = array(JsonWriter.students(List.of(SAMPLE, second)));
        assertEquals(2, parsed.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) parsed.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> other = (Map<String, Object>) parsed.get(1);
        assertEquals(STUDENT_KEYS, new ArrayList<>(first.keySet()));
        assertEquals("ab12cd34", first.get("id"));
        assertEquals("ef56ab78", other.get("id"));
        assertEquals("Bob", other.get("name"));
    }

    @Test
    void listOfTwoIsCommaSeparatedWithoutSpaces() {
        Student second = new Student("ef56ab78", "Bob", "bob@example.org", "Mathematics", "", "active");
        assertEquals("[" + JsonWriter.student(SAMPLE) + "," + JsonWriter.student(second) + "]",
            JsonWriter.students(List.of(SAMPLE, second)));
    }

    @Test
    void emptyListIsExactlyEmptyBrackets() {
        assertEquals("[]", JsonWriter.students(List.of()));
    }

    // ---- error ----

    @Test
    void errorHasExactlyTheKeysErrorThenCode() {
        Map<String, Object> parsed = object(JsonWriter.error(ErrorCode.NOT_FOUND, "Student not found"));
        assertEquals(List.of("error", "code"), new ArrayList<>(parsed.keySet()));
        assertEquals("Student not found", parsed.get("error"));
        assertEquals(ErrorCode.NOT_FOUND.name(), parsed.get("code"));
    }

    @Test
    void errorExactText() {
        assertEquals("{\"error\":\"Student not found\",\"code\":\"NOT_FOUND\"}",
            JsonWriter.error(ErrorCode.NOT_FOUND, "Student not found"));
    }

    @Test
    void errorMessageWithQuotesAndANewlineRoundTrips() {
        String message = "bad \"input\"\nsecond line";
        Map<String, Object> parsed = object(JsonWriter.error(ErrorCode.VALIDATION_ERROR, message));
        assertEquals(message, parsed.get("error"));
        assertEquals("VALIDATION_ERROR", parsed.get("code"));
    }

    // ---- deleteResult ----

    @Test
    void deleteResultExactText() {
        assertEquals("{\"deleted\":true}", JsonWriter.deleteResult(true));
        assertEquals("{\"deleted\":false}", JsonWriter.deleteResult(false));
    }

    // ---- ErrorCode ----

    @Test
    void errorCodeConstantsExistWithTheirHttpStatuses() {
        assertEquals(400, ErrorCode.valueOf("VALIDATION_ERROR").status());
        assertEquals(404, ErrorCode.valueOf("NOT_FOUND").status());
        assertEquals(405, ErrorCode.valueOf("METHOD_NOT_ALLOWED").status());
        assertEquals(500, ErrorCode.valueOf("INTERNAL_SERVER_ERROR").status());
    }

    @Test
    void errorOutputIsValidJsonForEveryErrorCode() {
        for (ErrorCode code : ErrorCode.values()) {
            Map<String, Object> parsed = object(JsonWriter.error(code, "message for " + code.name()));
            assertEquals(code.name(), parsed.get("code"));
            assertEquals("message for " + code.name(), parsed.get("error"));
        }
    }

    // ---- JsonChecker self-tests ----

    @ParameterizedTest
    @ValueSource(strings = {
        "Student[id=1, name=a, email=b]",
        "{\"a\":1,}",
        "[1,]",
        "{'a':1}",
        "{\"a\":1} x",
        "{\"a\":1}{\"b\":2}",
        "",
        "   ",
        "\"line\nbreak\"",
        "\"tab\there\"",
        "01",
        "-01",
        "{a:1}",
        "{\"a\"}",
        "[",
        "\"unterminated",
        "tru",
        "NaN",
        "+1",
        "1.",
        ".5",
        "1e",
        "-",
        "\"\\x\"",
        "\"\\u12G4\"",
        "\"\\u12\"",
        "\"\\"
    })
    void checkerRejectsInvalidJsonAndNamesThePosition(String text) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> JsonChecker.parse(text));
        assertTrue(thrown.getMessage().contains("position"), thrown.getMessage());
    }

    @Test
    void checkerRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> JsonChecker.parse(null));
    }

    @Test
    void checkerAcceptsValidNestedJson() {
        Map<String, Object> parsed = object("{\"a\":[1,2.5,true,null,{\"b\":\"c\"}],\"d\":\"\u00e9\"}");
        assertEquals(List.of("a", "d"), new ArrayList<>(parsed.keySet()));
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) parsed.get("a");
        assertEquals(5, items.size());
        assertEquals(new BigDecimal("1"), items.get(0));
        assertEquals(new BigDecimal("2.5"), items.get(1));
        assertEquals(Boolean.TRUE, items.get(2));
        assertNull(items.get(3));
        assertEquals(Map.of("b", "c"), items.get(4));
        assertEquals("\u00e9", parsed.get("d"));
    }

    @Test
    void checkerAcceptsScalarsWhitespaceAndEscapes() {
        assertEquals(new BigDecimal("0"), JsonChecker.parse("0"));
        assertEquals(new BigDecimal("-1.5E+3"), JsonChecker.parse("-1.5E+3"));
        assertEquals(Boolean.FALSE, JsonChecker.parse(" false "));
        assertNull(JsonChecker.parse("null"));
        assertEquals(List.of(), JsonChecker.parse("[]"));
        assertEquals(Map.of(), JsonChecker.parse("{}"));
        assertEquals("\u00e9\ud83d\ude00/\n", JsonChecker.parse("\"\\u00e9\\ud83d\\ude00\\/\\n\""));
        assertEquals(Arrays.asList(new BigDecimal("1"), new BigDecimal("2")), JsonChecker.parse("\n[ 1 ,\t2 ]\r\n"));
    }
}
