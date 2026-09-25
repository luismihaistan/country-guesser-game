package com.countryguesser.game.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok("Authenticated as user ID: " + userId);
    }
}