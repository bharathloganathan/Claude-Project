package org.example.docsync.docs;

import org.example.docsync.error.DocSyncException;
import org.example.docsync.error.MarkerIntegrityException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkedDocumentTest {

    private static final Set<String> IDS = Set.of("a", "b", "c", "api-routes", "contract-student-fields");

    private static String begin(String id) {
        return "<!-- docsync:begin " + id + " -->";
    }

    private static String end(String id) {
        return "<!-- docsync:end " + id + " -->";
    }

    private static MarkedDocument parse(String text) {
        return MarkedDocument.parse(text, IDS);
    }

    private static MarkerIntegrityException fault(String text) {
        return assertThrows(MarkerIntegrityException.class, () -> parse(text));
    }

    private static void assertFault(String text, int line, String id) {
        String message = fault(text).getMessage();
        assertTrue(message.contains("line " + line + ")") || message.contains("line " + line + ","), message);
        if (id != null) {
            assertTrue(message.contains("'" + id + "'"), message);
        }
        assertTrue(message.contains("must not contain literal"), message);
    }

    // AC-E2-03: exact marker syntax and stable ids

    @Test
    void markerSyntaxIsExact() {
        assertEquals("<!-- docsync:begin api-routes -->", begin("api-routes"));
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("api-routes", "x");
        String text = MarkedDocument.create("T", blocks);
        assertTrue(text.contains("\n<!-- docsync:begin api-routes -->\n"));
        assertTrue(text.contains("\n<!-- docsync:end api-routes -->\n"));
    }

    @Test
    void parsesSingleBlock() {
        MarkedDocument doc = parse("# T\n\n" + begin("a") + "\nrow\n" + end("a") + "\n");
        assertTrue(doc.hasBlock("a"));
        assertFalse(doc.hasBlock("b"));
        assertEquals(Optional.of("row"), doc.body("a"));
    }

    @Test
    void parsesSeveralBlocksInOneFile() {
        String text = "# T\n\n" + begin("a") + "\n1\n" + end("a") + "\n\nmanual\n\n"
                + begin("b") + "\n2\n" + end("b") + "\n\n" + begin("c") + "\n3\n" + end("c") + "\n";
        MarkedDocument doc = parse(text);
        assertEquals(Optional.of("1"), doc.body("a"));
        assertEquals(Optional.of("2"), doc.body("b"));
        assertEquals(Optional.of("3"), doc.body("c"));
    }

    @Test
    void textWithoutAnyMarkerHasNoBlocks() {
        MarkedDocument doc = parse("# T\n\nplain text\n");
        assertFalse(doc.hasBlock("a"));
        assertEquals(Optional.empty(), doc.body("a"));
    }

    @Test
    void emptyTextParses() {
        MarkedDocument doc = parse("");
        assertFalse(doc.hasBlock("a"));
        assertEquals(MarkedDocument.LineEnding.LF, doc.lineEnding());
    }

    @Test
    void endMarkerAtEndOfTextWithoutNewlineIsValid() {
        MarkedDocument doc = parse(begin("a") + "\nx\n" + end("a"));
        assertEquals(Optional.of("x"), doc.body("a"));
        assertEquals(begin("a") + "\ny\n" + end("a"), doc.withBlock("a", "y"));
    }

    @Test
    void markerPairInsideCodeFenceIsOrdinaryBlock() {
        String text = "```\n" + begin("a") + "\nin fence\n" + end("a") + "\n```\n";
        MarkedDocument doc = parse(text);
        assertEquals(Optional.of("in fence"), doc.body("a"));
    }

    // body extraction

    @Test
    void bodyIsLfNormalizedFromCrlfDocument() {
        MarkedDocument doc = parse("# T\r\n" + begin("a") + "\r\nx\r\ny\r\n" + end("a") + "\r\n");
        assertEquals(Optional.of("x\ny"), doc.body("a"));
    }

    @Test
    void emptyBodyIsEmptyString() {
        MarkedDocument doc = parse(begin("a") + "\n" + end("a") + "\n");
        assertTrue(doc.hasBlock("a"));
        assertEquals(Optional.of(""), doc.body("a"));
    }

    @Test
    void multiLineBodyKeepsInnerBlankLinesAndNoTrailingNewline() {
        MarkedDocument doc = parse(begin("a") + "\nl1\nl2\n\nl4\n" + end("a") + "\n");
        assertEquals(Optional.of("l1\nl2\n\nl4"), doc.body("a"));
    }

    // line ending detection

    @Test
    void lineEndingIsLfWithoutCrlf() {
        assertEquals(MarkedDocument.LineEnding.LF, parse("a\nb\n").lineEnding());
    }

    @Test
    void lineEndingIsCrlfWhenAnyCrlfPresent() {
        assertEquals(MarkedDocument.LineEnding.CRLF, parse("a\r\nb\r\n").lineEnding());
        assertEquals(MarkedDocument.LineEnding.CRLF, parse("a\nb\r\nc\n").lineEnding());
    }

    // AC-E2-04: manual text byte-identical, only the matching block changes

    @Test
    void replaceKeepsManualTextBeforeBetweenAndAfterByteIdentical() {
        String prefix = "Intro  with trailing spaces  \n\t tab line\n\n";
        String between = "\nmanual between\n\n\n";
        String suffix = "\nTail text without final newline";
        String old = prefix + begin("a") + "\nold\n" + end("a") + "\n" + between
                + begin("b") + "\nkeep\n" + end("b") + "\n" + suffix;
        String expected = prefix + begin("a") + "\nnew1\nnew2\n" + end("a") + "\n" + between
                + begin("b") + "\nkeep\n" + end("b") + "\n" + suffix;
        String result = parse(old).withBlock("a", "new1\nnew2");
        assertEquals(expected, result);
        assertTrue(result.startsWith(prefix + begin("a") + "\n"));
        assertTrue(result.endsWith(between + begin("b") + "\nkeep\n" + end("b") + "\n" + suffix));
        assertEquals(Optional.of("keep"), parse(result).body("b"));
    }

    @Test
    void replaceOfLaterBlockLeavesEarlierBlockUntouched() {
        String old = begin("a") + "\nA\n" + end("a") + "\nmid\n" + begin("b") + "\nold\n" + end("b") + "\n";
        String expected = begin("a") + "\nA\n" + end("a") + "\nmid\n" + begin("b") + "\nnew\n" + end("b") + "\n";
        assertEquals(expected, parse(old).withBlock("b", "new"));
    }

    @Test
    void replaceWithEmptyBodyLeavesAdjacentMarkers() {
        String old = "x\n" + begin("a") + "\nold\n" + end("a") + "\ny\n";
        String result = parse(old).withBlock("a", "");
        assertEquals("x\n" + begin("a") + "\n" + end("a") + "\ny\n", result);
        assertEquals(Optional.of(""), parse(result).body("a"));
    }

    @Test
    void replaceKeepsCrlfInBlockAndUntouchedText() {
        String old = "Intro\r\n\r\n" + begin("a") + "\r\nold\r\n" + end("a") + "\r\n\r\nOutro\r\n";
        String expected = "Intro\r\n\r\n" + begin("a") + "\r\nn1\r\nn2\r\n" + end("a") + "\r\n\r\nOutro\r\n";
        assertEquals(expected, parse(old).withBlock("a", "n1\nn2"));
    }

    @Test
    void lfDocumentStaysLf() {
        String old = "Intro\n" + begin("a") + "\nold\n" + end("a") + "\n";
        String result = parse(old).withBlock("a", "n1\nn2");
        assertEquals("Intro\n" + begin("a") + "\nn1\nn2\n" + end("a") + "\n", result);
        assertFalse(result.contains("\r"));
    }

    @Test
    void mixedEndingsUseCrlfForGeneratedLinesAndKeepUntouchedBytes() {
        String old = "A\r\n" + begin("a") + "\nold\n" + end("a") + "\r\ntail\n";
        MarkedDocument doc = parse(old);
        assertEquals(MarkedDocument.LineEnding.CRLF, doc.lineEnding());
        assertEquals("A\r\n" + begin("a") + "\nn1\r\nn2\r\n" + end("a") + "\r\ntail\n", doc.withBlock("a", "n1\nn2"));
    }

    @Test
    void bodyArgumentWithCrlfAndLoneCrIsNormalizedFirst() {
        String old = begin("a") + "\nold\n" + end("a") + "\n";
        assertEquals(begin("a") + "\nx\ny\nz\n" + end("a") + "\n", parse(old).withBlock("a", "x\r\ny\rz"));
    }

    @Test
    void replaceIsIdempotent() {
        String old = "T\n" + begin("a") + "\nold\n" + end("a") + "\n";
        String once = parse(old).withBlock("a", "new");
        assertEquals(once, parse(once).withBlock("a", "new"));
    }

    // append when the block is absent

    @Test
    void appendsToTextWithTrailingNewline() {
        String old = "# T\n\nmanual\n";
        String result = parse(old).withBlock("a", "x\ny");
        assertEquals(old + "\n" + begin("a") + "\nx\ny\n" + end("a") + "\n", result);
        assertTrue(result.startsWith(old));
    }

    @Test
    void appendsToTextWithoutTrailingNewline() {
        String old = "# T\n\nmanual";
        assertEquals(old + "\n\n" + begin("a") + "\nx\n" + end("a") + "\n", parse(old).withBlock("a", "x"));
    }

    @Test
    void appendUsesCrlfOfCrlfDocument() {
        String old = "# T\r\nmanual";
        assertEquals(old + "\r\n\r\n" + begin("a") + "\r\nx\r\ny\r\n" + end("a") + "\r\n",
                parse(old).withBlock("a", "x\ny"));
    }

    @Test
    void appendToEmptyTextWritesBlockDirectly() {
        assertEquals(begin("a") + "\nx\n" + end("a") + "\n", parse("").withBlock("a", "x"));
    }

    @Test
    void appendAfterExistingBlockKeepsItUntouched() {
        String old = begin("a") + "\nA\n" + end("a") + "\n";
        String result = parse(old).withBlock("b", "B");
        assertEquals(old + "\n" + begin("b") + "\nB\n" + end("b") + "\n", result);
        assertEquals(Optional.of("A"), parse(result).body("a"));
        assertEquals(Optional.of("B"), parse(result).body("b"));
    }

    @Test
    void appendIsIdempotent() {
        String once = parse("# T\n\nmanual\n").withBlock("a", "x");
        assertEquals(once, parse(once).withBlock("a", "x"));
    }

    // generated body and id validation

    @Test
    void withBlockRejectsBodyContainingMarkerSyntax() {
        MarkedDocument doc = parse(begin("a") + "\nold\n" + end("a") + "\n");
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("a", "x docsync:begin y"));
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("a", "ok\n" + end("a")));
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("b", "docsync:end"));
    }

    @Test
    void withBlockRejectsInvalidId() {
        MarkedDocument doc = parse("text\n");
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("", "x"));
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("A", "x"));
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("a b", "x"));
        assertThrows(MarkerIntegrityException.class, () -> doc.withBlock("a_b", "x"));
    }

    @Test
    void withBlockIsDeterministic() {
        String old = "T\n" + begin("a") + "\nold\n" + end("a") + "\n";
        assertEquals(parse(old).withBlock("a", "n"), parse(old).withBlock("a", "n"));
        assertEquals(parse(old).withBlock("b", "n"), parse(old).withBlock("b", "n"));
    }

    // create

    @Test
    void createWritesExactLayout() {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("b", "l1\nl2");
        blocks.put("a", "r");
        String expected = "# API Endpoints\n\n"
                + begin("b") + "\nl1\nl2\n" + end("b") + "\n\n"
                + begin("a") + "\nr\n" + end("a") + "\n";
        assertEquals(expected, MarkedDocument.create("API Endpoints", blocks));
    }

    @Test
    void createNormalizesBodyLineEndingsToLf() {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("a", "x\r\ny");
        assertEquals("# T\n\n" + begin("a") + "\nx\ny\n" + end("a") + "\n", MarkedDocument.create("T", blocks));
    }

    @Test
    void createWithEmptyBodyAndNoBlocks() {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        assertEquals("# T\n", MarkedDocument.create("T", blocks));
        blocks.put("a", "");
        assertEquals("# T\n\n" + begin("a") + "\n" + end("a") + "\n", MarkedDocument.create("T", blocks));
    }

    @Test
    void createdTextParsesBackAndIsDeterministic() {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("a", "x");
        blocks.put("b", "y\nz");
        String text = MarkedDocument.create("T", blocks);
        MarkedDocument doc = parse(text);
        assertEquals(Optional.of("x"), doc.body("a"));
        assertEquals(Optional.of("y\nz"), doc.body("b"));
        assertEquals(text, MarkedDocument.create("T", blocks));
        assertEquals(text, doc.withBlock("a", "x"));
    }

    @Test
    void createRejectsBodyContainingMarkerSyntax() {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("a", "see docsync:end here");
        assertThrows(MarkerIntegrityException.class, () -> MarkedDocument.create("T", blocks));
    }

    @Test
    void createRejectsInvalidId() {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        blocks.put("Bad Id", "x");
        assertThrows(MarkerIntegrityException.class, () -> MarkedDocument.create("T", blocks));
    }

    // integrity faults (FR-E2-09, DR-12, DR-13)

    @Test
    void markerIntegrityExceptionIsADocSyncException() {
        assertInstanceOf(DocSyncException.class, new MarkerIntegrityException("m"));
        assertInstanceOf(DocSyncException.class, new MarkerIntegrityException("m", new RuntimeException()));
    }

    @Test
    void faultBeginWithoutEnd() {
        assertFault("# T\n\n" + begin("a") + "\nx\n", 3, "a");
    }

    @Test
    void faultEndWithoutBegin() {
        assertFault("x\n" + end("a") + "\n", 2, "a");
    }

    @Test
    void faultEndAfterClosedBlockWithoutBegin() {
        assertFault(begin("a") + "\n" + end("a") + "\n" + end("a") + "\n", 3, "a");
    }

    @Test
    void faultMismatchedEndId() {
        String message = fault(begin("a") + "\nx\n" + end("b") + "\n").getMessage();
        assertTrue(message.contains("line 3"), message);
        assertTrue(message.contains("'b'"), message);
        assertTrue(message.contains("'a'"), message);
        assertTrue(message.contains("must not contain literal"), message);
    }

    @Test
    void faultNestedBegin() {
        String message = fault(begin("a") + "\n" + begin("b") + "\n" + end("b") + "\n" + end("a") + "\n").getMessage();
        assertTrue(message.contains("line 2"), message);
        assertTrue(message.contains("'b'"), message);
        assertTrue(message.contains("'a'"), message);
    }

    @Test
    void faultDuplicateId() {
        assertFault(begin("a") + "\n1\n" + end("a") + "\n\n" + begin("a") + "\n2\n" + end("a") + "\n", 5, "a");
    }

    @Test
    void faultUnknownId() {
        assertFault("x\n" + begin("zzz") + "\n" + end("zzz") + "\n", 2, "zzz");
    }

    @Test
    void faultLineNumbersCountCrlfLines() {
        assertFault("a\r\nb\r\n" + begin("a") + "\r\nx\r\n", 3, "a");
    }

    @Test
    void faultMalformedNoSpaces() {
        assertFault("x\n<!--docsync:begin a-->\n", 2, null);
    }

    @Test
    void faultMalformedExtraSpaces() {
        assertFault("x\n<!--  docsync:begin a  -->\n", 2, null);
        assertFault("x\n<!-- docsync:begin  a -->\n", 2, null);
    }

    @Test
    void faultMalformedLeadingSpace() {
        assertFault("x\n " + begin("a") + "\n", 2, null);
    }

    @Test
    void faultMalformedUppercaseId() {
        assertFault("x\n<!-- docsync:begin A -->\n", 2, null);
    }

    @Test
    void faultMalformedEmptyId() {
        assertFault("x\n<!-- docsync:begin  -->\n", 2, null);
        assertFault("x\n<!-- docsync:begin -->\n", 2, null);
    }

    @Test
    void faultMalformedTrailingText() {
        assertFault("x\n" + begin("a") + " trailing\n", 2, null);
        assertFault(begin("a") + "\nx\n" + end("a") + " trailing\n", 3, null);
    }

    @Test
    void faultMalformedTextBeforeMarker() {
        assertFault("x\nnote " + begin("a") + "\n", 2, null);
    }

    @Test
    void faultMalformedMarkerInsideSentence() {
        assertFault("x\nUse " + begin("a") + " to start a block.\n", 2, null);
        assertFault("x\nSee the docsync:end marker for details.\n", 2, null);
    }

    @Test
    void faultMalformedWithoutCommentSyntax() {
        assertFault("x\ndocsync:begin a\n", 2, null);
        assertFault(begin("a") + "\ndocsync:end a\n" + end("a") + "\n", 2, null);
    }

    @Test
    void faultMessageHasNoAbsolutePath() {
        String message = fault("x\n" + begin("zzz") + "\n").getMessage();
        assertFalse(message.contains(":\\"), message);
        assertFalse(message.contains("/Users/"), message);
    }
}
