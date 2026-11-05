package ua.edu.tntu.library.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.edu.tntu.library.model.Role;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевірка обмежень вхідних даних форм (FR-012) методами
 * розбиття на класи еквівалентності та аналізу граничних значень.
 */
class ApiRequestsValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static boolean hasViolation(Set<? extends ConstraintViolation<?>> violations, String property) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith(property));
    }

    private static ApiRequests.BookCreate book(String title, Integer year, List<String> copies) {
        return new ApiRequests.BookCreate(title, "Автор", "Роман", "978-966-0000-00-0", year,
                "Видавництво", copies);
    }

    private static ApiRequests.ReaderRegistration reader(String login, String password, String email) {
        return new ApiRequests.ReaderRegistration("Олена Коваль", "RC-0001", login, password, email,
                "+380671234567");
    }

    // ---------- Рік видання: діапазон 1000..2100 ----------

    @ParameterizedTest(name = "TC-060 Рік видання {0} -> допустимий: {1}")
    @CsvSource({
            "999, false",
            "1000, true",
            "1001, true",
            "2026, true",
            "2099, true",
            "2100, true",
            "2101, false",
            "-1, false"
    })
    void publicationYearBoundaries(int year, boolean valid) {
        Set<ConstraintViolation<ApiRequests.BookCreate>> violations =
                validator.validate(book("Назва", year, List.of("INV-1")));

        assertEquals(!valid, hasViolation(violations, "publicationYear"));
    }

    @Test
    @DisplayName("TC-061 Рік видання не обов'язковий (null допустимий)")
    void publicationYearIsOptional() {
        assertTrue(validator.validate(book("Назва", null, List.of("INV-1"))).isEmpty());
    }

    // ---------- Назва книжки: обов'язкова, 1..240 символів ----------

    @ParameterizedTest(name = "TC-062 Назва довжиною {0} -> допустима: {1}")
    @CsvSource({"1, true", "239, true", "240, true", "241, false"})
    void titleLengthBoundaries(int length, boolean valid) {
        Set<ConstraintViolation<ApiRequests.BookCreate>> violations =
                validator.validate(book("т".repeat(length), 2000, List.of("INV-1")));

        assertEquals(!valid, hasViolation(violations, "title"));
    }

    @ParameterizedTest(name = "TC-063 Порожня назва [{0}] недопустима")
    @ValueSource(strings = {"", " ", "   "})
    void blankTitleIsRejected(String title) {
        assertTrue(hasViolation(validator.validate(book(title, 2000, List.of("INV-1"))), "title"));
    }

    // ---------- Інвентарні номери ----------

    @Test
    @DisplayName("TC-064 Книжка без жодного примірника недопустима")
    void bookWithoutCopiesIsRejected() {
        assertTrue(hasViolation(validator.validate(book("Назва", 2000, List.of())), "inventoryNumbers"));
    }

    @ParameterizedTest(name = "TC-065 Інвентарний номер довжиною {0} -> допустимий: {1}")
    @CsvSource({"1, true", "60, true", "61, false"})
    void inventoryNumberLengthBoundaries(int length, boolean valid) {
        Set<ConstraintViolation<ApiRequests.BookCreate>> violations =
                validator.validate(book("Назва", 2000, List.of("9".repeat(length))));

        assertEquals(!valid, hasViolation(violations, "inventoryNumbers"));
    }

    @Test
    @DisplayName("TC-066 Порожній інвентарний номер у списку недопустимий")
    void blankInventoryNumberIsRejected() {
        assertTrue(hasViolation(validator.validate(book("Назва", 2000, List.of("INV-1", " "))),
                "inventoryNumbers"));
    }

    // ---------- Пароль: 8..100 символів ----------

    @ParameterizedTest(name = "TC-067 Пароль довжиною {0} -> допустимий: {1}")
    @CsvSource({"7, false", "8, true", "9, true", "99, true", "100, true", "101, false"})
    void passwordLengthBoundaries(int length, boolean valid) {
        Set<ConstraintViolation<ApiRequests.ReaderRegistration>> violations =
                validator.validate(reader("reader1", "p".repeat(length), null));

        assertEquals(!valid, hasViolation(violations, "password"));
    }

    @ParameterizedTest(name = "TC-068 Новий пароль довжиною {0} при зміні -> допустимий: {1}")
    @CsvSource({"7, false", "8, true", "100, true", "101, false"})
    void changedPasswordLengthBoundaries(int length, boolean valid) {
        Set<ConstraintViolation<ApiRequests.PasswordChange>> violations =
                validator.validate(new ApiRequests.PasswordChange("current", "n".repeat(length)));

        assertEquals(!valid, hasViolation(violations, "newPassword"));
    }

    // ---------- Логін: обов'язковий, до 80 символів ----------

    @ParameterizedTest(name = "TC-069 Логін довжиною {0} -> допустимий: {1}")
    @CsvSource({"1, true", "80, true", "81, false"})
    void loginLengthBoundaries(int length, boolean valid) {
        Set<ConstraintViolation<ApiRequests.Login>> violations =
                validator.validate(new ApiRequests.Login("l".repeat(length), "password"));

        assertEquals(!valid, hasViolation(violations, "login"));
    }

    @Test
    @DisplayName("TC-070 Порожні логін і пароль при вході недопустимі")
    void blankLoginAndPasswordAreRejected() {
        Set<ConstraintViolation<ApiRequests.Login>> violations =
                validator.validate(new ApiRequests.Login(" ", ""));

        assertTrue(hasViolation(violations, "login"));
        assertTrue(hasViolation(violations, "password"));
    }

    // ---------- E-mail читача ----------

    @ParameterizedTest(name = "TC-071 Коректний e-mail [{0}] приймається")
    @ValueSource(strings = {"reader@example.com", "o.koval@tntu.edu.ua"})
    void validEmailIsAccepted(String email) {
        assertTrue(validator.validate(reader("reader1", "password123", email)).isEmpty());
    }

    @ParameterizedTest(name = "TC-072 Некоректний e-mail [{0}] відхиляється")
    @ValueSource(strings = {"not-an-email", "reader@", "@example.com", "reader example@mail.com"})
    void invalidEmailIsRejected(String email) {
        assertTrue(hasViolation(validator.validate(reader("reader1", "password123", email)), "email"));
    }

    @Test
    @DisplayName("TC-073 E-mail не обов'язковий (null допустимий)")
    void emailIsOptional() {
        assertTrue(validator.validate(reader("reader1", "password123", null)).isEmpty());
    }

    // ---------- Видача: ідентифікатори > 0 ----------

    @ParameterizedTest(name = "TC-074 readerId={0}, copyId={1} -> допустимо: {2}")
    @CsvSource({
            "1, 1, true",
            "0, 1, false",
            "-1, 1, false",
            "1, 0, false",
            "9223372036854775807, 1, true"
    })
    void issueLoanIdentifiers(long readerId, long copyId, boolean valid) {
        assertEquals(valid, validator.validate(new ApiRequests.IssueLoan(readerId, copyId)).isEmpty());
    }

    @Test
    @DisplayName("TC-075 Видача без читача або примірника (null) недопустима")
    void issueLoanRequiresBothIdentifiers() {
        Set<ConstraintViolation<ApiRequests.IssueLoan>> violations =
                validator.validate(new ApiRequests.IssueLoan(null, null));

        assertTrue(hasViolation(violations, "readerId"));
        assertTrue(hasViolation(violations, "copyId"));
    }

    // ---------- Працівник: роль обов'язкова ----------

    @Test
    @DisplayName("TC-076 Обліковий запис працівника без ролі недопустимий")
    void staffRoleIsRequired() {
        assertTrue(hasViolation(validator.validate(new ApiRequests.Staff("worker", "password123", null)), "role"));
        assertTrue(validator.validate(new ApiRequests.Staff("worker", "password123", Role.LIBRARIAN)).isEmpty());
    }
}
