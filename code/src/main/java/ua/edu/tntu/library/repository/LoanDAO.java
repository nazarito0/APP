package ua.edu.tntu.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class LoanDAO {
    private final JdbcTemplate jdbc;

    public LoanDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countActiveForBook(long bookId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM loans l
                JOIN book_copies c ON c.id = l.copy_id
                WHERE c.book_id = ? AND l.returned_at IS NULL
                """, Integer.class, bookId);
    }

    public int countActiveForReader(long readerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM loans WHERE reader_id = ? AND returned_at IS NULL
                """, Integer.class, readerId);
    }

    public List<Map<String, Object>> findAll(Long readerId) {
        return jdbc.queryForList("""
                SELECT l.id, l.reader_id, r.full_name AS reader_name, r.card_number,
                       b.title, b.author, c.inventory_number, l.issued_at, l.due_date,
                       l.returned_at, librarian.login AS librarian_login,
                       CASE WHEN l.returned_at IS NULL AND l.due_date < CURRENT_DATE THEN TRUE ELSE FALSE END AS overdue
                FROM loans l
                JOIN readers r ON r.id = l.reader_id
                JOIN book_copies c ON c.id = l.copy_id
                JOIN books b ON b.id = c.book_id
                JOIN users librarian ON librarian.id = l.librarian_id
                WHERE (? IS NULL OR l.reader_id = ?)
                ORDER BY l.issued_at DESC
                """, readerId, readerId);
    }

    public long insert(long copyId, long readerId, long librarianId, LocalDate dueDate) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO loans (copy_id, reader_id, librarian_id, due_date) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, copyId);
            statement.setLong(2, readerId);
            statement.setLong(3, librarianId);
            statement.setDate(4, Date.valueOf(dueDate));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Не вдалося створити запис видачі");
        }
        return key.longValue();
    }

    public Map<String, Object> findByIdForUpdate(long loanId) {
        return jdbc.queryForMap("SELECT id, copy_id, returned_at FROM loans WHERE id = ? FOR UPDATE", loanId);
    }

    public Map<String, Object> findIssued(long loanId) {
        return jdbc.queryForMap("""
                SELECT id, reader_id, copy_id, issued_at, due_date
                FROM loans WHERE id = ?
                """, loanId);
    }

    public int markReturned(long loanId) {
        return jdbc.update("UPDATE loans SET returned_at = CURRENT_TIMESTAMP WHERE id = ? AND returned_at IS NULL",
                loanId);
    }

        public int deleteReturnedOlderThanRetention() {
                return jdbc.update("""
                                DELETE FROM loans
                                WHERE returned_at IS NOT NULL
                                    AND returned_at < CURRENT_TIMESTAMP - INTERVAL 3 YEAR
                                """);
        }
}