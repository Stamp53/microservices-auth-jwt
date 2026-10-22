package com.example.admission.controller;

import com.example.admission.model.AdmissionApplication;
import com.example.admission.model.User;
import com.example.admission.service.ApplicationService;
import com.example.admission.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/student")
@CrossOrigin(origins = "*")
public class StudentController {

    private final ApplicationService applicationService;
    private final UserService userService;

    public StudentController(ApplicationService applicationService, UserService userService) {
        this.applicationService = applicationService;
        this.userService = userService;
    }

    /**
     * Returns the logged-in student's profile and their admission application(s).
     * Spring Security guarantees Authentication is non-null here because
     * SecurityConfig requires ROLE_STUDENT for all /student/** paths.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication) {
        String username = authentication.getName();

        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found: " + username));
        }
        User user = userOpt.get();

        Optional<AdmissionApplication> latestApp = applicationService.getApplicationForUser(user);

        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "fullName", user.getFullName(),
                "role", user.getRole(),
                "hasApplication", latestApp.isPresent(),
                "admissionApp", latestApp.orElse(null),
                "allApplications", applicationService.getAllApplicationsForUser(user)
        ));
    }
}
