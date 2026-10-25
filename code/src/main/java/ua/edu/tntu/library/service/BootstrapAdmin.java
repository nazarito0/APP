package ua.edu.tntu.library.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.edu.tntu.library.model.Role;
import ua.edu.tntu.library.repository.UserDAO;

@Configuration
public class BootstrapAdmin {
    @Bean
    ApplicationRunner createInitialAdmin(
            UserDAO userDAO,
            @Value("${library.bootstrap.admin-login}") String login,
            @Value("${library.bootstrap.admin-password}") String password) {
        return args -> {
            Integer admins = userDAO.countAdmins();
            if (admins != null && admins == 0) {
                userDAO.insert(login, password, Role.ADMIN);
            }
        };
    }
}