# API Endpoints

<!-- docsync:begin api-routes -->
| Method | Path | Summary | Request | Response |
|---|---|---|---|---|
| GET | /students | List all students | none | Student[] |
| POST | /students | Create a student | StudentRequest | Student |
| GET | /students/{id} | Fetch a student by id | none | Student |
| DELETE | /students/{id} | Delete a student | none | DeleteResponse |
<!-- docsync:end api-routes -->

<!-- docsync:begin api-errors -->
Error responses use the body {"error":"<message>","code":"<CODE>"}.

| Code | HTTP status |
|---|---|
| VALIDATION_ERROR | 400 |
| NOT_FOUND | 404 |
| METHOD_NOT_ALLOWED | 405 |
| INTERNAL_SERVER_ERROR | 500 |
<!-- docsync:end api-errors -->
