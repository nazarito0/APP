package ua.edu.tntu.library.service;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.tntu.library.dto.ApiRequests;
import ua.edu.tntu.library.model.Role;
import ua.edu.tntu.library.repository.ReaderDAO;
import ua.edu.tntu.library.repository.UserDAO;
import ua.edu.tntu.library.web.ApiException;

import java.util.Map;

@Service
public class AuthService {
    private final UserDAO userDAO;
    private final ReaderDAO readerDAO;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserDAO userDAO, ReaderDAO readerDAO, PasswordEncoder passwordEncoder) {
        this.userDAO = userDAO;
        this.readerDAO = readerDAO;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> login(ApiRequests.Login request, HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(true);
        Map<String, Object> user;
        try {
                user = userDAO.findForLogin(request.login());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Невірний логін або пароль");
        }

        if (!"ACTIVE".equals(user.get("status"))
            || !passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Невірний логін або пароль");
        }

        long userId = ((Number) user.get("id")).longValue();
        userDAO.updateLastLogin(userId);
        servletRequest.changeSessionId();
        session.setAttribute("userId", userId);
        return profile(userId);
    }

    @Transactional
    public Map<String, Object> registerReader(ApiRequests.ReaderRegistration request,
                                              HttpServletRequest servletRequest) {
        long userId = createReaderAccount(request);
        userDAO.updateLastLogin(userId);
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute("userId", userId);
        return profile(userId);
    }

    @Transactional
    public Map<String, Object> createReaderForLibrarian(ApiRequests.ReaderRegistration request) {
        return profile(createReaderAccount(request));
    }

    private long createReaderAccount(ApiRequests.ReaderRegistration request) {
        long userId = userDAO.insert(request.login(), request.password(), Role.READER);
        readerDAO.insert(userId, request.cardNumber(), request.fullName(),
            emptyToNull(request.email()), emptyToNull(request.phone()));
        return userId;
    }

    public Map<String, Object> currentUser(HttpSession session) {
        return profile(requireUserId(session));
    }

    public long requireUserId(HttpSession session) {
        Object value = session.getAttribute("userId");
        if (!(value instanceof Number userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Спершу увійдіть у систему");
        }
        try {
            String status = userDAO.findStatus(userId.longValue());
            if (!"ACTIVE".equals(status)) {
                session.invalidate();
                throw new ApiException(HttpStatus.FORBIDDEN, "Обліковий запис деактивовано");
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            session.invalidate();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Обліковий запис не знайдено");
        }
        return userId.longValue();
    }

    public Role requireRole(HttpSession session, Role... allowedRoles) {
        long userId = requireUserId(session);
        String roleName = userDAO.findRole(userId);
        Role role = Role.valueOf(roleName);
        for (Role allowedRole : allowedRoles) {
            if (role == allowedRole) {
                return role;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Недостатньо прав для цієї операції");
    }

    public void changePassword(long userId, ApiRequests.PasswordChange request) {
        String currentHash = userDAO.findPasswordHash(userId);
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Поточний пароль неправильний");
        }
        if (passwordEncoder.matches(request.newPassword(), currentHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Новий пароль має відрізнятися від поточного");
        }
        userDAO.updatePassword(userId, request.newPassword());
    }

    public void resetAccountPassword(long userId, ApiRequests.PasswordReset request) {
        try {
            userDAO.findById(userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Обліковий запис не знайдено");
        }
        userDAO.updatePassword(userId, request.newPassword());
    }

    public Long readerIdForUser(long userId) {
        return readerDAO.findIdByUserId(userId);
    }

    public Long insertUser(String login, String rawPassword, Role role) {
        return userDAO.insert(login, rawPassword, role);
    }

        @Scheduled(cron = "0 0 3 * * *")
        public void deactivateInactiveAccounts() {
                userDAO.deactivateInactiveAccounts();
        }

    private Map<String, Object> profile(long userId) {
        Map<String, Object> user = userDAO.findProfile(userId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", user.get("id"));
        result.put("login", user.get("login"));
        result.put("role", user.get("role"));
        Long readerId = readerIdForUser(userId);
        result.put("readerId", readerId);
        if (readerId != null) {
                result.put("cardNumber", readerDAO.findCardNumber(readerId));
        }
        return result;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}