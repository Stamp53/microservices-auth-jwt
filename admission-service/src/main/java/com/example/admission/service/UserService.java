package com.example.admission.service;

import com.example.admission.model.Role;
import com.example.admission.model.User;
import com.example.admission.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User registerUser(String username, String rawPassword, String fullName, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }
        User user = new User(
                username,
                passwordEncoder.encode(rawPassword),
                fullName,
                role
        );
        return userRepository.save(user);
    }

    @Transactional
    public User getOrCreateUser(String username, String rawPassword, String fullName, Role role) {
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        String password = (rawPassword != null && !rawPassword.isBlank()) ? rawPassword : "student123";
        User user = new User(
                username,
                passwordEncoder.encode(password),
                fullName,
                role
        );
        return userRepository.save(user);
    }
}
