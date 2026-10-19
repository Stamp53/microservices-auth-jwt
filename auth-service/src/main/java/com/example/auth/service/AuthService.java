package com.example.auth.service;

import com.example.auth.dto.*;
import com.example.auth.model.RefreshToken;
import com.example.auth.model.Role;
import com.example.auth.model.User;
import com.example.auth.repository.RefreshTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole(), user.getEmail());
        RefreshToken refreshToken = createOrUpdateRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                tokenProvider.getAccessTokenExpirationSeconds(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email '" + email + "' is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.ROLE_STUDENT;

        User user = new User(
                username,
                passwordEncoder.encode(request.getPassword()),
                email,
                request.getFullName().trim(),
                role
        );
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole(), user.getEmail());
        RefreshToken refreshToken = createOrUpdateRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                tokenProvider.getAccessTokenExpirationSeconds(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public AuthResponse refreshAccessToken(String requestRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (token.isRevoked()) {
            throw new IllegalArgumentException("Refresh token was revoked. Please log in again.");
        }

        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new IllegalArgumentException("Refresh token expired. Please log in again.");
        }

        User user = token.getUser();
        String newAccessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole(), user.getEmail());

        return new AuthResponse(
                newAccessToken,
                token.getToken(),
                tokenProvider.getAccessTokenExpirationSeconds(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    public TokenValidationResponse validateToken(String token) {
        if (!tokenProvider.validateToken(token)) {
            return TokenValidationResponse.invalid("Invalid or expired JWT token");
        }

        try {
            String username = tokenProvider.getUsernameFromToken(token);
            String roleStr = tokenProvider.getRoleFromToken(token);
            String email = tokenProvider.getEmailFromToken(token);
            Role role = Role.valueOf(roleStr);

            return TokenValidationResponse.valid(username, role, email);
        } catch (Exception ex) {
            return TokenValidationResponse.invalid("Could not extract token claims: " + ex.getMessage());
        }
    }

    @Transactional
    public void logout(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        userOpt.ifPresent(refreshTokenRepository::deleteByUser);
    }

    public UserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private RefreshToken createOrUpdateRefreshToken(User user) {
        RefreshToken refreshToken = refreshTokenRepository.findByUser(user)
                .orElse(new RefreshToken());

        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(tokenProvider.getRefreshTokenExpirationMs()));
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }
}
