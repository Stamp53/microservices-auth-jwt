# Controller to Service to Repository: Complete Flow Documentation

## Architecture Overview

  CLIENT (Browser / REST Client)
          |
          v
  [API GATEWAY :8080]   <- routes /auth/** -> AUTH-SERVICE
          |             <- routes /**    -> ADMISSION-SERVICE
          |             <- JWT Global Filter validates token before forwarding
          |
          +----------------+-------------------+
          |                                    |
  [AUTH-SERVICE :8082]            [ADMISSION-SERVICE :8081]
  (JWT issue, register,            (Business logic, admin ops,
   refresh, validate)               student dashboard, reports)

---

## ==================================================
## SERVICE 1: auth-service  (Port 8082)
## ==================================================

Repositories in auth-service:
  - UserRepository          -> Table: users
  - RefreshTokenRepository  -> Table: refresh_tokens

---

### [A] POST /auth/login
Purpose: Authenticate user, issue access token + refresh token

  AuthController.login(LoginRequest)
      |
      +-- [VALIDATION] @Valid: username and password not blank
      |
      +-- AuthService.login(request)
              |
              +-- AuthenticationManager.authenticate(UsernamePasswordAuthToken)
              |       +-- CustomUserDetailsService.loadUserByUsername(username)
              |               +-- UserRepository.findByUsername(username)  [DB READ]
              |                       -> Returns User entity -> builds UserDetails
              |                       -> BCrypt password match
              |                       -> Throws BadCredentialsException if wrong password
              |
              +-- UserRepository.findByUsername(username)  [DB READ]
              |       -> Fetches full User entity (for email, role)
              |
              +-- JwtTokenProvider.generateAccessToken(username, role, email)
              |       -> Builds HMAC-SHA256 signed JWT with claims
              |       -> expiry = current time + 3600000ms (1 hour)
              |
              +-- createOrUpdateRefreshToken(user)
              |       +-- RefreshTokenRepository.findByUser(user)  [DB READ]
              |       |       -> finds existing refresh token for this user if any
              |       +-- Sets new UUID token string + expiry (7 days)
              |       +-- RefreshTokenRepository.save(refreshToken)  [DB WRITE]
              |               -> INSERT or UPDATE refresh_tokens table
              |
              +-- Returns AuthResponse { accessToken, refreshToken, expiresIn, username, email, role }

  Result:
    HTTP 200 OK + AuthResponse
    On BadCredentialsException -> HTTP 401 + error message

---

### [B] POST /auth/register
Purpose: Create a new user account and immediately issue JWT pair

  AuthController.register(RegisterRequest)
      |
      +-- [VALIDATION] @Valid: username 3-50 chars, password 6+, valid email, fullName not blank
      |
      +-- AuthService.register(request)
              |
              +-- UserRepository.existsByUsername(username)  [DB READ]
              |       -> Throws IllegalArgumentException if username taken
              |
              +-- UserRepository.existsByEmail(email)  [DB READ]
              |       -> Throws IllegalArgumentException if email already registered
              |
              +-- BCryptPasswordEncoder.encode(rawPassword)  -> hashed password
              |
              +-- UserRepository.save(new User(...))  [DB WRITE]
              |       -> INSERT into users table
              |       -> @PrePersist sets createdAt = LocalDateTime.now()
              |
              +-- JwtTokenProvider.generateAccessToken(username, role, email)
              |
              +-- createOrUpdateRefreshToken(user)
              |       +-- RefreshTokenRepository.save(refreshToken)  [DB WRITE]
              |
              +-- Returns AuthResponse { accessToken, refreshToken, ... }

  Result:
    HTTP 201 Created + AuthResponse
    On IllegalArgumentException -> HTTP 400 + error message

---

### [C] POST /auth/refresh
Purpose: Silently issue a new access token using a valid refresh token

  AuthController.refreshToken(RefreshTokenRequest)
      |
      +-- [VALIDATION] @Valid: refreshToken not blank
      |
      +-- AuthService.refreshAccessToken(refreshTokenString)
              |
              +-- RefreshTokenRepository.findByToken(tokenString)  [DB READ]
              |       -> Throws IllegalArgumentException("Refresh token not found") if missing
              |
              +-- Check: token.isRevoked() == true
              |       -> Throws IllegalArgumentException("...revoked. Please log in again")
              |
              +-- Check: token.getExpiryDate().isBefore(Instant.now())
              |       +-- RefreshTokenRepository.delete(token)  [DB DELETE]
              |       +-- Throws IllegalArgumentException("...expired. Please log in again")
              |
              +-- token.getUser()  -> User entity from the refresh_tokens FK join
              |
              +-- JwtTokenProvider.generateAccessToken(user.username, user.role, user.email)
              |       -> New short-lived access token; refresh token is REUSED (not rotated)
              |
              +-- Returns AuthResponse { newAccessToken, same refreshToken, ... }

  Result:
    HTTP 200 OK + AuthResponse
    On IllegalArgumentException -> HTTP 401 + error message

---

### [D] POST /auth/validate
Purpose: Verify if a JWT is valid and not expired (used by gateway/other services)

  AuthController.validateToken(TokenValidationRequest)
      |
      +-- [VALIDATION] @Valid: token not blank
      |
      +-- AuthService.validateToken(tokenString)
              |
              +-- JwtTokenProvider.validateToken(token)
              |       -> Parses signature with HMAC key
              |       -> Checks expiry claim
              |       -> Returns false if any JwtException thrown
              |
              +-- If invalid -> Returns TokenValidationResponse { valid: false, message: "..." }
              |
              +-- JwtTokenProvider.getUsernameFromToken(token)   -> claims.getSubject()
              +-- JwtTokenProvider.getRoleFromToken(token)       -> claims.get("role")
              +-- JwtTokenProvider.getEmailFromToken(token)      -> claims.get("email")
              |
              +-- Returns TokenValidationResponse { valid: true, username, role, email }
                   NOTE: No DB call -- purely stateless JWT inspection

  Result:
    HTTP 200 OK (always); the "valid" field in the body carries the truth

---

### [E] GET /auth/me
Purpose: Fetch profile of the currently authenticated user (requires Bearer token)

  AuthController.getCurrentUser(Authentication)
      |
      +-- [SECURITY] JwtAuthenticationFilter (runs before this endpoint)
      |       -> Reads "Authorization: Bearer <token>" header
      |       -> Validates token, extracts username
      |       -> Loads UserDetails from CustomUserDetailsService
      |       -> Sets SecurityContextHolder authentication
      |
      +-- authentication.getPrincipal() -> UserDetails -> username
      |
      +-- AuthService.getUserProfile(username)
              |
              +-- UserRepository.findByUsername(username)  [DB READ]
                      -> Throws IllegalArgumentException if user not found
                      -> Returns UserProfileResponse { id, username, email, fullName, role, createdAt }

  Result:
    HTTP 200 OK + UserProfileResponse
    If null/unauthenticated  -> HTTP 401
    If user not found        -> HTTP 404

---

### [F] POST /auth/logout
Purpose: Revoke the user's persisted refresh token from the database

  AuthController.logout(Authentication)
      |
      +-- [SECURITY] JwtAuthenticationFilter populates Authentication from Bearer token
      |
      +-- authentication.getName() -> username
      |
      +-- AuthService.logout(username)
              |
              +-- UserRepository.findByUsername(username)  [DB READ]
              |       -> Returns Optional<User>
              |
              +-- If present: RefreshTokenRepository.deleteByUser(user)  [DB DELETE]
                      -> DELETE FROM refresh_tokens WHERE user_id = ?
                      -> Future /auth/refresh calls with old token will now return "not found"

  Result:
    HTTP 200 OK + MessageResponse("Successfully logged out")

---

## ==================================================
## SERVICE 2: admission-service  (Port 8081)
## ==================================================

Repositories in admission-service:
  - UserRepository                  -> Table: users
  - AdmissionApplicationRepository  -> Table: admission_applications

---

### [G] POST /login  (Admission Web UI Login)
Purpose: Authenticate via web form, issue HttpOnly JWT cookie (stateless)

  AuthController.handleLogin(username, password, HttpServletResponse)
      |
      +-- AuthenticationManager.authenticate(UsernamePasswordAuthToken)
      |       +-- CustomUserDetailsService.loadUserByUsername(username)
      |               +-- UserRepository.findByUsername(username)  [DB READ]
      |                       -> BCrypt match; throws BadCredentialsException on failure
      |
      +-- authentication.getAuthorities() -> determines ROLE_ADMIN or ROLE_STUDENT
      |
      +-- JwtUtils.generateToken(username, role)
      |       -> Signs JWT with HMAC-SHA256, embeds role + iat + exp claims
      |
      +-- Sets HttpOnly Cookie: jwt_token = <token>; Path=/; Max-Age=3600
      |       -> No JSESSIONID, no server session stored -- fully stateless
      |
      +-- Redirects to /admin/dashboard (ROLE_ADMIN) or /student/dashboard (ROLE_STUDENT)
         On BadCredentialsException -> stays on /login with errorMessage

---

### [H] GET/POST /logout  (Admission Web UI Logout)
Purpose: Clear the JWT cookie from the browser

  AuthController.handleLogout(HttpServletResponse)
      |
      +-- Creates Cookie("jwt_token", "") with Max-Age=0
      |       -> Instructs browser to immediately expire and delete the jwt_token cookie
      |
      +-- SecurityContextHolder.clearContext()
      |       -> Clears in-memory auth for current request only
      |
      +-- Redirects to /login?logout=true
         NOTE: No DB call -- stateless cookies need no server-side cleanup

---

### [I] POST /register  (Student Self-Registration)
Purpose: Student submits admission application and creates a user account

  AuthController.handleRegistration(StudentRegistrationDto, BindingResult, ...)
      |
      +-- [VALIDATION] @Valid on StudentRegistrationDto
      |
      +-- UserService.usernameExists(username)
      |       +-- UserRepository.existsByUsername(username)  [DB READ]
      |               -> Adds field error if username already taken
      |
      +-- ApplicationService.existsByEmailAndClassAndYear(email, appliedClass, year)
      |       +-- AdmissionApplicationRepository
      |              .existsByEmailAndAppliedClassAndAcademicYear(...)  [DB READ]
      |               -> Adds field error if duplicate application found
      |
      +-- If bindingResult.hasErrors() -> return "register" (redisplay form)
      |
      +-- ApplicationService.registerStudentApplication(dto)
              |
              +-- applicationRepository
              |       .existsByEmailAndAppliedClassAndAcademicYear(...)  [DB READ]
              |       -> Service-level double-check guard
              |
              +-- UserService.registerUser(username, password, fullName, ROLE_STUDENT)
              |       +-- UserRepository.existsByUsername(username)  [DB READ]
              |       +-- BCryptPasswordEncoder.encode(rawPassword)
              |       +-- UserRepository.save(new User(...))  [DB WRITE]
              |               -> INSERT into users table
              |
              +-- generateNextApplicationNumber()
              |       +-- applicationRepository.count()  [DB READ]
              |       +-- applicationRepository.findByApplicationNumber(appNum)  [DB READ]
              |               -> Loop ensures no collision (e.g. APP-2026-1007)
              |
              +-- Builds AdmissionApplication entity (status=PENDING, registeredBy="STUDENT")
              |
              +-- applicationRepository.save(app)  [DB WRITE]
                      -> INSERT into admission_applications
                      -> @PrePersist sets appliedDate = LocalDateTime.now()

  Result:
    Redirects to /login with successMessage showing the application number

---

### [J] GET /track  (Public Application Tracker)
Purpose: Anyone can look up application status by application number

  AuthController.showTrackPage(appNum, Model)
      |
      +-- If appNum param present:
      |       ApplicationService.findByApplicationNumber(appNum.trim())
      |               +-- AdmissionApplicationRepository
      |                      .findByApplicationNumber(appNum)  [DB READ]
      |                       -> Adds "admissionApp" to model if found
      |                       -> Adds "errorMessage" if not found
      |
      +-- Returns "track" Thymeleaf view (PUBLIC -- no authentication required)

---

### [K] GET /admin/dashboard
Purpose: Admin sees summary stats and paginated application list

  AdminController.dashboard(page, size, Model)
      |
      +-- ApplicationService.getApplicationsPaged(page, size)
      |       +-- applicationRepository.findAllByOrderByAppliedDateDesc(Pageable)  [DB READ]
      |               -> Returns Page<AdmissionApplication> with pagination metadata
      |
      +-- ApplicationService.countTotal()
      |       +-- applicationRepository.count()  [DB READ]
      |
      +-- ApplicationService.countPending()
      |       +-- applicationRepository.countByStatus(PENDING)  [DB READ]
      |
      +-- ApplicationService.countApproved()
      |       +-- applicationRepository.countByStatus(APPROVED)  [DB READ]
      |
      +-- ApplicationService.countRejected()
      |       +-- applicationRepository.countByStatus(REJECTED)  [DB READ]
      |
      +-- Returns "admin/dashboard" view with counts + paged applications

---

### [L] GET /admin/applications  (List with Status Filter)
Purpose: Admin views all applications, optionally filtered by status

  AdminController.listApplications(status, Model)
      |
      +-- status == "PENDING"  -> applicationRepository.findByStatus(PENDING)   [DB READ]
      +-- status == "APPROVED" -> applicationRepository.findByStatus(APPROVED)  [DB READ]
      +-- status == "REJECTED" -> applicationRepository.findByStatus(REJECTED)  [DB READ]
      +-- status == "ALL"      -> applicationRepository.findAllByOrderByAppliedDateDesc()  [DB READ]
      |
      +-- Returns "admin/applications" view

---

### [M] GET /admin/applications/{id}  (View Single Application)
Purpose: Admin views full detail with approve/reject forms

  AdminController.viewApplication(id, Model)
      |
      +-- ApplicationService.findById(id)
      |       +-- applicationRepository.findById(id)  [DB READ]
      |               -> Throws IllegalArgumentException if not found
      |
      +-- Prepopulates ApprovalDto (assignedClass, assignedSection, remarks)
      +-- Adds empty RejectionDto to model
      |
      +-- Returns "admin/application-detail" view

---

### [N] POST /admin/applications/{id}/approve
Purpose: Admin approves an application, assigns class/section, generates roll number

  AdminController.approveApplication(id, ApprovalDto, RedirectAttributes)
      |
      +-- ApplicationService.approveApplication(id, approvalDto)
              |
              +-- applicationRepository.findById(id)  [DB READ]
              |       -> Throws IllegalArgumentException if not found
              |
              +-- app.setStatus(APPROVED)
              |
              +-- resolveAssignedSection(requestedSection, assignedClass, academicYear, appId)
              |       |
              |       +-- If section specified (not AUTO):
              |       |       applicationRepository.countApprovedInSection(...)  [DB READ - JPQL]
              |       |       -> Throws IllegalStateException if section >= 30 students
              |       |
              |       +-- If AUTO or empty:
              |               autoAssignAvailableSection(assignedClass, year, excludeId)
              |               -> Loops Section A through Z
              |               -> applicationRepository.countApprovedInSection(...)  [DB READ per section]
              |               -> Returns first section with count < 30
              |               -> Throws IllegalStateException if ALL sections are full
              |
              +-- generateNextPermanentRollNumber()  (if not already assigned)
              |       +-- applicationRepository.countByStatus(APPROVED)  [DB READ]
              |       +-- applicationRepository.findByPermanentRollNumber(roll)  [DB READ]
              |               -> Loop ensures no collision (e.g. ADM-2026-007)
              |
              +-- app.setDecisionDate(LocalDateTime.now())
              |
              +-- applicationRepository.save(app)  [DB WRITE]
                      -> UPDATE: status, assignedClass, assignedSection,
                                 permanentRollNumber, decisionDate, remarks

  Result:
    Redirects to /admin/applications/{id} with successMessage or errorMessage

---

### [O] POST /admin/applications/{id}/reject
Purpose: Admin rejects an application with a reason

  AdminController.rejectApplication(id, RejectionDto, RedirectAttributes)
      |
      +-- ApplicationService.rejectApplication(id, rejectionDto)
              |
              +-- applicationRepository.findById(id)  [DB READ]
              |       -> Throws IllegalArgumentException if not found
              |
              +-- app.setStatus(REJECTED)
              +-- app.setRemarks(dto.getRemarks())
              +-- app.setDecisionDate(LocalDateTime.now())
              |
              +-- applicationRepository.save(app)  [DB WRITE]
                      -> UPDATE: status=REJECTED, remarks, decisionDate

  Result:
    Redirects to /admin/applications/{id} with rejection notice

---

### [P] GET + POST /admin/register-student
Purpose: Admin manually registers a student (optionally also creates a user account)

  GET: AdminController.showAdminRegisterStudentForm(Model)
    -> Returns "admin/register-student" view with empty AdminStudentRegistrationDto

  POST: AdminController.handleAdminRegisterStudent(dto, BindingResult, ...)
      |
      +-- [VALIDATION] @Valid on AdminStudentRegistrationDto
      |
      +-- If createUserAccount == true and username provided:
      |       UserService.usernameExists(username)
      |               +-- UserRepository.existsByUsername(username)  [DB READ]
      |               -> Adds field error if username taken
      |
      +-- ApplicationService.existsByEmailAndClassAndYear(email, class, year)
      |       +-- applicationRepository.existsByEmailAndAppliedClassAndAcademicYear(...)  [DB READ]
      |
      +-- If bindingResult.hasErrors() -> return "admin/register-student"
      |
      +-- ApplicationService.adminRegisterStudent(dto)
              |
              +-- applicationRepository.existsByEmailAndAppliedClassAndAcademicYear(...)  [DB READ]
              |   -> Service-level duplicate guard
              |
              +-- If createUserAccount == true:
              |       UserService.registerUser(username, password, fullName, ROLE_STUDENT)
              |               +-- UserRepository.existsByUsername(username)  [DB READ]
              |               +-- BCryptPasswordEncoder.encode(password)
              |               +-- UserRepository.save(user)  [DB WRITE]
              |
              +-- generateNextApplicationNumber()  [DB READ x2]
              |
              +-- If initialStatus == "APPROVED":
              |       +-- resolveAssignedSection(...)  [DB READ x1 to 26]
              |       +-- generateNextPermanentRollNumber()  [DB READ x2]
              |       +-- app.setStatus(APPROVED) + setDecisionDate(now)
              |
              +-- Else: app.setStatus(PENDING)
              |
              +-- applicationRepository.save(app)  [DB WRITE]

  Result:
    Redirects to /admin/applications/{createdId} with successMessage

---

### [Q] GET /admin/reports
Purpose: Admin generates class/section enrollment reports for approved students

  AdminController.classSectionReports(className, section, Model)
      |
      +-- ApplicationService.getReport(className, section)
      |       +-- applicationRepository.findApprovedStudentsForReport(filterClass, filterSec)  [DB READ - JPQL]
      |           -> SELECT FROM admission_applications WHERE status=APPROVED
      |              AND (className IS NULL OR assignedClass = className)
      |              AND (section IS NULL OR assignedSection = section)
      |              ORDER BY assignedClass, assignedSection, permanentRollNumber
      |
      +-- ApplicationService.getAvailableClasses()
      |       +-- applicationRepository.findDistinctAssignedClasses()  [DB READ - JPQL]
      |               -> SELECT DISTINCT assignedClass WHERE status=APPROVED
      |
      +-- ApplicationService.getAvailableSections()
      |       +-- applicationRepository.findDistinctAssignedSections()  [DB READ - JPQL]
      |               -> SELECT DISTINCT assignedSection WHERE status=APPROVED
      |
      +-- Returns "admin/reports" view with students list + filter dropdowns

---

### [R] GET /student/dashboard
Purpose: Student sees their own admission application(s) and status

  StudentController.dashboard(UserDetails, Model)
      |
      +-- [SECURITY] JwtAuthenticationFilter validates jwt_token cookie
      |       -> Sets SecurityContextHolder authentication (ROLE_STUDENT)
      |
      +-- @AuthenticationPrincipal UserDetails -> username
      |
      +-- UserService.findByUsername(username)
      |       +-- UserRepository.findByUsername(username)  [DB READ]
      |               -> Redirects to /login?logout=true if user not found
      |
      +-- ApplicationService.getApplicationForUser(user)
      |       +-- applicationRepository.findFirstByUserIdOrderByAppliedDateDesc(userId)  [DB READ]
      |               -> Most recent application for this student
      |
      +-- ApplicationService.getAllApplicationsForUser(user)
      |       +-- applicationRepository.findByUserIdOrderByAppliedDateDesc(userId)  [DB READ]
      |               -> Full application history for this student
      |
      +-- Returns "student/dashboard" view

---

## ==================================================
## API GATEWAY FLOW  (Port 8080)
## ==================================================

### JWT Global Filter  (JwtAuthenticationFilter - runs on EVERY request)

  Incoming Request -> JwtAuthenticationFilter.filter(exchange, chain)
      |
      +-- Check path against PUBLIC_ENDPOINTS whitelist:
      |       /auth/**, /login, /register, /track, /css/**, /js/**,
      |       /images/**, /h2-console/**, /actuator/**, /fallback/**
      |       -> If matches: chain.filter(exchange)  [PASS THROUGH, no token check]
      |
      +-- Read "Authorization: Bearer <token>" header
      |
      +-- If Bearer token present:
      |       +-- JJWT: parse + verify HMAC-SHA256 signature
      |       +-- Extract claims: subject=username, role
      |       +-- Mutate request headers:
      |       |       X-Auth-Username = <username>
      |       |       X-Auth-Role     = <role>
      |       +-- Forward mutated request to downstream service
      |
      +-- If token validation fails (expired, tampered, malformed):
      |       +-- REST client (JSON accept or /api/ path) -> HTTP 401 JSON response
      |       +-- Browser request -> Falls through; downstream handles redirect
      |
      +-- If no token at all:
              -> Pass through; downstream decides (e.g. redirect to /login)

  Routes:
    /auth/** -> lb://AUTH-SERVICE        (port 8082, via Eureka)
    /**      -> lb://ADMISSION-SERVICE   (port 8081, with CircuitBreaker)
                On failure -> forward:/fallback/admission -> FallbackController

---

## ==================================================
## ENTITY RELATIONSHIP SUMMARY
## ==================================================

### admission-service DB (./data/admissiondb)

  TABLE: users
    id             PK AUTO
    username       UNIQUE
    password       BCrypt hashed
    full_name
    role           ROLE_STUDENT | ROLE_ADMIN
    enabled
    created_at

  TABLE: admission_applications
    id                    PK AUTO
    application_number    UNIQUE  (e.g. APP-2026-1001)
    academic_year
    permanent_roll_number UNIQUE  (e.g. ADM-2026-001, set on APPROVE)
    student_name
    email
    phone
    date_of_birth
    gender
    address
    applied_class
    assigned_class        (set on APPROVE)
    assigned_section      (set on APPROVE, max 30 per section)
    previous_school
    marks_percentage
    status                PENDING | APPROVED | REJECTED
    remarks
    registered_by         STUDENT | ADMIN
    applied_date
    decision_date         (set on APPROVE/REJECT)
    user_id               FK -> users.id

### auth-service DB (./data/authdb)

  TABLE: users
    id          PK AUTO
    username    UNIQUE
    password    BCrypt hashed
    email       UNIQUE
    full_name
    role        ROLE_STUDENT | ROLE_ADMIN
    enabled
    created_at

  TABLE: refresh_tokens
    id           PK AUTO
    token        UNIQUE UUID string
    user_id      FK -> users.id  (OneToOne)
    expiry_date  Instant  (7 days from issuance)
    revoked      boolean  (false on create; row deleted on logout)

---

## ==================================================
## SECURITY LAYER SUMMARY
## ==================================================

### auth-service Security

  Request -> JwtAuthenticationFilter (OncePerRequestFilter)
      -> Reads Authorization: Bearer header
      -> Validates via JwtTokenProvider.validateToken()
      -> If valid: CustomUserDetailsService.loadUserByUsername()
          -> UserRepository.findByUsername()  [DB READ]
      -> Sets SecurityContextHolder authentication

  SecurityFilterChain config:
    Session policy : STATELESS (no JSESSIONID)
    CSRF           : disabled
    Public paths   : /auth/login, /auth/register, /auth/refresh,
                     /auth/validate, /actuator/**, /h2-console/**
    Protected      : /auth/me, /auth/logout  (valid Bearer token required)

### admission-service Security

  Request -> JwtAuthenticationFilter (OncePerRequestFilter)
      -> Reads jwt_token cookie OR Authorization: Bearer header
      -> Validates via JwtUtils.validateToken()
      -> If valid: CustomUserDetailsService.loadUserByUsername()
          -> UserRepository.findByUsername()  [DB READ]
      -> If X-Auth-Username header present (forwarded by Gateway):
          -> Builds synthetic UserDetails without DB call

  SecurityFilterChain config:
    Session policy       : STATELESS (no JSESSIONID cookie)
    CSRF                 : disabled
    Public paths         : /, /login, /register, /track, /css/**,
                           /js/**, /images/**, /h2-console/**, /actuator/**
    ROLE_ADMIN required  : /admin/**
    ROLE_STUDENT required: /student/**
