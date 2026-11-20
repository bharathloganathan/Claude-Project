# DESIGN REVIEW: Automated Documentation Sync, Agentic SDLC

**Status: APPROVED by the human on 2026-11-16, with the DR-09 correction applied (uncaught application exceptions are caught and mapped to exit 2, and fatal JVM errors are not swallowed).**

**File status:** written 2026-11-16. The human also approved on 2026-11-16: the lean structure (25 docsync types in 6 packages plus 3 E1 API types), the DR-10 failed-report mechanism, the DR-12 marker rules, characterization tests before any `/students/` routing change, and the AD-18 engine spike plan (Section 6) with its four additions. Implementation Planning and implementation have not started. No approved document was modified by this review.

**Inputs:** `docs/requirements.md` (approved 2026-11-13), `docs/architecture.md` (approved 2026-11-13, final approval recorded 2026-11-16), all Java and frontend sources, `README.md` and `pom.xml`.

**Reviewers:** the main Claude Code session (the proposed `design-review-agent` does not exist) and one independent read-only review agent that was not shown the main review's findings. Independence is limited because the main session also drafted the architecture. Nothing was executed except tool-version listings and read-only file checks.

**Severity:** High = wrong behavior, a failed acceptance criterion or a broken guarantee if unaddressed. Medium = needs an explicit decision or a plan task. Low = note for implementation. **Origin:** M = main review, X = independent review.

## 1. Verdict

- No contradiction with the approved requirements (D1 to D25) was found. No Approved architecture decision is reopened.
- The 12 open items cited by the architecture are exactly the 12 non-closed items in the requirements. No closed item is cited.
- The findings are gaps to close in Implementation Planning, not design reversals.
- The core generator (E1, then the engine) can proceed without waiting for the workflow track.
- Gating is unchanged: AD-01, AD-17 and AD-12/AD-14 are not relied on until their AD-18 checks pass. **The spike is pending.**

## 2. Human decisions applied (2026-11-16)

These are the human's decisions made during this review. The approved requirements and architecture documents are not edited. This table is where the decisions are recorded.

| # | Decision | Open item | How it applies |
|---|---|---|---|
| 1 | Unexpected handler failures and static-file read failures return HTTP 500 with INTERNAL_SERVER_ERROR | OI-06 | DR-03 |
| 2 | HEAD and OPTIONS on static routes return 405 | OI-21 | Router allowed-methods set is GET only |
| 3 | No `StudentRequest` or `DeleteResponse` classes. Contracts derive from existing source declarations | OI-16 | DR-19 |
| 4 | For `/students/` with an empty id, characterization tests establish today's behavior first. No change without a reported compatibility impact | none | DR-01 |
| 5 | Malformed percent encoding in a form body returns 400 VALIDATION_ERROR with a clear fixed message | none | DR-02 |
| 6 | The `Allow` header is omitted for now | OI-04 | Not asserted by any AC |
| 7 | Source digest is SHA-256 of the LF-normalized `StudentService` source | OI-07 | DR-14 |
| 8 | An exit-2 failure cannot leave a stale report that appears to represent the current run | OI-08 | DR-10 (mechanism proposed) |
| 9 | Unknown or nested marker IDs are integrity failures, exit 2 | none | DR-12 |
| 10 | Email requiredness comes from actual frontend and backend behavior, not from `contains("@")` alone | OI-09 (part) | DR-19 |

## 3. Scope direction

- The Student Management System is the input project. It is not rebuilt.
- E1 is the minimal change the approved requirements demand (FR-E1-01 to 13). It reuses the existing `routes()` list and `Route` record as the single route source, adds a static-route list, and replaces the five contexts with one exact-match `/` handler.
- `StudentService`, `StudentRepository` and `Student` stay unchanged (see DR-06).

**Lean structure (replaces the roughly 79 types of AD-08, which is a Proposed decision).** Docsync main code is 25 types in 6 packages. E1 adds 3 types in `org.example.api`: `Router`, `JsonWriter` and `ErrorCode`.

| Package | Types |
|---|---|
| `docsync` | `DocSyncCli` (main, argument parsing, the one exit-code boundary), `DocSyncException` (one class with a kind) |
| `docsync.io` | `RootPaths` (resolves `--root` once, logical names, containment guard), `ProjectFiles` (read-only), `DocWriter` (catalog docs only), `ReportWriter` (`docs/reports/` only), `AtomicWriter` (staging and move, with an injectable mover) |
| `docsync.model` | `SourceModel` (nested immutable records), `RuleSpec`, `Findings` (nested stale, drift and run-result records) |
| `docsync.analysis` | `JavaAnalyzer`, `BackendRules` (idiom table), `HtmlAnalyzer`, `JsAnalyzer` |
| `docsync.docs` | `DocCatalog`, `EndpointsDoc`, `ContractDoc`, `ServiceDoc`, `UiDoc`, `MarkedDocument` (marker parsing, replace, append, line endings) |
| `docsync.engine` | `DocSyncEngine`, `SyncService`, `VerifyService`, `DriftDetector`, `ReportRenderer` |

**Safety properties kept:** verify has no `DocWriter`, and its `ReportWriter` is fenced to `docs/reports/`. `--root` is resolved once. All I/O is root-bound. A single exception-to-exit-code boundary exists. Output is deterministic. Replacement is atomic-move only with no fallback. All approved FR, NFR and AC behavior is unchanged.

## 4. Findings

### A. E1 API (existing code changes minimally)

| ID | Sev | Finding | Required action | Origin |
|---|---|---|---|---|
| DR-01 | High | Router matching is unspecified for an empty id, a trailing slash, extra segments, `%2F` and query strings. This decides 404 against 405 for `POST /students/` (`StudentApiServer.java:63-69, :97`). | Write characterization tests first, covering all methods on `/students/`, `/students/a/b` and `/students?x=1`. Define `{id}` as exactly one non-empty segment. Report any behavior change as a compatibility note before making it. The likely change is `POST /students/` from 405 to 404. | M, X |
| DR-02 | High | A malformed `%` escape makes `URLDecoder` throw. `readForm` runs outside the `try`, so today the connection drops (`:80, :125`). | Catch form-parsing failures separately from the service's validation exception. Return 400 VALIDATION_ERROR with a fixed message. | M, X |
| DR-03 | High | An unexpected handler exception, or a missing static file (`:30, :39, :48`), has no response today. | 500 INTERNAL_SERVER_ERROR with a generic message and no internals. Test with a failing service subclass and with a missing `frontend/` directory. | M, X |
| DR-04 | Medium | AC-E1-02 needs JSON parsing in tests, but no parser exists and no dependency is allowed. The hand-written `JsonWriter` has no escaping AC. | A small test-side JSON validator under `src/test`, with no `pom.xml` change. Add escaping cases: quote, backslash, control character, non-ASCII. | M, X |
| DR-05 | Medium | `StudentApiServer(service, port)` has no start, stop or bound-port contract. Static files still come from the working directory (OBS-01). | Add `start()`, `stop()` and `port()`. Bind tests to loopback with port 0. AC-E1-12 runs from the Maven base directory. | M, X |
| DR-06 | Low | The architecture marks `StudentService` as CHANGED for an "injectable seam". The class is non-final with a public no-arg constructor, so tests can subclass it. Any edit changes the digest and risks D10. | Leave `StudentService` unchanged. `Main` is the only user of `attachRoutes` and `routes()` (verified by search), so changing them is safe. | M |
| DR-07 | Low | AC-E1-01 becomes tautological once dispatch is built from the single table. | Add HTTP-level per-route tests that call each route over HTTP. | M, X |
| DR-08 | Low | AC-E1-04 lists `GET /students/unknown` among unknown paths, but it matches a known route and is a missing-student 404. | Both cases return 404 NOT_FOUND, so there is no behavior difference. The tests name the two cases separately. | X |

### B. Engine

| ID | Sev | Finding | Required action | Origin |
|---|---|---|---|---|
| DR-09 | High | An uncaught application exception ends the JVM with exit code 1, the same value as STALE, so a tool failure could be mistaken for stale documentation. The architecture catches only its own `DocSyncException` hierarchy at the exit boundary. | `DocSyncCli` catches ordinary application exceptions (`DocSyncException` and any other `Exception`) and returns 2 with a message. It does not catch fatal JVM errors such as `VirtualMachineError` (including `OutOfMemoryError` and `StackOverflowError`). Those propagate and are not swallowed. Test with an injected `RuntimeException` (exit 2) and an injected `Error` (not converted to 2). | X |
| DR-10 | High | An exit-2 run can leave an earlier report that looks current, and Verify and PR evidence cites these reports (IP-3). | **Mechanism proposed:** after the root resolves, an exit-2 run atomically replaces its own report with a FAILED report (command, exit 2, message, no timestamp). A failed sync report also lists the REPLACED, FAILED and NOT_ATTEMPTED outcomes. If that write fails, delete the old report. If that also fails, stderr says the report may be stale. An invalid root writes nothing. | M |
| DR-11 | Medium | The atomic-move failure paths have no ACs and no way to inject a failure. Crash-left `.docsync-*.tmp` files are not git-ignored. | `AtomicWriter` takes an injectable mover. Add architecture-derived tests for stop at the first failure, NOT_ATTEMPTED listing and temp cleanup. Add `docs/**/.docsync-*.tmp` to `.gitignore` when implementing. There is no retry, so a transient lock fails the run. The spike records whether that is acceptable. | M, X |
| DR-12 | Medium | Marker edge cases are unspecified: unknown IDs, nested blocks, and marker text inside manual prose or code fences. | Unknown or nested IDs give exit 2 (decision 9). A marker must stand alone on its line. There is no code-fence handling. Manual text in catalog docs must not contain literal markers, and the error message says so. | M, X |
| DR-13 | Medium | Generated Markdown needs escaping of pipe characters and backticks, and generated text must never contain marker syntax. | Escape in one helper. Exit 2 if generated text contains `docsync:begin` or `docsync:end`. Test with a rule that contains a pipe. | M |
| DR-14 | Medium | Mixed line endings are possible. `core.autocrlf=true` and there is no `.gitattributes`. CRLF files in the repo are `style.css`, `README.md` and `StudentRepository.java`. The engine's inputs are LF today. A BOM would make javac reject the source. | On read: strip a leading BOM, decode strict UTF-8 (exit 2 on malformed), and normalize to LF. The digest and the compiler input both use the normalized text (decision 7). Test with CRLF and BOM copies of the inputs. | M |
| DR-15 | Medium | The four-file ACs (AC-E2-06, 07, 08 and 15) assume E3 is delivered. The UI doc also contains the DRIFT block, so a backend rule change makes `student-ui.md` STALE. | The plan states which ACs hold at E2 completion (three docs) and which only after E3. The consequence for the UI doc is documented. | M |
| DR-16 | Medium | The atomic-move failure scope in AD-17 and R-19 names only `sync`. If an existing target cannot be replaced, the second `verify-report.md` write also fails, so verify breaks. | AD-18 check 1 gates every write, including reports. | M |
| DR-17 | Low | The local JDK is Oracle 27.0.1 and the pom targets release 17. AD-18's Compiler Tree check covers only the README-style launch. | Add a Maven compile with release 17 that imports `com.sun.source` (see Section 6). | M, X |
| DR-18 | Low | Path containment on Windows (case-insensitive paths, drive letters, backslashes, trailing dots, junctions) is stated only for `..` and symlinks (R-08, a deferred design detail). | Add Windows traversal tests for `RootPaths`. Symlink and junction handling stays a deferred design detail. | M |

### C. Extraction and drift

| ID | Sev | Finding | Required action | Origin |
|---|---|---|---|---|
| DR-19 | High | AC-E3-04 requires exactly two DRIFT findings, but the idioms and how `required` is derived are unlisted (OI-09). The email check `email == null or !email.contains("@")` has no required idiom. The phone check has a null and blank ambiguity. `validateStudentInput` has a 3-argument overload that delegates. | Write the idiom table before E3. Analyze the 4-argument overload, and treat the 3-argument one as delegation. **Derive requiredness from behavior (decision 10):** evaluate each recognized idiom on a blank input. Rejected means required, accepted means optional. Cross-check with characterization tests of the real service. | M, X |
| DR-20 | Medium | The architecture's drift flowchart is an if-else chain, so a field with both a required and a pattern difference reports only one finding (AC-E3-05). | Compare `required`, `minLength` and `pattern` independently, in fixed order, one finding per differing attribute. | X |
| DR-21 | Medium | The scope of the restricted JS lexer is undefined. A new syntax anywhere in `app.js` (including the FR-E1-12 change) could exit 2 for every document. | Lex only the `validationRules` literal and the payload object. Everything else in `app.js` is skipped. HTML extraction covers only the form controls. An unrecognized backend condition still exits 2. | X |
| DR-22 | Low | The route extractor treats the handler argument as an ordinary argument. | The first five `Route` arguments must be string literals, or the run exits 2. The sixth (the handler reference) is ignored. | M |

**Expected extraction on today's source (to be proved by tests):**

| Field | Frontend rule | Backend rule (behavior on blank input) | Result |
|---|---|---|---|
| name | required, min length 2 | required (blank rejected), no minimum | DRIFT minLength |
| email | required, regex | required (blank rejected by the contains check), CONTAINS "@" | DRIFT pattern |
| department | required | required | none |
| phone | optional, regex | optional (blank accepted), same regex | none after stripping one leading `^` and one trailing `$` and unescaping the Java literal |

### D. Workflow track (does not block the core generator)

| ID | Sev | Finding | Required action | Origin |
|---|---|---|---|---|
| DR-23 | High | Allowlisted `mvn`, `java` and `javac` can run arbitrary code. H2 filtering by command text is bypassable. The implementation agent could edit `.claude/settings.json` and hooks. The "no git writes" guarantee is only best-effort. | State these guards as best-effort in `CLAUDE.md`. Allow only exact commands through wrapper scripts. Deny git for agents. Protect `settings.json` and `.claude/hooks/**` with ask or deny rules. Manual approval mode stays the effective control. | M, X |
| DR-24 | High | H1 gates agent launches only. The main session can do stage work itself, as in phases 1 to 3. | Do not claim a stronger guarantee. Approval recording stays a human-accepted permission prompt (OQ-4). | M, X |
| DR-25 | High | The hook exit-code mapping is inconsistent. The CLI uses 0, 1 and 2, hooks block only on 2, and exit 2 from a Stop hook forces the agent to continue. WF-05 wants exit 1 to "require sync" and exit 2 to "stop". | Write an explicit table before H3 is built: CLI code, hook code and message. Re-check the AD-14 candidates against it. | X |
| DR-26 | Medium | After the engine is built but before the first `sync`, verify exits 2 (D6), and WF-05 stops the workflow. | The first `sync` is a human-authorized step at the end of E2. The hook reports "docs not generated" distinctly. | X |
| DR-27 | Medium | Windows and Git Bash: paths with spaces, backslashes and `;` in classpaths, MSYS-style paths passed to a native Java process, and no `.gitattributes` for `.sh` files. `jq` is missing and `python` is only the Store stub. Node v26 is present. | Hook parsing uses Node or a Java single-file program. Add `.gitattributes` with `*.sh text eol=lf`. The hook spike (deferred) prints the received `--root` from the real path. | M, X |
| DR-28 | Medium | No formal approval records exist (OI-17 open). Stages 1 to 3 are recorded only in document text. | Decide OI-17 at the start of the workflow track. Back-fill with the human's confirmation. | M |

### E. Process notes

- Status lines in the approved documents are point-in-time. They are not edited. Progress is recorded in the phase deliverables.
- The review stage was run by the main session plus one independent agent, because no `design-review-agent` exists.

## 5. Remaining blockers

| # | Blocker | Needed before | Owner |
|---|---|---|---|
| B1 | Approve this Design Review | Implementation Planning | Human |
| B2 | Approve the AD-18 engine spike plan (Section 6) | Engine code | Human |
| B3 | Approve the DR-10 failed-report mechanism and the DR-12 marker rules (no fences) | Engine code | Human |
| B4 | Approve the DR-01 outcome after characterization tests (any behavior change) | E1 router code | Human, after tests |
| B5 | Idiom table and extraction subset (DR-19, DR-21, DR-22) written in the plan | E3 code | Plan |
| W1 | Hook exit-code table, OI-11 trigger, bootstrap path | Workflow hooks | Human |
| W2 | OI-17 record location, OI-01 stage content | Workflow track | Human |
| W3 | OI-20 manual `app.js` check procedure | Verify stage | Plan |

**Status update 2026-11-16:** B1, B2 and B3 are approved. B4, B5 and W1 to W3 remain.

Still open from the architecture: OI-01, OI-09 (details), OI-11, OI-17, OI-18 (to verify in testing) and OI-20.

## 6. AD-18 engine spike plan (APPROVED 2026-11-16; results are reported separately, not in this document)

**Scope:** engine checks only: the Compiler Tree API smoke test (OQ-7) and the four atomic-move checks (OQ-9). The hook and permission checks stay in AD-18 and move to the workflow track. All files go in a scratch directory with a space in its name, outside the repository. **Nothing under the repository changes.** The pass criteria are those already approved in AD-18.

**Additions to AD-18 that need approval (clarifications, not new decisions):**
1. A Maven compile with `maven.compiler.release=17` that imports `com.sun.source.util.JavacTask` (DR-17).
2. A BOM-prefixed source parse check (DR-14).
3. For the "locked by another process" case, a native non-share lock (PowerShell `FileShare.None`), because a Java-held file usually allows delete-sharing and would give a false pass.
4. The environment record also lists the Maven version and whether the folder is OneDrive-redirected.

---

**This review was approved by the human on 2026-11-16 with the DR-09 correction. Its gating is unchanged: AD-01, AD-17 and AD-12/AD-14 are not relied on until their AD-18 checks pass. Implementation Planning and implementation have not started.**
