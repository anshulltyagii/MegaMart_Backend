package com.ecommerce.service.impl;

import com.ecommerce.dto.OrderItemResponse;
import com.ecommerce.dto.ReturnRequestDTO;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.model.Order;
import com.ecommerce.model.ReturnRequest;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ReturnRepository;
import com.ecommerce.service.InventoryService;
import com.ecommerce.service.ReturnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Service
public class ReturnServiceImpl implements ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnServiceImpl.class);
    private static final int RETURN_WINDOW_DAYS = 7;
    private static final int MIN_REASON_LENGTH = 10;

    private final ReturnRepository returnRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public ReturnServiceImpl(ReturnRepository returnRepository, 
                             OrderRepository orderRepository,
                             InventoryService inventoryService) {
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        
        log.info("════════════════════════════════════════════════════════════");
        log.info("ReturnService Initialized");
        log.info("Return Window: {} days from last order activity", RETURN_WINDOW_DAYS);
        log.info("Policy: Customer-friendly - window extends with admin activity");
        log.info("════════════════════════════════════════════════════════════");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. REQUEST RETURN (User) - WITH ALL EDGE CASES
    // ════════════════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public void requestReturn(Long userId, ReturnRequestDTO requestDto) {
        log.info("Processing return request - User: {}, Order: {}", userId, requestDto.getOrderId());
        
        // ═══════════════════════════════════════════════════════════════════
        // VALIDATION - EDGE CASES 1-3
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 1: Null Order ID
        if (requestDto.getOrderId() == null) {
            throw new BadRequestException("Order ID is required");
        }
        
        // Edge Case 2: Null or blank reason
        if (requestDto.getReason() == null || requestDto.getReason().isBlank()) {
            throw new BadRequestException("Return reason is required");
        }
        
        // Edge Case 3: Reason too short (prevents spam/abuse)
        if (requestDto.getReason().trim().length() < MIN_REASON_LENGTH) {
            throw new BadRequestException(
                "Return reason must be at least " + MIN_REASON_LENGTH + " characters. " +
                "Please provide specific details about the issue."
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // FETCH ORDER - EDGE CASE 4
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 4: Order not found
        Order order = orderRepository.findById(requestDto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Order not found with ID: " + requestDto.getOrderId()
                ));

        // ═══════════════════════════════════════════════════════════════════
        // AUTHORIZATION & STATUS CHECKS - EDGE CASES 5-7
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 5: Wrong user (unauthorized)
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to return this order");
        }
        
        // Edge Case 6: Order not in DELIVERED status
        if (!"DELIVERED".equalsIgnoreCase(order.getStatus())) {
            throw new BadRequestException(
                "Returns are only allowed for delivered orders. Current status: " + order.getStatus()
            );
        }
        
        // Edge Case 7: Order already returned
        if ("RETURNED".equalsIgnoreCase(order.getStatus())) {
            throw new BadRequestException("This order has already been returned");
        }

        // ═══════════════════════════════════════════════════════════════════
        // TIME WINDOW VALIDATION - EDGE CASES 8-9
        // Customer-Friendly Policy: Window based on last order activity
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 8: updatedAt exists (normal case)
        if (order.getUpdatedAt() != null) {
            LocalDateTime lastActivity = order.getUpdatedAt();
            LocalDateTime returnDeadline = lastActivity.plusDays(RETURN_WINDOW_DAYS);
            LocalDateTime now = LocalDateTime.now();
            
            if (now.isAfter(returnDeadline)) {
                long daysSinceActivity = Duration.between(lastActivity, now).toDays();
                throw new BadRequestException(
                    "Return window has closed. Last order activity was " + daysSinceActivity + 
                    " days ago. Returns are accepted within " + RETURN_WINDOW_DAYS + 
                    " days of last activity to ensure fairness during issue resolution."
                );
            }
            
            // Log remaining time for debugging
            long daysRemaining = Duration.between(now, returnDeadline).toDays();
            log.info("Return window valid. {} days remaining for order {}", daysRemaining, order.getId());
            
        } else {
            // Edge Case 9: updatedAt is null (fallback to createdAt)
            log.warn("Order {} has no updatedAt timestamp. Using createdAt as fallback.", order.getId());
            
            if (order.getCreatedAt() != null) {
                // Give extra buffer days since we don't know actual delivery date
                LocalDateTime deadline = order.getCreatedAt().plusDays(RETURN_WINDOW_DAYS + 5);
                if (LocalDateTime.now().isAfter(deadline)) {
                    throw new BadRequestException(
                        "Return window has closed based on order age. " +
                        "Please contact support if you believe this is an error."
                    );
                }
            } else {
                // Edge Case 10: Both timestamps missing (data integrity issue)
                log.error("Order {} has no createdAt or updatedAt timestamps!", order.getId());
                throw new BadRequestException(
                    "Unable to verify return window due to missing order timestamps. " +
                    "Please contact support."
                );
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // DUPLICATE CHECK - EDGE CASE 11
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 11: Return request already exists
        if (returnRepository.existsByOrderId(order.getId())) {
            throw new BadRequestException(
                "A return request already exists for this order. " +
                "Please check your return history or contact support."
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // CREATE RETURN REQUEST
        // ═══════════════════════════════════════════════════════════════════
        
        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrderId(order.getId());
        returnRequest.setReason(requestDto.getReason().trim());
        returnRequest.setStatus("REQUESTED");
        returnRequest.setCreatedAt(LocalDateTime.now());
        
        ReturnRequest saved = returnRepository.save(returnRequest);
        
        log.info("✓ Return request created successfully - ID: {}, Order: {}", saved.getId(), order.getId());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. APPROVE RETURN (Admin/Shopkeeper) - WITH ALL EDGE CASES
    // ════════════════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public void approveReturn(Long returnRequestId) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("Approving return request: {}", returnRequestId);
        log.info("═══════════════════════════════════════════════════════════");
        
        // ═══════════════════════════════════════════════════════════════════
        // FETCH & VALIDATE RETURN REQUEST - EDGE CASES 12-13
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 12: Return request not found
        ReturnRequest req = returnRepository.findById(returnRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Return request not found with ID: " + returnRequestId
                ));

        // Edge Case 13: Return request not in REQUESTED status
        if (!"REQUESTED".equalsIgnoreCase(req.getStatus())) {
            throw new BadRequestException(
                "Cannot approve return. Current status: " + req.getStatus() + 
                ". Only REQUESTED returns can be approved."
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // UPDATE RETURN STATUS
        // ═══════════════════════════════════════════════════════════════════
        
        req.setStatus("APPROVED");
        returnRepository.update(req);
        log.info("✓ Return request status updated to APPROVED");

        // ═══════════════════════════════════════════════════════════════════
        // FETCH ORDER - EDGE CASE 14
        // ═══════════════════════════════════════════════════════════════════
        
        Long orderId = req.getOrderId();
        
        // Edge Case 14: Order not found (data integrity issue)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Order not found with ID: " + orderId + ". This is a data integrity issue."
                ));
        
        log.info("Order details - ID: {}, Amount: ₹{}, Status: {}", 
                order.getId(), order.getTotalAmount(), order.getStatus());

        // ═══════════════════════════════════════════════════════════════════
        // UPDATE ORDER & PAYMENT STATUS
        // ═══════════════════════════════════════════════════════════════════
        
        orderRepository.updateOrderStatus(orderId, "RETURNED");
        log.info("✓ Order status updated to RETURNED");
        
        orderRepository.updatePaymentStatus(orderId, "REFUNDED", "RETURNED");
        log.info("✓ Payment status updated to REFUNDED (Amount: ₹{})", order.getTotalAmount());

        // ═══════════════════════════════════════════════════════════════════
        // RESTOCK INVENTORY - EDGE CASES 15-17
        // CRITICAL: Use try-catch to prevent inventory failures from blocking returns
        // ═══════════════════════════════════════════════════════════════════
        
        log.info("Restocking inventory for order: {}", orderId);
        List<OrderItemResponse> items = orderRepository.findItemsByOrderId(orderId);
        
        // Edge Case 15: No items found (data integrity issue)
        if (items == null || items.isEmpty()) {
            log.warn("⚠ No items found for order {}. Skipping inventory restock.", orderId);
            log.warn("This may indicate a data integrity issue - manual review recommended.");
        } else {
            int successCount = 0;
            int failCount = 0;
            
            for (OrderItemResponse item : items) {
                try {
                    // Edge Case 16: Inventory service throws exception
                    inventoryService.addStock(item.getProductId(), item.getQuantity());
                    successCount++;
                    log.info("  ✓ Product {}: Restocked {} units", item.getProductId(), item.getQuantity());
                    
                } catch (ResourceNotFoundException e) {
                    // Edge Case 17: Product not found in inventory
                    failCount++;
                    log.error("  ✗ Product {}: Not found in inventory - {}", 
                            item.getProductId(), e.getMessage());
                    // Continue with other items - don't fail the entire return
                    
                } catch (Exception e) {
                    // Edge Case 18: Other inventory errors
                    failCount++;
                    log.error("  ✗ Product {}: Restock failed - {}", 
                            item.getProductId(), e.getMessage());
                    // Continue with other items - don't fail the entire return
                }
            }
            
            log.info("✓ Inventory restock summary: {} succeeded, {} failed", successCount, failCount);
            
            if (failCount > 0) {
                log.warn("⚠️ {} item(s) failed to restock. Manual inventory review recommended.", failCount);
                // Note: Return is still approved - inventory issues shouldn't block customer refunds
            }
        }
        
        log.info("═══════════════════════════════════════════════════════════");
        log.info("RETURN APPROVED SUCCESSFULLY");
        log.info("  Return ID: {}", returnRequestId);
        log.info("  Order ID: {}", orderId);
        log.info("  Refund Amount: ₹{}", order.getTotalAmount());
        log.info("  Status: RETURNED | Payment: REFUNDED | Inventory: Processed");
        log.info("═══════════════════════════════════════════════════════════");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. REJECT RETURN (Admin/Shopkeeper) - WITH EDGE CASES
    // ════════════════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public void rejectReturn(Long returnRequestId) {
        log.info("Rejecting return request: {}", returnRequestId);
        
        // ═══════════════════════════════════════════════════════════════════
        // FETCH & VALIDATE - EDGE CASES 19-20
        // ═══════════════════════════════════════════════════════════════════
        
        // Edge Case 19: Return request not found
        ReturnRequest req = returnRepository.findById(returnRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Return request not found with ID: " + returnRequestId
                ));

        // Edge Case 20: Return request not in REQUESTED status
        if (!"REQUESTED".equalsIgnoreCase(req.getStatus())) {
            throw new BadRequestException(
                "Cannot reject return. Current status: " + req.getStatus() + 
                ". Only REQUESTED returns can be rejected."
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // UPDATE STATUS
        // ═══════════════════════════════════════════════════════════════════
        
        req.setStatus("REJECTED");
        returnRepository.update(req);
        
        log.info("✓ Return request {} rejected successfully", returnRequestId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // GETTER METHODS - WITH EDGE CASES
    // ════════════════════════════════════════════════════════════════════════
    
    @Override
    public List<ReturnRequest> getUserReturnRequests(Long userId) {
        log.info("Fetching return requests for user: {}", userId);
        
        // Edge Case 21: Null userId
        if (userId == null || userId <= 0) {
            throw new BadRequestException("Invalid user ID");
        }
        
        List<ReturnRequest> requests = returnRepository.findByUserId(userId);
        log.info("Found {} return requests for user {}", requests.size(), userId);
        
        return requests;
    }

    @Override
    public ReturnRequest getReturnByOrderId(Long userId, Long orderId) {
        log.info("Fetching return for user {} and order {}", userId, orderId);
        
        // Edge Case 22: Null parameters
        if (userId == null || userId <= 0) {
            throw new BadRequestException("Invalid user ID");
        }
        if (orderId == null || orderId <= 0) {
            throw new BadRequestException("Invalid order ID");
        }
        
        // Edge Case 23: Return request not found
        ReturnRequest returnRequest = returnRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No return request found for order ID: " + orderId
                ));
        
        // Edge Case 24: Return belongs to different user (security check)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to view this return request");
        }
        
        return returnRequest;
    }
}