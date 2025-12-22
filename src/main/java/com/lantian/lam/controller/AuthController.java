package com.lantian.lam.controller;

import com.lantian.lam.model.entity.User;
import com.lantian.lam.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lam/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String token = authService.login(loginRequest.getUsername(), loginRequest.getPassword());
        if (token != null) {
            return ResponseEntity.ok(new JwtResponse(token));
        } else {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        User registeredUser = authService.register(user);

        return ResponseEntity.ok(registeredUser);
    }
    
    // 登录请求DTO
    static class LoginRequest {
        private String username;
        private String password;
        
        // getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    // JWT响应DTO
    static class JwtResponse {
        private String token;
        
        public JwtResponse(String token) {
            this.token = token;
        }
        
        // getter and setter
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}
