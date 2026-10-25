package ua.edu.tntu.library.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ua.edu.tntu.library.model.Role;
import ua.edu.tntu.library.model.UserStatus;

import java.util.List;

public final class ApiRequests {
    private ApiRequests() {
    }

    public record Login(
            @NotBlank @Size(max = 80) String login,
            @NotBlank @Size(max = 100) String password) {
    }

    public record PasswordChange(
            @NotBlank @Size(max = 100) String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record PasswordReset(
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record ReaderRegistration(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 40) String cardNumber,
            @NotBlank @Size(max = 80) String login,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Email @Size(max = 160) String email,
            @Size(max = 40) String phone) {
    }

    public record ReaderUpdate(
            @NotBlank @Size(max = 160) String fullName,
            @Email @Size(max = 160) String email,
            @Size(max = 40) String phone) {
    }

    public record BookCreate(
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 180) String author,
            @Size(max = 100) String genre,
            @Size(max = 20) String isbn,
            @Min(1000) @Max(2100) Integer publicationYear,
            @Size(max = 180) String publisher,
            @NotEmpty List<@NotBlank @Size(max = 60) String> inventoryNumbers) {
    }

    public record BookUpdate(
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 180) String author,
            @Size(max = 100) String genre,
            @Size(max = 20) String isbn,
            @Min(1000) @Max(2100) Integer publicationYear,
            @Size(max = 180) String publisher) {
    }

    public record IssueLoan(
            @NotNull @Positive Long readerId,
            @NotNull @Positive Long copyId) {
    }

    public record Staff(
            @NotBlank @Size(max = 80) String login,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role) {
    }

        public record StaffStatus(@NotNull UserStatus status) {
        }
}