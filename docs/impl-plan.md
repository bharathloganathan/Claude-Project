# IMPLEMENTATION PLAN: Documentation Sync Generator (APPROVED by the human on 2026-11-16)

Approved by the human on 2026-11-16 and written to `docs/impl-plan.md` from the corrected draft v2. Nothing is implemented. Implementation needs separate explicit human approval.

Sources of truth: `docs/requirements.md`, `docs/architecture.md`, `docs/design-review.md` (all approved, unchanged), and the human decisions of 2026-11-16 listed in section 0.

## 0. Human decisions applied

| # | Decision |
|---|---|
| 1 | Orchestrator: Option A. The main Claude Code session is the orchestrator. No ninth Orchestrator Agent. The eight existing stage agents are retained. |
| 2 | P-1: Option 1. `routes()` and `staticRoutes()` stay in `StudentApiServer`. See PN-1. |
| 3 | P-2 accepted: the block IDs and new-file layout in section 7. |
| 4 | P-4 accepted: the `.gitignore` addition in section 7. |
| 5 | OI-01: all unavailable original-assignment details stay unresolved. Nothing is invented. |
| 6 | OI-17: `docs/workflow/` stays a proposal pending approval. |
| 7 | B5: the E3 extraction specification (section 6B) is a separate approval gate. E3 does not start without it. |
| 8 | DC-1 stays a proposal. The Student Management System is not modified for a demonstration without explicit approval. |
| 9 | Traceability uses a mechanical requirement-ID and acceptance-criterion coverage check (section 10). |
| 10 | The custom `implementation-planning-agent` reviewed this plan independently (report dated 2026-11-16). |
| 11 | H1: `ProjectCopySelfCheckTest` works on a temporary project copy and never runs sync or verify on the real repository root during `mvn test`. The first real-repository sync is a separate human-authorized operation executed by the human operator. |
| 12 | H3: an E2 extraction note (section 6A) is approved together with this plan. S2 to S9 and all E3 extraction stay behind B5. |
| 13 | M7: the missing-static-file check is a subprocess test (task E1-5). If it proves infeasible, work stops for a human decision. |
| 14 | M8: the human accepts the AD-18 engine spike as passed for the tested environment (PN-7). |
| 15 | M3: a human approval gate sits between each review task and its verify task. |
| 16 | H2: the revised manual procedure in section 8 is accepted as the proposal. Its approval (OI-20) is a prerequisite before E1-6. |
| 17 | The confirmed review findings M1 to M6, M8 to M10 and L1 to L12 are applied. |

## 1. Plan notes

- **PN-1 (RouteTable naming).** The approved architecture names a `RouteTable` class (section 3.1, AD-09, section 8.2, section 10). The later approved Design Review, section 3, says E1 "reuses the existing `routes()` list and `Route` record as the single route source, adds a static-route list", and its E1 types are `Router`, `JsonWriter` and `ErrorCode` only. By the human decision of 2026-11-16 (P-1, Option 1), the approved Design Review supersedes the proposed `RouteTable` naming in AD-09, which is a Proposed decision. `routes()` and `staticRoutes()` stay in `StudentApiServer`, and no `RouteTable` type is created. The engine reads `routes()` in `StudentApiServer.java`. The approved architecture file is not edited. This plan is the record of the supersession.
- **PN-2 (orchestration).** The main session orchestrates through the stage commands (AD-11, WF-02). No orchestrator agent, task or change request is created.
- **PN-3 (OI-01 unresolved).** The following stay unresolved because the original assignment is not available: per-stage deliverables, the stage-to-deliverable mapping, handoff criteria, naming and format rules for agents, prompts, instructions, skills and hooks, the assignment's own approval-recording semantics, and demonstration requirements. The approval mechanism itself is approved (OQ-4, AD-12): a human-accepted permission prompt on approval records. Stage agents use the generic gate only: present the output, state what was and was not verified, and wait for explicit human approval. Tasks RV-1 to RV-8, PR-1, CL-1 and RC-1 are name-based candidates and are not confirmed stage deliverables.
- **PN-4 (OI-17 proposal).** `docs/workflow/` (`state.json`, `approval-log.md`, `traceability.md`) remains a proposal. Until it is approved, review and verification evidence is presented in chat and stored only where the human approves.
- **PN-5 (DC-1 proposal).** The demonstration subject is undecided. W-6 cannot run the all-eight-stage demonstration until the human approves a subject. DC-1 (a small reversible `StudentService` addition) is only a proposal.
- **PN-6 (E3 gate).** Tasks E3-1 to E3-5, RV-5 and RV-6 are blocked until the human approves items A1 to A8 of section 6B. Approving this plan approves section 6A only.
- **PN-7 (AD-18 engine spike accepted).** On 2026-11-16 the human accepted the AD-18 engine spike as passed for the tested environment: Oracle JDK 27.0.1.0.3, Maven 3.10.0, Windows 11 (10.0.26200.9711), NTFS, a local Temp-directory path containing a space. The evidence is a set of scratch files, not repository records, under `C:\Users\BharathLa\AppData\Local\Temp\docsync spike\`: `env.txt`, `results-treesmoke-launcher.txt`, `results-treesmoke-classpath.txt`, `results-mvnprobe.txt`, `results.txt` (atomic-move checks), `results-m7-probe.txt` (the M7 feasibility probe), and the sources `TreeSmoke.java`, `AtomicSpike.java`, `MissingFrontendProbe.java` and `mvnprobe/`. Observed and accepted: the Compiler Tree API parses in-memory source with no classpath, tells literal from non-literal arguments and reports a syntax error as a diagnostic; javac rejects a BOM-prefixed source (so the reader strips a leading BOM); a Maven compile with release 17 importing `com.sun.source` succeeds; `Files.move` with ATOMIC_MOVE replaced an existing file and in the tested failure cases (target locked by a native handle, read-only target, directory target, missing temp file) it threw and left the original intact; a same-directory move on a path with spaces worked. **Limitations:** these are observations for the tested environment and cases only. They establish no universal filesystem guarantee. Untested and unverified: network or synced folders, antivirus or indexer interference, other JDK or Windows versions, transient locks that clear on their own, and AtomicMoveNotSupportedException (it could not be forced). The AD-18 hook and permission checks have not run, so AD-12 and AD-14 may not be relied on. E2-4 and E2-5 carry this acceptance as a precondition.
- **PN-8 (E2 extraction note).** Section 6A is approved together with this plan. It covers only what E2 needs: the route table, the create-handler lookup and form-parameter keys, the Student record, the service methods and digest, and the ErrorCode constants. It does not approve any E3 extraction (HTML, JS, backend validation idioms, drift, UI doc), which stays behind B5.
- **PN-9 (UI doc and drift).** The UI doc contains the DRIFT block (FR-E3-07), so a backend validation change that changes a drift finding makes `docs/ui/student-ui.md` STALE (DR-15). ACs that name four files are claimable in full only after E3.
- **PN-10 (agent runs before guards exist).** Review, verify and PR runs before W-5 rely on the agents' own written rules and on the human's manual approval prompts. Their Bash restriction is not enforced by settings or hooks until W-5 and the W-0 evidence exist.
- **PN-11 (first real-repository sync).** The first sync of the real repository (E2-10, E3-5) is a separately documented, human-authorized operation. The human operator reviews and authorizes it, runs sync, then runs verify. No agent runs it, and `mvn test` never does. The exact CLI commands are recorded in the task report once the actual `DocSyncCli` invocation is established. The README's target form (`java -cp target\classes org.example.docsync.DocSyncCli sync --root .`, and the same with `verify`) is the specification of intent, not a verified command.

## 2. Tasks

Conventions: every task ends at a human gate. Nothing is committed without authorization. Commands run from the repository root, and each gate also runs the full `mvn test` (verify tasks run it, review tasks do not). Tests are under `src/test/java/org/example/`. Main code is under `src/main/java/org/example/`.

### M-1 Plan record (first action after the plan is approved)

| Task | Scope (files and types) | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| PL-1 | After human approval, write `docs/impl-plan.md` from the approved plan text. The implementation agent acts only after plan approval. | WF-08 | none | none | `git status`, line count | File matches the approved text. |

### M0 Characterization tests (no production change)

| Task | Scope (files and types) | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| T0.1 | `api/ApiCharacterizationTest` against today's `attachRoutes` on a loopback port-0 server. Covers every method on `/students`, `/students/`, `/students/a/b`, `/students?x=1`, `/studentsabc`, `/nope`, `/index.html`, the 3 static routes (GET, POST, HEAD, OPTIONS), malformed `%`, current 400 and 404 bodies and `{"deleted":false}`. Unique values, no list-size assertions (shared static service). | FR-E1-09 | none directly | as left | `mvn test -Dtest=ApiCharacterizationTest` | Passes on unchanged code. The differences table below is presented. Gate G0: the human approves each behavior change. |
| T0.2 | `service/ServiceCharacterizationTest`: 1-character name and `a@b` accepted, check order, phone rules, both overloads, accept or reject per field on blank input. | FR-E3-08 | AC-E3-07 | as left | `mvn test -Dtest=ServiceCharacterizationTest` | Passes. Gate G0b. |

Expected differences after E1, presented at G0 (the characterization assertions that will change):

| Item | Today | After E1 | Basis |
|---|---|---|---|
| Unknown path (`/index.html`, `/favicon.ico`, `/nope`) | `index.html` with 200 | 404 NOT_FOUND, structured | BC-1 |
| DELETE of a missing id | `{"deleted":false}` with 404 | structured 404 | BC-2 |
| Static route with an unsupported method | 200 | 405 METHOD_NOT_ALLOWED, structured | BC-5 |
| 400 body | `{"error":"Invalid student input"}` | the service message, with code VALIDATION_ERROR | FR-E1-05 |
| 405 body on API routes | no `code` | adds `code` METHOD_NOT_ALLOWED | D9 |
| Success bodies | record-syntax text | valid JSON | FR-E1-02 |
| Malformed `%` in a form body | connection drops | 400 VALIDATION_ERROR, fixed message | decision 5 |
| `/students/` with an empty id | established by T0.1 | not changed without a reported compatibility impact; the likely change is `POST /students/` from 405 to 404 | decision 4 |

### M1 E1 (minimal API change)

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| E1-1 | `api/ErrorCode` (constants declared with literal arguments only, so the engine can read them), `api/JsonWriter`, and a test-side JSON checker under `src/test` | FR-E1-02, FR-E1-03, FR-E1-04 | AC-E1-02 | `JsonWriterTest` (quote, backslash, control character, non-ASCII, array, error object, and a checker self-test that rejects record syntax such as `Student[id=...]`) | `mvn test -Dtest=JsonWriterTest` | Valid by the checker. Gate. |
| E1-2 | `StudentApiServer(service, port)` with `start`, `stop`, `port` (loopback binding for tests), `Main` wiring. The old dispatch stays inside the new constructor so behavior is unchanged. T0.1 is re-pointed to the new API with the same assertions. | FR-E1-09, FR-E1-10 | AC-E1-08 | `ApiLifecycleTest` (two servers side by side, non-default ports, stop frees the port) and the re-pointed `ApiCharacterizationTest` | `mvn test -Dtest=ApiLifecycleTest,ApiCharacterizationTest` | Characterization assertions unchanged. Gate. |
| E1-3 | `api/Router` (one `/` handler, exact match, `{id}` is one non-empty segment, GET-only statics, structured 404, 405 and 500). `routes()` becomes an instance method returning `List.of(new Route(...))` with string literals for the first five arguments, and `staticRoutes()` is added (PN-1). T0.1 changes only where G0 approved. | FR-E1-01, FR-E1-03, FR-E1-04, FR-E1-06, FR-E1-07, FR-E1-09, FR-E1-13 | AC-E1-01, AC-E1-04, AC-E1-05, AC-E1-09, AC-E1-12 | `ApiRoutingTest` (one HTTP test per route, every method, HEAD and OPTIONS give 405, static content equals the `frontend/` files, a throwing handler gives 500, and `GET /students/unknown` tested separately from an unknown path) | `mvn test -Dtest=ApiRoutingTest,ApiCharacterizationTest` | Gate |
| E1-4 | JSON handlers, 400 with the service message, structured 404 on a DELETE miss, `{"deleted":true}`, malformed `%` gives 400 VALIDATION_ERROR with a fixed message, a failing service gives 500. Form parsing is separate from the service try-block. The service is constructor-injected through the instance field `service`, and the create handler keeps exactly one call of the form `service.createStudent(a1, a2, a3, a4)`, whose four arguments are `getOrDefault` calls with unique string-literal keys and empty-string defaults (the shape section 6A reads). | FR-E1-02, FR-E1-03, FR-E1-04, FR-E1-05, FR-E1-08, FR-E1-09 | AC-E1-02, AC-E1-03, AC-E1-06, AC-E1-07, AC-E1-09 | `ApiResponseTest` | `mvn test -Dtest=ApiResponseTest` | All E1 JUnit ACs pass. Gate. |
| E1-5 | Missing-static-file check as a subprocess test. A test-only server class under `src/test` is started in a child JVM whose working directory is a temp directory with no `frontend/`. The test asserts `GET /` gives a structured 500 INTERNAL_SERVER_ERROR. No production change, and OBS-01 stays intact. A feasibility probe against today's code showed the mechanism works (PN-7). If it proves infeasible after E1-3, work stops and the human decides. | FR-E1-03, FR-E1-04 | AC-E1-09 | `MissingFrontendSubprocessTest` | `mvn test -Dtest=MissingFrontendSubprocessTest` | Test passes, or work stops for a human decision. Gate. |
| E1-6 | Minimal `frontend/app.js` change in the error path only. Prerequisite: the human approves the OI-20 procedure (section 8) before this task starts. | FR-E1-12 | AC-E1-11 (manual, EX-01) | none; manual procedure in section 8 | `git diff frontend/app.js`; optionally `node --check frontend/app.js` | Only the catch path changed. Manual result recorded. Gate. |
| RV-1 | Review stage: `review-agent` reviews the E1 increment (name-based candidate, PN-3). Read-only git commands only. It runs no build or test. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | the review report | Review gate: the human triages the findings before RV-2 starts. |
| RV-2 | Verify stage: `verify-agent` runs after the review gate. It runs `mvn test` and reports real output. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | `mvn test` | The human approves the evidence. |

### M2 Engine core

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| E2-1 | `io/RootPaths` (resolve once, logical names, containment), `io/ProjectFiles` (strip BOM, strict UTF-8, LF-normalize) | FR-E2-20 | AC-E2-13 | `RootPathsTest` (`..`, drive letters, backslashes, trailing dots, case), `ProjectFilesTest`, `NoCwdApiScanTest` | `mvn test -Dtest=RootPathsTest,ProjectFilesTest,NoCwdApiScanTest` | Gate |
| E2-2 | `DocSyncException`, `DocSyncCli` (catches `Exception` and returns 2, never catches `Error`), `DocSyncEngine` stub | FR-E2-01, FR-E2-17, FR-E2-19 | AC-E2-01, AC-E2-12 | `DocSyncCliTest` | `mvn test -Dtest=DocSyncCliTest` | Gate |
| E2-3 | `docs/MarkedDocument`: markers alone on a line, no fence handling, unknown or nested IDs give exit 2, generated text containing marker syntax gives exit 2, existing line endings preserved (CRLF stays CRLF, LF for new files) | FR-E2-05, FR-E2-06, FR-E2-07, FR-E2-08, FR-E2-09, FR-E2-10 | AC-E2-03, AC-E2-04, AC-E2-05 | `MarkedDocumentTest` (missing end, unmatched, malformed, duplicate, nested, manual text byte-identical, CRLF doc keeps CRLF) | `mvn test -Dtest=MarkedDocumentTest` | Gate |
| E2-4 | `io/AtomicWriter` (injectable mover), `DocWriter` (catalog docs only), `ReportWriter` (`docs/reports/` only). Append the accepted `.gitignore` lines (section 7). Precondition: spike acceptance recorded in PN-7. | FR-E2-08, FR-E2-13 | AC-E2-09 | `AtomicWriterTest` (stage all first, stop at the first failure, REPLACED, FAILED and NOT_ATTEMPTED, temp cleanup, one real-filesystem replace), `WriterFenceTest` (`ReportWriter` rejects paths outside `docs/reports/`, `DocWriter` rejects non-catalog paths) | `mvn test -Dtest=AtomicWriterTest,WriterFenceTest`; `git check-ignore -v docs/api/.docsync-1.tmp` prints the rule | Gate |

### M3 and M4 Backend extractor and generators

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| E2-5 | `analysis/JavaAnalyzer` and `model/SourceModel`, per the E2 extraction note (section 6A). `SourceModel` carries only the E2 shapes. The UI and drift records are added in E3. Uses the Compiler Tree API on in-memory text and exits 2 if no compiler is present. Precondition: spike acceptance recorded in PN-7. | FR-E2-02, FR-E2-04, FR-E2-22 | AC-E2-02, AC-E2-10 | `JavaAnalyzerTest` (real post-E1 sources, plus fixtures for dynamic routes, non-literal arguments, BOM and CRLF digest) | `mvn test -Dtest=JavaAnalyzerTest` | Gate |
| E2-6 | `docs/DocCatalog` (three docs at this point), `EndpointsDoc` (blocks `api-routes`, `api-errors`), `ContractDoc` (Student fields and the 4 create parameters only), `ServiceDoc` (7 methods and the digest). One escape helper for pipe characters and backticks, located in `DocCatalog` (no new type). | FR-E2-03, FR-E2-22, FR-E1-11 | AC-E1-10, AC-E2-02 | `EndpointsDocTest` (two separate assertions. First, a baseline assertion that the four required constants `VALIDATION_ERROR`, `NOT_FOUND`, `METHOD_NOT_ALLOWED` and `INTERNAL_SERVER_ERROR` are still present in `ErrorCode`, so an existing required code cannot silently disappear. This baseline is a minimum and not an exhaustive list. Second, a dynamic assertion that iterates over every `ErrorCode` enum constant, for example through `ErrorCode.values()`, and verifies that the generated endpoint documentation contains a corresponding documentation case for each. This assertion does not depend on a hardcoded list, so a constant added later automatically requires its documentation case. Satisfies AC-E1-10), `ContractDocTest`, `ServiceDocTest`, `DocEscapingTest` (a rule containing a pipe) | `mvn test -Dtest=EndpointsDocTest,ContractDocTest,ServiceDocTest,DocEscapingTest` | Gate |

### M5 Sync, verify, STALE and reports

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| E2-7 | `engine/SyncService` (7 phases), `ReportRenderer`, `Findings` (E2 shapes only). A failed sync writes a FAILED report with per-file outcomes. | FR-E2-08, FR-E2-09, FR-E2-10, FR-E2-11, FR-E2-12, FR-E2-13, FR-E2-15, FR-E2-18, NFR-02, NFR-03 | AC-E2-03, AC-E2-04, AC-E2-06, AC-E2-07, AC-E2-11, AC-NFR-02 | `SyncServiceTest` (empty `docs/`, append a missing block, three-run byte-identical, CRLF preserved and a second sync unchanged, a marker fault writes nothing, an injected move failure gives a FAILED report) | `mvn test -Dtest=SyncServiceTest` | Gate |
| E2-8 | `engine/VerifyService`, the verify report and the failed-run report (DR-10) | FR-E2-14, FR-E2-15, FR-E2-16, FR-E2-17, FR-E2-18 | AC-E2-05, AC-E2-06, AC-E2-08, AC-E2-09, AC-E2-11 | `VerifyServiceTest` (STALE per source change, an edit outside a block gives 0, a CRLF conversion gives 0, a missing file gives 2, doc hashes unchanged) | `mvn test -Dtest=VerifyServiceTest` | Gate |
| E2-9 | `DocSyncEngine` wiring and the end-to-end checks | FR-E2-01, FR-E2-17, FR-E2-19, FR-E2-20 | AC-E2-01, AC-E2-12, AC-E2-13, AC-E2-14 | `EndToEndTest` (`sync` exit 0 on a fixture root, a subprocess started from another directory, decoy files), `NoGitUseTest`, `ProjectCopySelfCheckTest` (works on a temporary project copy only) | `mvn test -Dtest=EndToEndTest,NoGitUseTest,ProjectCopySelfCheckTest` | Gate |
| E2-10 | Verification evidence, not implementation work: the first real-repository sync of the three E2 docs, executed by the human operator (PN-11). The operator reviews and authorizes it, runs sync, then runs verify. Writes `docs/api`, `docs/backend` and `docs/reports`. | FR-E2-17 | none | none | commands recorded once the `DocSyncCli` invocation is established (PN-11); verify must exit 0 | E2 gate |
| RV-3 | Review stage: `review-agent` reviews the E2 increment (name-based candidate, PN-3). No build or test. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | the review report | Review gate: the human triages the findings before RV-4 starts. |
| RV-4 | Verify stage: `verify-agent` runs after the review gate and reports real `mvn test` output. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | `mvn test` | The human approves the evidence. |

### M6 E3 (blocked until B5 is approved, PN-6)

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| E3-1 | `analysis/HtmlAnalyzer` | FR-E3-02 | AC-E3-01 | `HtmlAnalyzerTest` | `mvn test -Dtest=HtmlAnalyzerTest` | Gate |
| E3-2 | `analysis/JsAnalyzer` | FR-E3-03, FR-E3-04 | AC-E3-02, AC-E3-03, AC-E3-08 | `JsAnalyzerTest` | `mvn test -Dtest=JsAnalyzerTest` | Gate |
| E3-3 | `analysis/BackendRules`, `model/RuleSpec`, `engine/DriftDetector`. The UI and drift record shapes are added to `SourceModel` and `Findings` here. | FR-E3-05, FR-E3-06, FR-E3-08, FR-E3-09 | AC-E3-04, AC-E3-05, AC-E3-07 | `BackendRulesTest` (each idiom, and requiredness equals the T0.2 behavior), `DriftDetectorTest`, `DriftEndToEndTest` (a fixture root giving exactly the two expected findings, and fixture changes giving new ones), `ValidationRulesUnchangedTest` (a golden check of the `validationRules` values) | `mvn test -Dtest=BackendRulesTest,DriftDetectorTest,DriftEndToEndTest,ValidationRulesUnchangedTest` | Gate |
| E3-4 | `docs/UiDoc`, four-file catalog, DRIFT in the UI doc and the verify report. Four-file variants of the E2 tests. | FR-E2-03, FR-E3-01, FR-E3-07, NFR-02, NFR-03 | AC-E2-05, AC-E2-06, AC-E2-07, AC-E2-08, AC-E2-09, AC-E2-13, AC-E2-14, AC-E2-15, AC-E3-06, AC-E3-09, AC-NFR-02 | `UiDocTest`, four-file variants of `SyncServiceTest`, `VerifyServiceTest` and `EndToEndTest`, and `ProjectCopySelfCheckTest` extended to four docs | `mvn test` | Gate |
| E3-5 | Verification evidence, not implementation work: the first real-repository sync of all four docs, executed by the human operator (PN-11), then verify, then a repeat sync that must be byte-identical. | NFR-02, NFR-03 | AC-NFR-02, AC-E2-15 | none | commands recorded once the invocation is established (PN-11) | E3 gate |
| RV-5 | Review stage: `review-agent` reviews the E3 increment (name-based candidate, PN-3). No build or test. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | the review report | Review gate: the human triages the findings before RV-6 starts. |
| RV-6 | Verify stage: `verify-agent` runs after the review gate and reports real `mvn test` output. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | `mvn test` | The human approves the evidence. |

### M7 README

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| D-1 | Correct `README.md`: build and run instructions, the CLI, exit codes, the marker format, the four generated files, BC-1 to BC-5, root-relative behavior. Remove the `.github/` claims and the "route declarations are not proof" caveat. | FR-DOC-01 | AC-DOC-01 | manual | Execute every command and path in the README, including the `javac $(find ...)` build line | Every claim executed. Gate. |

### M8 Workflow track (after the generator, does not block it)

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| W-0 | Enabling verification evidence, not implementation work: the remaining AD-18 hook and permission checks (needs human authorization for throwaway scripts) | WF-05, WF-06 | none | none | evidence presented | Gate |
| W-1 | Decide OI-01 details (only when supplied), OI-11, OI-17 and the hook exit-code table | WF-01, WF-NFR-06 | none | none | n/a | Gate |
| W-2 | `CLAUDE.md`, with the guards stated as best-effort (DR-23, DR-24) | WF-03, WF-06, WF-07, WF-09, WF-10 | AC-WF-03, AC-WF-08 | demonstration | review of the file | Gate |
| W-3 | `docsync` skill | WF-04 | AC-WF-04 | demonstration | review of the file | Gate |
| W-4 | Stage commands plus approve, reject and status. The main session orchestrates through them (PN-2). | WF-02 | AC-WF-02 | demonstration | review of the files | Gate |
| W-5 | `.claude/settings.json`, hooks H1 to H3, `.gitattributes` for `.sh` files. Includes ask or deny protection of `settings.json` and the hooks with exact-command wrappers (DR-23), the "docs not generated" message (DR-26), Node or Java for hook parsing (DR-27) and the "engine not built" message (R-10). | WF-05, WF-06, WF-07, WF-NFR-02, WF-NFR-05 | AC-WF-05, AC-WF-06, AC-WF-NFR-02, AC-WF-NFR-05 | real or simulated hook run | hook run output | Gate |
| W-6 | Records (per the OI-17 decision), traceability file and the all-eight-stage demonstration. Blocked until the subject is approved (PN-5). | WF-01, WF-06, WF-08, WF-NFR-01, WF-NFR-02, WF-NFR-03, WF-NFR-04, WF-NFR-06 | AC-WF-01, AC-WF-06, AC-WF-07, AC-WF-NFR-01, AC-WF-NFR-02, AC-WF-NFR-03, AC-WF-NFR-04, AC-WF-NFR-06 | demonstration | demonstration evidence | Gate |

### M9 Final evidence and delivery artifacts (name-based candidates, PN-3)

| Task | Scope | Requirements | Acceptance criteria | Tests | Verify | Done |
|---|---|---|---|---|---|---|
| RV-7 | Review stage: `review-agent` reviews everything, including the workflow files. No build or test. | WF-08 (review and verification evidence only; stage mapping unconfirmed, OI-01) | none | n/a | the review report | Review gate: the human triages the findings before RV-8 starts. |
| RV-8 | Verify stage: `verify-agent` runs the full suite after the review gate. | NFR-01, NFR-04, WF-08 | AC-NFR-01, AC-NFR-03 | the full suite | `mvn test`; the `DocSyncCli` verify command once its invocation is established (PN-11) | The human approves the evidence. |
| PR-1 | `pr-agent` drafts the PR description | WF-08 | none | none | the human reads it | Approved. No commit or push without authorization. |
| CL-1 | Changelog entry. Only if requested; OI-01 unresolved (no confirmed stage mapping) | WF-08 | none | none | the human reads it | Approved. |
| RC-1 | Reviewer checklist. Only if requested; OI-01 unresolved (no confirmed stage mapping) | WF-08 | none | none | the human reads it | Approved. |

## 3. Requirement to task to test matrix

Every requirement ID is written in full. A row passes the mechanical check only if it names at least one task defined in section 2, or is marked EXEMPT.

| Requirement | Tasks | Tests |
|---|---|---|
| FR-E1-01 | E1-3 | `ApiRoutingTest` |
| FR-E1-02 | E1-1, E1-4 | `JsonWriterTest`, `ApiResponseTest` |
| FR-E1-03 | E1-1, E1-3, E1-4, E1-5 | `JsonWriterTest`, `ApiRoutingTest`, `ApiResponseTest`, `MissingFrontendSubprocessTest` |
| FR-E1-04 | E1-1, E1-3, E1-4, E1-5 | `JsonWriterTest`, `ApiRoutingTest`, `ApiResponseTest`, `MissingFrontendSubprocessTest` |
| FR-E1-05 | E1-4 | `ApiResponseTest` |
| FR-E1-06 | E1-3 | `ApiRoutingTest` |
| FR-E1-07 | E1-3 | `ApiRoutingTest` |
| FR-E1-08 | E1-4 | `ApiResponseTest` |
| FR-E1-09 | T0.1, E1-2, E1-3, E1-4 | `ApiCharacterizationTest`, `ApiRoutingTest`, `ApiResponseTest` |
| FR-E1-10 | E1-2 | `ApiLifecycleTest` |
| FR-E1-11 | E2-6 | `EndpointsDocTest` |
| FR-E1-12 | E1-6 | none (EX-01 manual check) |
| FR-E1-13 | E1-3 | `ApiRoutingTest` |
| FR-E2-01 | E2-2, E2-9 | `DocSyncCliTest`, `EndToEndTest` |
| FR-E2-02 | E2-5 | `JavaAnalyzerTest` |
| FR-E2-03 | E2-6, E3-4 | `EndpointsDocTest`, `UiDocTest` |
| FR-E2-04 | E2-5 | `JavaAnalyzerTest` |
| FR-E2-05 | E2-3 | `MarkedDocumentTest` |
| FR-E2-06 | E2-3 | `MarkedDocumentTest` |
| FR-E2-07 | E2-3 | `MarkedDocumentTest` |
| FR-E2-08 | E2-3, E2-4, E2-7 | `MarkedDocumentTest`, `AtomicWriterTest`, `SyncServiceTest` |
| FR-E2-09 | E2-3, E2-7 | `MarkedDocumentTest`, `SyncServiceTest` |
| FR-E2-10 | E2-3, E2-7 | `MarkedDocumentTest`, `SyncServiceTest` |
| FR-E2-11 | E2-7 | `SyncServiceTest` |
| FR-E2-12 | E2-7 | `SyncServiceTest` |
| FR-E2-13 | E2-4, E2-7 | `AtomicWriterTest`, `SyncServiceTest` |
| FR-E2-14 | E2-8 | `VerifyServiceTest` |
| FR-E2-15 | E2-7, E2-8 | `SyncServiceTest`, `VerifyServiceTest` |
| FR-E2-16 | E2-8 | `VerifyServiceTest` |
| FR-E2-17 | E2-2, E2-8, E2-9, E2-10 (verification evidence only) | `DocSyncCliTest`, `VerifyServiceTest`, `EndToEndTest`, the human-run verify exit-0 output at E2-10 |
| FR-E2-18 | E2-7, E2-8 | `SyncServiceTest`, `VerifyServiceTest` |
| FR-E2-19 | E2-2, E2-9 | `DocSyncCliTest`, `NoGitUseTest` |
| FR-E2-20 | E2-1, E2-9 | `NoCwdApiScanTest`, `EndToEndTest` |
| FR-E2-22 | E2-5, E2-6 | `JavaAnalyzerTest`, `EndpointsDocTest`, `ContractDocTest`, `ServiceDocTest` |
| FR-E3-01 | E3-4 | `UiDocTest` |
| FR-E3-02 | E3-1 | `HtmlAnalyzerTest` |
| FR-E3-03 | E3-2 | `JsAnalyzerTest` |
| FR-E3-04 | E3-2 | `JsAnalyzerTest` |
| FR-E3-05 | E3-3 | `DriftDetectorTest`, `DriftEndToEndTest` |
| FR-E3-06 | E3-3 | `DriftDetectorTest`, `DriftEndToEndTest` |
| FR-E3-07 | E3-4 | `UiDocTest`, `VerifyServiceTest` |
| FR-E3-08 | T0.2, E3-3 | `ServiceCharacterizationTest`, `ValidationRulesUnchangedTest` |
| FR-E3-09 | E3-3 | `BackendRulesTest` |
| FR-DOC-01 | D-1 | manual README check |
| NFR-01 | RV-8 | `pom.xml` diff and `mvn test` |
| NFR-02 | E2-7, E3-4, E3-5 | three-run tests |
| NFR-03 | E2-7, E3-4, E3-5 | three-run tests |
| NFR-04 | RV-8 | this matrix and the full suite |
| NFR-05 | EXEMPT (states that no numeric targets exist, no AC by design) | none |
| WF-01 | W-1, W-6 | demonstration |
| WF-02 | W-4 | demonstration |
| WF-03 | W-2 | demonstration |
| WF-04 | W-3 | demonstration |
| WF-05 | W-0, W-5 | hook run |
| WF-06 | W-0 (enabling evidence only), W-2, W-5, W-6 | demonstration |
| WF-07 | W-2, W-5 | demonstration |
| WF-08 | PL-1, RV-1, RV-2, RV-3, RV-4, RV-5, RV-6, RV-7, RV-8 (review and verification evidence only; stage mapping unconfirmed, OI-01), PR-1, CL-1, RC-1 (CL-1 and RC-1 only if requested; OI-01 unresolved), W-6 | the human reads each artifact |
| WF-09 | W-2 | demonstration |
| WF-10 | W-2 | demonstration |
| WF-NFR-01 | W-6 | demonstration |
| WF-NFR-02 | W-5, W-6 | demonstration |
| WF-NFR-03 | W-6 | demonstration |
| WF-NFR-04 | W-6 | demonstration |
| WF-NFR-05 | W-5 | demonstration |
| WF-NFR-06 | W-1, W-6 | demonstration |

## 4. Acceptance-criteria coverage

Every acceptance criterion is written in full. A row passes the mechanical check only if it names at least one task defined in section 2.

| Acceptance criterion | Tasks | Claimable after |
|---|---|---|
| AC-E1-01 | E1-3 | E1 |
| AC-E1-02 | E1-1, E1-4 | E1 |
| AC-E1-03 | E1-4 | E1 |
| AC-E1-04 | E1-3 | E1 |
| AC-E1-05 | E1-3 | E1 |
| AC-E1-06 | E1-4 | E1 |
| AC-E1-07 | E1-4 | E1 |
| AC-E1-08 | E1-2 | E1 |
| AC-E1-09 | E1-3, E1-4, E1-5 | E1 |
| AC-E1-10 | E2-6 | E2 |
| AC-E1-11 | E1-6 | manual check |
| AC-E1-12 | E1-3 | E1 |
| AC-E2-01 | E2-2, E2-9 | E2 |
| AC-E2-02 | E2-5, E2-6 | E2 |
| AC-E2-03 | E2-3, E2-7 | E2 |
| AC-E2-04 | E2-3, E2-7 | E2 |
| AC-E2-05 | E2-3, E2-8, E3-4 | E2 for the E2 docs, E3 for the UI doc |
| AC-E2-06 | E2-7, E2-8, E3-4 | 3 docs after E2, 4 after E3 |
| AC-E2-07 | E2-7, E3-4 | 3 docs after E2, 4 after E3 |
| AC-E2-08 | E2-8, E3-4 | E2 without the UI bullet, E3 in full |
| AC-E2-09 | E2-4, E2-8, E3-4 | 3 docs after E2, 4 after E3 |
| AC-E2-10 | E2-5 | E2 |
| AC-E2-11 | E2-7, E2-8 | E2 |
| AC-E2-12 | E2-2, E2-9 | E2 |
| AC-E2-13 | E2-1, E2-9, E3-4 | E2 for the source-file inputs, E3 for the drift findings |
| AC-E2-14 | E2-9, E3-4 | 3 docs after E2, 4 after E3 |
| AC-E2-15 | E3-4, E3-5 | E3 |
| AC-E3-01 | E3-1 | E3 |
| AC-E3-02 | E3-2 | E3 |
| AC-E3-03 | E3-2 | E3 |
| AC-E3-04 | E3-3 | E3 |
| AC-E3-05 | E3-3 | E3 |
| AC-E3-06 | E3-4 | E3 |
| AC-E3-07 | T0.2, E3-3 | E3 |
| AC-E3-08 | E3-2 | E3 |
| AC-E3-09 | E3-4 | E3 |
| AC-NFR-01 | RV-8 | end |
| AC-NFR-02 | E2-7, E3-4, E3-5 | 3 docs after E2, 4 after E3 |
| AC-NFR-03 | RV-8 | end |
| AC-DOC-01 | D-1 | after E3 |
| AC-WF-01 | W-6 | content check blocked on OI-01 |
| AC-WF-02 | W-4 | workflow |
| AC-WF-03 | W-2 | workflow |
| AC-WF-04 | W-3 | workflow |
| AC-WF-05 | W-5 | workflow |
| AC-WF-06 | W-5, W-6 | workflow |
| AC-WF-07 | W-6 | blocked on OI-01 and on the demonstration subject (PN-5) |
| AC-WF-08 | W-2 | workflow |
| AC-WF-NFR-01 | W-6 | workflow |
| AC-WF-NFR-02 | W-5, W-6 | workflow |
| AC-WF-NFR-03 | W-6 | workflow |
| AC-WF-NFR-04 | W-6 | workflow |
| AC-WF-NFR-05 | W-5 | workflow |
| AC-WF-NFR-06 | W-6 | workflow |

## 5. Deliverable coverage (name-based candidates, OI-01 unresolved)

| Deliverable | Task | Verification |
|---|---|---|
| `requirements.md`, `architecture.md`, `design-review.md` | done (approved) | checksums and line counts |
| `impl-plan.md` | PL-1 | the file matches the approved text |
| Implementation code changes | T0.1 to E3-5 | `mvn test` and the human-run first real syncs (E2-10, E3-5) |
| Code review evidence | RV-1, RV-3, RV-5, RV-7 | review reports triaged by the human |
| Automated tests and verification evidence | RV-2, RV-4, RV-6, RV-8 | real command output |
| PR description | PR-1 | the human reads it |
| Changelog, reviewer checklist | CL-1, RC-1 (only if requested) | the human reads them |
| Eight stage agents (exist), commands, `CLAUDE.md`, skill, hooks, records | existing, W-4, W-2, W-3, W-5, W-6 | demonstration |
| README | D-1 | every command executed |

## 6. Extraction specifications

### 6A. E2 extraction note (approved together with this plan, PN-8)

- **N1 Routes.** Read `routes()` in `StudentApiServer.java` (an instance method after E1-3). The body must be `List.of(new Route(...))` of `new Route` expressions only. Arguments 1 to 5 must be string literals, otherwise exit 2. Argument 6 (the handler) is ignored and is not used to find the create handler (DR-22). Static routes are not documented.
- **N2 Create handler lookup.** Find the calls in `StudentApiServer.java` of the syntactic form `service.createStudent(a1, a2, a3, a4)`: the receiver is the identifier `service`, the method name is `createStudent`, and there are four arguments. Exactly one such call must exist. Each of its four arguments must be a call of the form `<map>.getOrDefault("<string literal>", "")`, with a string-literal key and an empty-string literal default. The four literal keys must be unique. The literal keys, in argument order, are the form parameter names (today `name`, `email`, `department`, `phone`). Their positions map to the service's `createStudent` parameters. A missing call, more than one matching call, a duplicate key, a non-literal key, a non-empty default, or any other argument shape exits 2. This rule checks source syntax only. The analyzer is parse-only (AD-01) and performs no semantic Java type resolution, so it does not verify that `service` is a `StudentService`. The rule is tested against the post-E1-4 source, where the create handler keeps this shape (E1-4, E2-5).
- **N3 Student record.** Read the single `record Student(...)` declaration in `model/Student.java`: component names and types in source order.
- **N4 Service methods and digest.** Read the public methods of `StudentService` (name, parameter types and names, return type) in source order. Seven are expected today (AC-E2-02). The digest is the lowercase SHA-256 of the LF-normalized `StudentService.java` text.
- **N5 ErrorCode constants.** Read the enum constants of `ErrorCode`, declared with literal arguments only (E1-1).
- **Not in this note:** validation idioms, HTML and JS analysis, drift and the UI doc. These stay in section 6B behind B5.

### 6B. E3 extraction specification (separate approval gate B5, NOT approved)

- **S2 HTML scope.** Read only `form#student-form`, which must exist exactly once. Allowed elements inside it: `label`, `input`, `select`, `option`, `button`. Any other element exits 2. Extract per control `id`, `name`, tag and `type`, `required` (as `required`, `required=""` or `required="required"`), `maxlength`, and select options (value and label), plus the submit-button text. Unknown attributes are ignored. Comments are skipped. Single or double quotes are accepted.
- **S3 JS lexer scope.** Lex only `const validationRules = {...}` and the first `new URLSearchParams({...})`. The rest of `app.js`, including the FR-E1-12 change, is skipped. Allowed values: `true`, `false`, integers, a regex literal without flags, and a single-quoted string. Allowed rule properties: `required`, `minLength`, `pattern`, `message`. A template literal, any other property or regex flags exit 2. A payload value must be `formData.<key>`.
- **S4 Backend idioms.** Analyze the 4-argument `validateStudentInput`. The 3-argument overload is accepted only as one delegating call that appends `""`. Each statement must be `if (COND) throw new IllegalArgumentException("<literal>")`, written with or without braces, and with no else. The i-th parameter of `validateStudentInput` is matched to the i-th argument of the `createStudent(...)` call found by N2, and through its literal key to the form field. A mapped field with no rule exits 2.

  | Idiom | Condition shape | Blank input | RuleSpec |
  |---|---|---|---|
  | N1 | null-or-`isBlank()` | rejected | required |
  | C1 | null-or-`!contains("<non-empty literal>")` | rejected | required, CONTAINS |
  | R1 | null-or-(not blank and not `trim().matches("<literal>")`) | accepted | optional, REGEX (Java literal unescaped) |

  Requiredness comes from behavior on a blank input, not from `contains` alone. Any other shape, including a length check, exits 2.
- **S5 Normalization and comparison.** For the JS regex, strip exactly one leading `^` and one trailing `$`, then compare text with the unescaped Java regex. Compare `required`, `minLength` and `pattern` independently, in that order, one finding per difference. Message text, select options, `maxlength` and `type` never count (D10, D20).
- **S6 Field join.** The join key is the control `name` attribute (FormData keys come from `name`). `validationRules` keys, payload `formData.<key>` and the HTML control must agree, otherwise exit 2. The `studentName` to `name` style mapping comes from the payload object.
- **S7 UI doc blocks.** `ui-controls`, `ui-rules`, `ui-field-mapping`, `ui-drift`. Exact table layout is fixed by golden tests at E3-4.
- **S8 Failure semantics.** An unsupported construct in any analyzed region fails the whole run with exit 2, naming the file and construct (FR-E2-04). DRIFT never changes the exit code.
- **S9 Expected result on today's source (by reasoning, not executed).** name: DRIFT minLength (frontend 2, backend none). email: DRIFT pattern (frontend regex, backend CONTAINS "@"). department: none. phone: none after normalization. Exactly two findings.
- **Approval items:** A1 (S2), A2 (S3), A3 (S4), A4 (S5), A5 (S6), A6 (S7), A7 (S8), A8 (S9).

## 7. Accepted P-2 and P-4

**Block IDs (ten blocks).** Markers are `<!-- docsync:begin <id> -->` and `<!-- docsync:end <id> -->`. IDs are unique across the catalog.

| File | Block IDs, in order |
|---|---|
| `docs/api/endpoints.md` | `api-routes`, `api-errors` |
| `docs/api/student-contract.md` | `contract-student-fields`, `contract-create-params` |
| `docs/backend/student-service.md` | `service-methods`, `service-digest` |
| `docs/ui/student-ui.md` | `ui-controls`, `ui-rules`, `ui-field-mapping`, `ui-drift` |

**New-file layout.** `# <title>` (API Endpoints, Student Contract, Student Service, Student UI), a blank line, then each block separated by a blank line in the order above, ending with a newline. No timestamps or absolute paths. The `service-digest` block holds one line giving the file path `src/main/java/org/example/service/StudentService.java` and its lowercase SHA-256 hex over the LF-normalized text.

**`.gitignore` addition** (appended to the existing 42 lines; nothing else changes):

```
### DocSync ###
docs/**/.docsync-*.tmp
```

## 8. OI-20 manual procedure (proposal; approval is a prerequisite before E1-6)

The application runs from the repository root, because it reads `frontend/` relative to the working directory (OBS-01). This procedure is a proposal, not approved.

1. Start the app from the repository root with the README's documented run command (its accuracy is verified in D-1), and open `http://localhost:8000`.
2. Enter a whitespace-only name, so the browser's native required check passes and the submit handler runs. Submit, and confirm the pre-submit alert is unchanged: "Student name must be at least 2 characters." (AC-E1-11, part c).
3. Submit a valid student and confirm "Student record created successfully." is unchanged (part b).
4. In the browser console set `validationRules.studentName.required=false` and `validationRules.studentName.minLength=0`, and also set `document.getElementById('studentName').required=false`. Submit a whitespace-only name. The server returns 400, and the alert must show only "Student name is required." and not JSON (part a).
5. Review `git diff frontend/app.js` and confirm only the error path changed.
6. Record the results.

## 9. Risks, blockers and decisions remaining

**Risks.**
- Surefire 3.2.5 and JUnit 5.10.3 on JDK 27 are untested together.
- The shared static `SERVICE` could make the characterization tests order-dependent.
- HEAD with a response body on the JDK server may warn. Tests assert status only.
- The API reads `frontend/` relative to the working directory (OBS-01), so the missing-file test uses a subprocess (E1-5).
- Until the first real sync, verify exits 2 (D6). The same applies to the UI doc at E3.
- Atomic-move results hold for the tested environment and cases only (PN-7). A transient lock fails the run, because there is no retry.
- Workflow guards are best-effort only (DR-23, DR-24, PN-10).

**Blockers.**
- Plan approval is needed before PL-1 and any implementation.
- B5 (A1 to A8) blocks E3.
- OI-20 approval blocks E1-6.
- The G0 behavior-change approval blocks E1-3 and E1-4.

**Decisions and gates remaining for the human, with a recommendation each.**

| # | Decision | Recommendation |
|---|---|---|
| 1 | Approve or correct this plan. Approving it also approves section 6A. | Approve after reviewing the diff. |
| 2 | B5: approve A1 to A8 (section 6B) before E3 starts. | Approve as written, or correct. |
| 3 | OI-20: approve the manual procedure (section 8) before E1-6. | Approve as written. |
| 4 | DC-1 or another demonstration subject (PN-5). No change to the Student Management System without explicit approval. | DC-1, reversible. Decide before W-6. |
| 5 | OI-17: approve `docs/workflow/` or choose another location (PN-4). | Approve before W-6. |
| 6 | OI-01: supply the original assignment details (PN-3). | Supply them. Otherwise the stage mapping stays a working assumption. |
| 7 | OI-11 and the hook exit-code table, decided at W-1. | Decide after W-0. |
| 8 | Gate G0: approve any behavior change found by T0.1 (the table in M0). | Review the table at G0. |
| 9 | Authorize the first real-repository syncs at E2-10 and E3-5 (PN-11). | Authorize when each task is reached. |
| 10 | Authorize the W-0 hook checks, and authorize each implementation task. | Authorize per task. |

**Not verified.** No build, test or hook run was performed for this plan. The S9 result, the exact-match router behavior, Maven and surefire on JDK 27, HEAD behavior (OI-18) and the browser-console steps in section 8 are checked by reasoning only. The M7 probe ran against today's production code, not the future E1 code. The AD-12 and AD-14 hook checks have not run. The original assignment is not available.

## 10. Mechanical coverage check

Run by a script in the scratch directory that parses sections 2, 3 and 4 of this file against `docs/requirements.md`. Its output is reported in the main session. It checks structure and cross-table consistency only. It does not judge whether a mapping is semantically right, and it does not check that a named test class will exist. Sections 5 to 9 are not machine-checked.
