package org.example.docsync.io;

import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.SourceReadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading project files through logical names (task E2-1, DR-14): a leading UTF-8 BOM is stripped,
 * malformed UTF-8 is rejected and never replaced, and all line endings become LF. The class is
 * read-only. Messages use root-relative paths and never an absolute path. This source file is ASCII
 * only: special characters are built from code points, never typed.
 */
class ProjectFilesTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String BOM_CHAR = String.valueOf((char) 0xFEFF);
    private static final String E_ACUTE = String.valueOf((char) 0xE9);
    private static final String GRINNING_FACE = new String(Character.toChars(0x1F600));
    private static final String REPLACEMENT_CHAR = String.valueOf((char) 0xFFFD);
    private static final RootPaths.Name SOURCE = RootPaths.Name.SERVICE_SOURCE;

    @TempDir
    Path temp;

    private RootPaths roots;
    private ProjectFiles files;

    private void init() throws IOException {
        Path project = Files.createDirectories(temp.resolve("project"));
        roots = RootPaths.of(project.toString(), temp);
        files = new ProjectFiles(roots);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] all = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, all, offset, part.length);
            offset += part.length;
        }
        return all;
    }

    private void put(RootPaths.Name name, byte[] content) throws IOException {
        Path target = roots.path(name);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private void put(RootPaths.Name name, String text) throws IOException {
        put(name, utf8(text));
    }

    private void assertNoAbsolutePath(String message) {
        String root = roots.root().toString();
        assertFalse(message.contains(root), "the message must not contain an absolute path: " + message);
        assertFalse(message.contains(root.replace('\\', '/')), "the message must not contain an absolute path: " + message);
    }

    // ---- line endings ----

    @Test
    void lfTextIsReadUnchanged() throws IOException {
        init();
        put(SOURCE, "a\nb\n");
        assertEquals("a\nb\n", files.readText(SOURCE));
    }

    @Test
    void crlfBecomesLf() throws IOException {
        init();
        put(SOURCE, "a\r\nb\r\n");
        assertEquals("a\nb\n", files.readText(SOURCE));
    }

    @Test
    void aLoneCrBecomesLf() throws IOException {
        init();
        put(SOURCE, "a\rb\r");
        assertEquals("a\nb\n", files.readText(SOURCE));
    }

    @Test
    void mixedEndingsAllBecomeLf() throws IOException {
        init();
        put(SOURCE, "a\r\nb\rc\nd");
        assertEquals("a\nb\nc\nd", files.readText(SOURCE));
    }

    @Test
    void aCrFollowedByCrLfGivesTwoLineBreaks() throws IOException {
        init();
        put(SOURCE, "a\r\r\nb");
        assertEquals("a\n\nb", files.readText(SOURCE));
    }

    @Test
    void aFinalNewlineIsNeitherAddedNorRemoved() throws IOException {
        init();
        put(SOURCE, "abc");
        assertEquals("abc", files.readText(SOURCE));
        put(SOURCE, "abc\n");
        assertEquals("abc\n", files.readText(SOURCE));
    }

    @Test
    void anEmptyFileIsAnEmptyString() throws IOException {
        init();
        put(SOURCE, new byte[0]);
        assertEquals("", files.readText(SOURCE));
    }

    // ---- BOM ----

    @Test
    void aLeadingBomIsStripped() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("hi")));
        assertEquals("hi", files.readText(SOURCE));
    }

    @Test
    void aFileWithOnlyABomIsEmpty() throws IOException {
        init();
        put(SOURCE, BOM);
        assertEquals("", files.readText(SOURCE));
    }

    @Test
    void onlyTheFirstBomIsStripped() throws IOException {
        init();
        put(SOURCE, concat(BOM, BOM, utf8("x")));
        assertEquals(BOM_CHAR + "x", files.readText(SOURCE));
    }

    @Test
    void aBomInTheMiddleIsKept() throws IOException {
        init();
        put(SOURCE, concat(utf8("a"), BOM, utf8("b")));
        assertEquals("a" + BOM_CHAR + "b", files.readText(SOURCE));
    }

    @Test
    void aBomAndCrlfAreBothHandled() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("a\r\nb")));
        assertEquals("a\nb", files.readText(SOURCE));
    }

    // ---- UTF-8 ----

    @Test
    void multiByteCharactersArePreserved() throws IOException {
        init();
        String text = "caf" + E_ACUTE + " " + GRINNING_FACE + " ok\n";
        put(SOURCE, text);
        assertEquals(text, files.readText(SOURCE));
    }

    static Stream<Arguments> malformedInputs() {
        return Stream.of(
            Arguments.of("invalid continuation byte", new byte[] {0x61, (byte) 0xC3, 0x28}),
            Arguments.of("lone 0xFF", new byte[] {0x61, (byte) 0xFF}),
            Arguments.of("truncated three-byte sequence", new byte[] {(byte) 0xE2, (byte) 0x82}),
            Arguments.of("overlong encoding", new byte[] {(byte) 0xC0, (byte) 0xAF}),
            Arguments.of("encoded surrogate", new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80}),
            Arguments.of("BOM followed by malformed bytes", concat(BOM, new byte[] {(byte) 0xC3, 0x28})));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedInputs")
    void malformedUtf8IsRejectedWithoutReplacement(String description, byte[] content) throws IOException {
        init();
        put(SOURCE, content);
        SourceReadException e = assertThrows(SourceReadException.class, () -> files.readText(SOURCE));
        assertTrue(e.getMessage().contains(roots.display(SOURCE)), e.getMessage());
        assertNoAbsolutePath(e.getMessage());
        assertFalse(e.getMessage().contains(REPLACEMENT_CHAR));
    }

    // ---- missing files ----

    @Test
    void aMissingFileDoesNotExistAndCannotBeRead() throws IOException {
        init();
        assertFalse(files.exists(SOURCE));
        MissingExpectedException e = assertThrows(MissingExpectedException.class, () -> files.readText(SOURCE));
        assertTrue(e.getMessage().contains(roots.display(SOURCE)), e.getMessage());
        assertNoAbsolutePath(e.getMessage());
    }

    @Test
    void aDirectoryIsNotAFile() throws IOException {
        init();
        Files.createDirectories(roots.path(SOURCE));
        assertFalse(files.exists(SOURCE));
        MissingExpectedException e = assertThrows(MissingExpectedException.class, () -> files.readText(SOURCE));
        assertTrue(e.getMessage().contains(roots.display(SOURCE)), e.getMessage());
        assertNoAbsolutePath(e.getMessage());
    }

    @Test
    void anExistingFileExists() throws IOException {
        init();
        put(SOURCE, "x");
        assertTrue(files.exists(SOURCE));
    }

    // ---- root-bound, read-only, deterministic ----

    @ParameterizedTest
    @EnumSource(RootPaths.Name.class)
    void everyLogicalNameIsReadFromItsOwnFile(RootPaths.Name name) throws IOException {
        init();
        for (RootPaths.Name other : EnumSet.allOf(RootPaths.Name.class)) {
            put(other, "content of " + other);
        }
        assertEquals("content of " + name, files.readText(name));
    }

    @Test
    void readingNeverModifiesTheFile() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("a\r\nb\r\n")));
        Path target = roots.path(SOURCE);
        byte[] before = Files.readAllBytes(target);
        FileTime modified = Files.getLastModifiedTime(target);
        files.readText(SOURCE);
        assertArrayEquals(before, Files.readAllBytes(target));
        assertEquals(modified, Files.getLastModifiedTime(target));
    }

    @Test
    void readingTwiceGivesTheSameText() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("a\r\nb\rc")));
        assertEquals(files.readText(SOURCE), files.readText(SOURCE));
    }

    // ---- readRawText: same rules as readText, but line endings are never normalized ----

    @Test
    void rawTextKeepsCrlfLfAndLoneCrExactly() throws IOException {
        init();
        String text = "a\r\nb\rc\nd\r\r\ne\r\n";
        put(SOURCE, text);
        assertEquals(text, files.readRawText(SOURCE));
    }

    @Test
    void rawTextKeepsAFinalNewlineStateAndEmptiness() throws IOException {
        init();
        put(SOURCE, "abc");
        assertEquals("abc", files.readRawText(SOURCE));
        put(SOURCE, new byte[0]);
        assertEquals("", files.readRawText(SOURCE));
    }

    @Test
    void rawTextStripsOnlyTheLeadingBom() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("hi")));
        assertEquals("hi", files.readRawText(SOURCE));
        put(SOURCE, concat(BOM, BOM, utf8("x")));
        assertEquals(BOM_CHAR + "x", files.readRawText(SOURCE));
        put(SOURCE, concat(utf8("a"), BOM, utf8("b")));
        assertEquals("a" + BOM_CHAR + "b", files.readRawText(SOURCE));
    }

    @Test
    void rawTextWithABomKeepsCrlf() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("a\r\nb")));
        assertEquals("a\r\nb", files.readRawText(SOURCE));
    }

    @Test
    void rawTextPreservesMultiByteCharacters() throws IOException {
        init();
        String text = "caf" + E_ACUTE + " " + GRINNING_FACE + "\r\nok\r\n";
        put(SOURCE, text);
        assertEquals(text, files.readRawText(SOURCE));
    }

    @ParameterizedTest(name = "raw: {0}")
    @MethodSource("malformedInputs")
    void rawTextRejectsMalformedUtf8WithoutReplacement(String description, byte[] content) throws IOException {
        init();
        put(SOURCE, content);
        SourceReadException e = assertThrows(SourceReadException.class, () -> files.readRawText(SOURCE));
        assertTrue(e.getMessage().contains(roots.display(SOURCE)), e.getMessage());
        assertNoAbsolutePath(e.getMessage());
        assertFalse(e.getMessage().contains(REPLACEMENT_CHAR));
    }

    @Test
    void rawTextOfAMissingFileOrDirectoryIsMissingExpected() throws IOException {
        init();
        MissingExpectedException missing = assertThrows(MissingExpectedException.class, () -> files.readRawText(SOURCE));
        assertTrue(missing.getMessage().contains(roots.display(SOURCE)), missing.getMessage());
        assertNoAbsolutePath(missing.getMessage());
        Files.createDirectories(roots.path(SOURCE));
        assertThrows(MissingExpectedException.class, () -> files.readRawText(SOURCE));
    }

    @Test
    void readTextIsTheLfNormalizationOfRawText() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("a\r\nb\rc\nd")));
        assertEquals("a\r\nb\rc\nd", files.readRawText(SOURCE));
        assertEquals("a\nb\nc\nd", files.readText(SOURCE));
    }

    @Test
    void readingRawTextNeverModifiesTheFile() throws IOException {
        init();
        put(SOURCE, concat(BOM, utf8("a\r\nb\r\n")));
        Path target = roots.path(SOURCE);
        byte[] before = Files.readAllBytes(target);
        FileTime modified = Files.getLastModifiedTime(target);
        files.readRawText(SOURCE);
        assertArrayEquals(before, Files.readAllBytes(target));
        assertEquals(modified, Files.getLastModifiedTime(target));
    }

    @Test
    void theApiAcceptsOnlyLogicalNames() {
        for (Method method : ProjectFiles.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertEquals(RootPaths.Name.class, parameter, "parameter of " + method.getName());
            }
        }
        for (Constructor<?> constructor : ProjectFiles.class.getConstructors()) {
            assertTrue(Arrays.equals(new Class<?>[] {RootPaths.class}, constructor.getParameterTypes()));
        }
    }
}
