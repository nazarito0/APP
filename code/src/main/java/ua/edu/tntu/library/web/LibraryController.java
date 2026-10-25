package ua.edu.tntu.library.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.tntu.library.dto.ApiRequests;
import ua.edu.tntu.library.model.Role;
import ua.edu.tntu.library.service.AuthService;
import ua.edu.tntu.library.service.LibraryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LibraryController {
    private final LibraryService libraryService;
    private final AuthService authService;

    public LibraryController(LibraryService libraryService, AuthService authService) {
        this.libraryService = libraryService;
        this.authService = authService;
    }

    @GetMapping("/books")
    public List<Map<String, Object>> books(@RequestParam(required = false) String search) {
        return libraryService.books(search);
    }

    @GetMapping("/books/{bookId}")
    public Map<String, Object> book(@PathVariable long bookId) {
        return libraryService.bookById(bookId);
    }

    @PostMapping("/books")
    public Map<String, Object> addBook(@Valid @RequestBody ApiRequests.BookCreate request, HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.addBook(request);
    }

    @PutMapping("/books/{bookId}")
    public Map<String, Object> updateBook(@PathVariable long bookId,
                                          @Valid @RequestBody ApiRequests.BookUpdate request,
                                          HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.updateBook(bookId, request);
    }

    @DeleteMapping("/books/{bookId}")
    public org.springframework.http.ResponseEntity<Void> deactivateBook(@PathVariable long bookId,
                                                                        HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        libraryService.deactivateBook(bookId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/readers")
    public List<Map<String, Object>> readers(HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.readers();
    }

    @GetMapping("/readers/{readerId}/loans")
    public List<Map<String, Object>> readerLoans(@PathVariable long readerId, HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.loansForReader(readerId);
    }

    @PostMapping("/readers")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createReader(@Valid @RequestBody ApiRequests.ReaderRegistration request,
                                             HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return authService.createReaderForLibrarian(request);
    }

    @PutMapping("/readers/{readerId}")
    public Map<String, Object> updateReader(@PathVariable long readerId,
                                             @Valid @RequestBody ApiRequests.ReaderUpdate request,
                                             HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.updateReader(readerId, request);
    }

    @GetMapping("/copies/available")
    public List<Map<String, Object>> availableCopies(HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.availableCopies();
    }

    @DeleteMapping("/readers/{readerId}")
    public org.springframework.http.ResponseEntity<Void> deactivateReader(@PathVariable long readerId,
                                                                           HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        libraryService.deactivateReader(readerId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/loans")
    public List<Map<String, Object>> loans(HttpSession session) {
        long userId = authService.requireUserId(session);
        Role role = authService.requireRole(session, Role.READER, Role.LIBRARIAN);
        if (role == Role.READER) {
            Long readerId = authService.readerIdForUser(userId);
            return readerId == null ? List.of() : libraryService.loansForReader(readerId);
        }
        return libraryService.loans(null);
    }

    @PostMapping("/loans")
    public Map<String, Object> issueLoan(@Valid @RequestBody ApiRequests.IssueLoan request,
                                         HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.issueLoan(request, authService.requireUserId(session));
    }

    @PostMapping("/loans/{loanId}/return")
    public Map<String, Object> returnLoan(@PathVariable long loanId, HttpSession session) {
        authService.requireRole(session, Role.LIBRARIAN);
        return libraryService.returnLoan(loanId);
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> reportSummary(HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        return libraryService.reportSummary();
    }

    @GetMapping("/reports")
    public List<Map<String, Object>> reportRows(@RequestParam(defaultValue = "INVENTORY") String type,
                                                HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        return libraryService.reportRows(type);
    }

    @GetMapping("/staff")
    public List<Map<String, Object>> staff(HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        return libraryService.staff();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        return libraryService.accounts();
    }

    @PostMapping("/staff")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createStaff(@Valid @RequestBody ApiRequests.Staff request, HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        return libraryService.createStaff(request);
    }

    @PutMapping({"/staff/{userId}/status", "/users/{userId}/status"})
    public Map<String, Object> updateStaffStatus(@PathVariable long userId,
                                                  @Valid @RequestBody ApiRequests.StaffStatus request,
                                                  HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        return libraryService.updateStaffStatus(authService.requireUserId(session), userId, request.status());
    }

    @PutMapping({"/staff/{userId}/password", "/users/{userId}/password"})
    public org.springframework.http.ResponseEntity<Void> resetStaffPassword(
            @PathVariable long userId, @Valid @RequestBody ApiRequests.PasswordReset request,
            HttpSession session) {
        authService.requireRole(session, Role.ADMIN);
        authService.resetAccountPassword(userId, request);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}