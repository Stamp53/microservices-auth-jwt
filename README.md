# Admission Management System - Microservices Architecture

A resilient, scalable microservices-based Admission Management System developed using **Java 17**, **Spring Boot 3.3.4**, and **Spring Cloud 2023.0.3**.

---

## 🏛️ Architecture Overview

The system consists of 4 distinct services:

1. **Service Registry (`service-registry`)** - Port `8761`:
   - Netflix Eureka Server for centralized service registration, discovery, and heartbeat monitoring.
2. **API Gateway (`api-gateway`)** - Port `8080`:
   - Spring Cloud Gateway acting as the single reverse proxy entry point.
   - Centralized JWT verification filter injecting user identity into downstream headers (`X-Auth-Username`, `X-Auth-Role`).
   - Client-side Load Balancing (`lb://AUTH-SERVICE`, `lb://ADMISSION-SERVICE`) via Spring Cloud LoadBalancer.
   - Fault tolerance & circuit breaking via **Resilience4j**.
   - Custom fallback page on service failure or timeouts.
3. **Auth Service (`auth-service`)** - Port `8082`:
   - Dedicated JWT Authentication Server.
   - Issues short-lived HMAC-SHA256 access tokens and long-lived refresh tokens using **JJWT 0.12.6**.
   - Endpoints for user registration, login, token refresh, validation, and profile retrieval.
   - H2 disk-persisted database with `AUTO_SERVER=TRUE` (`./data/authdb`).
4. **Admission Service (`admission-service`)** - Port `8081`:
   - Core admission business logic, application processing, and reporting.
   - Role-Based Access Control (`ROLE_ADMIN`, `ROLE_STUDENT`).
   - Thymeleaf frontend templates and H2 disk-persisted database with `AUTO_SERVER=TRUE` (`./data/admissiondb`).

---

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.8+

### Build the Entire Project
Run from the root directory:
```bash
mvn clean package -DskipTests
```

### Starting the Services (Order Matters)
Start the services in the following sequence:

1. **Service Registry**:
   ```bash
   cd service-registry
   mvn spring-boot:run
   ```
2. **Auth Service**:
   ```bash
   cd auth-service
   mvn spring-boot:run
   ```
3. **Admission Service**:
   ```bash
   cd admission-service
   mvn spring-boot:run
   ```
4. **API Gateway**:
   ```bash
   cd api-gateway
   mvn spring-boot:run
   ```

---

## 🌐 URLs & Access Points

| Service / Page | URL | Description |
|---|---|---|
| **API Gateway (Main Entry)** | `http://localhost:8080` | Reverse proxy and JWT entry point |
| **Auth Service Direct** | `http://localhost:8082/auth/login` | Direct Auth Server endpoint |
| **Gateway Auth Route** | `http://localhost:8080/auth/login` | Gateway-routed JWT authentication |
| **Student & Admin Login (Web UI)** | `http://localhost:8080/login` | Web UI Login |
| **Track Application** | `http://localhost:8080/track` | Public application status tracker |
| **Student Registration (Web UI)** | `http://localhost:8080/register` | Online admission application form |
| **Eureka Registry Dashboard** | `http://localhost:8761` | Live microservices instance dashboard |
| **Auth H2 Database Console** | `http://localhost:8082/h2-console` | JDBC URL: `jdbc:h2:file:./data/authdb` |
| **Admission H2 Database Console** | `http://localhost:8081/h2-console` | JDBC URL: `jdbc:h2:file:./data/admissiondb` |

---

## 🔐 JWT Auth Server Endpoints

All endpoints are accessible via API Gateway (`http://localhost:8080/auth/...`) or directly on Auth Service (`http://localhost:8082/auth/...`).

### 1. User Login (Obtain JWT Pair)
- **POST** `/auth/login`
- **Request Body:**
  ```json
  {
    "username": "admin",
    "password": "admin123"
  }
  ```
- **Response (`200 OK`):**
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "3b7ef38f-9a1b-426b-9c60-c4e97d8b51d1",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "admin",
    "email": "admin@admission.com",
    "role": "ROLE_ADMIN"
  }
  ```

### 2. User Registration
- **POST** `/auth/register`
- **Request Body:**
  ```json
  {
    "username": "john_doe",
    "password": "password123",
    "email": "john@example.com",
    "fullName": "John Doe",
    "role": "ROLE_STUDENT"
  }
  ```

### 3. Refresh Access Token
- **POST** `/auth/refresh`
- **Request Body:**
  ```json
  {
    "refreshToken": "3b7ef38f-9a1b-426b-9c60-c4e97d8b51d1"
  }
  ```

### 4. Validate Token
- **POST** `/auth/validate`
- **Request Body:**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
  ```
- **Response:**
  ```json
  {
    "valid": true,
    "username": "admin",
    "role": "ROLE_ADMIN",
    "email": "admin@admission.com",
    "message": "Token is valid"
  }
  ```

### 5. Get Current User Profile (Requires Bearer Token)
- **GET** `/auth/me`
- **Header:** `Authorization: Bearer <accessToken>`

### 6. Logout (Revoke Refresh Token)
- **POST** `/auth/logout`
- **Header:** `Authorization: Bearer <accessToken>`

---

## 🔑 Demo Credentials

- **Administrator**: `admin` / `admin123`
- **Approved Student**: `alice` / `student123`
- **Approved Student 2**: `bob` / `student123`
- **Approved Student 3**: `carol` / `student123`
- **Pending Student**: `david` / `student123`
- **Rejected Student**: `emma` / `student123`
