package org.example.docsync.io;

import org.example.docsync.error.DocumentWriteException;
import org.example.docsync.error.DocumentWriteException.FileOutcome;
import org.example.docsync.error.DocumentWriteException.Status;
import org.example.docsync.io.RootPaths.Name;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atomic, staged, non-transactional document writing (task E2-4, FR-E2-08, FR-E2-13, DR-11): every
 * changed file is staged as a same-directory temp file first, then replaced one at a time with an
 * atomic move only. The outcome list is exact: REPLACED, FAILED (attempted and threw) or
 * NOT_ATTEMPTED (an earlier file failed). This source file is ASCII only.
 */
class AtomicWriterTest {

    private static final String SENTENCE = "Replacement is not transactional: files listed as REPLACED remain updated.";
    private static final String OLD = "old content\n";
    private static final List<Name> NAMES = List.of(Name.DOC_ENDPOINTS, Name.DOC_CONTRACT, Name.DOC_SERVICE);

    @TempDir
    Path dir;

    private RootPaths roots() {
        return RootPaths.of(".", dir);
    }

    private static String newText(Name name) {
        return "new " + name.name() + "\n";
    }

    private LinkedHashMap<Name, String> newContents() {
        LinkedHashMap<Name, String> contents = new LinkedHashMap<>();
        for (Name name : NAMES) {
            contents.put(name, newText(name));
        }
        return contents;
    }

    private void seed(Name name, String text) throws IOException {
        Path file = roots().path(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private void seedAllOld() throws IOException {
        for (Name name : NAMES) {
            seed(name, OLD);
        }
    }

    private String read(Name name) throws IOException {
        return Files.readString(roots().path(name), StandardCharsets.UTF_8);
    }

    private static boolean isTemp(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith(".docsync-") && name.endsWith(".tmp");
    }

    private List<Path> tempsUnderRoot() throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).filter(AtomicWriterTest::isTemp).collect(Collectors.toList());
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class FailAt implements AtomicWriter.Mover {
        private final int failAt;
        private int calls;

        FailAt(int failAt) {
            this.failAt = failAt;
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            if (calls++ == failAt) {
                throw new IOException("injected failure at " + target);
            }
            moveAtomically(source, target);
        }
    }

    private void assertNoAbsolutePath(String text) {
        String root = dir.toString();
        assertFalse(text.contains(root), "must not contain the absolute root: " + text);
        assertFalse(text.contains(root.replace('\\', '/')), "must not contain the absolute root: " + text);
        assertFalse(text.contains(root.replace('/', '\\')), "must not contain the absolute root: " + text);
    }

    @Test
    void successfulWriteCreatesParentsAndExactBytes() throws IOException {
        String crlf = "line one\r\nline two\r\n";
        String accented = "caf" + (char) 0xE9 + "\n";
        LinkedHashMap<Name, String> contents = new LinkedHashMap<>();
        contents.put(Name.DOC_ENDPOINTS, crlf);
        contents.put(Name.DOC_CONTRACT, accented);
        contents.put(Name.DOC_SERVICE, "plain\n");

        new AtomicWriter(roots()).writeAll(contents);

        for (var entry : contents.entrySet()) {
            assertArrayEquals(entry.getValue().getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(roots().path(entry.getKey())), entry.getKey().name());
        }
        assertTrue(tempsUnderRoot().isEmpty());
    }

    @Test
    void anEmptyMapWritesNothing() throws IOException {
        new AtomicWriter(roots()).writeAll(new LinkedHashMap<>());
        try (Stream<Path> list = Files.list(dir)) {
            assertEquals(0, list.count());
        }
    }

    @Test
    void unchangedFilesAreSkippedWithoutTempOrWrite() throws IOException {
        seed(Name.DOC_ENDPOINTS, "same\n");
        FileTime fixed = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(roots().path(Name.DOC_ENDPOINTS), fixed);
        LinkedHashMap<Name, String> contents = new LinkedHashMap<>();
        contents.put(Name.DOC_ENDPOINTS, "same\n");
        contents.put(Name.DOC_CONTRACT, "new\n");
        List<Path> moved = new ArrayList<>();
        List<Path> sources = new ArrayList<>();

        new AtomicWriter(roots(), (source, target) -> {
            sources.add(source);
            moved.add(target);
            moveAtomically(source, target);
        }).writeAll(contents);

        assertEquals(List.of(roots().path(Name.DOC_CONTRACT)), moved);
        assertEquals(1, sources.size());
        assertEquals(fixed, Files.getLastModifiedTime(roots().path(Name.DOC_ENDPOINTS)));
        assertEquals("same\n", read(Name.DOC_ENDPOINTS));
        assertEquals("new\n", read(Name.DOC_CONTRACT));
        assertTrue(tempsUnderRoot().isEmpty());
    }

    @Test
    void anUnchangedFileIsNotListedInTheOutcomes() throws IOException {
        seed(Name.DOC_ENDPOINTS, "same\n");
        seed(Name.DOC_CONTRACT, OLD);
        LinkedHashMap<Name, String> contents = new LinkedHashMap<>();
        contents.put(Name.DOC_ENDPOINTS, "same\n");
        contents.put(Name.DOC_CONTRACT, "new\n");

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), new FailAt(0)).writeAll(contents));

        assertEquals(List.of(new FileOutcome(roots().display(Name.DOC_CONTRACT), Status.FAILED, "IOException")),
            e.outcomes());
    }

    @Test
    void everyChangedFileIsStagedBeforeTheFirstMove() throws IOException {
        List<Integer> tempsAtFirstMove = new ArrayList<>();
        List<Boolean> targetsPresentAtFirstMove = new ArrayList<>();
        List<Boolean> everyTargetDirectoryHasOneTempPerTarget = new ArrayList<>();
        int[] calls = {0};

        new AtomicWriter(roots(), (source, target) -> {
            if (calls[0]++ == 0) {
                tempsAtFirstMove.add(tempsUnderRoot().size());
                boolean anyTarget = false;
                boolean oneTempEach = true;
                for (Name name : NAMES) {
                    anyTarget |= Files.exists(roots().path(name));
                    Path parent = roots().path(name).getParent();
                    long targetsInDirectory = NAMES.stream().filter(n -> roots().path(n).getParent().equals(parent)).count();
                    try (Stream<Path> list = Files.list(parent)) {
                        oneTempEach &= list.filter(AtomicWriterTest::isTemp).count() == targetsInDirectory;
                    }
                }
                targetsPresentAtFirstMove.add(anyTarget);
                everyTargetDirectoryHasOneTempPerTarget.add(oneTempEach);
            }
            moveAtomically(source, target);
        }).writeAll(newContents());

        assertEquals(List.of(3), tempsAtFirstMove);
        assertEquals(List.of(false), targetsPresentAtFirstMove);
        assertEquals(List.of(true), everyTargetDirectoryHasOneTempPerTarget);
        assertEquals(3, calls[0]);
        assertTrue(tempsUnderRoot().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aFailureStopsAtTheFirstFailedFileAndReportsExactOutcomes(int failAt) throws IOException {
        seedAllOld();

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), new FailAt(failAt)).writeAll(newContents()));

        List<FileOutcome> expected = new ArrayList<>();
        for (int i = 0; i < NAMES.size(); i++) {
            Status status = i < failAt ? Status.REPLACED : i == failAt ? Status.FAILED : Status.NOT_ATTEMPTED;
            expected.add(new FileOutcome(roots().display(NAMES.get(i)), status, status == Status.FAILED ? "IOException" : null));
        }
        assertEquals(expected, e.outcomes());
        for (int i = 0; i < NAMES.size(); i++) {
            assertEquals(i < failAt ? newText(NAMES.get(i)) : OLD, read(NAMES.get(i)), NAMES.get(i).name());
        }
        assertTrue(tempsUnderRoot().isEmpty(), "no temp file may remain: " + tempsUnderRoot());
        assertTrue(e.getMessage().contains(SENTENCE), e.getMessage());
        assertFalse(e.getMessage().contains("all-or-nothing"));
        assertFalse(e.getMessage().contains("rolled back"));
        assertNotNull(e.getCause());
    }

    @Test
    void causeIsTheSimpleClassNameAndNothingContainsAnAbsolutePath() throws IOException {
        seedAllOld();

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), new FailAt(1)).writeAll(newContents()));

        assertNoAbsolutePath(e.getMessage());
        for (FileOutcome outcome : e.outcomes()) {
            assertNoAbsolutePath(outcome.path());
            assertNoAbsolutePath(String.valueOf(outcome.cause()));
            if (outcome.status() != Status.FAILED) {
                assertNull(outcome.cause());
            }
        }
        assertEquals("IOException", e.outcomes().get(1).cause());
    }

    @Test
    void theOutcomeListIsUnmodifiable() throws IOException {
        seedAllOld();

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), new FailAt(0)).writeAll(newContents()));

        assertThrows(UnsupportedOperationException.class, () -> e.outcomes().add(null));
    }

    @Test
    void aRuntimeExceptionFromTheMoverIsAFailedOutcome() throws IOException {
        seedAllOld();

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), (s, t) -> {
                throw new UnsupportedOperationException("injected");
            }).writeAll(newContents()));

        assertEquals(Status.FAILED, e.outcomes().get(0).status());
        assertEquals("UnsupportedOperationException", e.outcomes().get(0).cause());
        assertEquals(Status.NOT_ATTEMPTED, e.outcomes().get(1).status());
        assertTrue(tempsUnderRoot().isEmpty());
    }

    @Test
    void aStagingFailureChangesNoDocumentAndLeavesNoTempFile() throws IOException {
        seed(Name.DOC_ENDPOINTS, OLD);
        Files.writeString(dir.resolve("docs").resolve("backend"), "i am a file\n", StandardCharsets.UTF_8);
        LinkedHashMap<Name, String> contents = new LinkedHashMap<>();
        contents.put(Name.DOC_ENDPOINTS, "new endpoints\n");
        contents.put(Name.DOC_SERVICE, "new service\n");
        contents.put(Name.DOC_CONTRACT, "new contract\n");
        int[] calls = {0};

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), (s, t) -> calls[0]++).writeAll(contents));

        assertEquals(0, calls[0], "no move may be attempted when staging fails");
        assertEquals(List.of(), e.outcomes());
        assertTrue(e.getMessage().contains("no document was modified"), e.getMessage());
        assertNoAbsolutePath(e.getMessage());
        assertEquals(OLD, read(Name.DOC_ENDPOINTS));
        assertFalse(Files.exists(roots().path(Name.DOC_CONTRACT)));
        assertEquals("i am a file\n", Files.readString(dir.resolve("docs").resolve("backend"), StandardCharsets.UTF_8));
        assertTrue(tempsUnderRoot().isEmpty(), "no temp file may remain: " + tempsUnderRoot());
    }

    @Test
    void theDefaultMoverReplacesAnExistingFileOnTheRealFilesystem() throws IOException {
        seedAllOld();

        new AtomicWriter(roots()).writeAll(newContents());

        for (Name name : NAMES) {
            assertEquals(newText(name), read(name), name.name());
        }
        assertTrue(tempsUnderRoot().isEmpty());
    }

    @Test
    void whenAtomicMoveIsNotSupportedThereIsNoFallbackReplace() throws IOException {
        seedAllOld();

        DocumentWriteException e = assertThrows(DocumentWriteException.class,
            () -> new AtomicWriter(roots(), (source, target) -> {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "not supported");
            }).writeAll(newContents()));

        assertEquals("AtomicMoveNotSupportedException", e.outcomes().get(0).cause());
        assertEquals(Status.FAILED, e.outcomes().get(0).status());
        for (Name name : NAMES) {
            assertEquals(OLD, read(name), name.name());
        }
        assertTrue(tempsUnderRoot().isEmpty());
    }

    @Test
    void theProductionSourceUsesTheAtomicMoveOnlyAndNoCopyFallback() throws IOException {
        Path source = Path.of("src", "main", "java", "org", "example", "docsync", "io", "AtomicWriter.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);

        assertTrue(text.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertFalse(text.contains("REPLACE_EXISTING"));
        assertFalse(text.contains("Files.copy"));
        assertFalse(text.contains("Files.delete(target"));
    }
}
