package com.ecommerce.controller;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.CouponRequest;
import com.ecommerce.dto.CouponResponse;
import com.ecommerce.model.Coupon;
import com.ecommerce.service.CouponService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    private final CouponService couponService;

    // Constructor Injection
    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ADMIN/SHOPKEEPER ENDPOINTS (Snigdha's)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * CREATE COUPON
     * TODO: Add role check to ensure only ADMIN or SHOPKEEPER can create
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @RequestBody CouponRequest request,
            HttpServletRequest httpRequest) {
        
        
        
        log.info("POST /api/coupons/create - Creating coupon: {}", 
                request != null ? request.getCode() : "NULL");
        
        CouponResponse coupon = couponService.createCoupon(request);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Coupon created successfully", coupon));
    }

   
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable Long id, 
            @RequestBody CouponRequest request,
            HttpServletRequest httpRequest) {
        
        // TODO: Add role check if needed
        
        log.info("PUT /api/coupons/{} - Updating coupon", id);
        
        CouponResponse coupon = couponService.updateCoupon(id, request);
        
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupon updated successfully", coupon));
    }

    /**
     * DELETE COUPON
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        
        // TODO: Add role check if needed
        
        log.info("DELETE /api/coupons/{} - Deleting coupon", id);
        
        couponService.deleteCoupon(id);
        
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupon deactivated successfully"));
    }

    /**
     * GET COUPON BY ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponById(@PathVariable Long id) {
        log.info("GET /api/coupons/{} - Fetching coupon", id);
        
        CouponResponse coupon = couponService.getCouponById(id);
        
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupon retrieved successfully", coupon));
    }

    /**
     * GET COUPONS BY SHOP
     */
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getCouponsByShop(@PathVariable Long shopId) {
        log.info("GET /api/coupons/shop/{} - Fetching shop coupons", shopId);
        
        List<CouponResponse> coupons = couponService.getCouponsByShop(shopId);
        
        String message = coupons.isEmpty() 
                ? "No coupons found for this shop" 
                : "Found " + coupons.size() + " coupon(s)";
        
        return ResponseEntity.ok(new ApiResponse<>(true, message, coupons));
    }

    /**
     * LIST ALL COUPONS
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> listAllCoupons() {
        log.info("GET /api/coupons/all - Fetching all coupons");
        
        List<CouponResponse> coupons = couponService.getAllCoupons();
        
        String message = coupons.isEmpty() 
                ? "No coupons found" 
                : "Found " + coupons.size() + " coupon(s)";
        
        return ResponseEntity.ok(new ApiResponse<>(true, message, coupons));
    }

    
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Coupon>> validateCoupon(
            @RequestParam String code, 
            @RequestParam Double cartTotal,
            HttpServletRequest request) {
        
        // Extract userId from JWT token
        Long userId = (Long) request.getAttribute("currentUserId");
        
        log.info("GET /api/coupons/validate - User: {} validating coupon: {} with cart: ₹{}", 
                userId, code, cartTotal);
        
        ApiResponse<Coupon> response = couponService.validateCoupon(code, userId, cartTotal);
        
        return ResponseEntity.ok(response);
    }

   
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getActiveCoupons() {
        log.info("GET /api/coupons/active - Fetching active coupons");
        
        List<CouponResponse> allCoupons = couponService.getAllCoupons();
        
        // Filter for active coupons only
        List<CouponResponse> activeCoupons = allCoupons.stream()
                .filter(CouponResponse::isActive)
                .collect(java.util.stream.Collectors.toList());
        
        String message = activeCoupons.isEmpty() 
                ? "No active coupons available" 
                : "Found " + activeCoupons.size() + " active coupon(s)";
        
        return ResponseEntity.ok(new ApiResponse<>(true, message, activeCoupons));
    }
}