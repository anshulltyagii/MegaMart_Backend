package com.ecommerce.config;

import com.ecommerce.model.User;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthInterceptor(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {

        // 1. Allow Options (CORS Pre-flight)
        if (req.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        String path = req.getRequestURI();
        String method = req.getMethod();

        // 2. Public Auth Endpoints (Redundant if WebConfig handles it, but good safety)
        if (path.startsWith("/api/auth") || path.startsWith("/api/otp")) {
            return true;
        }

        // 3. ✅ SMART CHECK: Allow Public BROWSING (GET only)
        // If user is just viewing products/categories, let them pass without token.
        // But if they try to CREATE/UPDATE/DELETE, they fall through to the token check.
        if (method.equalsIgnoreCase("GET")) {
            if (path.startsWith("/api/products") || 
                path.startsWith("/api/categories") || 
                path.startsWith("/api/reviews") || 
                path.startsWith("/api/inventory")) {
                return true;
            }
        }

        // 4. Token Validation (For PUT, POST, DELETE, or Protected GETs)
        String header = req.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            res.setStatus(401);
            res.getWriter().write("Missing or Invalid Authorization Header");
            return false;
        }

        String token = header.substring(7);

        if (!jwtUtil.validate(token)) {
            res.setStatus(401);
            res.getWriter().write("Invalid or Expired Token");
            return false;
        }

        Long userId = jwtUtil.getUserId(token);
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            res.setStatus(401);
            res.getWriter().write("User does not exist");
            return false;
        }

        // 5. Success: Attach User to Request
        req.setAttribute("currentUser", user);
        req.setAttribute("currentUserId", user.getId());

        return true;
    }
}