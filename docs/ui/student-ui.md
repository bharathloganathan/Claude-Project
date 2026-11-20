# Student UI

<!-- docsync:begin ui-controls -->
| Id | Name | Type | Required | Max length |
|---|---|---|---|---|
| studentName | studentName | text | yes | none |
| studentEmail | studentEmail | email | yes | none |
| studentDepartment | studentDepartment | select | yes | none |
| studentPhone | studentPhone | tel | no | 20 |

Options of studentDepartment:

| Value | Label |
|---|---|
| "" | Select department |
| Computer Science | Computer Science |
| Mathematics | Mathematics |

Submit button: Add student
<!-- docsync:end ui-controls -->

<!-- docsync:begin ui-rules -->
| UI field | Required | Min length | Pattern |
|---|---|---|---|
| studentName | yes | 2 | none |
| studentEmail | yes | none | ^[^\s@]+@[^\s@]+\.[^\s@]+$ |
| studentDepartment | yes | none | none |
| studentPhone | no | none | ^(?=.{7,20}$)\+?[0-9 -]+$ |
<!-- docsync:end ui-rules -->

<!-- docsync:begin ui-field-mapping -->
| UI field | API field | Mapping |
|---|---|---|
| studentName | name | studentName→name |
| studentEmail | email | studentEmail→email |
| studentDepartment | department | studentDepartment→department |
| studentPhone | phone | studentPhone→phone |
<!-- docsync:end ui-field-mapping -->

<!-- docsync:begin ui-drift -->
Report only: drift never changes the exit code.

| UI field | Property | Frontend | Backend |
|---|---|---|---|
| studentName | minLength | 2 | none |
| studentEmail | pattern | ^[^\s@]+@[^\s@]+\.[^\s@]+$ | contains("@") |
<!-- docsync:end ui-drift -->
