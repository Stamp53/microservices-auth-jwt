package com.example.admission.controller;

import com.example.admission.dto.StudentRegistrationDto;
import com.example.admission.model.AdmissionApplication;
import com.example.admission.security.JwtUtils;
import com.example.admission.service.ApplicationService;
import com.example.admission.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class AuthController {

    private final ApplicationService applicationService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthController(ApplicationService applicationService,
                          UserService userService,
                          AuthenticationManager authenticationManager,
                          JwtUtils jwtUtils) {
        this.applicationService = applicationService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return isAdmin ? "redirect:/admin/dashboard" : "redirect:/student/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out securely.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              HttpServletResponse response,
                              Model model) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username.trim(), password)
            );

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            String role = isAdmin ? "ROLE_ADMIN" : "ROLE_STUDENT";

            // Generate Stateless JWT Token (No server-stored session)
            String token = jwtUtils.generateToken(username.trim(), role);

            // Store in HTTP-Only cookie for stateless web authentication
            Cookie jwtCookie = new Cookie("jwt_token", token);
            jwtCookie.setHttpOnly(true);
            jwtCookie.setPath("/");
            jwtCookie.setMaxAge(3600); // 1 hour
            response.addCookie(jwtCookie);

            return isAdmin ? "redirect:/admin/dashboard" : "redirect:/student/dashboard";
        } catch (BadCredentialsException ex) {
            model.addAttribute("errorMessage", "Invalid username or password.");
            return "login";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Authentication failed: " + ex.getMessage());
            return "login";
        }
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String handleLogout(HttpServletResponse response) {
        // Clear stateless JWT cookie
        Cookie jwtCookie = new Cookie("jwt_token", "");
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0);
        response.addCookie(jwtCookie);

        SecurityContextHolder.clearContext();
        return "redirect:/login?logout=true";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registrationDto", new StudentRegistrationDto());
        return "register";
    }

    @PostMapping("/register")
    public String handleRegistration(@Valid @ModelAttribute("registrationDto") StudentRegistrationDto dto,
                                     BindingResult bindingResult,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        if (dto.getUsername() != null && userService.usernameExists(dto.getUsername().trim())) {
            bindingResult.rejectValue("username", "error.user", "This username is already in use.");
        }

        int currentYear = java.time.Year.now().getValue();
        if (dto.getEmail() != null && dto.getAppliedClass() != null 
                && applicationService.existsByEmailAndClassAndYear(dto.getEmail().trim(), dto.getAppliedClass(), currentYear)) {
            bindingResult.rejectValue("email", "error.duplicate", 
                    "An application for " + dto.getAppliedClass() + " in " + currentYear + " already exists with this email.");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            AdmissionApplication createdApp = applicationService.registerStudentApplication(dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Registration successful! Your Application Number is: " + createdApp.getApplicationNumber()
                    + ". Please login to check your admission status.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "register";
        }
    }

    @GetMapping("/track")
    public String showTrackPage(@RequestParam(value = "appNum", required = false) String appNum, Model model) {
        if (appNum != null && !appNum.trim().isEmpty()) {
            Optional<AdmissionApplication> appOpt = applicationService.findByApplicationNumber(appNum.trim());
            if (appOpt.isPresent()) {
                model.addAttribute("admissionApp", appOpt.get());
                model.addAttribute("application", appOpt.get());
            } else {
                model.addAttribute("errorMessage", "No application found with number: " + appNum);
            }
            model.addAttribute("searchedAppNum", appNum);
        }
        return "track";
    }
}
