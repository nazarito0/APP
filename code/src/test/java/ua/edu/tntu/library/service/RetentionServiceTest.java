package ua.edu.tntu.library.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ua.edu.tntu.library.repository.BookDAO;
import ua.edu.tntu.library.repository.LoanDAO;
import ua.edu.tntu.library.repository.ReaderDAO;
import ua.edu.tntu.library.repository.UserDAO;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * Тест очищення застарілих даних (політика зберігання).
 */
class RetentionServiceTest {

    @Test
    @DisplayName("TC-057 Очищення виконується у порядку, що не порушує зовнішніх ключів")
    void purgeDeletesChildRecordsBeforeParents() {
        LoanDAO loanDAO = mock(LoanDAO.class);
        BookDAO bookDAO = mock(BookDAO.class);
        ReaderDAO readerDAO = mock(ReaderDAO.class);
        UserDAO userDAO = mock(UserDAO.class);
        RetentionService service = new RetentionService(loanDAO, bookDAO, readerDAO, userDAO);

        service.purgeExpiredData();

        InOrder order = inOrder(loanDAO, bookDAO, readerDAO, userDAO);
        order.verify(loanDAO).deleteReturnedOlderThanRetention();
        order.verify(bookDAO).deleteExpiredUnreferencedCopies();
        order.verify(bookDAO).deleteExpiredUnreferencedBooks();
        order.verify(readerDAO).deleteExpiredUnreferencedReaders();
        order.verify(userDAO).deleteExpiredUnreferencedAccounts();
    }
}
