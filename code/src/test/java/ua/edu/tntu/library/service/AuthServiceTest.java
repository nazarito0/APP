package ua.edu.tntu.library.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua.edu.tntu.library.dto.ApiRequests;
import ua.edu.tntu.library.model.Role;
import ua.edu.tntu.library.repository.ReaderDAO;
import ua.edu.tntu.library.repository.UserDAO;
import ua.edu.tntu.library.web.ApiException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Модульні тести автентифікації та авторизації AuthService (FR-013, FR-014).
 */
class AuthServiceTest {
    private UserDAO userDAO;
    private ReaderDAO readerDAO;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        userDAO = mock(UserDAO.class);
        readerDAO = mock(ReaderDAO.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authService = new AuthService(userDAO, readerDAO, passwordEncoder);
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(session);
    }

    private static Map<String, Object> storedUser(String status) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", 1L);
        user.put("login", "librarian");
        user.put("password_hash", "hash");
        user.put("role", "LIBRARIAN");
        user.put("status", status);
        return user;
    }

    // ---------- Вхід у систему ----------

    @Test
    @DisplayName("TC-042 Успішний вхід: оновлюється last_login, змінюється id сесії")
    void successfulLogin() {
        when(userDAO.findForLogin("librarian")).thenReturn(storedUser("ACTIVE"));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(userDAO.findProfile(1L)).thenReturn(Map.of("id", 1L, "login", "librarian", "role", "LIBRARIAN"));

        Map<String, Object> profile = authService.login(new ApiRequests.Login("librarian", "secret123"), request);

        assertEquals("LIBRARIAN", profile.get("role"));
        verify(userDAO).updateLastLogin(1L);
        verify(request).changeSessionId();
        verify(session).setAttribute("userId", 1L);
    }

    @Test
    @DisplayName("TC-043 Вхід з неіснуючим логіном -> 401")
    void loginWithUnknownUser() {
        when(userDAO.findForLogin("ghost")).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.login(new ApiRequests.Login("ghost", "whatever"), request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(userDAO, never()).updateLastLogin(anyLong());
    }

    @Test
    @DisplayName("TC-044 Вхід з неправильним паролем -> 401")
    void loginWithWrongPassword() {
        when(userDAO.findForLogin("librarian")).thenReturn(storedUser("ACTIVE"));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.login(new ApiRequests.Login("librarian", "wrong"), request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(userDAO, never()).updateLastLogin(anyLong());
        verify(session, never()).setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("TC-045 Деактивований акаунт не може увійти навіть з правильним паролем")
    void deactivatedAccountCannotLogin() {
        when(userDAO.findForLogin("librarian")).thenReturn(storedUser("DEACTIVATED"));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.login(new ApiRequests.Login("librarian", "secret123"), request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(userDAO, never()).updateLastLogin(anyLong());
    }

    // ---------- Реєстрація читача (FR-006) ----------

    @Test
    @DisplayName("TC-046 Реєстрація читача створює акаунт READER і формуляр")
    void registerReaderCreatesAccountAndCard() {
        when(userDAO.insert("reader1", "password123", Role.READER)).thenReturn(20L);
        when(userDAO.findProfile(20L)).thenReturn(Map.of("id", 20L, "login", "reader1", "role", "READER"));
        when(readerDAO.findIdByUserId(20L)).thenReturn(30L);
        when(readerDAO.findCardNumber(30L)).thenReturn("RC-0001");

        Map<String, Object> profile = authService.registerReader(new ApiRequests.ReaderRegistration(
                "Олена Коваль", "RC-0001", "reader1", "password123", "", " +380671234567 "), request);

        verify(readerDAO).insert(20L, "RC-0001", "Олена Коваль", null, "+380671234567");
        verify(session).setAttribute("userId", 20L);
        assertEquals("RC-0001", profile.get("cardNumber"));
        assertEquals(30L, profile.get("readerId"));
    }

    // ---------- Сесія та авторизація ----------

    @Test
    @DisplayName("TC-047 Запит без сесії користувача -> 401")
    void requestWithoutUserInSession() {
        when(session.getAttribute("userId")).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> authService.requireUserId(session));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    @DisplayName("TC-048 Сесія деактивованого акаунта анулюється -> 403")
    void deactivatedAccountSessionIsInvalidated() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userDAO.findStatus(1L)).thenReturn("DEACTIVATED");

        ApiException exception = assertThrows(ApiException.class, () -> authService.requireUserId(session));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(session).invalidate();
    }

    @Test
    @DisplayName("TC-049 Сесія видаленого акаунта анулюється -> 401")
    void deletedAccountSessionIsInvalidated() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userDAO.findStatus(1L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class, () -> authService.requireUserId(session));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(session).invalidate();
    }

    @ParameterizedTest(name = "TC-050 Роль {0}, дозволено {1} -> доступ {2}")
    @CsvSource({
            "LIBRARIAN, LIBRARIAN, true",
            "READER, LIBRARIAN, false",
            "ADMIN, LIBRARIAN, false",
            "ADMIN, ADMIN, true",
            "LIBRARIAN, ADMIN, false",
            "READER, ADMIN, false"
    })
    void roleBasedAccess(String actualRole, String allowedRole, boolean granted) {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userDAO.findStatus(1L)).thenReturn("ACTIVE");
        when(userDAO.findRole(1L)).thenReturn(actualRole);

        if (granted) {
            assertEquals(Role.valueOf(actualRole), authService.requireRole(session, Role.valueOf(allowedRole)));
        } else {
            ApiException exception = assertThrows(ApiException.class,
                    () -> authService.requireRole(session, Role.valueOf(allowedRole)));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        }
    }

    @Test
    @DisplayName("TC-051 Розділ «Видачі» доступний і читачу, і бібліотекарю")
    void loansSectionAllowsReaderAndLibrarian() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userDAO.findStatus(1L)).thenReturn("ACTIVE");
        when(userDAO.findRole(1L)).thenReturn("READER");

        assertEquals(Role.READER, authService.requireRole(session, Role.READER, Role.LIBRARIAN));
    }

    // ---------- Зміна та скидання пароля ----------

    @Test
    @DisplayName("TC-052 Зміна пароля з правильним поточним паролем")
    void changePasswordWithCorrectCurrentPassword() {
        when(userDAO.findPasswordHash(7L)).thenReturn("stored-hash");
        when(passwordEncoder.matches("oldPassword", "stored-hash")).thenReturn(true);
        when(passwordEncoder.matches("newPassword123", "stored-hash")).thenReturn(false);

        authService.changePassword(7L, new ApiRequests.PasswordChange("oldPassword", "newPassword123"));

        verify(userDAO).updatePassword(7L, "newPassword123");
    }

    @Test
    @DisplayName("TC-053 Новий пароль, що збігається з поточним, відхиляється")
    void newPasswordMustDifferFromCurrent() {
        when(userDAO.findPasswordHash(7L)).thenReturn("stored-hash");
        when(passwordEncoder.matches("samePassword", "stored-hash")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> authService.changePassword(7L,
                new ApiRequests.PasswordChange("samePassword", "samePassword")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(userDAO, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("TC-054 Скидання пароля неіснуючого акаунта -> 404")
    void resetPasswordOfMissingAccount() {
        when(userDAO.findById(99L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.resetAccountPassword(99L, new ApiRequests.PasswordReset("newPassword1")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(userDAO, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("TC-055 Адміністратор скидає пароль існуючого акаунта")
    void resetPasswordOfExistingAccount() {
        when(userDAO.findById(5L)).thenReturn(Map.of("id", 5L));

        authService.resetAccountPassword(5L, new ApiRequests.PasswordReset("newPassword1"));

        verify(userDAO).updatePassword(5L, "newPassword1");
    }

    // ---------- FR-014: автоматична деактивація ----------

    @Test
    @DisplayName("TC-056 Планувальник деактивує акаунти без входу понад 30 днів")
    void scheduledDeactivationDelegatesToDao() {
        authService.deactivateInactiveAccounts();

        verify(userDAO).deactivateInactiveAccounts();
    }
}
