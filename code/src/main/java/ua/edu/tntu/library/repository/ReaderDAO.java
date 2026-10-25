package ua.edu.tntu.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ReaderDAO {
    private final JdbcTemplate jdbc;

    public ReaderDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList("""
                SELECT r.id, r.card_number, r.full_name, r.email, r.phone, r.active,
                       u.login, u.status AS account_status
                FROM readers r JOIN users u ON u.id = r.user_id
                ORDER BY r.full_name
                """);
    }

    public Map<String, Object> findById(long readerId) {
        return jdbc.queryForMap("""
                SELECT r.id, r.card_number, r.full_name, r.email, r.phone,
                       r.active, u.login, u.status AS account_status
                FROM readers r JOIN users u ON u.id = r.user_id
                WHERE r.id = ?
                """, readerId);
    }

    public Map<String, Object> findForUpdate(long readerId) {
        return jdbc.queryForMap("""
                SELECT r.id, r.active, u.status AS account_status
                FROM readers r JOIN users u ON u.id = r.user_id
                WHERE r.id = ? FOR UPDATE
                """, readerId);
    }

    public Long findIdByUserId(long userId) {
        return jdbc.query("SELECT id FROM readers WHERE user_id = ?", result ->
                result.next() ? result.getLong("id") : null, userId);
    }

    public String findCardNumber(long readerId) {
        return jdbc.queryForObject("SELECT card_number FROM readers WHERE id = ?", String.class, readerId);
    }

    public void lockActive(long readerId) {
        jdbc.queryForObject("SELECT id FROM readers WHERE id = ? AND active = TRUE FOR UPDATE",
                Long.class, readerId);
    }

    public boolean emailExists(String email, long exceptReaderId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM readers
                WHERE active = TRUE AND LOWER(email) = LOWER(?) AND id <> ?
                """, Integer.class, email, exceptReaderId) > 0;
    }

    public boolean phoneExists(String phone, long exceptReaderId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM readers WHERE active = TRUE AND phone = ? AND id <> ?
                """, Integer.class, phone, exceptReaderId) > 0;
    }

    public void insert(long userId, String cardNumber, String fullName, String email, String phone) {
        jdbc.update("""
                INSERT INTO readers (user_id, card_number, full_name, email, phone)
                VALUES (?, ?, ?, ?, ?)
                """, userId, cardNumber, fullName, email, phone);
    }

    public int update(long readerId, String fullName, String email, String phone) {
        return jdbc.update("""
                UPDATE readers SET full_name = ?, email = ?, phone = ?
                WHERE id = ? AND active = TRUE
                """, fullName, email, phone, readerId);
    }

    public int deactivate(long readerId) {
        return jdbc.update("""
            UPDATE readers SET active = FALSE, deactivated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND active = TRUE
            """, readerId);
    }

    public void setActiveByUserId(long userId, boolean active) {
        jdbc.update("""
                UPDATE readers
                SET active = ?,
                    deactivated_at = CASE WHEN ? THEN NULL ELSE COALESCE(deactivated_at, CURRENT_TIMESTAMP) END
                WHERE user_id = ?
                """, active, active, userId);
    }

    public List<Map<String, Object>> reportRows() {
        return jdbc.queryForList("""
                SELECT r.id, r.card_number, r.full_name, r.email, r.phone, r.active,
                       u.login, u.status AS account_status,
                       COUNT(l.id) AS total_loans,
                       COALESCE(SUM(l.returned_at IS NULL), 0) AS active_loans
                FROM readers r
                JOIN users u ON u.id = r.user_id
                LEFT JOIN loans l ON l.reader_id = r.id
                GROUP BY r.id
                ORDER BY r.full_name
                """);
    }

        public int deleteExpiredUnreferencedReaders() {
                return jdbc.update("""
                                DELETE FROM readers
                                WHERE active = FALSE
                                    AND deactivated_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY
                                    AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.reader_id = readers.id)
                                """);
        }
}