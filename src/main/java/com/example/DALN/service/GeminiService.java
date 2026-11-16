package com.example.DALN.service;

import com.example.DALN.model.Court;
import com.example.DALN.repository.CourtRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String apiUrl;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private final CourtRepository courtRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GeminiService(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
    }

    public String askAssistant(String userMessage) {
        List<Court> activeCourts = getActiveCourts();

        if (apiKey == null || apiKey.isBlank() || "YOUR_GEMINI_API_KEY".equals(apiKey) || apiKey.startsWith("YOUR_")) {
            return fallbackResponse(userMessage, activeCourts);
        }

        try {
            StringBuilder courtsContext = new StringBuilder();
            for (Court c : activeCourts) {
                String fullUrl = baseUrl + "/san-bong/" + c.getId();
                String priceStr = (c.getPricePerHour() != null)
                        ? String.format("%,.0f VNĐ/giờ", c.getPricePerHour())
                        : "Liên hệ";

                courtsContext.append("- ID: ").append(c.getId())
                        .append(" | Tên sân: '").append(c.getName() != null ? c.getName() : "Chưa cập nhật")
                        .append("' | Môn thể thao: ").append(normalizeSportLabel(c.getSportType()))
                        .append(" | Địa chỉ: ").append(c.getAddress() != null ? c.getAddress() : "Đang cập nhật")
                        .append(" | Giá tham khảo: ").append(priceStr)
                        .append(" | Giờ mở cửa: ").append(c.getOpenHours() != null ? c.getOpenHours() : "06:00 - 22:00")
                        .append(" | Đường dẫn đặt sân: ").append(fullUrl)
                        .append("\n");
            }

            String promptText = "Bạn là 'SportHub AI' - Trợ lý ảo tư vấn đặt sân thể thao thông minh của hệ thống SportHub.\n" +
                    "Nhiệm vụ của bạn là tư vấn sân bãi phù hợp nhất với nhu cầu khách hàng.\n\n" +
                    "QUY TẮC BẮT BUỘC:\n" +
                    "1. Trả lời thân thiện, ngắn gọn, tự nhiên bằng tiếng Việt.\n" +
                    "2. PHẢI KIỂM TRA ĐÚNG MÔN THỂ THAO khách hỏi (ví dụ khách hỏi 'sân bóng đá' thì CHỈ được gợi ý các sân có Môn thể thao là Bóng đá, tuyệt đối không gợi ý Pickleball hay Cầu lông).\n" +
                    "3. Chỉ gợi ý các sân có trong DANH SÁCH SÂN bên dưới. Nếu không có sân nào khớp môn thể thao khách tìm, hãy xin lỗi khéo léo và báo hiện tại hệ thống chưa có sân thuộc bộ môn đó.\n" +
                    "4. Khi giới thiệu sân, hãy in đậm **Tên sân**, nêu rõ Địa chỉ, Giá tham khảo và dán trực tiếp đường dẫn URL đầy đủ của sân đó (ví dụ: http://localhost:8080/san-bong/1) để khách bấm vào đặt ngay.\n\n" +
                    "DANH SÁCH SÂN ĐANG HOẠT ĐỘNG TRÊN HỆ THỐNG:\n" +
                    (courtsContext.isEmpty() ? "(Hiện chưa có sân nào hoạt động)\n" : courtsContext.toString()) + "\n" +
                    "CÂU HỎI CỦA KHÁCH HÀNG:\n" + userMessage;

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", promptText);

            Map<String, Object> contentPart = new HashMap<>();
            contentPart.put("parts", Collections.singletonList(textPart));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(contentPart));

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String targetUrl = apiUrl + "?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                    return textNode.asText();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return fallbackResponse(userMessage, activeCourts);
        }

        return fallbackResponse(userMessage, activeCourts);
    }

    private List<Court> getActiveCourts() {
        List<Court> all = courtRepository.findAll();
        if (all == null) return Collections.emptyList();
        return all.stream()
                .filter(c -> c.getStatus() == null || "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .collect(Collectors.toList());
    }

    private String fallbackResponse(String message, List<Court> activeCourts) {
        if (activeCourts.isEmpty()) {
            return "Chào bạn! Hiện tại hệ thống SportHub đang cập nhật danh sách sân. Bạn vui lòng quay lại sau nhé!";
        }

        String requestedSport = detectSportFromQuery(message);
        List<Court> matchedCourts = activeCourts;

        if (requestedSport != null) {
            matchedCourts = activeCourts.stream()
                    .filter(c -> isSportMatch(c.getSportType(), c.getName(), requestedSport))
                    .collect(Collectors.toList());

            if (matchedCourts.isEmpty()) {
                return String.format("Dạ xin lỗi bạn, hiện tại trên SportHub chưa có sân **%s** nào đang trống. Bạn có muốn tham khảo sang bộ môn khác không ạ?", requestedSport);
            }
        }

        StringBuilder reply = new StringBuilder("Dạ, SportHub gợi ý cho bạn cơ sở phù hợp sau:\n\n");
int limit = Math.min(matchedCourts.size(), 2);
for (int i = 0; i < limit; i++) {
    Court c = matchedCourts.get(i);
    String relativeUrl = "/san-bong/" + c.getId();
    reply.append(String.format("• **%s** (%s)\n  Địa chỉ: %s\n  👉 [Xem chi tiết & đặt sân ngay](%s)\n\n",
            c.getName(),
            normalizeSportLabel(c.getSportType()),
            c.getAddress() != null ? c.getAddress() : "Đang cập nhật",
            relativeUrl));
}

return reply.toString().trim();
    }

    private String detectSportFromQuery(String query) {
        if (query == null) return null;
        String clean = removeAccents(query).toLowerCase(Locale.ROOT);
        if (clean.contains("bong da") || clean.contains("football") || clean.contains("san co")) return "Bóng đá";
        if (clean.contains("pickleball") || clean.contains("pickle")) return "Pickleball";
        if (clean.contains("cau long") || clean.contains("badminton")) return "Cầu lông";
        if (clean.contains("bong ro") || clean.contains("basketball")) return "Bóng rổ";
        if (clean.contains("tennis") || clean.contains("quan vot")) return "Tennis";
        return null;
    }

    private boolean isSportMatch(String dbSportType, String courtName, String targetSport) {
        String combined = removeAccents((dbSportType != null ? dbSportType : "") + " " + (courtName != null ? courtName : ""))
                .toLowerCase(Locale.ROOT);
        return switch (targetSport) {
            case "Bóng đá" -> combined.contains("football") || combined.contains("bong da");
            case "Pickleball" -> combined.contains("pickleball") || combined.contains("pickle");
            case "Cầu lông" -> combined.contains("badminton") || combined.contains("cau long");
            case "Bóng rổ" -> combined.contains("basketball") || combined.contains("bong ro");
            case "Tennis" -> combined.contains("tennis");
            default -> true;
        };
    }

    private String normalizeSportLabel(String sportType) {
        if (sportType == null) return "Đa năng";
        String clean = removeAccents(sportType).toLowerCase(Locale.ROOT);
        if (clean.contains("football") || clean.contains("bong da")) return "Bóng đá";
        if (clean.contains("pickleball")) return "Pickleball";
        if (clean.contains("badminton") || clean.contains("cau long")) return "Cầu lông";
        if (clean.contains("basketball") || clean.contains("bong ro")) return "Bóng rổ";
        if (clean.contains("tennis")) return "Tennis";
        return sportType;
    }

    private String removeAccents(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace("đ", "d").replace("Đ", "D");
    }
}