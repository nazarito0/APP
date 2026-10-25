package ua.edu.tntu.library.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.tntu.library.dto.ApiRequests;
import ua.edu.tntu.library.model.Role;
import ua.edu.tntu.library.model.UserStatus;
import ua.edu.tntu.library.repository.BookDAO;
import ua.edu.tntu.library.repository.LoanDAO;
import ua.edu.tntu.library.repository.ReaderDAO;
import ua.edu.tntu.library.repository.ReportDAO;
import ua.edu.tntu.library.repository.UserDAO;
import ua.edu.tntu.library.web.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class LibraryService {
    private final BookDAO bookDAO;
    private final ReaderDAO readerDAO;
    private final LoanDAO loanDAO;
    private final ReportDAO reportDAO;
    private final UserDAO userDAO;

    public LibraryService(BookDAO bookDAO, ReaderDAO readerDAO, LoanDAO loanDAO,
                          ReportDAO reportDAO, UserDAO userDAO) {
        this.bookDAO = bookDAO;
        this.readerDAO = readerDAO;
        this.loanDAO = loanDAO;
        this.reportDAO = reportDAO;
        this.userDAO = userDAO;
    }

    public List<Map<String, Object>> books(String search) {
        String query = search == null ? "" : search.trim();
        if (!query.isEmpty() && query.length() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Для пошуку введіть щонайменше 3 символи");
        }
         String term = query.isEmpty() ? null : "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
         return bookDAO.findActiveBooks(term);
    }

    @Transactional
    public Map<String, Object> addBook(ApiRequests.BookCreate request) {
        long bookId = bookDAO.insert(request.title().trim(), request.author().trim(), request.genre(),
                request.isbn(), request.publicationYear(), request.publisher());
        for (String inventoryNumber : request.inventoryNumbers()) {
            bookDAO.insertCopy(bookId, inventoryNumber.trim());
        }
        return bookById(bookId);
    }

    public Map<String, Object> bookById(long bookId) {
        try {
            return bookDAO.findActiveBook(bookId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Книгу не знайдено");
        }
    }

    @Transactional
    public Map<String, Object> updateBook(long bookId, ApiRequests.BookUpdate request) {
        int updated = bookDAO.update(bookId, request.title().trim(), request.author().trim(), request.genre(),
                request.isbn(), request.publicationYear(), request.publisher());
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Книгу не знайдено або деактивовано");
        }
        return bookById(bookId);
    }

    public List<Map<String, Object>> readers() {
        return readerDAO.findAll();
    }

    @Transactional
    public Map<String, Object> updateReader(long readerId, ApiRequests.ReaderUpdate request) {
        String email = emptyToNull(request.email());
        String phone = emptyToNull(request.phone());
        ensureReaderContactAvailable(readerId, email, phone);
        int updated = readerDAO.update(readerId, request.fullName().trim(), email, phone);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Читача не знайдено або деактивовано");
        }
        return readerDAO.findById(readerId);
    }

    public List<Map<String, Object>> availableCopies() {
        return bookDAO.findAvailableCopies();
    }

    @Transactional
    public void deactivateBook(long bookId) {
        try {
            bookDAO.lockActiveBook(bookId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Книгу не знайдено або вже деактивовано");
        }
        int activeLoans = bookDAO.countActiveLoans(bookId);
        if (activeLoans > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Не можна вилучити книгу з активними видачами");
        }
        int updated = bookDAO.deactivate(bookId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Книгу не знайдено або вже деактивовано");
        }
    }

    @Transactional
    public void deactivateReader(long readerId) {
        try {
            readerDAO.lockActive(readerId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Читача не знайдено або вже деактивовано");
        }
        int activeLoans = loanDAO.countActiveForReader(readerId);
        if (activeLoans > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Не можна деактивувати читача з активними видачами");
        }
        readerDAO.deactivate(readerId);
        userDAO.deactivateByReaderId(readerId);
    }

    public List<Map<String, Object>> staff() {
        return userDAO.findStaff();
    }

    public List<Map<String, Object>> accounts() {
        return userDAO.findAllAccounts();
    }

    public List<Map<String, Object>> loans(Long readerId) {
        return loanDAO.findAll(readerId);
    }

    public List<Map<String, Object>> loansForReader(long readerId) {
        return loans(readerId);
    }

    public List<Map<String, Object>> reportRows(String requestedType) {
        String type = requestedType == null ? "INVENTORY" : requestedType.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (type) {
            case "INVENTORY" -> bookDAO.inventoryReport();
            case "READERS" -> readerDAO.reportRows();
            case "LOANS" -> loanDAO.findAll(null);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Невідомий тип звіту");
        };
    }

    @Transactional
    public Map<String, Object> issueLoan(ApiRequests.IssueLoan request, long librarianId) {
        Map<String, Object> reader;
        try {
            reader = readerDAO.findForUpdate(request.readerId());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Читача не знайдено");
        }
        if (!Boolean.TRUE.equals(reader.get("active")) || !"ACTIVE".equals(reader.get("account_status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Читацький формуляр неактивний");
        }

        Map<String, Object> copy;
        try {
            copy = bookDAO.findCopyForUpdate(request.copyId());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Примірник не знайдено");
        }
        if (!Boolean.TRUE.equals(copy.get("book_active"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Книгу вилучено з каталогу");
        }
        if (!"AVAILABLE".equals(copy.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Цей примірник уже видано");
        }

        bookDAO.updateCopyStatus(request.copyId(), "ISSUED");
        long loanId = loanDAO.insert(request.copyId(), request.readerId(), librarianId,
            LocalDate.now().plusDays(14));
        return loanDAO.findIssued(loanId);
    }

    @Transactional
    public Map<String, Object> returnLoan(long loanId) {
        Map<String, Object> loan;
        try {
            loan = loanDAO.findByIdForUpdate(loanId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Видачу не знайдено");
        }
        if (loan.get("returned_at") != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Цю книгу вже повернуто");
        }
        long copyId = ((Number) loan.get("copy_id")).longValue();
        if (loanDAO.markReturned(loanId) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Цю книгу вже повернуто");
        }
        bookDAO.updateCopyStatus(copyId, "AVAILABLE");
        return Map.of("id", loanId, "copyId", copyId, "returned", true);
    }

    public Map<String, Object> reportSummary() {
        return reportDAO.summary();
    }

    @Transactional
    public Map<String, Object> createStaff(ApiRequests.Staff request) {
        if (request.role() == ua.edu.tntu.library.model.Role.READER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Тут можна створити лише працівника");
        }
        Long userId = userDAO.insert(request.login(), request.password(), request.role());
        return Map.of("id", userId, "login", request.login(), "role", request.role().name());
    }

    @Transactional
    public Map<String, Object> updateStaffStatus(long requesterId, long staffId, UserStatus requestedStatus) {
        Map<String, Object> account;
        try {
            account = userDAO.findById(staffId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Обліковий запис не знайдено");
        }
        String currentStatus = (String) account.get("status");
        if (requestedStatus.name().equals(currentStatus)) {
            return account;
        }
        if (requestedStatus == UserStatus.DEACTIVATED && requesterId == staffId) {
            throw new ApiException(HttpStatus.CONFLICT, "Не можна деактивувати власний обліковий запис");
        }
        if (requestedStatus == UserStatus.DEACTIVATED && Role.READER.name().equals(account.get("role"))) {
            Long readerId = readerDAO.findIdByUserId(staffId);
            if (readerId == null) {
                throw new ApiException(HttpStatus.CONFLICT, "Для читацького акаунта не знайдено формуляр");
            }
            if (loanDAO.countActiveForReader(readerId) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Не можна деактивувати читача з активними видачами");
            }
        }
        if (requestedStatus == UserStatus.DEACTIVATED
                && Role.ADMIN.name().equals(account.get("role"))
                && UserStatus.ACTIVE.name().equals(account.get("status"))
                && userDAO.lockAndCountActiveAdmins() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "У системі має залишитися хоча б один активний адміністратор");
        }
        userDAO.setStatus(staffId, requestedStatus.name());
        if (Role.READER.name().equals(account.get("role"))) {
            readerDAO.setActiveByUserId(staffId, requestedStatus == UserStatus.ACTIVE);
        }
        return userDAO.findById(staffId);
    }

    public void ensureReaderContactAvailable(long exceptReaderId, String email, String phone) {
        if (email != null && readerDAO.emailExists(email, exceptReaderId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Читач із такою електронною поштою вже існує");
        }
        if (phone != null && readerDAO.phoneExists(phone, exceptReaderId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Читач із таким телефоном уже існує");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}