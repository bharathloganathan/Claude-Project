# DocSync Verify Report

Command: verify
Result: UP_TO_DATE

| File | Status |
|---|---|
| docs/api/endpoints.md | up to date |
| docs/api/student-contract.md | up to date |
| docs/backend/student-service.md | up to date |
| docs/ui/student-ui.md | up to date |

## Drift (report only)

| UI field | Property | Frontend | Backend |
|---|---|---|---|
| studentName | minLength | 2 | none |
| studentEmail | pattern | ^[^\s@]+@[^\s@]+\.[^\s@]+$ | contains("@") |
