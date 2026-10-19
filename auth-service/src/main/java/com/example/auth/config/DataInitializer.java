package com.example.auth.config;

import com.example.auth.model.Role;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "admin@admission.com",
                    "System Administrator",
                    Role.ROLE_ADMIN
            ));

            userRepository.save(new User(
                    "alice",
                    passwordEncoder.encode("student123"),
                    "alice@example.com",
                    "Alice Smith",
                    Role.ROLE_STUDENT
            ));

            userRepository.save(new User(
                    "bob",
                    passwordEncoder.encode("student123"),
                    "bob@example.com",
                    "Bob Jones",
                    Role.ROLE_STUDENT
            ));

            userRepository.save(new User(
                    "carol",
                    passwordEncoder.encode("student123"),
                    "carol@example.com",
                    "Carol White",
                    Role.ROLE_STUDENT
            ));

            userRepository.save(new User(
                    "david",
                    passwordEncoder.encode("student123"),
                    "david@example.com",
                    "David Miller",
                    Role.ROLE_STUDENT
            ));

            userRepository.save(new User(
                    "emma",
                    passwordEncoder.encode("student123"),
                    "emma@example.com",
                    "Emma Watson",
                    Role.ROLE_STUDENT
            ));
        }
    }
}
