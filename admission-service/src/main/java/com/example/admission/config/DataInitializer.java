package com.example.admission.config;

import com.example.admission.model.AdmissionApplication;
import com.example.admission.model.ApplicationStatus;
import com.example.admission.model.Role;
import com.example.admission.model.User;
import com.example.admission.repository.AdmissionApplicationRepository;
import com.example.admission.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds a minimal set of demo users and applications at startup.
 * Only runs when the users table is empty (fresh start).
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
        if (userRepository.count() > 0) {
            return; // Already seeded
        }

        // Admin account
        User admin = new User("admin", passwordEncoder.encode("admin123"),
                "System Administrator", Role.ROLE_ADMIN);
        userRepository.save(admin);

        // Student 1 — approved application
        User alice = new User("alice", passwordEncoder.encode("student123"),
                "Alice Smith", Role.ROLE_STUDENT);
        userRepository.save(alice);

        AdmissionApplication app1 = new AdmissionApplication();
        app1.setApplicationNumber("APP-2026-1001");
        app1.setAcademicYear(2026);
        app1.setPermanentRollNumber("ADM-2026-001");
        app1.setStudentName("Alice Smith");
        app1.setEmail("alice@example.com");
        app1.setPhone("9876543210");
        app1.setDateOfBirth(LocalDate.of(2008, 5, 14));
        app1.setGender("Female");
        app1.setAddress("123 Maple Street, Springfield");
        app1.setAppliedClass("Class 11 Science");
        app1.setAssignedClass("Class 11 Science");
        app1.setAssignedSection("Section A");
        app1.setMarksPercentage(92.5);
        app1.setStatus(ApplicationStatus.APPROVED);
        app1.setRegisteredBy("STUDENT");
        app1.setRemarks("Admitted directly.");
        app1.setAppliedDate(LocalDateTime.now().minusDays(5));
        app1.setDecisionDate(LocalDateTime.now().minusDays(2));
        app1.setUser(alice);
        applicationRepository.save(app1);

        // Student 2 — pending application
        User bob = new User("bob", passwordEncoder.encode("student123"),
                "Bob Jones", Role.ROLE_STUDENT);
        userRepository.save(bob);

        AdmissionApplication app2 = new AdmissionApplication();
        app2.setApplicationNumber("APP-2026-1002");
        app2.setAcademicYear(2026);
        app2.setStudentName("Bob Jones");
        app2.setEmail("bob@example.com");
        app2.setPhone("9876543211");
        app2.setDateOfBirth(LocalDate.of(2008, 9, 21));
        app2.setGender("Male");
        app2.setAddress("456 Oak Avenue, Springfield");
        app2.setAppliedClass("Class 11 Commerce");
        app2.setMarksPercentage(79.0);
        app2.setStatus(ApplicationStatus.PENDING);
        app2.setRegisteredBy("STUDENT");
        app2.setAppliedDate(LocalDateTime.now().minusDays(1));
        app2.setUser(bob);
        applicationRepository.save(app2);
    }
}
