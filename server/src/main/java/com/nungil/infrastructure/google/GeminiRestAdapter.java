package com.nungil.infrastructure.google;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.google.auth.oauth2.GoogleCredentials;

@Component
public class GeminiRestAdapter {

    // Vertex AI(aiplatform) 경로 — 후불 결제(Cloud Billing)로 청구. AI Studio 선불 우회.
    @Value("${google.service-account-key}")
    private Resource serviceAccountKey;

    @Value("${google.vertex.project-id:nungil-cf833}")
    private String projectId;

    @Value("${google.vertex.location:us-central1}")
    private String location;

    private static final String MODEL = "gemini-2.5-flash";

    /** STT와 동일한 서비스 계정을 cloud-platform 스코프로 재활용 */
    private GoogleCredentials credentials;

    private synchronized String accessToken() throws Exception {
        if (credentials == null) {
            try (InputStream is = serviceAccountKey.getInputStream()) {
                credentials = GoogleCredentials.fromStream(is)
                        .createScoped("https://www.googleapis.com/auth/cloud-platform");
            }
        }
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

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

    /** 평문 텍스트 응답: responseSchema 미적용 (완료 요약 등). 실패 시 null 반환 → 호출측 폴백 사용 */
    public String generateText(String systemInstruction, String userMessage) {
        return doRequest(systemInstruction, userMessage, null, null, null);
    }

    // ── 실제 Gemini REST 호출 ────────────────────────────────────────────────
    private String doRequest(String systemInstruction, String userMessage,
                             String base64Image, String contentType,
                             Map<String, Object> schema) {
        RestTemplate restTemplate = new RestTemplate();
        String finalUrl = "https://" + location + "-aiplatform.googleapis.com/v1/projects/"
                + projectId + "/locations/" + location
                + "/publishers/google/models/" + MODEL + ":generateContent";

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

        // generationConfig: schema 있으면 JSON 강제, 없으면 평문. thinking 비활성, 토큰 제한
        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("maxOutputTokens", 512);
        genConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));
        if (schema != null) {
            genConfig.put("responseMimeType", "application/json");
            genConfig.put("responseSchema", schema);
        }
        requestBody.put("generationConfig", genConfig);

        try {
            System.out.println("[Gemini] user:\n" + userMessage);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate
                    .exchange(finalUrl, HttpMethod.POST, entity, Map.class).getBody();
            String result = parseResponse(response);
            System.out.println("[Gemini] 응답:\n" + result);
            return result;
        } catch (Exception e) {
            System.err.println("[Gemini Error] " + e.getMessage());
            if (schema == null) return null;   // 평문 모드: 호출측이 자체 폴백 처리
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
