package ua.edu.tntu.library.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевірка формування повідомлень про помилки для GUI (FR-012).
 */
class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("TC-058 Бізнес-помилка повертається з її HTTP-статусом і текстом")
    void apiExceptionIsMappedToStatusAndMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleApiException(
                new ApiException(HttpStatus.CONFLICT, "Цей примірник уже видано"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Цей примірник уже видано", response.getBody().get("error"));
    }

    @Test
    @DisplayName("TC-059 Порушення унікальності в БД -> 409 і зрозуміле повідомлення")
    void duplicateKeyIsMappedToConflict() {
        ResponseEntity<Map<String, String>> response = handler.handleDuplicate(
                new DuplicateKeyException("Duplicate entry 'admin' for key 'login'"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String message = response.getBody().get("error");
        assertTrue(message.contains("уже використовується"));
        assertTrue(!message.contains("Duplicate entry"), "Технічні деталі БД не мають потрапляти в GUI");
    }
}
