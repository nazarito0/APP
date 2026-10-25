package ua.edu.tntu.library.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.tntu.library.repository.BookDAO;
import ua.edu.tntu.library.repository.LoanDAO;
import ua.edu.tntu.library.repository.ReaderDAO;
import ua.edu.tntu.library.repository.UserDAO;

@Service
public class RetentionService {
    private final LoanDAO loanDAO;
    private final BookDAO bookDAO;
    private final ReaderDAO readerDAO;
    private final UserDAO userDAO;

    public RetentionService(LoanDAO loanDAO, BookDAO bookDAO, ReaderDAO readerDAO, UserDAO userDAO) {
        this.loanDAO = loanDAO;
        this.bookDAO = bookDAO;
        this.readerDAO = readerDAO;
        this.userDAO = userDAO;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredData() {
        loanDAO.deleteReturnedOlderThanRetention();
        bookDAO.deleteExpiredUnreferencedCopies();
        bookDAO.deleteExpiredUnreferencedBooks();
        readerDAO.deleteExpiredUnreferencedReaders();
        userDAO.deleteExpiredUnreferencedAccounts();
    }
}