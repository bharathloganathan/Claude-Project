# Student Service

<!-- docsync:begin service-methods -->
| Method | Parameters | Returns |
|---|---|---|
| createStudent | String name, String email, String department | Student |
| createStudent | String name, String email, String department, String phone | Student |
| getStudents | none | List<Student> |
| getStudent | String id | Optional<Student> |
| deleteStudent | String id | boolean |
| validateStudentInput | String name, String email, String department | void |
| validateStudentInput | String name, String email, String department, String phone | void |
<!-- docsync:end service-methods -->

<!-- docsync:begin service-digest -->
Source: `src/main/java/org/example/service/StudentService.java` sha256: `0bfbcf50dc212c5845ff237d917ffcf6de1f8ce070be4b714c938f81f42dbcfc`
<!-- docsync:end service-digest -->
