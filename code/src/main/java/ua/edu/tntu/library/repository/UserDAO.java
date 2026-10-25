package ua.edu.tntu.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua.edu.tntu.library.model.Role;

import java.sql.Statement;
import java.util.Map;
import java.util.List;

@Repository
public class UserDAO {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public UserDAO(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> findForLogin(String login) {
        return jdbc.queryForMap("SELECT id, login, password_hash, role, status FROM users WHERE login = ?", login);
    }

    public String findStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    public String findRole(long userId) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
    }

    public String findPasswordHash(long userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    public void updatePassword(long userId, String rawPassword) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(rawPassword), userId);
    }

    public Map<String, Object> findProfile(long userId) {
        return jdbc.queryForMap("SELECT id, login, role FROM users WHERE id = ?", userId);
    }

    public void updateLastLogin(long userId) {
        jdbc.update("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
    }

    public long insert(String login, String rawPassword, Role role) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO users (login, password_hash, role) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, login);
            statement.setString(2, passwordEncoder.encode(rawPassword));
            statement.setString(3, role.name());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Не вдалося створити обліковий запис");
        }
        return key.longValue();
    }

    public Integer countAdmins() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class);
    }

    public Integer countActiveAdmins() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'", Integer.class);
    }

    public int lockAndCountActiveAdmins() {
        return jdbc.query("SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' FOR UPDATE",
                results -> {
                    int count = 0;
                    while (results.next()) {
                        count++;
                    }
                    return count;
                });
    }

    public Map<String, Object> findById(long userId) {
        return jdbc.queryForMap("SELECT id, login, role, status FROM users WHERE id = ?", userId);
    }

    public List<Map<String, Object>> findStaff() {
        return jdbc.queryForList("""
                SELECT id, login, role, status, last_login_at
                FROM users WHERE role IN ('LIBRARIAN', 'ADMIN')
                ORDER BY role, login
                """);
    }

    public List<Map<String, Object>> findAllAccounts() {
        return jdbc.queryForList("""
                SELECT u.id, u.login, u.role, u.status, u.last_login_at,
                       r.id AS reader_id, r.full_name AS reader_name, r.card_number
                FROM users u LEFT JOIN readers r ON r.user_id = u.id
                ORDER BY u.role, u.login
                """);
    }

    public void deactivateInactiveAccounts() {
        jdbc.update("""
                                UPDATE users SET status = 'DEACTIVATED', deactivated_at = CURRENT_TIMESTAMP
                WHERE status = 'ACTIVE'
                  AND last_login_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY
                """);
    }

    public void deactivateByReaderId(long readerId) {
        jdbc.update("""
                UPDATE users SET status = 'DEACTIVATED', deactivated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT user_id FROM readers WHERE id = ?)
                """, readerId);
    }

    public void setStatus(long userId, String status) {
        jdbc.update("""
                UPDATE users
                SET status = ?,
                    deactivated_at = CASE WHEN ? = 'DEACTIVATED'
                        THEN COALESCE(deactivated_at, CURRENT_TIMESTAMP) ELSE NULL END
                WHERE id = ?
                """, status, status, userId);
    }

    public int deleteExpiredUnreferencedAccounts() {
        return jdbc.update("""
                DELETE FROM users
                WHERE status = 'DEACTIVATED'
                  AND deactivated_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY
                  AND NOT EXISTS (SELECT 1 FROM readers r WHERE r.user_id = users.id)
                  AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.librarian_id = users.id)
                """);
    }
}