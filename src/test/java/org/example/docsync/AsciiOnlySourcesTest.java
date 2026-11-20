package org.example.docsync;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task RV5-03: every docsync source file, main and test, is ASCII only, so a build never depends on the
 * compiler's default encoding. Special characters are built from code points. The sources are read read-only
 * through the project directory (the surefire working directory). This source file is ASCII only.
 */
class AsciiOnlySourcesTest {

    private static final List<Path> TREES = List.of(
        Path.of("src", "main", "java", "org", "example", "docsync"),
        Path.of("src", "test", "java", "org", "example", "docsync"));

    @Test
    void noDocsyncSourceFileContainsANonAsciiByte() throws IOException {
        List<String> problems = new ArrayList<>();
        int scanned = 0;
        for (Path tree : TREES) {
            assertTrue(Files.isDirectory(tree), "the source tree must be found relative to the project directory: " + tree);
            try (Stream<Path> walk = Files.walk(tree)) {
                for (Path file : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    scanned++;
                    byte[] bytes = Files.readAllBytes(file);
                    int line = 1;
                    for (byte b : bytes) {
                        if (b == '\n') {
                            line++;
                        } else if (b < 0) {
                            problems.add(file + ":" + line);
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(scanned >= 50, "the scan must see the main and test sources, saw " + scanned);
        assertEquals(List.of(), problems);
    }
}
