package ua.edu.tntu.library.web;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.tntu.library.dto.ApiRequests;
import ua.edu.tntu.library.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody ApiRequests.Login request,
                                     HttpServletRequest servletRequest) {
        return authService.login(request, servletRequest);
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody ApiRequests.ReaderRegistration request,
                                        HttpServletRequest servletRequest) {
        return authService.registerReader(request, servletRequest);
    }

    @GetMapping("/me")
    public Map<String, Object> currentUser(HttpSession session) {
        return authService.currentUser(session);
    }

    @PutMapping("/password")
    public org.springframework.http.ResponseEntity<Void> changePassword(
            @Valid @RequestBody ApiRequests.PasswordChange request, HttpSession session) {
        authService.changePassword(authService.requireUserId(session), request);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public org.springframework.http.ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return org.springframework.http.ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}