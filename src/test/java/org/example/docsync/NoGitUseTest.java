package org.example.docsync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine never uses git or starts a process (task E2-9, FR-E2-19, AC-E2-12). A static scan of the
 * real production sources (comments ignored, string literals kept, with self-tests so it cannot pass
 * vacuously) is combined with a run on a fixture root that holds a fake .git directory, which must be
 * byte-identical afterwards. This source file is ASCII only.
 */
class NoGitUseTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "org", "example", "docsync");

    private static final List<String> FORBIDDEN = List.of("ProcessBuilder", "Runtime.getRuntime", ".exec(", "\"git", "JGit");

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
        assertTrue(sources.size() >= 6, "expected at least the engine sources, found " + sources.size());
        assertTrue(sources.contains(SOURCES.resolve("DocSyncCli.java")), "DocSyncCli.java must be found");
        assertTrue(sources.contains(SOURCES.resolve("engine").resolve("DocSyncEngine.java")), "DocSyncEngine.java must be found");
    }

    @Test
    void noEngineSourceStartsAProcessOrUsesGit() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : productionSources()) {
            for (String token : violations(Files.readString(file, StandardCharsets.UTF_8))) {
                problems.add(file + " uses " + token);
            }
        }
        assertTrue(problems.isEmpty(), "process or git use in the engine: " + problems);
    }

    // ---- self-tests of the scanner ----

    @Test
    void theScannerDetectsEveryForbiddenToken() {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("ProcessBuilder", "class A { Object p = new ProcessBuilder(\"x\"); }");
        samples.put("Runtime.getRuntime", "class A { Object r = Runtime.getRuntime(); }");
        samples.put(".exec(", "class A { void f(Object r) throws Exception { r.exec(\"x\"); } }");
        samples.put("\"git", "class A { String s = \"git status\"; }");
        samples.put("JGit", "class A { JGit g; }");
        assertEquals(FORBIDDEN, new ArrayList<>(samples.keySet()));
        for (Map.Entry<String, String> sample : samples.entrySet()) {
            assertTrue(violations(sample.getValue()).contains(sample.getKey()), sample.getKey());
        }
    }

    @Test
    void theScannerIgnoresForbiddenTextInComments() {
        String sample = "// ProcessBuilder \"git JGit\n/* Runtime.getRuntime() .exec( */\n/** \"git status */\nclass A { }";
        assertEquals(List.of(), violations(sample));
    }

    @Test
    void commentMarkersInsideStringsDoNotHideLaterCode() {
        assertTrue(violations("class A { String s = \"// not a comment\"; Object p = new ProcessBuilder(\"a\"); }")
            .contains("ProcessBuilder"));
    }

    @Test
    void aQuoteInsideACharLiteralDoesNotStartAString() {
        assertTrue(violations("class A { char c = '\"'; Object r = Runtime.getRuntime(); }").contains("Runtime.getRuntime"));
    }

    @Test
    void aTextBlockIsHandled() {
        String sample = "class A { String t = \"\"\"\n // x\n \"\"\"; Object r = Runtime.getRuntime(); }";
        assertTrue(violations(sample).contains("Runtime.getRuntime"));
    }

    // ---- a run on a root that holds a git directory ----

    @Test
    void aFakeGitDirectoryIsByteIdenticalAfterSyncAndVerify(@TempDir Path root) throws IOException {
        EndToEndTest.copyProject(root);
        Path git = root.resolve(".git");
        Files.createDirectories(git.resolve("refs"));
        Files.writeString(git.resolve("HEAD"), "ref: refs/heads/main\n");
        Files.writeString(git.resolve("refs").resolve("main"), "0123456789012345678901234567890123456789\n");
        Map<String, String> before = EndToEndTest.snapshot(git);
        assertTrue(before.containsKey("HEAD") && before.containsKey("refs/main"), before.toString());

        EndToEndTest.Result sync = EndToEndTest.inProcess("sync", "--root", root.toString());
        assertEquals(0, sync.exit(), sync.err());
        EndToEndTest.Result verify = EndToEndTest.inProcess("verify", "--root", root.toString());
        assertEquals(0, verify.exit(), verify.err());

        assertEquals(before, EndToEndTest.snapshot(git), "the .git tree must be byte-identical");
    }
}
