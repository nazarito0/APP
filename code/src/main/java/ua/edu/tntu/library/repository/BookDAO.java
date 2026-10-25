package ua.edu.tntu.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Repository
public class BookDAO {
    private final JdbcTemplate jdbc;

    public BookDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findActiveBooks(String searchTerm) {
        return jdbc.queryForList("""
                SELECT b.id, b.title, b.author, b.genre, b.isbn, b.publication_year, b.publisher,
                       COUNT(c.id) AS total_copies,
                       COALESCE(SUM(CASE WHEN c.status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS available_copies
                FROM books b
                LEFT JOIN book_copies c ON c.book_id = b.id
                WHERE b.active = TRUE
                  AND (? IS NULL OR LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ?
                       OR LOWER(COALESCE(b.genre, '')) LIKE ? OR LOWER(COALESCE(b.isbn, '')) LIKE ?)
                GROUP BY b.id
                ORDER BY b.title, b.author
                """, searchTerm, searchTerm, searchTerm, searchTerm, searchTerm);
    }

    public Map<String, Object> findActiveBook(long bookId) {
        return jdbc.queryForMap("""
                SELECT b.id, b.title, b.author, b.genre, b.isbn, b.publication_year, b.publisher,
                       COUNT(c.id) AS total_copies,
                       COALESCE(SUM(CASE WHEN c.status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS available_copies
                FROM books b LEFT JOIN book_copies c ON c.book_id = b.id
                WHERE b.id = ? AND b.active = TRUE
                GROUP BY b.id
                """, bookId);
    }

    public long insert(String title, String author, String genre, String isbn,
                       Integer publicationYear, String publisher) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO books (title, author, genre, isbn, publication_year, publisher)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, author);
            statement.setString(3, genre);
            statement.setString(4, isbn);
            if (publicationYear == null) {
                statement.setNull(5, java.sql.Types.SMALLINT);
            } else {
                statement.setInt(5, publicationYear);
            }
            statement.setString(6, publisher);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Не вдалося створити книгу");
        }
        return key.longValue();
    }

    public int update(long bookId, String title, String author, String genre, String isbn,
                      Integer publicationYear, String publisher) {
        return jdbc.update("""
                UPDATE books SET title = ?, author = ?, genre = ?, isbn = ?,
                    publication_year = ?, publisher = ?
                WHERE id = ? AND active = TRUE
                """, title, author, genre, isbn, publicationYear, publisher, bookId);
    }

    public List<Map<String, Object>> findAvailableCopies() {
        return jdbc.queryForList("""
                SELECT c.id, c.inventory_number, b.title, b.author
                FROM book_copies c JOIN books b ON b.id = c.book_id
                WHERE c.status = 'AVAILABLE' AND b.active = TRUE
                ORDER BY b.title, c.inventory_number
                """);
    }

    public Map<String, Object> findCopyForUpdate(long copyId) {
        return jdbc.queryForMap("""
                SELECT c.id, c.status, b.active AS book_active
                FROM book_copies c JOIN books b ON b.id = c.book_id
                WHERE c.id = ? FOR UPDATE
                """, copyId);
    }

    public void updateCopyStatus(long copyId, String status) {
        jdbc.update("UPDATE book_copies SET status = ? WHERE id = ?", status, copyId);
    }

    public List<Map<String, Object>> inventoryReport() {
        return jdbc.queryForList("""
                SELECT b.id, b.title, b.author, b.genre, b.publication_year, b.publisher,
                       COUNT(c.id) AS total_copies,
                       COALESCE(SUM(c.status = 'AVAILABLE'), 0) AS available_copies,
                       COALESCE(SUM(c.status = 'ISSUED'), 0) AS issued_copies
                FROM books b LEFT JOIN book_copies c ON c.book_id = b.id
                WHERE b.active = TRUE
                GROUP BY b.id
                ORDER BY b.title, b.author
                """);
    }

    public void lockActiveBook(long bookId) {
        jdbc.queryForObject("SELECT id FROM books WHERE id = ? AND active = TRUE FOR UPDATE", Long.class, bookId);
    }

    public int countActiveLoans(long bookId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM loans l
                JOIN book_copies c ON c.id = l.copy_id
                WHERE c.book_id = ? AND l.returned_at IS NULL
                """, Integer.class, bookId);
    }

    public int deactivate(long bookId) {
        return jdbc.update("""
                UPDATE books SET active = FALSE, deactivated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND active = TRUE
                """, bookId);
    }

    public void insertCopy(long bookId, String inventoryNumber) {
        jdbc.update("INSERT INTO book_copies (book_id, inventory_number) VALUES (?, ?)",
                bookId, inventoryNumber);
    }

        public int deleteExpiredUnreferencedCopies() {
                return jdbc.update("""
                                DELETE c FROM book_copies c
                                JOIN books b ON b.id = c.book_id
                                WHERE b.active = FALSE
                                    AND b.deactivated_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY
                                    AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.copy_id = c.id)
                                """);
        }

        public int deleteExpiredUnreferencedBooks() {
                return jdbc.update("""
                                DELETE FROM books
                                WHERE active = FALSE
                                    AND deactivated_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY
                                    AND NOT EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = books.id)
                                """);
        }
}