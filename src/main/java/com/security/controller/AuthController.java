package com.security.controller;

import com.security.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CustomUserDetailsService customUserDetailsService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/api/login")
    public ResponseEntity<?> handleLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request
    ) {
        log.info("Invoke handleLogin method for email : {}", username);
        try {
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            if (!passwordEncoder.matches(password, userDetails.getPassword())) {
                log.info("Password is not match for email : {}", username);
                return ResponseEntity.ok().header("HX-Retarget", "#error-message")
                        .body("<div class='alert alert-danger'>Invalid email or password</div>");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            password
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext()
            );

            return ResponseEntity.ok()
                    .header("HX-Redirect", "/dashboard")
                    .build();
        } catch (AuthenticationException ex) {
            log.info("Error while login", ex);
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid username or password"
                    ));
        }
    }
}
