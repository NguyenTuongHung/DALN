package com.example.DALN.service;

import com.example.DALN.model.Court;
import com.example.DALN.repository.CourtRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String apiUrl;

    private final CourtRepository courtRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public GeminiService(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
    }

    public String askAssistant(String userMessage) {
        if (apiKey == null || apiKey.isBlank() || "YOUR_GEMINI_API_KEY".equals(apiKey) || apiKey.startsWith("YOUR_")) {
            return fallbackResponse(userMessage);
        }

        try {
            List<Court> activeCourts = courtRepository.findAll();
            if (activeCourts == null) {
                activeCourts = Collections.emptyList();
            }

            StringBuilder courtsContext = new StringBuilder();
            for (Court c : activeCourts) {
                courtsContext.append("- ID: ").append(c.getId())
                        .append(" | Tên sân: '").append(c.getName() != null ? c.getName() : "Chưa cập nhật")
                        .append("' | Địa chỉ: ").append(c.getAddress() != null ? c.getAddress() : "Đang cập nhật")
                        .append("\n");
            }

            String promptText = "Bạn là 'SportHub AI' - Trợ lý ảo tư vấn đặt sân thể thao thông minh của sàn Sân Thảo.\n" +
                    "Nhiệm vụ của bạn là giải đáp, tư vấn sân bãi phù hợp với nhu cầu người dùng.\n\n" +
                    "QUY TẮC TRÌNH BÀY BẮT BUỘC:\n" +
                    "1. Trả lời thân thiện, ngắn gọn, súc tích bằng tiếng Việt. Chia ý rõ ràng bằng gạch đầu dòng.\n" +
                    "2. Chỉ gợi ý các cụm sân có thật trong danh sách cơ sở dữ liệu dưới đây.\n" +
                    "3. Khi giới thiệu sân, hãy nêu rõ Tên sân, Địa chỉ và chèn link Markdown chính xác để người dùng bấm vào xem chi tiết: [Xem chi tiết & Đặt ngay](/san-bong/{ID}).\n\n" +
                    "DANH SÁCH SÂN HIỆN CÓ TRÊN HỆ THỐNG:\n" + courtsContext.toString() + "\n" +
                    "CÂU HỎI CỦA KHÁCH HÀNG:\n" + userMessage;

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", promptText);

            Map<String, Object> contentPart = new HashMap<>();
            contentPart.put("parts", Collections.singletonList(textPart));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(contentPart));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity requestEntity = new HttpEntity(requestBody, headers);
            String targetUrl = apiUrl + "?key=" + apiKey;

            ResponseEntity response = restTemplate.postForEntity(targetUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Ép kiểu tường minh tránh lỗi type mismatch từ Object sang Map
                Map body = (Map) response.getBody();
                Object candidatesObj = body.get("candidates");

                if (candidatesObj instanceof List) {
                    List<?> candidates = (List<?>) candidatesObj;
                    if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
                        Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                        Object contentObj = firstCandidate.get("content");

                        if (contentObj instanceof Map) {
                            Map<?, ?> content = (Map<?, ?>) contentObj;
                            Object partsObj = content.get("parts");

                            if (partsObj instanceof List) {
                                List<?> parts = (List<?>) partsObj;
                                if (!parts.isEmpty() && parts.get(0) instanceof Map) {
                                    Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                                    Object textObj = firstPart.get("text");
                                    if (textObj instanceof String) {
                                        return (String) textObj;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return fallbackResponse(userMessage);
        }

        return fallbackResponse(userMessage);
    }

    private String fallbackResponse(String message) {
        List<Court> activeCourts = courtRepository.findAll();
        if (activeCourts != null && !activeCourts.isEmpty()) {
            Court c = activeCourts.get(0);
            return String.format(
                    "Dạ, SportHub gợi ý bạn cơ sở **%s** tại %s. Bạn có thể bấm [Xem chi tiết & Đặt ngay](/san-bong/%d) nhé!",
                    c.getName(), c.getAddress() != null ? c.getAddress() : "Liên hệ", c.getId()
            );
        }

        return "Chào bạn! Mình là trợ lý SportHub AI. Bạn đang muốn tìm sân thể thao nào để mình hỗ trợ nhé!";
    }
}