# FINAL REQUIREMENTS DOCUMENT: Automated Documentation Sync, Agentic SDLC

**Status: APPROVED (Revision 3 + D24 + D25), approved by the human on 2026-11-13; OI-01 remains OPEN by decision (D15).**

**File status: this text was written to `docs/requirements.md` on the explicit authorization of the human. The Architecture phase has not started.**

Tags: [EXISTING] = verified in the repo. [PROPOSED] = new. [ASSUMPTION] = unconfirmed. "Approved (Dn)" means a human decision backs the requirement. Paths are relative to the repository root.

## Final revision notes (changes vs Revision 3 only)

**Decision D24 (OI-19, approved) and its effects**
- Unsupported HTTP methods on the known static routes `/`, `/style.css` and `/app.js` must return 405 METHOD_NOT_ALLOWED.
- The supported method on these routes is GET, which returns 200.
- Whether HEAD (or OPTIONS) is supported on static routes is not decided. It is deferred as new OI-21.
- Method handling is consistent across API and static routes.
- The 405 body on static routes uses the same structured error format as API routes.
- Precedence is acceptance behavior: a known route with an unsupported method gives 405, and an unknown path gives 404 regardless of method.
- Changes to the document:
  - FR-E1-03, 04, 06, 07 and 13 are updated, and FR-E1-07's "OI-19 open" caveat is removed.
  - BC-5 is added.
  - G-18 is reworded.
  - AC-E1-04, 05 and 12 are updated.
  - D24 is added to the decision log.
  - OI-19 is closed.
  - The traceability table is updated.
  - The Section C "shaped by" column and out-of-scope item 7 now mention D24.
- OI-04 (the `Allow` header) stays deferred and now applies to 405 on all routes.

**Decision D25 (OI-05, approved) and its effects**
- A successful `DELETE /students/{id}` returns HTTP 200 with the JSON body `{"deleted":true}`, preserving the existing confirmed response contract.
- OI-05 (exact delete-result JSON shape) is closed.
- FR-E1-02, A-02 and AC-E1-02 are updated, and D25 is added to the decision log.
- OI-16 (the code source of the request and delete-result contracts) stays deferred.

**Final consistency check (performed)**
- Every FR and NFR maps to at least one AC in section J. NFR-05 is intentionally without an AC, because it states that no numeric targets exist.
- FR-E1-12 is covered by a manual check (EX-01, D22) by decision.
- No dangling IDs. FR-E2-21 is a retired ID, merged into FR-E2-20, and is not reused. OI-02, 03, 05, 10, 12 to 15 and 19 are closed.
- No contradictions with D1 to D25.
  - D12 against D22 is resolved by EX-01.
  - D8, D17, D23 and D24 are now aligned on 404 and 405 behavior.
  - D13 against D18 is consistent.
  - D19 is extended by D21.
- All four generated files are covered in FR-E2-03, 11 to 15, FR-E3-01, FR-DOC-01 and AC-E2-02, 05 to 08, 14, 15, AC-E3-01, 09 and AC-DOC-01.
- Deferred design items remain marked deferred and are not asserted as final in any AC. These are OI-04, 06 to 09, 11, 16 to 18, 20 and 21.
- OI-01 stays OPEN, and the stage-to-deliverable mapping stays PROPOSED and unconfirmed.

## 0. Decision log

| ID | Source | Decision |
|---|---|---|
| D1 | Q1 | The README is the initial **target specification** for the Sync Engine. It is not verified functionality. |
| D2 | Q1 | The README must be updated to match the implemented app. |
| D3 | Q1 | The SDLC layer lives under `.claude/`. The README's `.github/` agents, prompts, skills and hooks are out of scope as implementation. |
| D4 | Q2 | HTML comment markers with unique, stable IDs. |
| D5 | Q3 | Exit 0 = success. Exit 1 = STALE found. Exit 2 = tool, usage or integrity failure. |
| D6 | Q4 | Strict STALE definition. A missing expected file or block is exit 2. |
| D7 | Q5 | Sync creates missing files and blocks. Verify never modifies docs but writes its report (confirmed). |
| D8 | Q6 | All five E1 items are in scope with compatibility rules. Unknown paths return 404 (BC-1). |
| D9 | Q7 | Error format `{"error","code"}`. A failed DELETE uses it (BC-2). Success shapes are confirmed. |
| D10 | Q8 | Report-only DRIFT. Validation rules are not changed. |
| D11 | Q9 | The workflow needs all five categories and eight stage agents, with human gates. |
| D12 | Q10 | Qualitative NFR baseline. |
| D13 | Q11 | Five items are out of scope. |
| D14 | Assignment info | The eight stages and the deliverables list are confirmed. The mapping is unknown. |
| D15 | OI-01 | Stage-specific deliverables and handoff criteria stay an open item. Nothing is invented. Mappings are PROPOSED only. |
| D16 | OI-02 | WF-NFR-01 to 06 are approved. |
| D17 | OI-03 | `/`, `/style.css` and `/app.js` keep returning 200 (refined by D24 for unsupported methods). Only unknown paths return 404. |
| D18 | OI-10 | A minimal `app.js` change shows the human-readable `error` message instead of raw JSON. No other frontend functionality is added. |
| D19 | OI-12 | The engine resolves frontend sources relative to `--root`. Changing the API's own static serving is not approved. |
| D20 | Dept. | Department is not drift. Expected current drift is the name min-length mismatch and the email pattern mismatch only. |
| D21 | OI-13 | `--root` is the base directory for all engine inputs and outputs: Java sources, frontend sources, generated docs, sync and verify reports, and any other project files the engine needs. The engine does not depend on the current working directory. OBS-01 is kept. |
| D22 | OI-14 | The `app.js` error-display change is verified by a documented manual browser check, as an explicit exception to automated coverage. API and error-contract behavior stays covered by JUnit. No new runtime dependency. |
| D23 | OI-15 | Only `/`, `/style.css` and `/app.js` return 200. `/index.html`, `/favicon.ico` and all other unknown paths return 404. |
| **D24** | OI-19 | Unsupported HTTP methods on the known static routes `/`, `/style.css` and `/app.js` return 405 METHOD_NOT_ALLOWED. The supported method is GET (200). Unsupported methods on known routes return 405 and unknown paths return 404. Method handling is consistent across API and static routes. HEAD support is not decided (OI-21). |
| **D25** | OI-05 | The successful `DELETE /students/{id}` response is HTTP 200 with the JSON body `{"deleted":true}`, preserving the existing confirmed response contract (`StudentApiServer.java:109`). OI-05 is closed. |

## A. Current state: VERIFIED vs README-described TARGET

### A1. Verified [EXISTING]

| Area | Fact | Evidence |
|---|---|---|
| Runtime | Java 17, JDK `com.sun.net.httpserver`. Port 8000 is hard-coded. | `pom.xml:12`, `Main.java:11-14` |
| Build | JUnit Jupiter 5.10.3 (test scope) and surefire 3.2.5. No other dependencies. | `pom.xml:16-31` |
| Tests | `src/test/` contains only empty directories, so no tests exist. | Glob |
| Dispatch | `/students` handles GET and POST. `/students/` handles GET and DELETE. Other methods get 405 `{"error":"Method not allowed"}`. | `StudentApiServer.java:56-69` (405 at `:60, :67`) |
| Static files | `/`, `/style.css` and `/app.js` are read from the relative path `frontend/` and answer any HTTP method. The `/` context is also the catch-all for paths no other context matches. | `:29-54`, content types at `:31, :40, :49` |
| Page links | `index.html` references `style.css` and `app.js` by relative URL. It has no link to `/index.html`. | `index.html:7, :32` |
| Route metadata | `routes()` is a separate list of 4 routes, GET and POST `/students`, GET and DELETE `/students/{id}`. Its handlers are not used for dispatch. The type strings `"StudentRequest"` and `"DeleteResponse"` have no matching classes. | `:19-26`, `:72` |
| Wiring | The service is a static singleton. The repository is created inside the service. | `:17`, `StudentService.java:11` |
| Responses | Bodies come from `List.toString()` and `Student.toString()` (record syntax, not JSON). The content type is `application/json`. | `:76, :93, :100, :133` |
| Errors | 405, 400 and 404 return `{"error":"..."}`. The 400 message is always "Invalid student input". A DELETE miss returns `{"deleted":false}` with 404. | `:60, :90, :102, :109` |
| DELETE success | 200 with `{"deleted":true}`. | `:109` |
| Create input | Form keys are `name`, `email`, `department` and `phone`. Missing keys default to "". Pairs without `=` are dropped. Keys are not URL-decoded. | `:79-88`, `:120-129` |
| Student | Record with `id, name, email, department, phone, status`. The id is the first 8 characters of a UUID. The status is "active". Values are trimmed. | `Student.java:3`, `StudentService.java:19-20` |
| Service | 7 public methods: `createStudent` (3 and 4 args), `getStudents`, `getStudent`, `deleteStudent`, `validateStudentInput` (3 and 4 args). | `StudentService.java:13, :17, :24, :28, :32, :36, :40` |
| Backend validation | Checked in order: name non-blank ("Student name is required."), email contains `@` ("Email must contain '@'."), department non-blank ("Department is required."), phone optional and matching `(?=.{7,20}$)\+?[0-9 -]+` ("Invalid phone number."). | `:41-52` |
| Persistence | In-memory `LinkedHashMap`. | `StudentRepository.java:12` |
| UI | The form `student-form` has `studentName` (text, required), `studentEmail` (email, required), `studentDepartment` (select, required) with 3 `<option>` elements, `studentPhone` (tel, maxlength 20) and a submit button "Add student". There is no list or delete view. | `index.html:12-30` |
| Department options | Placeholder: value `""`, label "Select department". Departments: "Computer Science" and "Mathematics" (value equals label). | `index.html:21-23` |
| Frontend rules | Name: required, min length 2. Email: required, regex `^[^\s@]+@[^\s@]+\.[^\s@]+$`. Department: required. Phone: optional, regex `^(?=.{7,20}$)\+?[0-9 -]+$`. | `app.js:1-21` |
| Mapping | `studentName→name`, `studentEmail→email`, `studentDepartment→department`, `studentPhone→phone`. | `app.js:55-60` |
| UI behavior | Rules are checked first and the first error is shown in `window.alert`. A non-OK response is thrown as `new Error(<raw response text>)` and the catch alerts that text (today the JSON string). Success alerts "Student record created successfully." and resets the form. | `app.js:45-79` (`:51, :63-77`) |
| Claude layer | The only file under `.claude/` is `agents/requirements-agent.md` (a read-only BA agent with tools Read, Grep, Glob). | `:1-5` |

### A2. README-described TARGET (does NOT exist today) [PROPOSED per D1]
- `org.example.docsync.DocSyncCli` with `sync --root .` and `verify --root .` (`README.md:28-29`).
- Generated blocks in **four** files (`README.md:32-34`): `docs/ui/student-ui.md`, `docs/backend/student-service.md`, `docs/api/endpoints.md` and `docs/api/student-contract.md`.
- Reports `docs/reports/sync-report.md` and `docs/reports/verify-report.md`. Verify exits 1 and lists STALE documents (`:37-39`).
- Sources for extraction: HTML controls, `validationRules`, `StudentService` public methods plus a source digest, `routes()` declarations, the `Student` record and the create endpoint's form parameters (`:34-37`).
- Fail rather than guess on unsupported constructs (`:40-41`). The CLI never commits or merges (`:43`).

## B. Gaps and discrepancies

| ID | Finding | Evidence |
|---|---|---|
| G-01 | The README describes a `docsync` engine, but no `docsync` package exists. Only the api, model, repository and service packages and `Main` exist. | `README.md:3-7, :23-43`, Glob |
| G-02 | `docs/` contains only a CodeMie analytics JSON. None of the four generated docs or the two reports exist. | Glob |
| G-03 | The README claims `.github/` holds custom agents, prompts, instructions, skills and hooks. It holds only the unrelated `modernize/java-upgrade` hook scripts. | `README.md:13`, Glob |
| G-04 | The README build line (`javac ... $(find ...)`, `README.md:19-20`) is unverified. It is a bash line, and the app also depends on the working directory for `frontend/` (`StudentApiServer.java:30`). | `README.md:19-20`, `StudentApiServer.java:30` |
| G-05 | Routes are defined twice. `routes()` is not used for dispatch. The README admits "route declarations are not proof of HTTP dispatch". | `:19-26` vs `:56-69` |
| G-06 | Responses are not valid JSON. | `:76, :93, :100` |
| G-07 | Errors are inconsistent. The 400 message is generic, and a DELETE miss uses another body shape. | `:90, :109` |
| G-08 | The `/` context is a catch-all, so unmatched paths such as `/index.html` and `/favicon.ico` return `index.html` with 200. | `:29-36` |
| G-09 | The service is a static singleton and the port is hard-coded, so the API is not injectable. | `:17`, `Main.java:11` |
| G-10 | There are no tests. | Glob |
| G-11 | Validation drift exists today for the name (frontend min 2, backend none) and email (frontend regex, backend contains `@`) fields. | `app.js:2-11` vs `StudentService.java:41, :44` |
| G-12 | The department select offers 2 real values while the backend accepts any non-blank value. This is not drift (D20). | `index.html:21-23`, `StudentService.java:47` |
| G-13 | `target/` holds stale build artifacts (a jar and a `$Route` class). It is git-ignored. | Glob, `.gitignore` |
| G-14 | E1, E2 and E3 are defined only in `.claude/agents/requirements-agent.md:45-66`. No project document defines them. The CodeMie log (`docs/codemie/analytics/...json:41`) asks for "3 realistic enhancements" and gives no text. | Grep |
| G-15 | Only the Requirements-stage agent exists. There is no `CLAUDE.md`, command, skill, hook or settings file. | Glob |
| G-16 | Error alerts already display the raw response body, so the UI shows JSON text. It keeps doing so after E1 unless `app.js` changes (FR-E1-12). | `app.js:69-72, :77` |
| G-17 | Route metadata references `StudentRequest` and `DeleteResponse`, which do not exist as classes. The request and delete-result contracts have no code source today (OI-16). | `StudentApiServer.java:22, :24` |
| G-18 | Method handling is inconsistent. The API contexts reject unsupported methods with 405, while the static handlers do not check the method and return 200 for any method. Resolved by FR-E1-07 (D24, BC-5). | `:29-54` vs `:56-69` |
| OBS-01 | The API's static serving depends on the working directory. Changing it is **not approved** (D19, D21) and is out of scope. | `:30, :39, :48` |

## C. Evaluation of E1, E2, E3
E1, E2 and E3 were defined only in the agent file and were treated as proposals. Decisions D1 to D25 made them requirements.

| | Exists today | Missing | Depends on | Shaped by |
|---|---|---|---|---|
| **E1** Truthful API | Partial: error JSON strings exist, but routes are duplicated and responses are not JSON (G-05 to G-09, G-18) | All five E1 items, plus the `app.js` error display | None. It is a prerequisite. | D8, D9, D13, D17, D18, D22, D23, D24, D25 |
| **E2** Backend/API doc sync (owns 3 of the 4 files) | Nothing (G-01) | CLI, markers, sync, verify, reports, three generated docs, root-relative resolution | E1 | D1, D4 to D7, D12, D19, D21 |
| **E3** Frontend/UI doc sync (owns `student-ui.md`) | Nothing. The HTML controls and `validationRules` exist as source. | The UI doc and drift detection | E2's engine: CLI, markers, sync, verify and reports | D1, D10, D19, D20, D21 |

The dependency order is E1, then E2, then E3. Tests (D12) and the README update (D2) apply across all three.

## D. Requirements

### Area 1: Documentation Sync Engine and API

#### E1: Truthful API

| ID | Requirement | Status |
|---|---|---|
| FR-E1-01 | Route definitions and actual request dispatch use one shared source of truth. | [PROPOSED] Approved D8 |
| FR-E1-02 | Success responses are valid JSON. `GET /students` is an array of Student objects. `GET /students/{id}` and `POST /students` (201) are Student objects. `DELETE /students/{id}` success returns HTTP 200 with the JSON response `{"deleted":true}`, preserving the existing confirmed response contract. | [PROPOSED] Approved D8, D9, D25 |
| FR-E1-03 | Error responses, for both API and static routes, are `{"error":"<human-readable string>","code":"<MACHINE_CODE>"}`. `error` stays a string. | [PROPOSED] Approved D9, D24 |
| FR-E1-04 | Codes include VALIDATION_ERROR, NOT_FOUND, METHOD_NOT_ALLOWED and INTERNAL_SERVER_ERROR where applicable. Codes are stable. The HTTP status matches the error condition. A 405 on a static route uses the same structured body and the METHOD_NOT_ALLOWED code as a 405 on an API route. | [PROPOSED] Approved D9, D24 |
| FR-E1-05 | A 400 returns the service's specific validation message. | [PROPOSED] Approved D9 |
| FR-E1-06 | Every path other than `/`, `/style.css`, `/app.js` and the student API paths returns 404 NOT_FOUND (BC-1). This includes `/index.html`, `/favicon.ico` and any path that merely shares a prefix with a known path. An unknown path returns 404 regardless of the HTTP method. | [PROPOSED] Approved D8, D17, D23, D24 |
| FR-E1-07 | A known route with an unsupported HTTP method returns 405 METHOD_NOT_ALLOWED in the structured format. This covers the API routes and the static routes `/`, `/style.css` and `/app.js` (BC-5). Handling is consistent across API and static routes. Precedence: known route plus unsupported method = 405, and unknown path = 404. | [PROPOSED] Approved D8, D24 |
| FR-E1-08 | A DELETE for a missing id returns 404 in the structured format instead of `{"deleted":false}` (BC-2). | [PROPOSED] Approved D9 |
| FR-E1-09 | Existing paths are preserved. `POST /students` stays form-urlencoded. Existing 200, 201, 400 and 404 statuses are preserved where applicable. Any other behavior change is documented explicitly. | Approved D8, D13 |
| FR-E1-10 | The service and the port are injectable, so the API can be tested in isolation. | [PROPOSED] Approved D8 |
| FR-E1-11 | The error format is documented in the generated API docs and covered by automated tests. | [PROPOSED] Approved D9 |
| FR-E1-12 | A minimal `app.js` change shows the human-readable `error` message from the API response instead of the raw body. Nothing else is added to the frontend. It is verified by a documented manual browser check (EX-01), not by JUnit. | [PROPOSED] Approved D18, D22 |
| FR-E1-13 | `GET /`, `GET /style.css` and `GET /app.js` return 200 with their current content types and content. GET is the supported method on these routes. Only these three non-API paths return 200. Whether HEAD (or OPTIONS) is supported is deferred (OI-21). | Approved D17, D23, D24 |

#### E2: Backend/API documentation sync

| ID | Requirement | Status |
|---|---|---|
| FR-E2-01 | `org.example.docsync.DocSyncCli` provides `sync --root <dir>` and `verify --root <dir>`. | [PROPOSED] Approved D1 |
| FR-E2-02 | The engine analyzes Java source for the route source (per FR-E1-01), the `StudentService` public methods, the `Student` record and the create endpoint's form parameters. | [PROPOSED] Approved D1 |
| FR-E2-03 | The engine manages **exactly four** generated documentation files: (1) `docs/api/endpoints.md`, (2) `docs/api/student-contract.md`, (3) `docs/backend/student-service.md` and (4) `docs/ui/student-ui.md`. **Ownership:** E2 owns files 1 to 3, and E3 owns file 4 (see FR-E3-01). The three E2 files are delivered with E2 and the fourth with E3, so the full set of four exists only after E3. Sync and verify requirements below apply to each file in the set. `sync-report.md` and `verify-report.md` are reports, not members of the set. | [PROPOSED] Approved D1 (README) |
| FR-E2-22 | Content of the E2-owned docs. The endpoints doc documents the API routes. The contract doc documents the six Student fields and the create endpoint's four form parameters. The service doc documents the `StudentService` public methods and a source digest. | [PROPOSED] Approved D1, D6 |
| FR-E2-04 | The engine fails on unsupported constructs instead of guessing (README's initial list: dynamic route metadata, non-literal parameter names). This is exit 2 and never reported as STALE. | [PROPOSED] Approved D1, D5 |
| FR-E2-05 | Generated sections use `<!-- docsync:begin <block-id> -->` and `<!-- docsync:end <block-id> -->`. | [PROPOSED] Approved D4 |
| FR-E2-06 | Every block has a unique ID. IDs stay stable across repeated syncs. | [PROPOSED] Approved D4 |
| FR-E2-07 | A file may contain multiple blocks. | [PROPOSED] Approved D4 |
| FR-E2-08 | Sync modifies only content inside the matching block. | [PROPOSED] Approved D4 |
| FR-E2-09 | All manual content outside blocks is always preserved. | [PROPOSED] Approved D4, D7 |
| FR-E2-10 | Existing markers that are missing, unmatched, malformed or duplicated give a clear error (exit 2). Sync and verify validate marker integrity before proceeding. Sync does not repair. This applies to each of the four files. | [PROPOSED] Approved D4, D7 |
| FR-E2-11 | For each of the four files: if it is missing, sync creates it with its required blocks. If it exists but a required block is missing, sync appends the block after the existing manual content. | [PROPOSED] Approved D7 |
| FR-E2-12 | Sync writes `docs/reports/sync-report.md`, classifying **each of the four** files as created, updated or unchanged. | [PROPOSED] Approved D7 |
| FR-E2-13 | Sync is idempotent for all four files and for its report. A repeated run with no source or doc changes makes no unnecessary modifications. | [PROPOSED] Approved D7, D12 |
| FR-E2-14 | Verify evaluates **each of the four** files. A file is STALE when an existing, well-formed block differs from what the current source would generate. The comparison is exact after CRLF to LF. A change in the `StudentService` source digest makes the backend block STALE. Text outside blocks never affects the result. The verify report identifies which files are STALE. | [PROPOSED] Approved D6 |
| FR-E2-15 | For any of the four files, a missing expected file or block is exit 2 in verify, never STALE. Verify never creates files or blocks. | [PROPOSED] Approved D6, D7 |
| FR-E2-16 | Verify never modifies the four generated docs. It writes `docs/reports/verify-report.md`, including when STALE is found. | [PROPOSED] Approved D5, D7 |
| FR-E2-17 | Exit 0 = sync completed, or docs up to date. Exit 1 = verify found STALE. Exit 2 = marker errors, unsupported constructs, invalid or missing `--root`, unknown commands or arguments. | [PROPOSED] Approved D5 |
| FR-E2-18 | Failures write clear, actionable messages to stderr. Reports are written whenever the run produced meaningful findings. Successful sync and verify runs write their reports. Marker errors and unsupported constructs are not reported as STALE. | [PROPOSED] Approved D5 |
| FR-E2-19 | The CLI never commits or merges. | [PROPOSED] Approved D1 |
| FR-E2-20 | The supplied `--root` directory is the base for resolving **all** engine inputs and outputs: Java sources, frontend sources, the four generated docs, `sync-report.md` and `verify-report.md`, and any other project file the engine needs. The engine does not depend on the process's current working directory. (Merges draft 2's FR-E2-20 and FR-E2-21. FR-E2-21 is a retired ID.) Applies to E2 and E3. | [PROPOSED] Approved D19, D21 |

#### E3: Frontend/UI documentation sync

| ID | Requirement | Status |
|---|---|---|
| FR-E3-01 | E3 owns `docs/ui/student-ui.md`, the fourth file of FR-E2-03. It is generated by the E2 engine and obeys all of FR-E2-05 to 20 (markers, creation, append, sync-report classification, STALE, missing file or block as exit 2, exit codes). | [PROPOSED] Approved D1, D4 to D7 |
| FR-E3-02 | The engine extracts HTML controls from `index.html` (id, name, type, required, select options, maxlength). | [PROPOSED] Approved D1 |
| FR-E3-03 | The engine extracts the JavaScript `validationRules` (required, minLength, pattern). Template literals in rules are unsupported and exit 2. | [PROPOSED] Approved D1 |
| FR-E3-04 | The UI doc maps UI fields to API fields (for example `studentName→name`). | [PROPOSED] Approved D1 |
| FR-E3-05 | The engine compares frontend and backend rules for each mapped field. It reports DRIFT when they differ in required vs optional, minimum length, or pattern/format. | [PROPOSED] Approved D10 |
| FR-E3-06 | Message-text differences and HTML-only restrictions (select option lists, `maxlength`, input type) are not drift unless a backend rule also represents them. Department is therefore not drift. | [PROPOSED] Approved D10, D20 |
| FR-E3-07 | DRIFT is a class separate from STALE. It appears in `verify-report.md` and in the UI doc. It never changes the exit code. | [PROPOSED] Approved D10 |
| FR-E3-08 | Existing frontend and backend validation rules are not changed. The name (min length) and email (pattern) mismatches are reported as findings. | Approved D10, D20 |
| FR-E3-09 | The engine obtains backend rules for the comparison from Java source. The method is a design matter (OI-09). | [PROPOSED], derived |

#### Documentation accuracy
- **FR-DOC-01:** `README.md` is updated after implementation to describe only what exists and works. It covers the build and run instructions, the CLI, exit codes, the marker format, the four generated files, BC-1 to BC-5 and the root-relative behavior. It removes the `.github/` claims and the "route declarations are not proof" caveat. [PROPOSED] Approved D2, D3.

#### Non-functional requirements (Engine and API only)

| ID | Requirement |
|---|---|
| NFR-01 | Java 17. The runtime uses only the JDK, with no new runtime dependencies. JUnit 5 is used for tests. |
| NFR-02 | Generated docs and reports are deterministic, with stable ordering. They contain no timestamps or other run-varying content. |
| NFR-03 | A repeated sync with no changes produces byte-identical generated files (all four) and reports. |
| NFR-04 | Every E1, E2 and E3 functional requirement is covered by at least one automated test runnable with `mvn test`. Tests include success, failure, stale detection, marker integrity and validation drift. **Exception EX-01 (D22):** FR-E1-12 is covered by a documented manual browser check, not by JUnit. The API and error-contract behavior it depends on remains covered by JUnit. |
| NFR-05 | There are no numeric runtime or coverage targets. The implementation stays maintainable and testable, and suited to the existing student app. |

### Area 2: Claude Code Agentic SDLC Workflow (separate list; under `.claude/`, D3)

| ID | Requirement | Confirmed / proposed |
|---|---|---|
| WF-01 | Stage agents cover all eight stages: Requirements, Architecture, Design Review, Implementation Planning, Implementation, Review, Verify, PR. Each stage has defined responsibilities, inputs, outputs and handoff criteria. Agents may collaborate. | The stage set and the requirement for definitions are Approved D11, D14. The **specific content** of per-stage deliverables and handoff criteria is OPEN (OI-01). Only the Requirements agent exists [EXISTING]. |
| WF-02 | Slash commands or prompts initiate and coordinate each stage. | [PROPOSED] Approved D11 |
| WF-03 | `CLAUDE.md` holds project-wide instructions, safety rules, human-approval requirements, and the separation between the Sync Engine and the Workflow. | [PROPOSED] Approved D11 |
| WF-04 | At least one reusable skill describes the docsync procedure, including when and how to run sync and verify. | [PROPOSED] Approved D11 |
| WF-05 | A hook runs the engine's `verify`. Exit 0 lets the workflow proceed. Exit 1 reports stale docs and requires synchronization first. Exit 2 reports the failure and stops the affected workflow. | [PROPOSED] Approved D11, depends on FR-E2-17 |
| WF-06 | Explicit human approval is required before moving between major stages. Requirements, architecture, design, implementation plans and review findings are never auto-approved. | [PROPOSED] Approved D11 |
| WF-07 | No commit, push, merge or deploy happens without explicit human authorization. | [PROPOSED] Approved D11 |
| WF-08 | The overall deliverables list is preserved: requirements.md, architecture.md, design-review.md, impl-plan.md, implementation code changes, code review evidence, automated tests and verification evidence, PR description, changelog, reviewer checklist. | **Confirmed** as a list (D14). The stage mapping is [PROPOSED] and unconfirmed (OI-01, table below). |
| WF-09 | The Sync Engine (the application) and the Workflow (the process) stay separate. Their integration points are defined explicitly. | [PROPOSED] Approved D11 |
| WF-10 | The `.github/` Copilot configuration is not the implementation. | Approved D3 |
| WF-NFR-01 | Each SDLC stage records its inputs, outputs, completion status and approval status. | [PROPOSED] Approved D16 |
| WF-NFR-02 | Explicit human approval is recorded before proceeding to the next major stage. | [PROPOSED] Approved D16 |
| WF-NFR-03 | A rejected deliverable is revised by the responsible stage before resubmission. | [PROPOSED] Approved D16 |
| WF-NFR-04 | Traceability is maintained between requirements, architecture, design, implementation, review findings, test evidence and PR artifacts. | [PROPOSED] Approved D16 |
| WF-NFR-05 | No automatic commits, pushes, merges or deployments. | [PROPOSED] Approved D16 (reinforces WF-07) |
| WF-NFR-06 | Approval and traceability records are available for reviewing the workflow execution. Location and format are a design matter (OI-17). | [PROPOSED] Approved D16 |

**Integration points between the two areas**
- IP-1: the WF-05 hook runs `DocSyncCli verify` and acts on exit codes 0, 1 and 2 (D5, D11). The trigger event is deferred (OI-11).
- IP-2: the WF-04 skill documents `sync` and `verify` usage.
- IP-3 [PROPOSED, unconfirmed]: the Verify and PR stages cite `verify-report.md` and `mvn test` output as evidence.

**Stage-to-deliverable mapping: PROPOSED, unconfirmed (OI-01 remains OPEN).** The names only suggest correspondences. They are not requirements.

| Stage | Name-based candidate |
|---|---|
| Requirements | requirements.md |
| Architecture | architecture.md |
| Design Review | design-review.md |
| Implementation Planning | impl-plan.md |
| Implementation | implementation code changes |
| Review | code review evidence |
| Verify | automated tests and verification evidence |
| PR | PR description |
| unmapped | changelog, reviewer checklist |

## E. Documented behavior changes

| ID | Change | Basis |
|---|---|---|
| BC-1 | Every path other than `/`, `/style.css`, `/app.js` and the student API paths returns 404 NOT_FOUND. Today they return `index.html` with 200 through the catch-all (`StudentApiServer.java:29-36`). This explicitly includes `/index.html` and `/favicon.ico`. | D8, D17, D23 |
| BC-2 | A failed `DELETE /students/{id}` returns the structured error with 404, replacing `{"deleted":false}` (`StudentApiServer.java:109`). | D9 |
| BC-3 | None. Validation rules are not changed. | D10 |
| BC-4 | The `app.js` error alert shows the `error` string instead of the raw body (`app.js:69-77`). | D18 |
| **BC-5** | Static routes `/`, `/style.css` and `/app.js` currently answer any HTTP method with 200 (`StudentApiServer.java:29-54`). Unsupported methods now return 405 METHOD_NOT_ALLOWED in the structured format. GET still returns 200. | D24 |
| Other E1 changes | Valid JSON replaces `toString()` bodies. The 400 message becomes specific. The 405 body gets a `code`. | D8, D9 |
| README | The README is corrected to match the implementation (FR-DOC-01). | D2 |

## F. Assumptions, constraints, dependencies

**Assumptions**
- A-02: Success bodies are JSON as in FR-E1-02. A successful DELETE returns HTTP 200 with `{"deleted":true}` (D25, OI-05 closed).
- A-03: The README's extraction sources (A2) are the initial scope for E2 and E3 (D1).
- A-04: Under `--root`, the project layout is as in this repository (`frontend/`, `src/main/java/`, `docs/`). Every path is resolved from `--root` (FR-E2-20, D21).
- A-01 is closed (D17, D23).

**Constraints**
- Java 17, JDK-only runtime, JUnit 5 (NFR-01).
- Keep the in-memory repository and the form-urlencoded `POST /students` (D13).
- No commits, pushes or merges by the engine or by agents without human authorization.
- The SDLC layer lives under `.claude/` (D3).
- Only the human approves requirements.
- No new runtime dependency to test the frontend change (D22).

**Dependencies**
- E2 depends on E1. E3 depends on E2's engine (CLI, markers, sync, verify, reports).
- WF-05 depends on FR-E2-17 and a built `DocSyncCli`.
- FR-E1-11 depends on E2 (the error format is documented in the generated docs).
- FR-E1-12 depends on FR-E1-03.
- FR-DOC-01 depends on the completion of E1 to E3.

## G. Out of scope (D13, D10, D19, D21)
1. Git impact analysis (possible future enhancement).
2. MySQL or any persistent storage.
3. New frontend features (list or delete views). Only the D18 `app.js` change is in scope.
4. Authentication and authorization.
5. Any change to the `POST /students` input format.
6. Changing existing frontend or backend validation rules.
7. Changing the API's own static file serving mechanism (OBS-01). The D23 and D24 changes to which paths and methods return 200, 404 or 405 are in scope.
8. Using `.github/` Copilot configuration as the implementation.
9. Automated JavaScript execution in tests (EX-01).

## H. Consolidated open items, assumptions and deferred decisions

| ID | Item | Status | Owner / phase |
|---|---|---|---|
| OI-01 | Per-stage deliverables and handoff criteria; stage-to-deliverable mapping. The original assignment is not available. | **OPEN by decision (D15)** | User; workflow Architecture |
| OI-02 | Workflow NFRs. | CLOSED (D16) | None |
| OI-03 / A-01 | Static routes stay 200. | CLOSED (D17, D23) | None |
| OI-04 | `Allow` header on 405 (all routes). | **Deferred** | Architecture/Design |
| OI-05 | Exact delete-result JSON shape. | CLOSED (D25): a successful `DELETE /students/{id}` returns HTTP 200 with `{"deleted":true}` (FR-E1-02) | None |
| OI-06 | When INTERNAL_SERVER_ERROR is produced. | **Deferred** | Architecture/Design |
| OI-07 | Concrete block IDs, per-file block layout and digest format. | **Deferred** | Architecture/Design |
| OI-08 | Report content and format, and whether an exit-2 run writes a report. | **Deferred** | Architecture/Design |
| OI-09 | Drift extraction: how backend rules are read from Java source, how `contains("@")` is treated as a format rule, how JS and Java regexes are compared, and how extraction targets the single route source (the README names `routes()`). | **Deferred** | Architecture/Design |
| OI-10 | `app.js` error display. | CLOSED (D18). The fallback when the body is not JSON or lacks `error` is a design detail. | Design |
| OI-11 | Hook trigger event, and who runs sync after exit 1. | **Deferred** | Architecture/Design |
| OI-12 | Engine path resolution. | CLOSED (FR-E2-20) | None |
| OI-13 | Root-relative resolution for all inputs and outputs. | CLOSED (D21) | None |
| OI-14 | Verification of the `app.js` change. | CLOSED (D22, EX-01) | None |
| OI-15 | `/index.html` and `/favicon.ico`. | CLOSED (D23) | None |
| OI-16 | Source for the request contract and delete-result contract, since `StudentRequest` and `DeleteResponse` do not exist as classes (G-17). This concerns where the contracts are declared in code, not the DELETE response shape, which is closed by D25. | **Deferred** | Architecture/Design |
| OI-17 | Location and format of the approval and traceability records (WF-NFR-06). | **Deferred** | Workflow Architecture |
| OI-18 | Whether the JDK `HttpServer` prefix-matches paths such as `/studentsabc` today (unverified by execution). The required behavior is in AC-E1-04. This is a to-verify-in-testing item only. | **To verify in testing** | Design/Testing |
| OI-19 | Methods on static routes. | CLOSED (D24) | None |
| OI-20 | The procedure for the manual check in AC-E1-11, including how an API error response is produced. | **Deferred** | Design/Testing |
| OI-21 | Whether HEAD (and OPTIONS) are supported on the static routes, or return 405 like other unsupported methods. | **Deferred** | Architecture/Design |

## I. Acceptance criteria

**E1**
- AC-E1-01 (FR-E1-01): A test enumerates the route source and confirms that every route in it is dispatched and every dispatched route is in it.
- AC-E1-02 (FR-E1-02, D25): `GET /students`, `GET /students/{id}` and `POST /students` (201) bodies each parse as JSON. The list is an array. Student objects have exactly `id, name, email, department, phone, status`. A successful `DELETE /students/{id}` returns HTTP 200 with the exact JSON body `{"deleted":true}`.
- AC-E1-03 (FR-E1-03 to 05): A POST with a blank name returns 400, `code`=VALIDATION_ERROR and `error`="Student name is required.". An email without `@` returns "Email must contain '@'.". A blank department returns "Department is required.". An invalid phone returns "Invalid phone number.". With several invalid fields, the first failing check in the order name, email, department, phone is returned (existing behavior).
- AC-E1-04 (FR-E1-06): `GET /nope`, `GET /index.html`, `GET /favicon.ico`, `GET /students/unknown` and prefix-sharing paths (`/studentsabc`, `/style.cssx`) each return 404 with `code`=NOT_FOUND in the structured format. An unknown path returns 404 for any method: `POST /nope` and `DELETE /index.html` return 404 and not 405.
- AC-E1-05 (FR-E1-03, 04, 07): For API routes, `PUT /students`, `PATCH /students` and `POST /students/{id}` return 405 with `code`=METHOD_NOT_ALLOWED in the structured format. For static routes, `POST /`, `PUT /style.css` and `DELETE /app.js` return 405 with `code`=METHOD_NOT_ALLOWED and the same structured body format. `GET` on `/`, `/style.css` and `/app.js` returns 200. A known route with an unsupported method returns 405, never 404. HEAD and OPTIONS on static routes are not asserted (OI-21). The `Allow` header is not asserted (OI-04).
- AC-E1-06 (FR-E1-08): `DELETE /students/missing` returns 404 with the structured error and no `deleted` key.
- AC-E1-07 (FR-E1-09): The existing paths work. A form-urlencoded POST returns 201. Invalid input returns 400. A missing id returns 404.
- AC-E1-08 (FR-E1-10): Tests start the server with an injected service and a non-default port. Two instances do not interfere.
- AC-E1-09 (FR-E1-04): When the design-defined condition for INTERNAL_SERVER_ERROR occurs (OI-06), the response is 500 with `code`=INTERNAL_SERVER_ERROR in the structured format.
- AC-E1-10 (FR-E1-11): The generated API docs contain the error format. A test exists per code.
- AC-E1-11 (FR-E1-12, **manual, EX-01**): A documented manual browser check, with its result recorded, confirms three things. (a) When the API returns a structured error, the alert shows only the human-readable `error` string and not the JSON. (b) The success alert ("Student record created successfully.", `app.js:74`) is unchanged. (c) The pre-submit validation alert (`app.js:51`) is unchanged. A review of the `app.js` diff confirms that only the error-handling path changed. This check is not a JUnit test, and no JavaScript is executed by JUnit. API response and error-contract behavior stays under AC-E1-02, 03, 05 and 06. The procedure is deferred (OI-20).
- AC-E1-12 (FR-E1-13): `GET /`, `GET /style.css` and `GET /app.js` return 200 with content types `text/html; charset=UTF-8`, `text/css; charset=UTF-8` and `application/javascript; charset=UTF-8`, and with the same content as the `frontend/` files. Unsupported methods on these routes follow AC-E1-05.

**E2**
- AC-E2-01 (FR-E2-01, 17): `sync --root <repo>` exits 0. An unknown command, a missing `--root` and a nonexistent root exit 2 with a stderr message.
- AC-E2-02 (FR-E2-22): After sync, the endpoints doc covers the four routes. The contract doc covers the six Student fields and the four form parameters `name, email, department, phone`. The service doc covers the 7 public methods (`createStudent` x2, `getStudents`, `getStudent`, `deleteStudent`, `validateStudentInput` x2) and carries a source digest.
- AC-E2-03 (FR-E2-05 to 07): Blocks use the exact marker syntax. IDs are unique and identical across two syncs. One file can hold multiple blocks.
- AC-E2-04 (FR-E2-08, 09): In a fixture with manual text before, between and after blocks, sync leaves the manual text byte-identical and changes only the matching block.
- AC-E2-05 (FR-E2-10): Fixtures with a missing end marker, unmatched IDs, malformed markers and duplicate IDs make sync and verify exit 2 with a clear, actionable stderr message. The file is unchanged and the result is not STALE. Cases are run for at least one file of each owner (an E2 doc and the UI doc).
- AC-E2-06 (FR-E2-11, 15): With an empty `docs/`, sync creates all four files and the sync-report marks all four created. Deleting any one of the four files, or one block from it, then run verify. Verify exits 2 and does not create or repair any missing generated documentation files or blocks. Running sync on a file that lacks a block appends it after the manual content, and the sync-report records the file as updated.
- AC-E2-07 (FR-E2-12, 13): After the initial sync, a second sync with no changes lists all four files as unchanged. A third sync with no changes produces byte-identical generated files and reports compared with the second sync.
- AC-E2-08 (FR-E2-14): After sync, verify exits 0. Each of these changes makes the stated file STALE with exit 1, and the verify report identifies it:
  - A route change makes `endpoints.md` STALE.
  - A `Student` field change or a create-parameter change makes `student-contract.md` STALE.
  - A `StudentService` signature or logic-only change (digest) makes `student-service.md` STALE.
  - An HTML control or `validationRules` change makes `student-ui.md` STALE.

  A hand edit inside any block is STALE. An edit outside blocks keeps exit 0. A CRLF conversion of a doc keeps exit 0.
- AC-E2-09 (FR-E2-16): Hashes of all four doc files are identical before and after verify. `verify-report.md` exists after exit 0 and after exit 1.
- AC-E2-10 (FR-E2-04): A fixture with dynamic route metadata or a non-literal parameter name exits 2 with a message. It is not STALE and nothing is guessed.
- AC-E2-11 (FR-E2-18): Failures print to stderr. Successful sync and verify write their reports.
- AC-E2-12 (FR-E2-19): Running either command creates no git commits.
- AC-E2-13 (FR-E2-20, inputs): `sync` and `verify` run with `--root <fixture>`, where the fixture's Java sources and `frontend/` files differ from those in the process's working directory (decoy files, or a subprocess started from another directory). The generated content and drift findings reflect the fixture files, not the working directory's files.
- AC-E2-14 (FR-E2-20, outputs): With the same setup, the four docs and both reports are written under the fixture root and nothing is written under the working directory.
- AC-E2-15 (FR-E2-03): After a full sync (with E3 delivered), exactly four generated docs exist at the four stated paths, each with blocks. The sync-report and verify-report cover all four. No other generated doc file is created.

**E3**
- AC-E3-01 (FR-E3-01, 02): The UI doc lists `studentName` (text, required), `studentEmail` (email, required), `studentDepartment` (select, required) and `studentPhone` (tel, maxlength 20, not required), plus the submit button "Add student". The select lists 3 `<option>` elements: the placeholder (value `""`, label "Select department"), "Computer Science" and "Mathematics".
- AC-E3-02 (FR-E3-03): The UI doc lists the four `validationRules` entries (required, minLength, pattern where present).
- AC-E3-03 (FR-E3-04): The UI doc contains `studentName→name`, `studentEmail→email`, `studentDepartment→department`, `studentPhone→phone`.
- AC-E3-04 (FR-E3-05, 06, 08): On current source, DRIFT is reported for exactly two findings. Name: min length (frontend 2, backend none). Email: pattern (frontend regex, backend `contains('@')`). There is no DRIFT for department, for phone, or for required-ness of any field. Message text differences and the select option list are not reported.
- AC-E3-05 (FR-E3-05, 06): In a fixture, changing a frontend `required`, `minLength` or `pattern` creates a new DRIFT. Changing only message text, select options, `maxlength` or `type` creates none.
- AC-E3-06 (FR-E3-07): With drift only, verify exits 0. With STALE plus drift, it exits 1. DRIFT is never labeled STALE. DRIFT appears in both `verify-report.md` and the UI doc.
- AC-E3-07 (FR-E3-08): Characterization tests show that the backend still accepts a 1-character name and `a@b`, and that `validationRules` is unchanged.
- AC-E3-08 (FR-E3-03): A template literal in a validation rule exits 2.
- AC-E3-09 (FR-E3-01): A hand edit inside a UI doc block makes verify report `docs/ui/student-ui.md` STALE. A missing UI doc file or block gives exit 2. Sync creates it, and the sync-report lists it (see AC-E2-06).

**NFR and documentation**
- AC-NFR-01 (NFR-01): The `pom.xml` diff adds no non-test dependency, and `mvn test` passes.
- AC-NFR-02 (NFR-02, 03): Once the initial sync has completed, repeated sync runs with no changes give byte-identical docs (all four) and reports, with no date or time content and stable ordering.
- AC-NFR-03 (NFR-04): A matrix shows at least one passing automated test per FR across the success, failure, stale, marker and drift categories. The only exception is FR-E1-12, which is recorded as covered by the documented manual check AC-E1-11 (EX-01).
- AC-DOC-01 (FR-DOC-01): Every command and path in the final README is executed or checked. The four generated files and two reports named in it exist after sync. No claim lacks an implementation. The build line is verified. The `.github/` claims are removed or true.

**Workflow**
- AC-WF-01 (WF-01): Eight agent files exist under `.claude/agents/`. Each states responsibilities, inputs, outputs and handoff criteria. The content check against the assignment is blocked on OI-01.
- AC-WF-02 (WF-02): A command or prompt exists per stage.
- AC-WF-03 (WF-03, 09): `CLAUDE.md` states the safety rules, approval requirements and the two-area separation.
- AC-WF-04 (WF-04): A docsync skill exists covering when and how to run sync and verify.
- AC-WF-05 (WF-05): A real or simulated hook run shows exit 0 proceeds, exit 1 reports stale and requires sync, and exit 2 reports failure and stops.
- AC-WF-06 (WF-06, 07): In a demonstration, every transition to the next major stage occurs only after explicit human approval is recorded. No commit, push, merge or deploy happens without authorization.
- AC-WF-07 (WF-01, 08): A demonstration runs all eight stages and shows artifacts, approval gates and a documentation verification result. The mapping of deliverables to stages is blocked on OI-01.
- AC-WF-08 (WF-09, 10): The integration points are documented and no `.github/` configuration is used.
- AC-WF-NFR-01 (WF-NFR-01): After a demonstration run, each stage's record shows inputs, outputs, completion status and approval status.
- AC-WF-NFR-02 (WF-NFR-02): A record of explicit human approval exists for each stage transition, and the next stage did not begin before it.
- AC-WF-NFR-03 (WF-NFR-03): When a deliverable is rejected in a demonstration, the responsible stage revises it, and the resubmission is recorded before the transition.
- AC-WF-NFR-04 (WF-NFR-04): A trace exists from each requirement through architecture, design, implementation, review finding, test evidence and PR artifact.
- AC-WF-NFR-05 (WF-NFR-05): The workflow configuration and demonstration show no automatic commit, push, merge or deployment.
- AC-WF-NFR-06 (WF-NFR-06): The approval and traceability records can be read by a reviewer after the run.

## J. Traceability summary

| Requirement | Decision(s) | Acceptance criteria |
|---|---|---|
| FR-E1-01 | D8 | AC-E1-01 |
| FR-E1-02 | D8, D9, D25 | AC-E1-02 |
| FR-E1-03, 04 | D9, D24 | AC-E1-03, 05, 09, 10 |
| FR-E1-05 | D9 | AC-E1-03 |
| FR-E1-06 | D8, D17, D23, D24 | AC-E1-04 |
| FR-E1-07 | D8, D24 | AC-E1-05 |
| FR-E1-08 | D9 | AC-E1-06 |
| FR-E1-09 | D8, D13 | AC-E1-07 |
| FR-E1-10 | D8 | AC-E1-08 |
| FR-E1-11 | D9 | AC-E1-10 |
| FR-E1-12 | D18, D22 | AC-E1-11 (manual, EX-01) |
| FR-E1-13 | D17, D23, D24 | AC-E1-12, 05 |
| FR-E2-01, 17 | D1, D5 | AC-E2-01 |
| FR-E2-02 | D1 | AC-E2-02 |
| FR-E2-03 | D1 | AC-E2-15, 06, 07 |
| FR-E2-22 | D1, D6 | AC-E2-02 |
| FR-E2-04 | D1, D5 | AC-E2-10 |
| FR-E2-05 to 07 | D4 | AC-E2-03 |
| FR-E2-08, 09 | D4, D7 | AC-E2-04 |
| FR-E2-10 | D4, D7 | AC-E2-05 |
| FR-E2-11, 15 | D6, D7 | AC-E2-06 |
| FR-E2-12, 13 | D7, D12 | AC-E2-07, 15 |
| FR-E2-14 | D6 | AC-E2-08 |
| FR-E2-16 | D5, D7 | AC-E2-09 |
| FR-E2-18 | D5 | AC-E2-11 |
| FR-E2-19 | D1 | AC-E2-12 |
| FR-E2-20 | D19, D21 | AC-E2-13, 14 |
| FR-E3-01 | D1, D4 to D7 | AC-E3-09, AC-E2-05, 06, 08, 15 |
| FR-E3-02 | D1 | AC-E3-01 |
| FR-E3-03 | D1 | AC-E3-02, 08 |
| FR-E3-04 | D1 | AC-E3-03 |
| FR-E3-05, 06 | D10, D20 | AC-E3-04, 05 |
| FR-E3-07 | D10 | AC-E3-06 |
| FR-E3-08 | D10, D20 | AC-E3-04, 07 |
| FR-E3-09 | derived (OI-09) | AC-E3-04, 05 |
| FR-DOC-01 | D2, D3 | AC-DOC-01 |
| NFR-01 | D12 | AC-NFR-01 |
| NFR-02, 03 | D12 | AC-NFR-02, AC-E2-07 |
| NFR-04 | D12, D22 | AC-NFR-03 |
| NFR-05 | D12 | none (a statement of no targets) |
| WF-01 | D11, D14, D15 | AC-WF-01, 07 |
| WF-02 | D11 | AC-WF-02 |
| WF-03 | D11 | AC-WF-03 |
| WF-04 | D11 | AC-WF-04 |
| WF-05 | D11, D5 | AC-WF-05 |
| WF-06, 07 | D11 | AC-WF-06 |
| WF-08 | D14, D15 | AC-WF-07 (mapping blocked on OI-01) |
| WF-09, 10 | D11, D3 | AC-WF-03, 08 |
| WF-NFR-01 | D16 | AC-WF-NFR-01 |
| WF-NFR-02 | D16 | AC-WF-NFR-02 |
| WF-NFR-03 | D16 | AC-WF-NFR-03 |
| WF-NFR-04 | D16 | AC-WF-NFR-04 |
| WF-NFR-05 | D16 | AC-WF-NFR-05 |
| WF-NFR-06 | D16 | AC-WF-NFR-06 |

---

**This text is marked APPROVED (Revision 3 + D24 + D25) by the human on 2026-11-13, and was written to `docs/requirements.md` on the explicit authorization of the human. OI-01 remains OPEN by decision (D15). The Architecture phase has not started.**
