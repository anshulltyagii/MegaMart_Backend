package com.ecommerce.controller;

import com.ecommerce.dto.InventoryResponse;
import com.ecommerce.enums.UserRole;
import com.ecommerce.model.User;
import com.ecommerce.service.InventoryService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * INVENTORY CONTROLLER - GET is public 
 * - Stock changes allowed only for ADMIN + SHOPKEEPER
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	// ------------------------------------------------------------
	// UTILITY: CHECK ADMIN OR SHOPKEEPER
	// ------------------------------------------------------------
	private void allowAdminOrShopkeeper(HttpServletRequest req) {
		User currentUser = (User) req.getAttribute("currentUser");

		if (currentUser == null)
			throw new RuntimeException("Unauthorized");

		UserRole role = currentUser.getRole();

		if (role != UserRole.ADMIN && role != UserRole.SHOPKEEPER) {
			throw new RuntimeException("Access denied: Only ADMIN or SHOPKEEPER can manage inventory.");
		}
	}

	// ------------------------------------------------------------
	// GET INVENTORY STATUS (PUBLIC)
	// ------------------------------------------------------------
	@GetMapping("/{productId}")
	public ResponseEntity<InventoryResponse> getInventory(@PathVariable Long productId) {

		InventoryResponse response = inventoryService.getInventory(productId);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// INIT / RESET INVENTORY (ADMIN/SHOPKEEPER)
	// ------------------------------------------------------------
	@PostMapping("/{productId}/init")
	public ResponseEntity<InventoryResponse> initInventory(@PathVariable Long productId, @RequestParam int quantity,
			HttpServletRequest req) {

		allowAdminOrShopkeeper(req);

		InventoryResponse response = inventoryService.createOrInitInventory(productId, quantity);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	// ------------------------------------------------------------
	// ADD STOCK
	// ------------------------------------------------------------
	@PostMapping("/{productId}/add")
	public ResponseEntity<InventoryResponse> addStock(@PathVariable Long productId, @RequestParam int quantity,
			HttpServletRequest req) {

		allowAdminOrShopkeeper(req);

		InventoryResponse response = inventoryService.addStock(productId, quantity);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// DECREASE STOCK
	// ------------------------------------------------------------
	@PostMapping("/{productId}/decrease")
	public ResponseEntity<InventoryResponse> decreaseStock(@PathVariable Long productId, @RequestParam int quantity,
			HttpServletRequest req) {

		allowAdminOrShopkeeper(req);

		InventoryResponse response = inventoryService.decreaseStock(productId, quantity);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// RESERVE STOCK (Checkout)
	// ------------------------------------------------------------
	@PostMapping("/{productId}/reserve")
	public ResponseEntity<InventoryResponse> reserveStock(@PathVariable Long productId, @RequestParam int quantity,
			HttpServletRequest req) {

		allowAdminOrShopkeeper(req);

		InventoryResponse response = inventoryService.reserveStock(productId, quantity);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// RELEASE RESERVED STOCK
	// ------------------------------------------------------------
	@PostMapping("/{productId}/release")
	public ResponseEntity<InventoryResponse> releaseStock(@PathVariable Long productId, @RequestParam int quantity,
			HttpServletRequest req) {

		allowAdminOrShopkeeper(req);

		InventoryResponse response = inventoryService.releaseReserved(productId, quantity);
		return ResponseEntity.ok(response);
	}

	// ------------------------------------------------------------
	// CONSUME RESERVED STOCK (Order Confirm)
	// ------------------------------------------------------------
	@PostMapping("/{productId}/consume")
	public ResponseEntity<InventoryResponse> consumeReserved(@PathVariable Long productId, @RequestParam int quantity,
			HttpServletRequest req) {

		allowAdminOrShopkeeper(req);

		InventoryResponse response = inventoryService.consumeReservedOnOrder(productId, quantity);
		return ResponseEntity.ok(response);
	}
}