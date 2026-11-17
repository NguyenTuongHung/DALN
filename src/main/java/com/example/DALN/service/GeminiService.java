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
        boolean isAskingForCourt = isCourtSearchQuery(userMessage);

        // Nếu chưa cấu hình Key hoặc Key trống, dùng ngay bộ lọc database cục bộ
        if (apiKey == null || apiKey.isBlank() || "YOUR_GEMINI_API_KEY".equals(apiKey) || apiKey.startsWith("YOUR_")) {
            return localSmartResponse(userMessage, activeCourts, isAskingForCourt);
        }

        try {
            StringBuilder courtsContext = new StringBuilder();
            if (isAskingForCourt) {
                for (Court c : activeCourts) {
                    String fullUrl = baseUrl + "/san-bong/" + c.getId();
                    String priceStr = (c.getPricePerHour() != null)
                            ? String.format("%,.0f VNĐ/giờ", c.getPricePerHour())
                            : "Liên hệ";

                    courtsContext.append("- ID: ").append(c.getId())
                            .append(" | Tên sân: '").append(c.getName() != null ? c.getName() : "Chưa cập nhật")
                            .append("' | Môn thể thao: ").append(normalizeSportLabel(c.getSportType()))
                            .append(" | Địa chỉ: ").append(c.getAddress() != null ? c.getAddress() : "Đang cập nhật")
                            .append(" | Giá: ").append(priceStr)
                            .append(" | Link xem chi tiết: ").append(fullUrl)
                            .append("\n");
                }
            }

            String promptText = "Bạn là 'SportHub AI' - Trợ lý ảo CSKH thông minh của hệ thống đặt sân thể thao SportHub.\n" +
                    (isAskingForCourt 
                        ? "Khách hàng đang muốn TÌM SÂN hoặc ĐẶT SÂN. Hãy lọc đúng chính xác môn thể thao khách yêu cầu từ danh sách sân dưới đây. Khi giới thiệu sân, BẮT BUỘC chèn đường link dạng chuẩn Markdown: [Xem chi tiết & đặt sân ngay](URL_SÂN).\n\nDANH SÁCH SÂN:\n" + courtsContext.toString()
                        : "Khách hàng đang hỏi về chính sách, quy định hủy lịch, hoàn tiền, đăng ký đối tác hoặc trò chuyện giao lưu. Hãy tư vấn lịch sự, chuyên nghiệp bằng tiếng Việt, KHÔNG liệt kê danh sách sân.\n\n") +
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
                    .timeout(Duration.ofSeconds(10))
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
            // Nếu gọi API lỗi, tự động chuyển sang xử lý cục bộ ngay lập tức để không làm gián đoạn trải nghiệm demo
            System.err.println("Gemini API call failed, falling back to local engine: " + e.getMessage());
            return localSmartResponse(userMessage, activeCourts, isAskingForCourt);
        }

        // Nếu API trả về rỗng, dùng kết quả cục bộ
        return localSmartResponse(userMessage, activeCourts, isAskingForCourt);
    }

    private boolean isCourtSearchQuery(String message) {
        if (message == null) return false;
        String clean = removeAccents(message).toLowerCase(Locale.ROOT);
        
        if (clean.contains("chinh sach") || clean.contains("huy") || clean.contains("hoan tien") 
                || clean.contains("doi lich") || clean.contains("dang ky") || clean.contains("lien he") 
                || clean.contains("chao") || clean.contains("cam on")) {
            return false;
        }

        return clean.contains("san bong") || clean.contains("san pickleball") || clean.contains("san cau long") 
                || clean.contains("san tennis") || clean.contains("san bong ro") || clean.contains("tim san") 
                || clean.contains("dat san") || clean.contains("con trong") || clean.contains("co so nao")
                || clean.contains("san nao") || clean.contains("dat lich");
    }

    private List<Court> getActiveCourts() {
        List<Court> all = courtRepository.findAll();
        if (all == null) return Collections.emptyList();
        return all.stream()
                .filter(c -> c.getStatus() == null || "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .collect(Collectors.toList());
    }

    // Xử lý thông minh cục bộ (vừa trả lời chính sách, vừa tra cứu sân chính xác 100%)
    private String localSmartResponse(String message, List<Court> activeCourts, boolean isAskingForCourt) {
        if (!isAskingForCourt) {
            String lower = message.toLowerCase();
            if (lower.contains("chinh sach") || lower.contains("huy") || lower.contains("hoan tien")) {
                return "Dạ, về chính sách của SportHub: Nếu bạn muốn hủy hoặc dời lịch sân đã đặt, vui lòng liên hệ trước ít nhất 2 tiếng để được hỗ trợ bảo lưu chi phí hoặc đổi giờ chơi bạn nhé!";
            }
            if (lower.contains("chu san") || lower.contains("doi tac") || lower.contains("dang ky")) {
                return "Dạ, để đăng ký trở thành chủ sân đối tác của SportHub, bạn vui lòng liên hệ qua hotline hoặc email của hệ thống để được hướng dẫn chi tiết ạ!";
            }
            return "Dạ, SportHub luôn sẵn sàng hỗ trợ bạn mọi thông tin về đặt sân cũng như các chính sách của hệ thống. Bạn cần tư vấn chi tiết nội dung nào ạ?";
        }

        String requestedSport = detectSportFromQuery(message);
        List<Court> matchedCourts;

        if (requestedSport != null) {
            matchedCourts = activeCourts.stream()
                    .filter(c -> isSportMatch(c.getSportType(), c.getName(), requestedSport))
                    .collect(Collectors.toList());

            if (matchedCourts.isEmpty()) {
                return String.format("Dạ xin lỗi bạn, hiện tại hệ thống chưa có sân **%s** nào đang hoạt động.", requestedSport);
            }
        } else {
            matchedCourts = activeCourts;
        }

        StringBuilder reply = new StringBuilder("Dạ, SportHub gợi ý cho bạn cơ sở phù hợp sau:\n\n");
        int limit = Math.min(matchedCourts.size(), 3);
        for (int i = 0; i < limit; i++) {
            Court c = matchedCourts.get(i);
            String fullUrl = baseUrl + "/san-bong/" + c.getId();
            reply.append(String.format("• **%s** (%s)\n  Địa chỉ: %s\n  👉 [Xem chi tiết & đặt sân ngay](%s)\n\n",
                    c.getName(),
                    normalizeSportLabel(c.getSportType()),
                    c.getAddress() != null ? c.getAddress() : "Đang cập nhật",
                    fullUrl));
        }
        return reply.toString().trim();
    }

    private String detectSportFromQuery(String query) {
        if (query == null) return null;
        String clean = removeAccents(query).toLowerCase(Locale.ROOT);
        if (clean.contains("bong da") || clean.contains("football")) return "Bóng đá";
        if (clean.contains("pickleball")) return "Pickleball";
        if (clean.contains("cau long") || clean.contains("badminton")) return "Cầu lông";
        if (clean.contains("bong ro") || clean.contains("basketball")) return "Bóng rổ";
        if (clean.contains("tennis")) return "Tennis";
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
            default -> false;
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