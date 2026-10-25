package ua.edu.tntu.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class ReportDAO {
    private final JdbcTemplate jdbc;

    public ReportDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary() {
        return jdbc.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM books WHERE active = TRUE) AS active_titles,
                    (SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE') AS available_copies,
                    (SELECT COUNT(*) FROM loans WHERE returned_at IS NULL) AS active_loans,
                    (SELECT COUNT(*) FROM loans WHERE returned_at IS NULL AND due_date < CURRENT_DATE) AS overdue_loans,
                    (SELECT COUNT(*) FROM readers WHERE active = TRUE) AS active_readers
                """);
    }
}