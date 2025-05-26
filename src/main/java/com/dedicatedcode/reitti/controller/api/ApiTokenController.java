package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.ApiTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tokens")
public class ApiTokenController {

    private final ApiTokenService apiTokenService;
    private final UserRepository userRepository;

    @Autowired
    public ApiTokenController(ApiTokenService apiTokenService, UserRepository userRepository) {
        this.apiTokenService = apiTokenService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> createToken(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String tokenName = request.get("name");
        
        if (username == null || tokenName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and token name are required"));
        }
        
        return userRepository.findByUsername(username)
                .map(user -> {
                    ApiToken token = apiTokenService.createToken(user, tokenName);
                    return ResponseEntity.ok(Map.of(
                            "id", token.getId(),
                            "token", token.getToken(),
                            "name", token.getName(),
                            "createdAt", token.getCreatedAt().toString()
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found")));
    }

    @DeleteMapping("/{tokenId}")
    public ResponseEntity<?> deleteToken(@PathVariable Long tokenId) {
        try {
            apiTokenService.deleteToken(tokenId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete token: " + e.getMessage()));
        }
    }
}
