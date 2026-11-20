package org.example.docsync;

import org.example.docsync.engine.DocSyncEngine;
import org.example.docsync.engine.ReportRenderer;
import org.example.docsync.error.DocSyncException;
import org.example.docsync.io.RootPaths;

import java.io.PrintStream;

public final class DocSyncCli {

    private static final String USAGE = "Usage: DocSyncCli <sync|verify> --root <dir>\n"
        + "  sync    update the generated blocks in the documentation under <dir>\n"
        + "  verify  check that the generated blocks are up to date (exit 1 when stale)\n"
        + "Exit codes: 0 success or up to date, 1 stale, 2 error.";

    private DocSyncCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, new DocSyncEngine());
    }

    static int run(String[] args, PrintStream out, PrintStream err, DocSyncEngine engine) {
        String problem = usageProblem(args);
        if (problem != null) {
            err.println("error: " + problem);
            err.println(USAGE);
            return 2;
        }
        try {
            RootPaths roots = RootPaths.of(args[2]);
            return args[0].equals("sync") ? engine.sync(roots, out, err) : engine.verify(roots, out, err);
        } catch (DocSyncException e) {
            err.println("error: " + e.getMessage());
            return 2;
        } catch (Exception e) {
            err.println("error: " + ReportRenderer.failureMessage(e));
            return 2;
        }
    }

    private static String usageProblem(String[] args) {
        if (args.length == 0) {
            return "no command given.";
        }
        if (!args[0].equals("sync") && !args[0].equals("verify")) {
            return "unknown command.";
        }
        if (args.length < 2 || !args[1].equals("--root")) {
            return "the --root option is missing.";
        }
        if (args.length < 3 || args[2].isBlank()) {
            return "the --root option needs a directory value.";
        }
        if (args.length > 3) {
            return "unexpected extra argument.";
        }
        return null;
    }
}
