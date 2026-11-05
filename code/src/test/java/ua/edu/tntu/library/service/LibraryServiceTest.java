package ua.edu.tntu.library.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Модульні тести бізнес-логіки LibraryService (Лабораторна робота №5).
 * Ідентифікатори TC-xxx відповідають тест-кейсам зі звіту.
 */
class LibraryServiceTest {
    private BookDAO bookDAO;
    private ReaderDAO readerDAO;
    private LoanDAO loanDAO;
    private ReportDAO reportDAO;
    private UserDAO userDAO;
    private LibraryService service;

    @BeforeEach
    void setUp() {
        bookDAO = mock(BookDAO.class);
        readerDAO = mock(ReaderDAO.class);
        loanDAO = mock(LoanDAO.class);
        reportDAO = mock(ReportDAO.class);
        userDAO = mock(UserDAO.class);
        service = new LibraryService(bookDAO, readerDAO, loanDAO, reportDAO, userDAO);
    }

    private static void assertStatus(HttpStatus expected, ApiException exception) {
        assertEquals(expected, exception.getStatus());
    }

    private static Map<String, Object> activeReader() {
        Map<String, Object> reader = new HashMap<>();
        reader.put("id", 3L);
        reader.put("active", true);
        reader.put("account_status", "ACTIVE");
        return reader;
    }

    private static Map<String, Object> copy(String status, boolean bookActive) {
        Map<String, Object> copy = new HashMap<>();
        copy.put("id", 7L);
        copy.put("status", status);
        copy.put("book_active", bookActive);
        return copy;
    }

    // ---------- FR-004, FR-005: перегляд каталогу та пошук ----------

    @Test
    @DisplayName("TC-001 Порожній пошук (null) повертає весь каталог")
    void nullSearchReturnsWholeCatalog() {
        List<Map<String, Object>> catalog = List.of(Map.of("id", 1L));
        when(bookDAO.findActiveBooks(null)).thenReturn(catalog);

        assertSame(catalog, service.books(null));
        verify(bookDAO).findActiveBooks(null);
    }

    @ParameterizedTest(name = "TC-002 Пошук з пробілів [{0}] трактується як порожній")
    @ValueSource(strings = {"", " ", "     "})
    void blankSearchIsTreatedAsEmpty(String search) {
        service.books(search);

        verify(bookDAO).findActiveBooks(null);
    }

    @ParameterizedTest(name = "TC-003 Запит [{0}] коротший за 3 символи відхиляється")
    @ValueSource(strings = {"a", "ab", " ab ", "  x  "})
    void searchShorterThanThreeCharactersIsRejected(String search) {
        ApiException exception = assertThrows(ApiException.class, () -> service.books(search));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
        verifyNoInteractions(bookDAO);
    }

    @ParameterizedTest(name = "TC-004 Запит [{0}] -> шаблон LIKE [{1}]")
    @CsvSource({
            "abc, %abc%",
            "Kobzar, %kobzar%",
            "'  Orwell  ', %orwell%",
            "ШЕВЧЕНКО, %шевченко%",
            "978-966, %978-966%"
    })
    void validSearchIsTrimmedLowercasedAndWrapped(String search, String expectedTerm) {
        service.books(search);

        verify(bookDAO).findActiveBooks(expectedTerm);
    }

    // ---------- FR-001, FR-002: додавання та редагування книжки ----------

    @Test
    @DisplayName("TC-005 Додавання книжки: обрізання пробілів і створення всіх примірників")
    void addBookTrimsFieldsAndCreatesEveryCopy() {
        when(bookDAO.insert("Кобзар", "Т. Шевченко", "Поезія", "978-1", 1840, "Видавець"))
                .thenReturn(10L);
        Map<String, Object> saved = Map.of("id", 10L, "title", "Кобзар");
        when(bookDAO.findActiveBook(10L)).thenReturn(saved);
        ApiRequests.BookCreate request = new ApiRequests.BookCreate("  Кобзар ", " Т. Шевченко ", "Поезія",
                "978-1", 1840, "Видавець", List.of(" INV-1 ", "INV-2"));

        Map<String, Object> result = service.addBook(request);

        assertSame(saved, result);
        verify(bookDAO).insertCopy(10L, "INV-1");
        verify(bookDAO).insertCopy(10L, "INV-2");
        verify(bookDAO, times(2)).insertCopy(eq(10L), anyString());
    }

    @Test
    @DisplayName("TC-006 Перегляд неіснуючої книжки повертає 404")
    void missingBookReturnsNotFound() {
        when(bookDAO.findActiveBook(99L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class, () -> service.bookById(99L));

        assertStatus(HttpStatus.NOT_FOUND, exception);
    }

    @Test
    @DisplayName("TC-007 Редагування існуючої книжки зберігає нові значення")
    void updateExistingBook() {
        when(bookDAO.update(5L, "Нова назва", "Автор", null, null, 2001, null)).thenReturn(1);
        Map<String, Object> updated = Map.of("id", 5L, "title", "Нова назва");
        when(bookDAO.findActiveBook(5L)).thenReturn(updated);

        Map<String, Object> result = service.updateBook(5L,
                new ApiRequests.BookUpdate(" Нова назва ", "Автор", null, null, 2001, null));

        assertSame(updated, result);
    }

    @Test
    @DisplayName("TC-008 Редагування неіснуючої/деактивованої книжки повертає 404")
    void updateMissingBookReturnsNotFound() {
        when(bookDAO.update(eq(5L), anyString(), anyString(), any(), any(), any(), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> service.updateBook(5L,
                new ApiRequests.BookUpdate("Назва", "Автор", null, null, null, null)));

        assertStatus(HttpStatus.NOT_FOUND, exception);
        verify(bookDAO, never()).findActiveBook(anyLong());
    }

    // ---------- FR-003: вилучення книжки ----------

    @Test
    @DisplayName("TC-009 Вилучення книжки без активних видач")
    void deactivateBookWithoutActiveLoans() {
        when(bookDAO.countActiveLoans(5L)).thenReturn(0);
        when(bookDAO.deactivate(5L)).thenReturn(1);

        service.deactivateBook(5L);

        verify(bookDAO).lockActiveBook(5L);
        verify(bookDAO).deactivate(5L);
    }

    @Test
    @DisplayName("TC-010 Не можна вилучити книжку з 1 активною видачею (межа 0/1)")
    void bookWithActiveLoanCannotBeDeactivated() {
        when(bookDAO.countActiveLoans(5L)).thenReturn(1);

        ApiException exception = assertThrows(ApiException.class, () -> service.deactivateBook(5L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(bookDAO, never()).deactivate(anyLong());
    }

    @Test
    @DisplayName("TC-011 Вилучення неіснуючої книжки повертає 404")
    void deactivateMissingBook() {
        doThrow(new EmptyResultDataAccessException(1)).when(bookDAO).lockActiveBook(42L);

        ApiException exception = assertThrows(ApiException.class, () -> service.deactivateBook(42L));

        assertStatus(HttpStatus.NOT_FOUND, exception);
        verify(bookDAO, never()).deactivate(anyLong());
    }

    // ---------- FR-007: редагування та деактивація читача ----------

    @Test
    @DisplayName("TC-012 Редагування читача з унікальними контактами")
    void updateReaderWithUniqueContacts() {
        when(readerDAO.update(3L, "Іван Петренко", "ivan@example.com", "+380501112233")).thenReturn(1);
        Map<String, Object> reader = Map.of("id", 3L);
        when(readerDAO.findById(3L)).thenReturn(reader);

        Map<String, Object> result = service.updateReader(3L,
                new ApiRequests.ReaderUpdate(" Іван Петренко ", " ivan@example.com ", "+380501112233"));

        assertSame(reader, result);
        verify(readerDAO).emailExists("ivan@example.com", 3L);
        verify(readerDAO).phoneExists("+380501112233", 3L);
    }

    @Test
    @DisplayName("TC-013 Порожні e-mail і телефон зберігаються як NULL без перевірки унікальності")
    void blankContactsAreStoredAsNull() {
        when(readerDAO.update(3L, "Іван", null, null)).thenReturn(1);

        service.updateReader(3L, new ApiRequests.ReaderUpdate("Іван", "  ", ""));

        verify(readerDAO, never()).emailExists(anyString(), anyLong());
        verify(readerDAO, never()).phoneExists(anyString(), anyLong());
        verify(readerDAO).update(3L, "Іван", null, null);
    }

    @Test
    @DisplayName("TC-014 Дубльований e-mail читача відхиляється (409)")
    void duplicateReaderEmailIsRejected() {
        when(readerDAO.emailExists("dup@example.com", 3L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> service.updateReader(3L,
                new ApiRequests.ReaderUpdate("Іван", "dup@example.com", null)));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(readerDAO, never()).update(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("TC-015 Дубльований телефон читача відхиляється (409)")
    void duplicateReaderPhoneIsRejected() {
        when(readerDAO.phoneExists("+380501112233", 3L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> service.updateReader(3L,
                new ApiRequests.ReaderUpdate("Іван", null, "+380501112233")));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(readerDAO, never()).update(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("TC-016 Редагування неіснуючого читача повертає 404")
    void updateMissingReader() {
        when(readerDAO.update(anyLong(), anyString(), any(), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> service.updateReader(77L,
                new ApiRequests.ReaderUpdate("Іван", null, null)));

        assertStatus(HttpStatus.NOT_FOUND, exception);
    }

    @Test
    @DisplayName("TC-017 Деактивація читача без активних видач деактивує і формуляр, і акаунт")
    void deactivateReaderWithoutLoans() {
        when(loanDAO.countActiveForReader(3L)).thenReturn(0);

        service.deactivateReader(3L);

        verify(readerDAO).deactivate(3L);
        verify(userDAO).deactivateByReaderId(3L);
    }

    @Test
    @DisplayName("TC-018 Не можна деактивувати читача з активною видачею")
    void readerWithActiveLoanCannotBeDeactivated() {
        when(loanDAO.countActiveForReader(3L)).thenReturn(1);

        ApiException exception = assertThrows(ApiException.class, () -> service.deactivateReader(3L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(readerDAO, never()).deactivate(anyLong());
        verify(userDAO, never()).deactivateByReaderId(anyLong());
    }

    @Test
    @DisplayName("TC-019 Деактивація неіснуючого читача повертає 404")
    void deactivateMissingReader() {
        doThrow(new EmptyResultDataAccessException(1)).when(readerDAO).lockActive(55L);

        ApiException exception = assertThrows(ApiException.class, () -> service.deactivateReader(55L));

        assertStatus(HttpStatus.NOT_FOUND, exception);
        verifyNoInteractions(loanDAO);
    }

    // ---------- FR-008: видача книжки ----------

    @Test
    @DisplayName("TC-020 Успішна видача: примірник ISSUED, строк повернення = сьогодні + 14 днів")
    void issueAvailableCopyToActiveReader() {
        when(readerDAO.findForUpdate(3L)).thenReturn(activeReader());
        when(bookDAO.findCopyForUpdate(7L)).thenReturn(copy("AVAILABLE", true));
        when(loanDAO.insert(eq(7L), eq(3L), eq(2L), any())).thenReturn(100L);
        Map<String, Object> issued = Map.of("id", 100L);
        when(loanDAO.findIssued(100L)).thenReturn(issued);

        Map<String, Object> result = service.issueLoan(new ApiRequests.IssueLoan(3L, 7L), 2L);

        assertSame(issued, result);
        verify(bookDAO).updateCopyStatus(7L, "ISSUED");
        verify(loanDAO).insert(7L, 3L, 2L, LocalDate.now().plusDays(14));
    }

    @Test
    @DisplayName("TC-021 Видача неіснуючому читачу повертає 404")
    void issueToMissingReader() {
        when(readerDAO.findForUpdate(3L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.issueLoan(new ApiRequests.IssueLoan(3L, 7L), 2L));

        assertStatus(HttpStatus.NOT_FOUND, exception);
        verifyNoInteractions(bookDAO, loanDAO);
    }

    @ParameterizedTest(name = "TC-022 Формуляр active={0}, акаунт={1} -> видача заборонена")
    @CsvSource({"false, ACTIVE", "true, DEACTIVATED", "false, DEACTIVATED"})
    void issueToInactiveReaderIsRejected(boolean active, String accountStatus) {
        Map<String, Object> reader = activeReader();
        reader.put("active", active);
        reader.put("account_status", accountStatus);
        when(readerDAO.findForUpdate(3L)).thenReturn(reader);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.issueLoan(new ApiRequests.IssueLoan(3L, 7L), 2L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verifyNoInteractions(bookDAO, loanDAO);
    }

    @Test
    @DisplayName("TC-023 Видача неіснуючого примірника повертає 404")
    void issueMissingCopy() {
        when(readerDAO.findForUpdate(3L)).thenReturn(activeReader());
        when(bookDAO.findCopyForUpdate(7L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.issueLoan(new ApiRequests.IssueLoan(3L, 7L), 2L));

        assertStatus(HttpStatus.NOT_FOUND, exception);
        verifyNoInteractions(loanDAO);
    }

    @Test
    @DisplayName("TC-024 Видача примірника вилученої книжки заборонена")
    void issueCopyOfDeactivatedBook() {
        when(readerDAO.findForUpdate(3L)).thenReturn(activeReader());
        when(bookDAO.findCopyForUpdate(7L)).thenReturn(copy("AVAILABLE", false));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.issueLoan(new ApiRequests.IssueLoan(3L, 7L), 2L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(bookDAO, never()).updateCopyStatus(anyLong(), anyString());
        verifyNoInteractions(loanDAO);
    }

    @Test
    @DisplayName("TC-025 Повторна видача вже виданого примірника заборонена")
    void issueAlreadyIssuedCopy() {
        when(readerDAO.findForUpdate(3L)).thenReturn(activeReader());
        when(bookDAO.findCopyForUpdate(7L)).thenReturn(copy("ISSUED", true));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.issueLoan(new ApiRequests.IssueLoan(3L, 7L), 2L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(bookDAO, never()).updateCopyStatus(anyLong(), anyString());
        verifyNoInteractions(loanDAO);
    }

    // ---------- FR-009: повернення книжки ----------

    @Test
    @DisplayName("TC-026 Успішне повернення: примірник знову AVAILABLE")
    void returnIssuedLoan() {
        Map<String, Object> loan = new HashMap<>();
        loan.put("id", 100L);
        loan.put("copy_id", 7L);
        loan.put("returned_at", null);
        when(loanDAO.findByIdForUpdate(100L)).thenReturn(loan);
        when(loanDAO.markReturned(100L)).thenReturn(1);

        Map<String, Object> result = service.returnLoan(100L);

        assertEquals(Boolean.TRUE, result.get("returned"));
        assertEquals(7L, result.get("copyId"));
        verify(bookDAO).updateCopyStatus(7L, "AVAILABLE");
    }

    @Test
    @DisplayName("TC-027 Повторне повернення відхиляється (409)")
    void returnAlreadyReturnedLoan() {
        Map<String, Object> loan = new HashMap<>();
        loan.put("copy_id", 7L);
        loan.put("returned_at", java.sql.Timestamp.valueOf("2026-01-10 10:00:00"));
        when(loanDAO.findByIdForUpdate(100L)).thenReturn(loan);

        ApiException exception = assertThrows(ApiException.class, () -> service.returnLoan(100L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(loanDAO, never()).markReturned(anyLong());
        verifyNoInteractions(bookDAO);
    }

    @Test
    @DisplayName("TC-028 Конкурентне повернення (markReturned = 0) не змінює статус примірника")
    void concurrentReturnDoesNotChangeCopy() {
        Map<String, Object> loan = new HashMap<>();
        loan.put("copy_id", 7L);
        loan.put("returned_at", null);
        when(loanDAO.findByIdForUpdate(100L)).thenReturn(loan);
        when(loanDAO.markReturned(100L)).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> service.returnLoan(100L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verifyNoInteractions(bookDAO);
    }

    @Test
    @DisplayName("TC-029 Повернення неіснуючої видачі повертає 404")
    void returnMissingLoan() {
        when(loanDAO.findByIdForUpdate(404L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class, () -> service.returnLoan(404L));

        assertStatus(HttpStatus.NOT_FOUND, exception);
    }

    // ---------- FR-010, FR-011: історія та звіти ----------

    @Test
    @DisplayName("TC-030 Історія видач фільтрується за читачем")
    void loanHistoryForReader() {
        List<Map<String, Object>> history = List.of(Map.of("id", 1L));
        when(loanDAO.findAll(3L)).thenReturn(history);

        assertSame(history, service.loansForReader(3L));
    }

    @ParameterizedTest(name = "TC-031 Тип звіту [{0}] приймається без урахування регістру")
    @ValueSource(strings = {"INVENTORY", "inventory", " Inventory "})
    void inventoryReportType(String type) {
        service.reportRows(type);

        verify(bookDAO).inventoryReport();
    }

    @Test
    @DisplayName("TC-032 Тип звіту не вказано (null) -> звіт по фонду")
    void nullReportTypeDefaultsToInventory() {
        service.reportRows(null);

        verify(bookDAO).inventoryReport();
    }

    @Test
    @DisplayName("TC-033 Звіти по читачах і видачах")
    void readersAndLoansReports() {
        service.reportRows("readers");
        service.reportRows("LOANS");

        verify(readerDAO).reportRows();
        verify(loanDAO).findAll(null);
    }

    @ParameterizedTest(name = "TC-034 Неприпустимий тип звіту [{0}] -> 400")
    @ValueSource(strings = {"", "   ", "SALES", "INVENTORY1"})
    void invalidReportTypes(String type) {
        ApiException exception = assertThrows(ApiException.class, () -> service.reportRows(type));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
        verifyNoInteractions(bookDAO, readerDAO, loanDAO);
    }

    // ---------- FR-013: керування обліковими записами персоналу ----------

    @Test
    @DisplayName("TC-035 Через форму персоналу не можна створити акаунт READER")
    void staffFormCannotCreateReader() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.createStaff(new ApiRequests.Staff("reader1", "password123", Role.READER)));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
        verifyNoInteractions(userDAO);
    }

    @ParameterizedTest(name = "TC-036 Створення працівника з роллю {0}")
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void createStaffMember(String roleName) {
        Role role = Role.valueOf(roleName);
        when(userDAO.insert("worker", "password123", role)).thenReturn(12L);

        Map<String, Object> result = service.createStaff(new ApiRequests.Staff("worker", "password123", role));

        assertEquals(12L, result.get("id"));
        assertEquals(roleName, result.get("role"));
    }

    @Test
    @DisplayName("TC-037 Зміна статусу неіснуючого акаунта повертає 404")
    void updateStatusOfMissingAccount() {
        when(userDAO.findById(9L)).thenThrow(new EmptyResultDataAccessException(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateStaffStatus(1L, 9L, UserStatus.DEACTIVATED));

        assertStatus(HttpStatus.NOT_FOUND, exception);
    }

    @Test
    @DisplayName("TC-038 Встановлення того самого статусу не змінює дані")
    void sameStatusIsNoOp() {
        Map<String, Object> account = Map.of("id", 5L, "role", "LIBRARIAN", "status", "ACTIVE");
        when(userDAO.findById(5L)).thenReturn(account);

        Map<String, Object> result = service.updateStaffStatus(1L, 5L, UserStatus.ACTIVE);

        assertSame(account, result);
        verify(userDAO, never()).setStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("TC-039 Деактивація адміністратора дозволена, якщо активних адмінів 2")
    void adminCanBeDeactivatedWhenAnotherAdminExists() {
        when(userDAO.findById(4L)).thenReturn(Map.of("id", 4L, "role", "ADMIN", "status", "ACTIVE"));
        when(userDAO.lockAndCountActiveAdmins()).thenReturn(2);

        service.updateStaffStatus(1L, 4L, UserStatus.DEACTIVATED);

        verify(userDAO).setStatus(4L, "DEACTIVATED");
    }

    @Test
    @DisplayName("TC-040 Не можна деактивувати акаунт читача з активними видачами")
    void readerAccountWithLoansCannotBeDeactivated() {
        when(userDAO.findById(8L)).thenReturn(Map.of("id", 8L, "role", "READER", "status", "ACTIVE"));
        when(readerDAO.findIdByUserId(8L)).thenReturn(3L);
        when(loanDAO.countActiveForReader(3L)).thenReturn(2);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateStaffStatus(1L, 8L, UserStatus.DEACTIVATED));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(userDAO, never()).setStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("TC-041 Активація акаунта читача активує і його формуляр")
    void activatingReaderAccountActivatesReaderCard() {
        when(userDAO.findById(8L)).thenReturn(Map.of("id", 8L, "role", "READER", "status", "DEACTIVATED"));

        service.updateStaffStatus(1L, 8L, UserStatus.ACTIVE);

        verify(userDAO).setStatus(8L, "ACTIVE");
        verify(readerDAO).setActiveByUserId(8L, true);
    }
}
