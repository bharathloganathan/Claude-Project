package org.example.docsync;

import org.example.docsync.engine.DocSyncEngine;
import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.RootException;
import org.example.docsync.io.RootPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocSyncCliTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private static class RecordingEngine extends DocSyncEngine {
        final List<String> calls = new ArrayList<>();
        RootPaths seenRoots;
        int syncCode;
        int verifyCode;
        Throwable failure;

        @Override
        public int sync(RootPaths roots, PrintStream out, PrintStream err) {
            calls.add("sync");
            seenRoots = roots;
            throwFailure();
            return syncCode;
        }

        @Override
        public int verify(RootPaths roots, PrintStream out, PrintStream err) {
            calls.add("verify");
            seenRoots = roots;
            throwFailure();
            return verifyCode;
        }

        private void throwFailure() {
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
        }
    }

    private int run(RecordingEngine engine, String... args) {
        return DocSyncCli.run(args, out, err, engine);
    }

    private void assertUsageError(String... args) {
        RecordingEngine engine = new RecordingEngine();
        assertEquals(2, run(engine, args), "arguments: " + String.join(" ", args));
        String text = err();
        assertTrue(text.startsWith("error: "), text);
        assertTrue(text.contains("Usage:"), text);
        assertTrue(text.contains("sync"), text);
        assertTrue(text.contains("verify"), text);
        assertTrue(text.contains("--root <dir>"), text);
        assertEquals("", out(), "a usage error writes nothing to out");
        assertEquals(List.of(), engine.calls, "a usage error never reaches the engine");
    }

    @Test
    void noArgumentsIsAUsageError() {
        assertUsageError();
    }

    @Test
    void unknownCommandIsAUsageError() {
        assertUsageError("publish", "--root", tempDir.toString());
    }

    @Test
    void commandOnlyIsAUsageError() {
        assertUsageError("sync");
    }

    @Test
    void rootWithoutValueIsAUsageError() {
        assertUsageError("sync", "--root");
    }

    @Test
    void blankRootValueIsAUsageError() {
        assertUsageError("sync", "--root", "   ");
        assertUsageError("verify", "--root", "");
    }

    @Test
    void extraArgumentIsAUsageError() {
        assertUsageError("sync", "--root", tempDir.toString(), "extra");
    }

    @Test
    void unknownOptionIsAUsageError() {
        assertUsageError("sync", "--dir", tempDir.toString());
    }

    @Test
    void reversedOrderIsAUsageError() {
        assertUsageError("--root", tempDir.toString(), "sync");
    }

    @Test
    void verifyWithoutRootIsAUsageError() {
        assertUsageError("verify", "--root");
        assertUsageError("verify");
    }

    @Test
    void aNonexistentRootReturnsTwoWithoutTheAbsolutePath() {
        Path missing = tempDir.resolve("does-not-exist");
        RecordingEngine engine = new RecordingEngine();
        assertEquals(2, run(engine, "sync", "--root", missing.toString()));
        assertTrue(err().startsWith("error: "), err());
        assertFalse(err().contains(missing.toString()), err());
        assertFalse(err().contains(tempDir.toString()), err());
        assertEquals("", out());
        assertEquals(List.of(), engine.calls);
    }

    @Test
    void syncDispatchesToSyncWithTheResolvedRootAndReturnsItsCode() {
        RecordingEngine engine = new RecordingEngine();
        engine.syncCode = 0;
        assertEquals(0, run(engine, "sync", "--root", tempDir.toString()));
        assertEquals(List.of("sync"), engine.calls);
        assertEquals(tempDir, engine.seenRoots.root());
        assertEquals("", out());
        assertEquals("", err());
    }

    @Test
    void verifyDispatchesToVerifyAndReturnsTheStaleCodeUnchanged() {
        RecordingEngine engine = new RecordingEngine();
        engine.verifyCode = 1;
        assertEquals(1, run(engine, "verify", "--root", tempDir.toString()));
        assertEquals(List.of("verify"), engine.calls);
        assertEquals(tempDir, engine.seenRoots.root());
        assertEquals("", out());
        assertEquals("", err());
    }

    @Test
    void aDocSyncExceptionFromTheEngineReturnsTwoWithItsMessage() {
        RecordingEngine engine = new RecordingEngine();
        engine.failure = new RootException("root problem");
        assertEquals(2, run(engine, "sync", "--root", tempDir.toString()));
        assertTrue(err().contains("error: root problem"), err());
        assertEquals("", out());

        errBytes.reset();
        RecordingEngine missing = new RecordingEngine();
        missing.failure = new MissingExpectedException("docs/api/endpoints.md is missing");
        assertEquals(2, run(missing, "verify", "--root", tempDir.toString()));
        assertTrue(err().contains("error: docs/api/endpoints.md is missing"), err());
        assertEquals("", out());
    }

    @Test
    void aPlainRuntimeExceptionReturnsTwoWithTheSafeMessage() {
        RecordingEngine engine = new RecordingEngine();
        engine.failure = new IllegalStateException("kaput");
        assertEquals(2, run(engine, "verify", "--root", tempDir.toString()));
        assertTrue(err().contains("error: unexpected failure (IllegalStateException); details are not recorded."), err());
        assertFalse(err().contains("kaput"), err());
        assertEquals("", out());
    }

    @Test
    void anUnexpectedFailureNeverExposesAPathFromTheExceptionMessage() {
        RecordingEngine engine = new RecordingEngine();
        engine.failure = new IllegalStateException("cannot open C:\\Users\\someone\\secret\\file.txt");
        assertEquals(2, run(engine, "sync", "--root", tempDir.toString()));
        assertTrue(err().startsWith("error: unexpected failure (IllegalStateException)"), err());
        assertFalse(err().contains("someone"), err());
        assertFalse(err().contains("secret"), err());
        assertEquals("", out());
    }

    @Test
    void anErrorFromTheEnginePropagatesOutOfRun() {
        RecordingEngine engine = new RecordingEngine();
        AssertionError boom = new AssertionError("boom");
        engine.failure = boom;
        AssertionError thrown = assertThrows(AssertionError.class,
            () -> run(engine, "sync", "--root", tempDir.toString()));
        assertSame(boom, thrown);
    }

    @Test
    void theDefaultEngineIsWired() {
        assertEquals(2, DocSyncCli.run(new String[] {"sync", "--root", tempDir.toString()}, out, err));
        assertTrue(err().contains("src/main/java/org/example/api/StudentApiServer.java"), err());
        assertFalse(err().contains(tempDir.toString()), err());
        assertFalse(err().contains("E2-9"), err());
        assertEquals("", out());
    }
}
