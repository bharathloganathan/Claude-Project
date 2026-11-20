package org.example.service;

import org.example.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization of TODAY's {@link StudentService} validation (task T0.2, basis for AC-E3-07
 * and the DR-19 requiredness cross-check). These tests describe the expected current behavior of
 * the unchanged code, based on inspection of its source. They do not say what it should do. The
 * first test run establishes the actual behavior, and any test that fails is investigated against
 * the observed behavior and reported.
 *
 * <p>Per-field behavior on BLANK input (null, empty, whitespace only), other fields valid:
 * <pre>
 *   field        null       ""         "   "      message when rejected
 *   name         rejected   rejected   rejected   Student name is required.
 *   email        rejected   rejected   rejected   Email must contain '@'.
 *   department   rejected   rejected   rejected   Department is required.
 *   phone        rejected   accepted   accepted   Invalid phone number.
 * </pre>
 * Only the phone is optional: a blank phone is accepted, a null phone is rejected.
 *
 * <p>Every test builds its own {@link StudentService} (it owns its repository), so no state is shared.
 */
class ServiceCharacterizationTest {

    private static final String NAME_MESSAGE = "Student name is required.";
    private static final String EMAIL_MESSAGE = "Email must contain '@'.";
    private static final String DEPARTMENT_MESSAGE = "Department is required.";
    private static final String PHONE_MESSAGE = "Invalid phone number.";

    private static final String VALID_NAME = "Ann Lee";
    private static final String VALID_EMAIL = "ann@example.org";
    private static final String VALID_DEPARTMENT = "Computer Science";
    private static final String VALID_PHONE = "1234567";

    private final StudentService service = new StudentService();

    private void assertRejected(String expectedMessage, String name, String email, String department, String phone) {
        int storedBefore = service.getStudents().size();
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> service.validateStudentInput(name, email, department, phone));
        assertEquals(expectedMessage, thrown.getMessage());
        // createStudent validates first, so it must reject with the same message and store nothing.
        IllegalArgumentException fromCreate = assertThrows(IllegalArgumentException.class,
            () -> service.createStudent(name, email, department, phone));
        assertEquals(expectedMessage, fromCreate.getMessage());
        assertEquals(storedBefore, service.getStudents().size());
    }

    private void assertAccepted(String name, String email, String department, String phone) {
        assertDoesNotThrow(() -> service.validateStudentInput(name, email, department, phone));
        assertDoesNotThrow(() -> service.createStudent(name, email, department, phone));
    }

    // ---- minimal values are accepted ----

    @Test
    void oneCharacterNameIsAccepted() {
        assertAccepted("A", VALID_EMAIL, VALID_DEPARTMENT, VALID_PHONE);
    }

    @Test
    void emailAtBIsAccepted() {
        assertAccepted(VALID_NAME, "a@b", VALID_DEPARTMENT, VALID_PHONE);
    }

    @Test
    void emailCheckOnlyRequiresAnAtSign() {
        assertAccepted(VALID_NAME, "@", VALID_DEPARTMENT, VALID_PHONE);
        assertRejected(EMAIL_MESSAGE, VALID_NAME, "ab.c", VALID_DEPARTMENT, VALID_PHONE);
    }

    // ---- check order: name, email, department, phone ----

    @Test
    void allFieldsInvalidReportsTheNameFirst() {
        assertRejected(NAME_MESSAGE, "", "no-at-sign", "", "abc");
    }

    @Test
    void nameValidReportsTheEmailBeforeDepartmentAndPhone() {
        assertRejected(EMAIL_MESSAGE, VALID_NAME, "no-at-sign", "", "abc");
    }

    @Test
    void nameAndEmailValidReportsTheDepartmentBeforeThePhone() {
        assertRejected(DEPARTMENT_MESSAGE, VALID_NAME, VALID_EMAIL, "", "abc");
    }

    @Test
    void onlyThePhoneInvalidReportsThePhone() {
        assertRejected(PHONE_MESSAGE, VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "abc");
    }

    @Test
    void nullNameAndNullEmailStillReportTheNameFirst() {
        assertRejected(NAME_MESSAGE, null, null, null, null);
    }

    // ---- phone rules ----

    @ParameterizedTest
    @ValueSource(strings = {
        "1234567",                // 7 characters, the minimum length
        "12345678901234567890",   // 20 characters, the maximum length
        "+1234567",               // optional leading plus
        "+123456",                // the plus counts toward the 7 characters
        "123 456 7890",           // spaces inside
        "123-456-7890",           // hyphens inside
        "+1 555-123-4567"         // plus, spaces and hyphens together
    })
    void validPhoneFormsAreAccepted(String phone) {
        assertAccepted(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "123456",                 // 6 characters, too short
        "12",                     // far too short
        "123456789012345678901",  // 21 characters, too long
        "abcdefg",                // non-matching: letters
        "123-45a-6789",           // non-matching: a letter inside
        "12345(67)",              // non-matching: parentheses
        "123.456.7890",           // non-matching: dots
        "++1234567",              // non-matching: two plus signs
        "123+4567890"             // non-matching: plus not at the start
    })
    void invalidPhoneFormsAreRejected(String phone) {
        assertRejected(PHONE_MESSAGE, VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, phone);
    }

    @Test
    void surroundingSpacesAreTrimmedBeforeThePhoneIsChecked() {
        // 7 characters after trimming, 11 before: accepted, so the length is measured after trimming.
        assertAccepted(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "  1234567  ");
        // 21 characters after trimming: still too long, the spaces do not rescue it.
        assertRejected(PHONE_MESSAGE, VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "  123456789012345678901  ");
        // 6 characters after trimming: too short even though the raw text is longer than 7.
        assertRejected(PHONE_MESSAGE, VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "  123456  ");
    }

    @Test
    void createStoresTheTrimmedPhone() {
        Student student = service.createStudent(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "  1234567  ");
        assertEquals("1234567", student.phone());
    }

    @Test
    void createStoresTrimmedNameEmailAndDepartmentWithStatusActive() {
        Student student = service.createStudent("  Ann Lee  ", "  ann@example.org  ", "  Computer Science  ", VALID_PHONE);
        assertEquals("Ann Lee", student.name());
        assertEquals("ann@example.org", student.email());
        assertEquals("Computer Science", student.department());
        assertEquals("active", student.status());
    }

    // ---- both overloads delegate with an empty phone ----

    @Test
    void threeArgumentCreateStudentDelegatesWithAnEmptyPhone() {
        Student student = service.createStudent(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT);
        assertEquals("", student.phone());
        assertEquals(VALID_NAME, student.name());
        assertEquals("active", student.status());
        assertEquals(1, service.getStudents().size());
    }

    @Test
    void threeArgumentCreateStudentRejectsWithTheSameMessagesInTheSameOrder() {
        assertEquals(NAME_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> service.createStudent("", "x", "")).getMessage());
        assertEquals(EMAIL_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> service.createStudent(VALID_NAME, "x", "")).getMessage());
        assertEquals(DEPARTMENT_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> service.createStudent(VALID_NAME, VALID_EMAIL, " ")).getMessage());
        assertEquals(0, service.getStudents().size());
    }

    @Test
    void threeArgumentValidateStudentInputAcceptsValidInputAndIgnoresThePhoneCheck() {
        assertDoesNotThrow(() -> service.validateStudentInput(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT));
        assertDoesNotThrow(() -> service.validateStudentInput("A", "a@b", "D"));
    }

    @Test
    void threeArgumentValidateStudentInputUsesTheSameOrderAndMessages() {
        assertEquals(NAME_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> service.validateStudentInput(null, null, null)).getMessage());
        assertEquals(EMAIL_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> service.validateStudentInput(VALID_NAME, null, null)).getMessage());
        assertEquals(DEPARTMENT_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> service.validateStudentInput(VALID_NAME, VALID_EMAIL, null)).getMessage());
    }

    // ---- accept or reject per field on BLANK input (null, "", whitespace only) ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankNameIsRejected(String blank) {
        assertRejected(NAME_MESSAGE, blank, VALID_EMAIL, VALID_DEPARTMENT, VALID_PHONE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankEmailIsRejected(String blank) {
        assertRejected(EMAIL_MESSAGE, VALID_NAME, blank, VALID_DEPARTMENT, VALID_PHONE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankDepartmentIsRejected(String blank) {
        assertRejected(DEPARTMENT_MESSAGE, VALID_NAME, VALID_EMAIL, blank, VALID_PHONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankPhoneIsAccepted(String blank) {
        assertAccepted(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, blank);
    }

    @Test
    void nullPhoneIsRejected() {
        assertRejected(PHONE_MESSAGE, VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, null);
    }

    @Test
    void blankPhoneIsStoredAsAnEmptyString() {
        assertEquals("", service.createStudent(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "").phone());
        assertEquals("", service.createStudent(VALID_NAME, VALID_EMAIL, VALID_DEPARTMENT, "   ").phone());
    }
}
