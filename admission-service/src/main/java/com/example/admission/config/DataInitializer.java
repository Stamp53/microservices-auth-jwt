package com.example.admission.config;

import com.example.admission.model.Role;
import com.example.admission.model.User;
import com.example.admission.repository.AdmissionApplicationRepository;
import com.example.admission.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds only the default Administrator account.
 * Clears demo applications and avoids pre-populating dummy data.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AdmissionApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           AdmissionApplicationRepository applicationRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Clear all previous dummy/demo applications
        applicationRepository.deleteAll();

        // Ensure default admin account exists
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin", passwordEncoder.encode("admin123"),
                    "System Administrator", Role.ROLE_ADMIN);
            userRepository.save(admin);
        }
    }
}
