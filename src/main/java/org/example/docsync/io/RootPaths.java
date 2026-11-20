package org.example.docsync.io;

import org.example.docsync.error.RootException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class RootPaths {
    public enum Name {
        API_ROUTES_SOURCE("src/main/java/org/example/api/StudentApiServer.java"),
        ERROR_CODE_SOURCE("src/main/java/org/example/api/ErrorCode.java"),
        STUDENT_RECORD_SOURCE("src/main/java/org/example/model/Student.java"),
        SERVICE_SOURCE("src/main/java/org/example/service/StudentService.java"),
        FRONTEND_HTML("frontend/index.html"),
        FRONTEND_JS("frontend/app.js"),
        DOC_ENDPOINTS("docs/api/endpoints.md"),
        DOC_CONTRACT("docs/api/student-contract.md"),
        DOC_SERVICE("docs/backend/student-service.md"),
        DOC_UI("docs/ui/student-ui.md"),
        REPORT_SYNC("docs/reports/sync-report.md"),
        REPORT_VERIFY("docs/reports/verify-report.md");

        private final String relativePath;

        Name(String relativePath) {
            this.relativePath = relativePath;
        }
    }

    private final Path root;

    private RootPaths(Path root) {
        this.root = root;
    }

    public static RootPaths of(String rootArgument) {
        return of(rootArgument, Path.of("").toAbsolutePath());
    }

    static RootPaths of(String rootArgument, Path launchDirectory) {
        if (rootArgument == null || rootArgument.isBlank()) {
            throw new RootException("The --root argument is missing or blank.");
        }
        Path candidate;
        try {
            candidate = launchDirectory.resolve(Path.of(rootArgument)).normalize();
        } catch (InvalidPathException e) {
            throw new RootException("The --root argument is not a valid path.");
        }
        if (!Files.isDirectory(candidate)) {
            throw new RootException("The --root directory does not exist or is not a directory.");
        }
        if (!Files.isReadable(candidate)) {
            throw new RootException("The --root directory is not readable.");
        }
        return new RootPaths(candidate);
    }

    static boolean isInside(Path root, Path candidate) {
        return candidate.normalize().startsWith(root.normalize());
    }

    public Path root() {
        return root;
    }

    public Path path(Name name) {
        return root.resolve(name.relativePath).normalize();
    }

    public String display(Name name) {
        return name.relativePath;
    }

    public Path resolveInside(String rootRelativePath) {
        if (rootRelativePath == null || rootRelativePath.isEmpty() || rootRelativePath.startsWith("/")) {
            throw rejected();
        }
        for (int i = 0; i < rootRelativePath.length(); i++) {
            char c = rootRelativePath.charAt(i);
            if (Character.isISOControl(c) || c == '\\' || c == ':') {
                throw rejected();
            }
        }
        for (String segment : rootRelativePath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                || segment.endsWith(".") || segment.endsWith(" ")) {
                throw rejected();
            }
        }
        Path resolved;
        try {
            resolved = root.resolve(rootRelativePath).normalize();
        } catch (InvalidPathException e) {
            throw rejected();
        }
        if (!isInside(root, resolved)) {
            throw rejected();
        }
        return resolved;
    }

    private static RootException rejected() {
        return new RootException("The path is not a valid root-relative path inside the project root.");
    }
}
