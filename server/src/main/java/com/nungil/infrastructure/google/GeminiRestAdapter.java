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

    /** 기존 호환용: systemInstruction 없이 전체 내용을 user prompt로 전송 */
    public String sendRequest(String prompt, String base64Image, String contentType) {
        return sendRequest(null, prompt, base64Image, contentType);
    }

    /** systemInstruction + userMessage 분리 전송 */
    public String sendRequest(String systemInstruction, String userMessage,
                              String base64Image, String contentType) {
        RestTemplate restTemplate = new RestTemplate();
        String finalUrl = BASE_URL + "/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        Map<String, Object> requestBody = new HashMap<>();

        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            requestBody.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", systemInstruction))
            ));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        List<Map<String, Object>> parts = new ArrayList<>();

        parts.add(Map.of("text", userMessage != null ? userMessage : "이 사진에 대해 설명해줘."));

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
            System.out.println("[Gemini] system:\n" + systemInstruction + "\n[Gemini] user:\n" + userMessage);
            Map<String, Object> response = restTemplate.postForObject(finalUrl, requestBody, Map.class);
            String result = parseResponse(response);
            System.out.println("[Gemini] 응답:\n" + result);
            return result;
        } catch (Exception e) {
            System.err.println("[Gemini Error] " + e.getMessage());
            return "{\"answer\": \"AI 통신 중 오류가 발생했습니다.\", \"suggestedQuestions\": []}";
        }
    }

    /** 단계 생성 등 순수 텍스트 응답용 (이미지 없음, 파싱 실패 시 원문 반환) */
    public String generateSteps(String systemInstruction, String userMessage) {
        return sendRequest(systemInstruction, userMessage, null, null);
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
