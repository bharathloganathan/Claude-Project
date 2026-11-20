package org.example.docsync.io;

import org.example.docsync.error.RootException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Root resolution, logical names and containment (task E2-1, FR-E2-20, AC-E2-13, DR-18).
 * Every root is a JUnit temporary directory. The injected launch directory replaces the process
 * working directory, so no test depends on where the build runs, except the one test of the
 * public factory.
 */
class RootPathsTest {

    private static final Map<RootPaths.Name, String> EXPECTED = new EnumMap<>(RootPaths.Name.class);

    static {
        EXPECTED.put(RootPaths.Name.API_ROUTES_SOURCE, "src/main/java/org/example/api/StudentApiServer.java");
        EXPECTED.put(RootPaths.Name.ERROR_CODE_SOURCE, "src/main/java/org/example/api/ErrorCode.java");
        EXPECTED.put(RootPaths.Name.STUDENT_RECORD_SOURCE, "src/main/java/org/example/model/Student.java");
        EXPECTED.put(RootPaths.Name.SERVICE_SOURCE, "src/main/java/org/example/service/StudentService.java");
        EXPECTED.put(RootPaths.Name.FRONTEND_HTML, "frontend/index.html");
        EXPECTED.put(RootPaths.Name.FRONTEND_JS, "frontend/app.js");
        EXPECTED.put(RootPaths.Name.DOC_ENDPOINTS, "docs/api/endpoints.md");
        EXPECTED.put(RootPaths.Name.DOC_CONTRACT, "docs/api/student-contract.md");
        EXPECTED.put(RootPaths.Name.DOC_SERVICE, "docs/backend/student-service.md");
        EXPECTED.put(RootPaths.Name.DOC_UI, "docs/ui/student-ui.md");
        EXPECTED.put(RootPaths.Name.REPORT_SYNC, "docs/reports/sync-report.md");
        EXPECTED.put(RootPaths.Name.REPORT_VERIFY, "docs/reports/verify-report.md");
    }

    @TempDir
    Path temp;

    private Path project;

    private RootPaths newRoots() throws IOException {
        project = Files.createDirectories(temp.resolve("project"));
        return RootPaths.of(project.toString(), temp);
    }

    private static void assertSafeMessage(RootException e, Path... mustNotAppear) {
        String message = e.getMessage();
        assertNotNull(message);
        assertFalse(message.isBlank(), "the message must say what is wrong");
        for (Path path : mustNotAppear) {
            String text = path.toString();
            assertFalse(message.contains(text), "the message must not contain an absolute path: " + message);
            assertFalse(message.contains(text.replace('\\', '/')), "the message must not contain an absolute path: " + message);
        }
    }

    // ---- resolving the root ----

    @Test
    void anAbsoluteRootIsKeptAndTheLaunchDirectoryIsIgnored() throws IOException {
        Path wanted = Files.createDirectories(temp.resolve("project"));
        Path launch = Files.createDirectories(temp.resolve("elsewhere"));
        assertEquals(wanted, RootPaths.of(wanted.toString(), launch).root());
    }

    @Test
    void aDotRootIsTheLaunchDirectory() throws IOException {
        Path launch = Files.createDirectories(temp.resolve("launch"));
        assertEquals(launch, RootPaths.of(".", launch).root());
    }

    @Test
    void aRelativeRootIsResolvedAgainstTheLaunchDirectory() throws IOException {
        Path launch = Files.createDirectories(temp.resolve("launch"));
        Path sub = Files.createDirectories(launch.resolve("sub"));
        assertEquals(sub, RootPaths.of("sub", launch).root());
    }

    @Test
    void aParentRelativeRootIsNormalized() throws IOException {
        Path launch = Files.createDirectories(temp.resolve("launch"));
        Path sibling = Files.createDirectories(temp.resolve("x"));
        assertEquals(sibling, RootPaths.of("../x", launch).root());
    }

    @Test
    void dotSegmentsInTheRootAreNormalizedAway() throws IOException {
        Path launch = Files.createDirectories(temp.resolve("launch"));
        Path target = Files.createDirectories(launch.resolve("a").resolve("b"));
        assertEquals(target, RootPaths.of("a/./b/../b", launch).root());
    }

    @Test
    void aTrailingSeparatorMakesNoDifference() throws IOException {
        Path launch = Files.createDirectories(temp.resolve("launch"));
        Path wanted = Files.createDirectories(temp.resolve("project"));
        assertEquals(wanted, RootPaths.of(wanted + File.separator, launch).root());
    }

    @Test
    void theRootIsAbsolute() throws IOException {
        assertTrue(newRoots().root().isAbsolute());
    }

    @Test
    void thePublicFactoryResolvesARelativeRootAgainstTheProcessLaunchDirectory() {
        Path expected = Path.of("").toAbsolutePath().normalize();
        RootPaths roots = RootPaths.of(".");
        assertEquals(expected, roots.root());
        assertTrue(roots.root().isAbsolute());
    }

    // ---- invalid roots ----

    @Test
    void aMissingAbsoluteRootIsRejectedWithoutLeakingThePath() {
        Path missing = temp.resolve("does-not-exist");
        RootException e = assertThrows(RootException.class, () -> RootPaths.of(missing.toString(), temp));
        assertSafeMessage(e, temp, missing);
    }

    @Test
    void aMissingRelativeRootIsRejectedWithoutLeakingThePath() {
        RootException e = assertThrows(RootException.class, () -> RootPaths.of("does-not-exist", temp));
        assertSafeMessage(e, temp);
    }

    @Test
    void aRegularFileIsNotAcceptedAsARoot() throws IOException {
        Path file = Files.writeString(temp.resolve("a-file.txt"), "x");
        RootException e = assertThrows(RootException.class, () -> RootPaths.of(file.toString(), temp));
        assertSafeMessage(e, temp, file);
    }

    @Test
    void aNullRootIsRejected() {
        RootException e = assertThrows(RootException.class, () -> RootPaths.of(null, temp));
        assertSafeMessage(e, temp);
    }

    @Test
    void blankRootsAreRejected() {
        for (String blank : new String[] {"", " ", "   ", "\t", "\n"}) {
            RootException e = assertThrows(RootException.class, () -> RootPaths.of(blank, temp), "root [" + blank + "]");
            assertSafeMessage(e, temp);
        }
    }

    @Test
    void aRootWithANulCharacterIsRejected() {
        RootException e = assertThrows(RootException.class, () -> RootPaths.of("a\0b", temp));
        assertSafeMessage(e, temp);
    }

    // ---- logical names ----

    @Test
    void theCatalogHasExactlyTheApprovedLogicalNames() {
        assertEquals(EXPECTED.keySet(), EnumSet.allOf(RootPaths.Name.class));
    }

    @ParameterizedTest
    @EnumSource(RootPaths.Name.class)
    void everyNameMapsToItsRootRelativePathInsideTheRoot(RootPaths.Name name) throws IOException {
        RootPaths roots = newRoots();
        assertEquals(EXPECTED.get(name), roots.display(name));
        assertEquals(roots.root().resolve(EXPECTED.get(name)).normalize(), roots.path(name));
        assertTrue(roots.path(name).isAbsolute());
        assertTrue(roots.path(name).startsWith(roots.root()));
    }

    @ParameterizedTest
    @EnumSource(RootPaths.Name.class)
    void displayTextIsRootRelativeWithForwardSlashes(RootPaths.Name name) throws IOException {
        String display = newRoots().display(name);
        assertFalse(display.contains("\\"), display);
        assertFalse(display.startsWith("/"), display);
        assertFalse(display.contains(":"), display);
    }

    // ---- resolveInside: accepted ----

    @ParameterizedTest
    @ValueSource(strings = {
        "docs/api/endpoints.md", "a", "DOCS/API/ENDPOINTS.MD", "dir with space/file name.txt",
        ".hidden/x", "..a/b", "a.b/c-d_e/f.txt", "frontend/app.js"
    })
    void resolveInsideAcceptsPlainRootRelativePaths(String relative) throws IOException {
        RootPaths roots = newRoots();
        Path result = roots.resolveInside(relative);
        assertEquals(roots.root().resolve(relative).normalize(), result);
        assertTrue(result.isAbsolute());
        assertTrue(result.startsWith(roots.root()));
    }

    // ---- resolveInside: rejected ----

    @ParameterizedTest
    @ValueSource(strings = {
        "..", "../x", "a/../../x", "a/../x", "a/..", "../", "x/../../etc/passwd",
        "/", "/abs", "/a/b",
        "C:/x", "C:x", "c:\\x", "C:", "\\\\server\\share\\x", "//server/share/x",
        "a\\b", "a/b\\..\\c", "\\a",
        "a//b", "a/", ".", "./a", "a/./b", "a/.",
        "", " ", "   ",
        "a/b.", "a./b", "a/b..", "a /b", "a/b ", "a/ ",
        "docs/api/endpoints.md:stream", "a:b"
    })
    void resolveInsideRejectsPathsThatCouldEscapeOrAreMalformed(String relative) throws IOException {
        RootPaths roots = newRoots();
        RootException e = assertThrows(RootException.class, () -> roots.resolveInside(relative));
        assertSafeMessage(e, project, temp);
    }

    @Test
    void resolveInsideRejectsControlCharacters() throws IOException {
        RootPaths roots = newRoots();
        for (String relative : new String[] {"a\0b", "a\nb", "a\tb", "a\rb", "a\u001fb"}) {
            RootException e = assertThrows(RootException.class, () -> roots.resolveInside(relative));
            assertSafeMessage(e, project, temp);
        }
    }

    @Test
    void resolveInsideRejectsNull() throws IOException {
        RootPaths roots = newRoots();
        RootException e = assertThrows(RootException.class, () -> roots.resolveInside(null));
        assertSafeMessage(e, project, temp);
    }

    // ---- the second containment layer ----

    @Test
    void isInsideAcceptsTheRootItselfAndItsDescendants() {
        Path root = temp.resolve("proj");
        assertTrue(RootPaths.isInside(root, root));
        assertTrue(RootPaths.isInside(root, root.resolve("a")));
        assertTrue(RootPaths.isInside(root, root.resolve("a").resolve("b.txt")));
        assertTrue(RootPaths.isInside(root, root.resolve("SUB")));
    }

    @Test
    void isInsideRejectsASiblingThatSharesANamePrefix() {
        Path root = temp.resolve("proj");
        Path sibling = temp.resolve("proj-evil");
        assertFalse(RootPaths.isInside(root, sibling));
        assertFalse(RootPaths.isInside(root, sibling.resolve("x")));
    }

    @Test
    void isInsideRejectsTheParentAndUnrelatedPaths() {
        Path root = temp.resolve("proj");
        assertFalse(RootPaths.isInside(root, temp));
        assertFalse(RootPaths.isInside(root, temp.resolve("other")));
        assertFalse(RootPaths.isInside(root, root.resolve("..")));
    }

    @Test
    void isInsideNormalizesBeforeComparing() {
        Path root = temp.resolve("proj");
        assertFalse(RootPaths.isInside(root, root.resolve("a").resolve("..").resolve("..").resolve("proj-evil")));
        assertTrue(RootPaths.isInside(root, root.resolve("a").resolve("..").resolve("b")));
    }

    @Test
    void isInsideRejectsARelativeCandidate() {
        assertFalse(RootPaths.isInside(temp.resolve("proj"), Path.of("x", "y")));
    }
}
