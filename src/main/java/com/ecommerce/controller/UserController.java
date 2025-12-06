package com.ecommerce.controller;

import com.ecommerce.dto.UserRequest;
import com.ecommerce.dto.UserResponse;
import com.ecommerce.enums.UserRole;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.User;
import com.ecommerce.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * USER CONTROLLER - Owner can access only his own profile - Admin can access
 * all users - No hard delete (soft delete only)
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	// Constructor Injection
	public UserController(UserService userService) {
		this.userService = userService;
	}

	// ------------------------------------------------------------
	// Utility: Checks if logged-in user is owner or admin
	// ------------------------------------------------------------
	private void checkAccess(Long targetUserId, HttpServletRequest req) {

		User currentUser = (User) req.getAttribute("currentUser");
		Long currentUserId = currentUser.getId();

		boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
		boolean isOwner = currentUserId.equals(targetUserId);

		if (!isAdmin && !isOwner) {
			throw new BadRequestException("Access Denied: You cannot modify/view another user's data.");
		}
	}

	// ------------------------------------------------------------
	// CREATE USER (Admin only)
	// ------------------------------------------------------------
	@PostMapping
	public ResponseEntity<UserResponse> createUser(@RequestBody UserRequest request, HttpServletRequest req) {

		User currentUser = (User) req.getAttribute("currentUser");

		if (currentUser.getRole() != UserRole.ADMIN) {
			throw new BadRequestException("Only ADMIN can create new users!");
		}

		UserResponse response = userService.createUser(request);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	// ------------------------------------------------------------
	// UPDATE USER — only owner or admin
	// ------------------------------------------------------------
	@PutMapping("/{id}")
	public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody UserRequest request,
			HttpServletRequest req) {

		checkAccess(id, req);

		UserResponse response = userService.updateUser(id, request);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// GET USER BY ID — owner or admin
	// ------------------------------------------------------------
	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUserById(@PathVariable Long id, HttpServletRequest req) {

		checkAccess(id, req);

		UserResponse response = userService.getUserById(id);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// GET ALL ACTIVE USERS — accessible by all logged-in users
	// ------------------------------------------------------------
	@GetMapping("/active")
	public ResponseEntity<List<UserResponse>> getAllActiveUsers() {

		List<UserResponse> list = userService.getAllActiveUsers();
		return ResponseEntity.ok(list);
	}

	// ------------------------------------------------------------
	// GET ALL USERS — Admin only
	// ------------------------------------------------------------
	@GetMapping
	public ResponseEntity<List<UserResponse>> getAllUsers(HttpServletRequest req) {

		User currentUser = (User) req.getAttribute("currentUser");

		if (currentUser.getRole() != UserRole.ADMIN) {
			throw new BadRequestException("Only ADMIN can view all users!");
		}

		List<UserResponse> list = userService.getAllUsers();
		return ResponseEntity.ok(list);
	}

	// ------------------------------------------------------------
	// SOFT DELETE USER — owner or admin
	// ------------------------------------------------------------
	@DeleteMapping("/{id}")
	public ResponseEntity<String> softDeleteUser(@PathVariable Long id, HttpServletRequest req) {

		checkAccess(id, req);

		boolean deleted = userService.softDeleteUser(id);

		if (!deleted) {
			throw new BadRequestException("Unable to delete user!");
		}

		return ResponseEntity.ok("User soft-deleted successfully (status = SUSPENDED)");
	}

	// ------------------------------------------------------------
	// UPDATE ACCOUNT STATUS (Admin only)
	// ------------------------------------------------------------
	@PatchMapping("/{id}/status")
	public ResponseEntity<String> updateAccountStatus(@PathVariable Long id, @RequestParam String status,
			HttpServletRequest req) {

		User currentUser = (User) req.getAttribute("currentUser");

		if (currentUser.getRole() != UserRole.ADMIN) {
			throw new BadRequestException("Only ADMIN can update user status!");
		}

		boolean updated = userService.updateAccountStatus(id, status.toUpperCase());

		if (!updated) {
			throw new BadRequestException("Failed to update user status");
		}

		return ResponseEntity.ok("Account status updated successfully");
	}
	
	// ------------------------------------------------------------
	// CHECK AVAILABILITY (username / email / phone)
	// ------------------------------------------------------------
	@GetMapping("/check")
	public ResponseEntity<String> checkAvailability(
	        @RequestParam(required = false) String username,
	        @RequestParam(required = false) String email,
	        @RequestParam(required = false) String phone
	) {

	    if (username != null && userService.existsByUsername(username)) {
	        return ResponseEntity.ok("USERNAME_TAKEN");
	    }

	    if (email != null && userService.existsByEmail(email)) {
	        return ResponseEntity.ok("EMAIL_TAKEN");
	    }

	    if (phone != null && userService.existsByPhone(phone)) {
	        return ResponseEntity.ok("PHONE_TAKEN");
	    }

	    return ResponseEntity.ok("AVAILABLE");
	}

	
}
