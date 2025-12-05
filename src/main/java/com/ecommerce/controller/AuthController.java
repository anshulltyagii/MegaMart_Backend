package com.ecommerce.controller;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.LoginRequest;
import com.ecommerce.dto.ResetPasswordRequest;
import com.ecommerce.dto.UserRequest;
import com.ecommerce.dto.UserResponse;
import com.ecommerce.model.User;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    // ---------- REGISTER ----------
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody UserRequest req) {
        UserResponse resp = authService.register(req);
        return ResponseEntity.ok(new ApiResponse<>(true, "User registered successfully", resp));
    }

    // ---------- LOGIN ----------
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody LoginRequest req) {
        String token = authService.login(req);
        return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", token));
    }

    // ---------- CHECK USERNAME ----------
    @GetMapping("/check-username")
    public ResponseEntity<Boolean> checkUsername(@RequestParam String username) {
        return ResponseEntity.ok(!userRepository.existsByUsername(username));
    }

    // ---------- CHECK EMAIL ----------
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(!userRepository.existsByEmail(email));
    }

    // ---------- CHECK PHONE ----------
    @GetMapping("/check-phone")
    public ResponseEntity<Boolean> checkPhone(@RequestParam String phone) {
        return ResponseEntity.ok(!userRepository.existsByPhone(phone));
    }

    // ---------- RESET PASSWORD ----------
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest req) {
        String msg = authService.resetPassword(req);
        return ResponseEntity.ok(msg);
    }

 // ==========================================================
 // ⭐ GET LOGGED-IN USER PUBLIC PROFILE
 // ==========================================================
 @GetMapping("/me")
 public ResponseEntity<ApiResponse<UserResponse>> getLoggedInUser(HttpServletRequest request) {

     User currentUser = (User) request.getAttribute("currentUser");

     if (currentUser == null) {
         return ResponseEntity.status(401)
                 .body(new ApiResponse<>(false, "Unauthorized access", null));
     }

     // Build safe UserResponse (no password, no sensitive data)
     UserResponse resp = new UserResponse();
     resp.setId(currentUser.getId());
     resp.setUsername(currentUser.getUsername());
     resp.setEmail(currentUser.getEmail());
     resp.setFullName(currentUser.getFullName());
     resp.setPhone(currentUser.getPhone());
     resp.setRole(currentUser.getRole().name());
     resp.setAccountStatus(currentUser.getAccountStatus().name());
     resp.setCreatedAt(currentUser.getCreatedAt());

     return ResponseEntity.ok(new ApiResponse<>(true, "User profile fetched", resp));
 }

}
