package com.nungil.infrastructure.google;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AnalysisOrchestrator {

    private final GoogleSttClient sttClient;
    private final GeminiRestAdapter geminiAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisOrchestrator(GoogleSttClient sttClient, GeminiRestAdapter geminiAdapter) {
        this.sttClient = sttClient;
        this.geminiAdapter = geminiAdapter;
    }

    /** 눈길 핵심 파이프라인: 음성/이미지/텍스트를 받아서 최종 분석 결과 반환 */
    public Map<String, Object> execute(String userId, String historyJson, MultipartFile voiceFile, MultipartFile imageFile, String textPrompt) {
        try {
            // 1. 음성 데이터 처리
            String finalQuestion = (voiceFile != null && !voiceFile.isEmpty())
                    ? sttClient.transcribe(voiceFile)
                    : textPrompt;

            if (finalQuestion == null || finalQuestion.trim().isEmpty()) {
                finalQuestion = "....";
            }

            // 2. 이미지 데이터 처리
            String base64Image = null;
            String contentType = "image/jpeg";

            if (imageFile != null && !imageFile.isEmpty()) {
                base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
                contentType = imageFile.getContentType();
                System.out.println("[STT] 업로드된 파일 타입: " + contentType);
            }

            // 3. Gemini 호출
            String prompt = String.format("""
                너는 시각 보조 AI '똘똘'이야. 지적 장애 사용자를 위해 아주 쉽고 친절하게 설명해줘.
                만약 너가 사진이 필요하다고 생각하면, photoRequest를 true로 보내주고 사용자에게 사진을 찍어달라 부탁해주고 suggestedQuestions을 빈 리스트로 보내줘
                이전 대화 내용: %s
                사용자 질문: "%s"
                반드시 아래 JSON 형식으로만 응답해:
                {
                  "answer": "친절한 설명 내용",
                  "suggestedQuestions": ["짧은 질문1", "짧은 질문2", "짧은 질문3"]
                  "photoRequest": 필요 여부(true or false)
                }
                """, historyJson, finalQuestion);

            String rawResponse = geminiAdapter.sendRequest(prompt, base64Image, contentType);

            // 4. 응답 파싱
            String cleanJson = rawResponse.replaceAll("```json|```", "").trim();
            Map<String, Object> aiResult = objectMapper.readValue(cleanJson, Map.class);

            Map<String, Object> finalResult = new HashMap<>(aiResult);
            finalResult.put("userId", userId);
            finalResult.put("transcribedText", finalQuestion);

            return finalResult;

        } catch (Exception e) {
            System.err.println("[Orchestrator Error] " + e.getMessage());
            return createErrorResponse("분석 중 오류가 발생했습니다.");
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("answer", message);
        errorMap.put("suggestedQuestions", java.util.Arrays.asList("다시 시도해볼까요?"));
        return errorMap;
    }
}
