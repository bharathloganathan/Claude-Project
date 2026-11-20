package org.example.docsync.analysis;

import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel.UiControl;
import org.example.docsync.model.SourceModel.UiForm;
import org.example.docsync.model.SourceModel.UiOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E3-1 and RV5-01, RV5-02, RV5-05, RV5-06 (FR-E3-02, AC-E3-01, S2): the HTML scope is form#student-form only.
 * The real index.html is read read-only through the project directory (the surefire working directory);
 * fixtures use a TempDir. This source file is ASCII only.
 */
class HtmlAnalyzerTest {

    private static final String HTML_FILE = "frontend/index.html";
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static String page(String formBody) {
        return "<!DOCTYPE html>\n<html><head><title>t</title></head><body>\n"
            + "<form id=\"student-form\">\n" + formBody + "\n</form>\n</body></html>\n";
    }

    private static final String BUTTON = "<button type=\"submit\">Add</button>";

    private static RootPaths fixture(Path dir, String html) throws IOException {
        RootPaths roots = RootPaths.of(dir.toString());
        Path file = roots.path(RootPaths.Name.FRONTEND_HTML);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return roots;
    }

    private static UiForm analyze(RootPaths roots) {
        return new HtmlAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    private static UiForm analyze(Path dir, String html) throws IOException {
        return analyze(fixture(dir, html));
    }

    private static UnsupportedConstructException rejected(Path dir, String html, String fragment) throws IOException {
        RootPaths roots = fixture(dir, html);
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class, () -> analyze(roots));
        assertTrue(e.getMessage().contains(HTML_FILE), e.getMessage());
        assertTrue(e.getMessage().contains(fragment), "message should contain [" + fragment + "]: " + e.getMessage());
        assertFalse(e.getMessage().contains(dir.toString()), e.getMessage());
        return e;
    }

    @Test
    void theRealIndexHtmlHasTheFourControlsTheDepartmentOptionsAndTheSubmitText() {
        RootPaths roots = RootPaths.of(".");

        UiForm form = analyze(roots);

        assertEquals(List.of(
            new UiControl("studentName", "studentName", "input", "text", true, null, List.of()),
            new UiControl("studentEmail", "studentEmail", "input", "email", true, null, List.of()),
            new UiControl("studentDepartment", "studentDepartment", "select", "select", true, null, List.of(
                new UiOption("", "Select department"),
                new UiOption("Computer Science", "Computer Science"),
                new UiOption("Mathematics", "Mathematics"))),
            new UiControl("studentPhone", "studentPhone", "input", "tel", false, "20", List.of())), form.controls());
        assertEquals("Add student", form.submitText());
    }

    @Test
    void aFixtureFormIsReadWithSingleOrDoubleQuotesCommentsAndUnknownAttributes(@TempDir Path dir) throws IOException {
        UiForm form = analyze(dir, page(
            "<!-- a comment <input id=\"ghost\" name=\"ghost\"> -->\n"
                + "<label for='a'>A</label>\n"
                + "<input id='a' name='a' type='text' class=\"wide\" data-x='1' required>\n"
                + "<input id=\"b\" name=\"b\" required=\"\" />\n"
                + "<input id=\"c\" name=\"c\" type=\"number\" required=\"required\" maxlength=\"5\">\n"
                + "<button type=\"submit\">Go</button>"));

        assertEquals(List.of(
            new UiControl("a", "a", "input", "text", true, null, List.of()),
            new UiControl("b", "b", "input", "text", true, null, List.of()),
            new UiControl("c", "c", "input", "number", true, "5", List.of())), form.controls());
        assertEquals("Go", form.submitText());
    }

    @Test
    void anOptionWithoutAValueUsesItsTextAndWhitespaceIsTrimmed(@TempDir Path dir) throws IOException {
        UiControl select = analyze(dir, page(
            "<select id=\"s\" name=\"s\">\n<option>  One  </option>\n<option value=\"2\">Two</option>\n</select>\n" + BUTTON))
            .controls().get(0);

        assertEquals(List.of(new UiOption("One", "One"), new UiOption("2", "Two")), select.options());
        assertFalse(select.required());
    }

    @Test
    void elementsOutsideTheFormAndOtherFormsAreIgnored(@TempDir Path dir) throws IOException {
        String html = "<html><head><script>if (1 < 2) { var x = '<div>'; }</script><style>a > b {}</style></head><body>\n"
            + "<div><input id=\"out\" name=\"out\"></div>\n<form id=\"other\"><textarea></textarea></form>\n"
            + "<form id=\"student-form\"><input id=\"in\" name=\"in\">" + BUTTON + "</form></body></html>";

        assertEquals(List.of("in"), analyze(dir, html).controls().stream().map(UiControl::id).toList());
    }

    @Test
    void nothingOutsideTheFormCanMakeTheAnalysisFail(@TempDir Path dir) throws IOException {
        String html = "<html><head><meta charset=utf-8><!-- never closed? no: closed --></head><body class=main>\n"
            + "<p>1 < 2 and <b unterminated</p>\n<a href=/x>link</a>\n"
            + "<form id=\"student-form\"><input id=\"a\" name=\"a\" size=5 class=wide>" + BUTTON + "</form></body></html>";

        UiForm form = analyze(dir, html);

        assertEquals(List.of(new UiControl("a", "a", "input", "text", false, null, List.of())), form.controls());
    }

    @Test
    void aCommentedOutStudentFormIsNotCounted(@TempDir Path dir) throws IOException {
        String html = "<!-- <form id=\"student-form\"><input id=\"old\" name=\"old\"></form> -->\n"
            + page("<input id=\"a\" name=\"a\">" + BUTTON);

        assertEquals(List.of("a"), analyze(dir, html).controls().stream().map(UiControl::id).toList());
    }

    @Test
    void unknownAttributesMayBeUnquotedInsideTheForm(@TempDir Path dir) throws IOException {
        UiForm form = analyze(dir, page("<input id=\"a\" name=\"a\" size=5 data-x=a/b autofocus>\n" + BUTTON));

        assertEquals(List.of(new UiControl("a", "a", "input", "text", false, null, List.of())), form.controls());
    }

    @Test
    void tagAndAttributeNamesAreCaseInsensitive(@TempDir Path dir) throws IOException {
        String html = "<HTML><BODY><FORM ID=\"student-form\"><LABEL FOR=\"a\">A</LABEL><INPUT ID=\"a\" NAME=\"a\" TYPE=\"Text\" REQUIRED>"
            + "<BUTTON>Go</BUTTON></FORM></BODY></HTML>";

        UiForm form = analyze(dir, html);

        assertEquals(List.of(new UiControl("a", "a", "input", "Text", true, null, List.of())), form.controls());
        assertEquals("Go", form.submitText());
    }

    @Test
    void whitespaceInExtractedValuesIsCollapsed(@TempDir Path dir) throws IOException {
        UiForm form = analyze(dir, page("<input id=\"a\n  b\" name=\"first\n second\" type=\"te\nxt\" maxlength=\" 5\n\">\n"
            + "<button>Add\n   a  student</button>"));

        assertEquals(List.of(new UiControl("a b", "first second", "input", "te xt", false, "5", List.of())), form.controls());
        assertEquals("Add a student", form.submitText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"div", "textarea", "a", "fieldset", "span", "p", "script", "form"})
    void anElementThatIsNotAllowedInsideTheFormIsRejected(String tag, @TempDir Path dir) throws IOException {
        rejected(dir, page("<input id=\"a\" name=\"a\">\n<" + tag + ">x</" + tag + ">\n" + BUTTON), "element <" + tag + "> is not allowed");
    }

    @Test
    void aMissingOrDoubledStudentFormIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, "<html><body><form id=\"other\"></form></body></html>", "must exist exactly once");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, page("<input id=\"a\" name=\"a\">" + BUTTON) + page("<input id=\"b\" name=\"b\">" + BUTTON),
            "must exist exactly once");
    }

    @ParameterizedTest
    @ValueSource(strings = {"required=\"false\"", "required=\"yes\""})
    void aRequiredValueOutsideTheSupportedFormsIsRejected(String attribute, @TempDir Path dir) throws IOException {
        rejected(dir, page("<input id=\"a\" name=\"a\" " + attribute + ">\n" + BUTTON), "unsupported required attribute value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"maxlength=7", "type=text", "name2=1 id=x"})
    void anUnquotedValueOfAnExtractedAttributeIsRejected(String attribute, @TempDir Path dir) throws IOException {
        String control = attribute.startsWith("name2") ? "<input name=\"a\" id=x>" : "<input id=\"a\" name=\"a\" " + attribute + ">";
        rejected(dir, page(control + "\n" + BUTTON), "needs a quoted value");
    }

    @Test
    void aControlWithoutAnIdOrANameIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, page("<input name=\"a\">\n" + BUTTON), "needs an id and a name");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, page("<input id=\"a\">\n" + BUTTON), "needs an id and a name");
        Path third = Files.createDirectories(dir.resolve("third"));
        rejected(third, page("<select name=\"s\"><option>x</option></select>\n" + BUTTON), "needs an id and a name");
    }

    @Test
    void aDuplicateControlIdOrNameIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, page("<input id=\"a\" name=\"a\"><input id=\"a\" name=\"b\">" + BUTTON), "duplicate control id a");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, page("<input id=\"a\" name=\"a\"><input id=\"b\" name=\"a\">" + BUTTON), "duplicate control name a");
    }

    @Test
    void theFormNeedsExactlyOneSubmitButton(@TempDir Path dir) throws IOException {
        rejected(dir, page("<input id=\"a\" name=\"a\">"), "exactly one button");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, page("<input id=\"a\" name=\"a\">" + BUTTON + "<button>Two</button>"), "exactly one button");
    }

    @Test
    void anUnterminatedFormCommentOrTagIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, page("<!-- never closed\n<input id=\"a\" name=\"a\">" + BUTTON), "is not closed");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, "<form id=\"student-form\"><input id=\"a\" name=\"a\"\n</form>", "is not terminated");
        Path third = Files.createDirectories(dir.resolve("third"));
        rejected(third, "<form id=\"student-form\"><input id=\"a\" name=\"a\">" + BUTTON, "is not closed");
    }

    @Test
    void aMissingIndexHtmlIsReportedAsMissing(@TempDir Path dir) {
        RootPaths roots = RootPaths.of(dir.toString());
        MissingExpectedException e = assertThrows(MissingExpectedException.class, () -> analyze(roots));
        assertTrue(e.getMessage().contains(HTML_FILE), e.getMessage());
        assertFalse(e.getMessage().contains(dir.toString()), e.getMessage());
    }

    @Test
    void aLeadingBomAndCrlfDoNotChangeTheResult(@TempDir Path dir) throws IOException {
        String html = page("<input id=\"a\" name=\"a\" required>\n" + BUTTON);
        UiForm plain = analyze(fixture(dir, html));
        Path crlfDir = Files.createDirectories(dir.resolve("crlf"));
        UiForm bom = analyze(fixture(crlfDir, BOM + html.replace("\n", "\r\n")));
        assertEquals(plain, bom);
        assertNull(bom.controls().get(0).maxLength());
    }
}
