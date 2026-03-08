package com.accounting.qbo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Handles root path requests and browser artifacts.
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> home() {
        return ResponseEntity.ok(Map.of(
                "application", "QBO Integration Service",
                "version", "1.0.0",
                "authorize", "GET /api/auth/connect",
                "health", "GET /actuator/health",
                "docs", "See README.md for full API reference"
        ));
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
