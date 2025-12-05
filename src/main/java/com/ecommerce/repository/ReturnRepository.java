package com.ecommerce.repository;

import com.ecommerce.model.ReturnRequest;
import java.util.List;
import java.util.Optional;

public interface ReturnRepository {

    // ✅ Changed return type to ReturnRequest (was void in some versions)
    ReturnRequest save(ReturnRequest returnRequest);

    // ✅ ADDED: Required by ReturnServiceImpl.approveReturn/rejectReturn
    Optional<ReturnRequest> findById(Long id);

    // ✅ ADDED: Required by ReturnServiceImpl.approveReturn/rejectReturn
    void update(ReturnRequest returnRequest);

    List<ReturnRequest> findByUserId(Long userId);
    
    boolean existsByOrderId(Long orderId);
    
    Optional<ReturnRequest> findByOrderId(Long orderId);
    
    List<ReturnRequest> findAll();
}