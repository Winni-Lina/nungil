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

    private final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String MODEL = "gemini-2.5-flash";

    // ── 채팅/일정 공통 responseSchema (4필드 고정) ──────────────────────────
    private static final Map<String, Object> CHAT_SCHEMA = Map.of(
        "type", "OBJECT",
        "properties", Map.of(
            "answer",             Map.of("type", "STRING"),
            "stepComplete",       Map.of("type", "BOOLEAN"),
            "suggestedQuestions", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
            "photoRequest",       Map.of("type", "BOOLEAN")
        ),
        "required", List.of("answer", "stepComplete", "suggestedQuestions", "photoRequest")
    );

    // ── 단계 생성용 responseSchema (문자열 배열) ────────────────────────────
    private static final Map<String, Object> STEPS_SCHEMA = Map.of(
        "type", "ARRAY",
        "items", Map.of("type", "STRING")
    );

    /** 기존 호환용 (3-arg) */
    public String sendRequest(String prompt, String base64Image, String contentType) {
        return sendRequest(null, prompt, base64Image, contentType);
    }

    /** 채팅/일정 AI 응답: systemInstruction + userMessage, responseSchema 강제 적용 */
    public String sendRequest(String systemInstruction, String userMessage,
                              String base64Image, String contentType) {
        return doRequest(systemInstruction, userMessage, base64Image, contentType, CHAT_SCHEMA);
    }

    /** 단계 생성용: 문자열 배열 responseSchema 적용 */
    public String generateSteps(String systemInstruction, String userMessage) {
        return doRequest(systemInstruction, userMessage, null, null, STEPS_SCHEMA);
    }

    // ── 실제 Gemini REST 호출 ────────────────────────────────────────────────
    private String doRequest(String systemInstruction, String userMessage,
                             String base64Image, String contentType,
                             Map<String, Object> schema) {
        RestTemplate restTemplate = new RestTemplate();
        String finalUrl = BASE_URL + "/models/" + MODEL + ":generateContent?key=" + apiKey;

        Map<String, Object> requestBody = new HashMap<>();

        // systemInstruction (camelCase — Gemini REST 스펙)
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            requestBody.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemInstruction))
            ));
        }

        // contents
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", userMessage != null ? userMessage : "이 사진에 대해 설명해줘."));
        if (base64Image != null && !base64Image.isEmpty()) {
            parts.add(Map.of("inline_data", Map.of(
                "mime_type", contentType != null ? contentType : "image/jpeg",
                "data", base64Image
            )));
        }
        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        content.put("parts", parts);
        requestBody.put("contents", List.of(content));

        // generationConfig: JSON 강제, thinking 비활성, 토큰 제한
        requestBody.put("generationConfig", Map.of(
            "responseMimeType", "application/json",
            "responseSchema",   schema,
            "maxOutputTokens",  512,
            "thinkingConfig",   Map.of("thinkingBudget", 0)
        ));

        try {
            System.out.println("[Gemini] user:\n" + userMessage);
            Map<String, Object> response = restTemplate.postForObject(finalUrl, requestBody, Map.class);
            String result = parseResponse(response);
            System.out.println("[Gemini] 응답:\n" + result);
            return result;
        } catch (Exception e) {
            System.err.println("[Gemini Error] " + e.getMessage());
            return "{\"answer\": \"잠깐, 다시 한 번 말해줄래?\", \"suggestedQuestions\": [], \"stepComplete\": false, \"photoRequest\": false}";
        }
    }

    private String parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            return "{\"answer\": \"응답 형식이 올바르지 않습니다.\", \"suggestedQuestions\": [], \"stepComplete\": false, \"photoRequest\": false}";
        }
    }
}
