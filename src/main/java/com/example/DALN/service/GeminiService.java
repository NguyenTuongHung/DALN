package com.example.DALN.service;

import com.example.DALN.model.Court;
import com.example.DALN.repository.CourtRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

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
        // Fallback nếu chưa cấu hình key
        if (apiKey == null || apiKey.isBlank() || "YOUR_GEMINI_API_KEY".equals(apiKey)) {
            return fallbackResponse(userMessage);
        }

        try {
            // 1. Trích xuất dữ liệu các sân ACTIVE từ database
            List<Court> activeCourts = courtRepository.findAll().stream()
                    .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || c.getStatus() == null)
                    .toList();

            StringBuilder courtsContext = new StringBuilder();
            for (Court c : activeCourts) {
                courtsContext.append(String.format(
                        "- Sân ID %d: '%s' | Môn: %s | Giá: %.0f đ/h | Địa chỉ: %s | Tiện ích: %s | Giờ mở: %s\n",
                        c.getId(), c.getName(), c.getSportType(),
                        c.getPricePerHour() != null ? c.getPricePerHour() : 0,
                        c.getAddress() != null ? c.getAddress() : "",
                        c.getAmenities() != null ? c.getAmenities() : "Tiêu chuẩn",
                        c.getOpenHours() != null ? c.getOpenHours() : "06:00 - 22:30"
                ));
            }

            // 2. Xây dựng System Instruction & Prompt
            String promptText = """
                Bạn là 'SportHub AI' - Trợ lý ảo tư vấn đặt sân thể thao thông minh của sàn SportHub.
                Nhiệm vụ của bạn là giải đáp, tư vấn sân bãi phù hợp với nhu cầu người dùng (bộ môn, ngân sách, khu vực, tiện ích).
                
                QUY TẮC BẮT BUỘC:
                1. Chỉ gợi ý các cụm sân có trong danh sách cơ sở dữ liệu dưới đây. Nếu không tìm thấy sân đúng yêu cầu, hãy báo lịch sự và gợi ý sân gần nhất.
                2. Khi gợi ý sân, hãy cung cấp tên sân, địa chỉ, giá niêm yết và chèn link đặt sân dạng markdown: [Xem chi tiết & Đặt sân](/san-bong/{ID}).
                3. Trả lời thân thiện, ngắn gọn, súc tích bằng tiếng Việt.
                
                DANH SÁCH SÂN HIỆN CÓ TRÊN HỆ THỐNG:
                """ + courtsContext + """
                
                CÂU HỎI CỦA KHÁCH HÀNG:
                """ + userMessage;

            // 3. Chuẩn bị Request Body theo chuẩn Gemini API
            Map<String, Object> textPart = Map.of("text", promptText);
            Map<String, Object> contentPart = Map.of("parts", List.of(textPart));
            Map<String, Object> requestBody = Map.of("contents", List.of(contentPart));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String targetUrl = apiUrl + "?key=" + apiKey;

            ResponseEntity<Map> response = restTemplate.postForEntity(targetUrl, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map body = response.getBody();
                List candidates = (List) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map firstCandidate = (Map) candidates.get(0);
                    Map content = (Map) firstCandidate.get("content");
                    List parts = (List) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map firstPart = (Map) parts.get(0);
                        return (String) firstPart.get("text");
                    }
                }
            }
        } catch (Exception e) {
            // Xử lý khi lỗi mạng hoặc lỗi API key
            return fallbackResponse(userMessage);
        }

        return fallbackResponse(userMessage);
    }

    // Phản hồi dự phòng bằng Rule-based nếu không kết nối được API bên ngoài
    private String fallbackResponse(String message) {
        String lower = message.toLowerCase();
        List<Court> activeCourts = courtRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || c.getStatus() == null)
                .toList();

        for (Court c : activeCourts) {
            if (lower.contains(c.getSportType().toLowerCase()) || lower.contains(c.getName().toLowerCase())) {
                return String.format(
                        "Dạ, SportHub gợi ý bạn cơ sở **%s** (%s) tại %s. Giá thuê từ %,.0f đ/giờ. Bạn có thể bấm [Xem chi tiết & Đặt ngay](/san-bong/%d) nhé!",
                        c.getName(), c.getSportType().toUpperCase(), c.getAddress(), c.getPricePerHour(), c.getId()
                );
            }
        }

        return "Chào bạn! Mình là trợ lý SportHub AI. Bạn đang muốn tìm sân bóng đá, cầu lông hay pickleball ở khu vực nào, tầm giá bao nhiêu để mình hỗ trợ tìm cụm sân thích hợp nhất nhé!";
    }
}