package org.example.docsync.io;

import org.example.docsync.error.DocumentWriteException;
import org.example.docsync.error.DocumentWriteException.FileOutcome;
import org.example.docsync.error.DocumentWriteException.Status;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public final class AtomicWriter {
    public interface Mover {
        void move(Path source, Path target) throws IOException;
    }

    private record Staged(RootPaths.Name name, Path temp, Path target) {
    }

    private final RootPaths roots;
    private final Mover mover;

    public AtomicWriter(RootPaths roots) {
        this(roots, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
    }

    public AtomicWriter(RootPaths roots, Mover mover) {
        this.roots = roots;
        this.mover = mover;
    }

    public void writeAll(LinkedHashMap<RootPaths.Name, String> contents) {
        replace(stage(contents));
    }

    private List<Staged> stage(LinkedHashMap<RootPaths.Name, String> contents) {
        List<Staged> staged = new ArrayList<>();
        RootPaths.Name current = null;
        try {
            for (var entry : contents.entrySet()) {
                current = entry.getKey();
                Path target = roots.path(current);
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
                    continue;
                }
                Path directory = target.getParent();
                Files.createDirectories(directory);
                Path temp = Files.createTempFile(directory, ".docsync-", ".tmp");
                staged.add(new Staged(current, temp, target));
                writeFully(temp, bytes);
            }
        } catch (IOException e) {
            deleteQuietly(staged);
            throw new DocumentWriteException("Staging failed for " + roots.display(current) + " ("
                + e.getClass().getSimpleName() + "); no document was modified.", List.of(), e);
        }
        return staged;
    }

    private void replace(List<Staged> staged) {
        List<FileOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < staged.size(); i++) {
            Staged file = staged.get(i);
            try {
                mover.move(file.temp(), file.target());
                outcomes.add(new FileOutcome(roots.display(file.name()), Status.REPLACED, null));
            } catch (IOException | RuntimeException e) {
                String cause = e.getClass().getSimpleName();
                outcomes.add(new FileOutcome(roots.display(file.name()), Status.FAILED, cause));
                for (Staged later : staged.subList(i + 1, staged.size())) {
                    outcomes.add(new FileOutcome(roots.display(later.name()), Status.NOT_ATTEMPTED, null));
                }
                deleteQuietly(staged.subList(i, staged.size()));
                throw new DocumentWriteException("Replacing " + roots.display(file.name()) + " failed (" + cause
                    + "). Replacement is not transactional: files listed as REPLACED remain updated."
                    + " The content of the FAILED file is not assumed intact. Outcomes: "
                    + outcomes.stream().map(o -> o.status() + " " + o.path()).collect(Collectors.joining("; "))
                    + ".", outcomes, e);
            }
        }
    }

    private static void writeFully(Path temp, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            try {
                channel.force(true);
            } catch (IOException ignored) {
                // Durability is best effort: a failed force must not fail the write.
            }
        }
    }

    private static void deleteQuietly(List<Staged> files) {
        for (Staged file : files) {
            try {
                Files.deleteIfExists(file.temp());
            } catch (IOException ignored) {
                // Best effort: a temp file that cannot be deleted must not hide the original failure.
            }
        }
    }
}
