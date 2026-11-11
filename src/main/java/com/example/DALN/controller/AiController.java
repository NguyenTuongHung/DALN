package com.example.DALN.controller;

import com.example.DALN.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public ResponseEntity chatWithAi(@RequestBody Map request) {
        String message = "";
        if (request != null && request.get("message") != null) {
            message = request.get("message").toString();
        }

        Map res = new HashMap();
        if (message.trim().isEmpty()) {
            res.put("reply", "Vui lòng nhập câu hỏi!");
            return ResponseEntity.badRequest().body(res);
        }

        try {
            String reply = geminiService.askAssistant(message);
            res.put("reply", reply);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("reply", "Dạ hệ thống AI đang bận, bạn vui lòng thử lại sau nhé!");
            return ResponseEntity.ok(res);
        }
    }
}