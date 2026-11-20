package org.example.docsync.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static guard for FR-E2-20 and AD-16 (task E2-1): no engine source may use an API that depends on the
 * process working directory, except RootPaths, which captures the launch directory exactly once. The
 * scan reads the real production sources relative to the project directory (the surefire working
 * directory) and fails if it finds none. Comments are ignored. String literals are kept, because
 * "user.dir" lives inside one. The scanner has its own self-tests, so it cannot pass vacuously.
 */
class NoCwdApiScanTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "org", "example", "docsync");

    private static final List<String> FORBIDDEN = List.of(
        "user.dir", "Path.of(", "Paths.get(", "new File(", "toAbsolutePath(", "getAbsolutePath(",
        "getAbsoluteFile(", "getResource(", "getResourceAsStream(", "new FileInputStream(", "new FileReader(");

    // ---- the scanner ----

    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? n : end + 3;
                out.append(source, i, stop);
                i = stop;
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < n && source.charAt(i) != c) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append('\\');
                        i++;
                    }
                    out.append(source.charAt(i));
                    i++;
                }
                if (i < n) {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    static List<String> violations(String source) {
        String code = stripComments(source);
        List<String> found = new ArrayList<>();
        for (String token : FORBIDDEN) {
            if (code.contains(token)) {
                found.add(token);
            }
        }
        return found;
    }

    static int count(String text, String token) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    static boolean isAllowListed(Path file) {
        Path name = file.getFileName();
        Path parent = file.getParent();
        return name != null && name.toString().equals("RootPaths.java")
            && parent != null && parent.getFileName() != null && parent.getFileName().toString().equals("io");
    }

    private static List<Path> productionSources() throws IOException {
        assertTrue(Files.isDirectory(SOURCES), "the engine sources must be found relative to the project directory: " + SOURCES);
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    // ---- the scan of the real production sources ----

    @Test
    void productionSourcesAreFound() throws IOException {
        List<Path> sources = productionSources();
        assertTrue(sources.size() >= 6, "expected at least the E2-1 sources, found " + sources.size());
        assertTrue(sources.contains(SOURCES.resolve("io").resolve("RootPaths.java")), "RootPaths.java must be found");
    }

    @Test
    void noWorkingDirectoryDependentApiIsUsedOutsideRootPaths() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : productionSources()) {
            if (isAllowListed(file)) {
                continue;
            }
            for (String token : violations(Files.readString(file, StandardCharsets.UTF_8))) {
                problems.add(file + " uses " + token);
            }
        }
        assertTrue(problems.isEmpty(), "working-directory dependent API outside RootPaths: " + problems);
    }

    @Test
    void rootPathsCapturesTheLaunchDirectoryExactlyOnce() throws IOException {
        Path rootPaths = SOURCES.resolve("io").resolve("RootPaths.java");
        assertTrue(Files.isRegularFile(rootPaths), "RootPaths.java must exist");
        String code = stripComments(Files.readString(rootPaths, StandardCharsets.UTF_8));
        assertEquals(1, count(code, "toAbsolutePath("), "toAbsolutePath( must appear exactly once");
        assertEquals(0, count(code, "user.dir"), "user.dir must not appear");
        String line = code.lines().filter(l -> l.contains("toAbsolutePath(")).findFirst().orElseThrow();
        assertTrue(line.contains("Path.of(\"\")"), "the single call must be Path.of(\"\").toAbsolutePath(): " + line);
    }

    // ---- self-tests of the scanner ----

    @ParameterizedTest
    @ValueSource(strings = {
        "user.dir", "Path.of(", "Paths.get(", "new File(", "toAbsolutePath(", "getAbsolutePath(",
        "getAbsoluteFile(", "getResource(", "getResourceAsStream(", "new FileInputStream(", "new FileReader("
    })
    void theScannerDetectsEveryForbiddenToken(String token) {
        assertTrue(violations("class A { Object o = " + token + " ; }").contains(token));
    }

    @Test
    void theScannerIgnoresForbiddenTextInComments() {
        String sample = "// Path.of(\"x\") user.dir\n/* new File(\"x\") toAbsolutePath() */\n/** getResource( */\nclass A { }";
        assertEquals(List.of(), violations(sample));
    }

    @Test
    void theScannerStillDetectsForbiddenTextInsideAStringLiteral() {
        assertTrue(violations("class A { String s = System.getProperty(\"user.dir\"); }").contains("user.dir"));
    }

    @Test
    void commentMarkersInsideStringsDoNotHideLaterCode() {
        assertTrue(violations("class A { String s = \"// not a comment\"; Object p = Path.of(\"a\"); }").contains("Path.of("));
    }

    @Test
    void aQuoteInsideACharLiteralDoesNotStartAString() {
        assertTrue(violations("class A { char c = '\"'; Object p = Path.of(\"a\"); }").contains("Path.of("));
    }

    @Test
    void aTextBlockIsHandled() {
        String sample = "class A { String t = \"\"\"\n // x\n \"\"\"; Object p = Paths.get(\"a\"); }";
        assertTrue(violations(sample).contains("Paths.get("));
    }

    @Test
    void onlyIoRootPathsIsAllowListed() {
        assertTrue(isAllowListed(Path.of("x", "io", "RootPaths.java")));
        assertFalse(isAllowListed(Path.of("x", "other", "RootPaths.java")));
        assertFalse(isAllowListed(Path.of("x", "io", "ProjectFiles.java")));
        assertFalse(isAllowListed(Path.of("RootPaths.java")));
    }
}
