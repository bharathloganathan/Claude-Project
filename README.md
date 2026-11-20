# Student Management Documentation Sync Demo

A small Student Management application (in-memory repository, no database) and a documentation
sync engine, `org.example.docsync`. The engine reads the project sources and generates four
Markdown documents inside marked blocks, and it verifies that those blocks are still up to date.
The target is Java 17, the runtime uses only the JDK, and the tests use JUnit 5.

## Key directories
- `frontend/` - the UI files: `index.html`, `app.js` and `style.css`
- `src/main/java/` - the Student Management backend, the HTTP API and the docsync engine
- `src/test/java/` - the JUnit 5 tests
- `docs/` - the approved SDLC documents, the four generated documents and the two reports
- `.claude/` - the Claude Code agents used by the agentic SDLC workflow

## Build and run

With Maven (add `-o` to work offline):

```bash
mvn compile
java -cp target/classes org.example.Main
```

Without Maven, with the Java compiler directly:

```bash
javac -d out $(find src/main/java -name "*.java")
java -cp out org.example.Main
```

The server listens on http://localhost:8000. Start it from the repository root: it reads the files in
`frontend/` relative to the working directory.

Run the tests with `mvn test`.

### API behavior

| Method and path | Result |
|---|---|
| `GET /students` | all students |
| `POST /students` (form fields `name`, `email`, `department`, `phone`) | the created student |
| `GET /students/{id}` | one student |
| `DELETE /students/{id}` | `{"deleted":true}` |
| `GET /`, `GET /style.css`, `GET /app.js` | the frontend files |

Errors use `{"error":"<message>","code":"<CODE>"}` with the codes `VALIDATION_ERROR` (400),
`NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405) and `INTERNAL_SERVER_ERROR` (500). Compared with the
original application:

- BC-1: every other path, including `/index.html` and `/favicon.ico`, returns 404 `NOT_FOUND`.
- BC-2: deleting a missing id returns 404 `NOT_FOUND` instead of `{"deleted":false}`.
- BC-3: the validation rules are unchanged.
- BC-4: the error alert in `app.js` shows the `error` text instead of the raw response body.
- BC-5: a known route called with an unsupported method returns 405 `METHOD_NOT_ALLOWED`
  (the static routes used to answer every method with 200).

## Documentation sync

Build first (`mvn compile`), then run from any directory:

```bash
java -cp target/classes org.example.docsync.DocSyncCli sync --root <project-directory>
java -cp target/classes org.example.docsync.DocSyncCli verify --root <project-directory>
```

`--root` is the base directory of every input and output: the Java sources, the frontend files, the
four documents and both reports. A relative `--root` is resolved against the directory the command is
started from; the engine reads and writes no other path relative to the working directory.

| Exit code | Meaning |
|---|---|
| 0 | `sync` completed, or `verify` found every document up to date |
| 1 | `verify` found at least one STALE document |
| 2 | usage error, invalid root, marker fault, unsupported construct, missing expected file or block, or a write failure |

### Generated files

| File | Blocks | Source |
|---|---|---|
| `docs/api/endpoints.md` | `api-routes`, `api-errors` | the `routes()` list of `StudentApiServer` and `ErrorCode` |
| `docs/api/student-contract.md` | `contract-student-fields`, `contract-create-params` | the `Student` record and the create endpoint's form parameters |
| `docs/backend/student-service.md` | `service-methods`, `service-digest` | the public methods of `StudentService` and a SHA-256 digest of its source |
| `docs/ui/student-ui.md` | `ui-controls`, `ui-rules`, `ui-field-mapping`, `ui-drift` | `form#student-form` in `index.html`, `validationRules` and the payload in `app.js`, and the rules of `validateStudentInput` |

`sync` writes `docs/reports/sync-report.md` (each file `created`, `updated` or `unchanged`) and
`verify` writes `docs/reports/verify-report.md`. Both reports are deterministic: no timestamps and
no absolute paths. A run that fails after the root is resolved replaces its report with a FAILED
report that holds the message.

### Marker format

A generated block sits between two marker lines, each alone on its line:

```
<!-- docsync:begin api-routes -->
...generated text...
<!-- docsync:end api-routes -->
```

Block ids use lowercase letters, digits and hyphens and are unique across the four files. `sync`
changes only the text inside a block, so manual text outside the markers is kept byte for byte. It
creates a missing file, appends a missing block after the existing text, and a second `sync` with no
changes writes nothing. A missing end marker, an unknown, duplicate or nested id, or a malformed marker
line is an error (exit 2) and nothing is written. Manual text must not contain literal
`docsync:begin` or `docsync:end` markers. `verify` never writes a document: a missing file or block
is exit 2, and a block that differs from the source is STALE (exit 1). Line endings are normalized
before comparing, and text outside the blocks never matters.

### Drift

The UI document also reports DRIFT: a field whose frontend rule (`required`, `minLength`, a pattern)
differs from the backend rule. Drift is listed in `docs/ui/student-ui.md` and in the verify report and
printed as `verify: DRIFT ...`. It is not STALE and never changes the exit code. Message text, select
options, `maxlength` and the input type are not drift. The current sources have two findings: the
frontend `minLength` of the name (the backend has none) and the frontend email pattern (the backend
only checks for `@`).

### Unsupported constructs

The engine fails (exit 2) instead of guessing when a source is outside the supported shapes, for
example a route argument that is not a string literal, a validation rule written as a template
literal, or a backend rule that is not one of the supported idioms.

### Safety

The CLI never starts a process and never uses Git: it does not commit, merge or push. Documents are
replaced one at a time with an atomic move. Replacement is not transactional across files, and a
failure lists which files were replaced, failed or not attempted. Path containment is by file name
only, so do not place links or junctions inside the project's `docs` directory.
