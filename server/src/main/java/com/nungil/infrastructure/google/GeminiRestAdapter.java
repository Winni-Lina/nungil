package com.nungil.infrastructure.google;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GeminiRestAdapter {

    @Value("${google.ai.api-key}")
    private String apiKey;

    private final String BASE_URL = "https://generativelanguage.googleapis.com/v1";

    public String sendRequest(String prompt, String base64Image, String contentType) {
        RestTemplate restTemplate = new RestTemplate();
        String finalUrl = BASE_URL + "/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();

        parts.add(Map.of("text", prompt != null ? prompt : "이 사진에 대해 설명해줘."));

        if (base64Image != null && !base64Image.isEmpty()) {
            parts.add(Map.of("inline_data", Map.of(
                "mime_type", (contentType != null) ? contentType : "image/jpeg",
                "data", base64Image
            )));
        }

        content.put("parts", parts);
        contents.add(content);
        requestBody.put("contents", contents);

        try {
            System.out.println("[Gemini] 프롬프트 전송:\n" + prompt);
            Map<String, Object> response = restTemplate.postForObject(finalUrl, requestBody, Map.class);
            String result = parseResponse(response);
            System.out.println("[Gemini] 응답:\n" + result);
            return result;
        } catch (Exception e) {
            System.err.println("[Gemini Error] " + e.getMessage());
            return "{\"answer\": \"AI 통신 중 오류가 발생했습니다.\", \"suggestedQuestions\": []}";
        }
    }

    private String parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            return "{\"answer\": \"응답 형식이 올바르지 않습니다.\", \"suggestedQuestions\": []}";
        }
    }
}
