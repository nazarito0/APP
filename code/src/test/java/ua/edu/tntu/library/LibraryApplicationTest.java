package ua.edu.tntu.library;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua.edu.tntu.library.dto.ApiRequests;
import ua.edu.tntu.library.repository.BookDAO;
import ua.edu.tntu.library.repository.LoanDAO;
import ua.edu.tntu.library.repository.ReaderDAO;
import ua.edu.tntu.library.repository.ReportDAO;
import ua.edu.tntu.library.repository.UserDAO;
import ua.edu.tntu.library.service.AuthService;
import ua.edu.tntu.library.service.LibraryService;
import ua.edu.tntu.library.model.UserStatus;
import ua.edu.tntu.library.web.ApiException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LibraryApplicationTest {
    @Test
    void applicationHasSpringBootConfiguration() {
        assertNotNull(LibraryApplication.class.getAnnotation(SpringBootApplication.class));
    }

    @Test
    void shortBookSearchIsRejectedBeforeDatabaseAccess() {
        BookDAO bookDAO = mock(BookDAO.class);
        ReaderDAO readerDAO = mock(ReaderDAO.class);
        LoanDAO loanDAO = mock(LoanDAO.class);
        ReportDAO reportDAO = mock(ReportDAO.class);
        UserDAO userDAO = mock(UserDAO.class);
        LibraryService service = new LibraryService(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);

        ApiException exception = assertThrows(ApiException.class, () -> service.books("ab"));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);
    }

    @Test
    void unknownReportTypeIsRejectedBeforeDatabaseAccess() {
        BookDAO bookDAO = mock(BookDAO.class);
        ReaderDAO readerDAO = mock(ReaderDAO.class);
        LoanDAO loanDAO = mock(LoanDAO.class);
        ReportDAO reportDAO = mock(ReportDAO.class);
        UserDAO userDAO = mock(UserDAO.class);
        LibraryService service = new LibraryService(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);

        ApiException exception = assertThrows(ApiException.class, () -> service.reportRows("UNKNOWN"));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        BookDAO bookDAO = mock(BookDAO.class);
        ReaderDAO readerDAO = mock(ReaderDAO.class);
        LoanDAO loanDAO = mock(LoanDAO.class);
        ReportDAO reportDAO = mock(ReportDAO.class);
        UserDAO userDAO = mock(UserDAO.class);
        when(userDAO.findById(1L)).thenReturn(Map.of("id", 1L, "role", "ADMIN", "status", "ACTIVE"));
        when(userDAO.lockAndCountActiveAdmins()).thenReturn(1);
        LibraryService service = new LibraryService(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateStaffStatus(2L, 1L, UserStatus.DEACTIVATED));

        Assertions.assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(userDAO, never()).setStatus(1L, "DEACTIVATED");
    }

    @Test
    void staffMemberCannotDeactivateTheirOwnAccount() {
        BookDAO bookDAO = mock(BookDAO.class);
        ReaderDAO readerDAO = mock(ReaderDAO.class);
        LoanDAO loanDAO = mock(LoanDAO.class);
        ReportDAO reportDAO = mock(ReportDAO.class);
        UserDAO userDAO = mock(UserDAO.class);
        when(userDAO.findById(2L)).thenReturn(Map.of("id", 2L, "role", "LIBRARIAN", "status", "ACTIVE"));
        LibraryService service = new LibraryService(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateStaffStatus(2L, 2L, UserStatus.DEACTIVATED));

        Assertions.assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(userDAO, never()).setStatus(2L, "DEACTIVATED");
    }

    @Test
    void wrongCurrentPasswordDoesNotUpdatePasswordHash() {
        UserDAO userDAO = mock(UserDAO.class);
        ReaderDAO readerDAO = mock(ReaderDAO.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userDAO.findPasswordHash(7L)).thenReturn("stored-hash");
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);
        AuthService authService = new AuthService(userDAO, readerDAO, passwordEncoder);

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.changePassword(7L, new ApiRequests.PasswordChange("wrong", "newPassword123")));

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(userDAO, never()).updatePassword(7L, "newPassword123");
    }
}