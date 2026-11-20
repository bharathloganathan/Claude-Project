package org.example.docsync.io;

import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.SourceReadException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectFiles {
    private final RootPaths roots;

    public ProjectFiles(RootPaths roots) {
        this.roots = roots;
    }

    public boolean exists(RootPaths.Name name) {
        return Files.isRegularFile(roots.path(name));
    }

    public String readText(RootPaths.Name name) {
        return readRawText(name).replace("\r\n", "\n").replace('\r', '\n');
    }

    public String readRawText(RootPaths.Name name) {
        Path path = roots.path(name);
        if (!Files.isRegularFile(path)) {
            throw new MissingExpectedException("Expected project file is missing: " + roots.display(name));
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new SourceReadException("Cannot read project file: " + roots.display(name), e);
        }
        int start = hasBom(bytes) ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.length - start))
                .toString();
        } catch (CharacterCodingException e) {
            throw new SourceReadException("Project file is not valid UTF-8: " + roots.display(name), e);
        }
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
    }
}
