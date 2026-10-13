package com.example.DALN.controller;

import com.example.DALN.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chatWithAi(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("reply", "Vui lòng nhập câu hỏi!"));
        }

        String reply = geminiService.askAssistant(message);
        return ResponseEntity.ok(Map.of("reply", reply));
    }
}