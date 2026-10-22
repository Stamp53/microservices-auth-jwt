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
        User user = userOpt.orElse(null);

        Optional<AdmissionApplication> latestApp = applicationService.getApplicationForUsernameOrUser(username, user);
        java.util.List<AdmissionApplication> allApps = applicationService.getAllApplicationsForUsernameOrUser(username, user);

        String fullName = (user != null && user.getFullName() != null)
                ? user.getFullName()
                : latestApp.map(AdmissionApplication::getStudentName).orElse(username);

        String role = (user != null && user.getRole() != null)
                ? user.getRole().name()
                : "ROLE_STUDENT";

        return ResponseEntity.ok(Map.of(
                "username", username,
                "fullName", fullName,
                "role", role,
                "hasApplication", latestApp.isPresent(),
                "admissionApp", latestApp.orElse(null),
                "allApplications", allApps
        ));
    }
}
