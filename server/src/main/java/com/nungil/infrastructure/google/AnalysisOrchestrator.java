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
    public Map<String, Object> execute(
            String userId, String historyJson, String userContext,
            MultipartFile voiceFile, MultipartFile imageFile,
            String textPrompt, String mode,
            String scheduleTitle, String currentStep,
            int stepIndex, int totalSteps,
            String specialNote, String stepsJson) {
        try {
            // ── 1. 음성 → 텍스트 변환 (STT) ──────────────────────────────────
            String question = (voiceFile != null && !voiceFile.isEmpty())
                    ? sttClient.transcribe(voiceFile)
                    : textPrompt;

            if (question == null || question.trim().isEmpty()) question = "....";

            // ── 2. 이미지 처리 ────────────────────────────────────────────────
            String base64Image = null;
            String contentType = "image/jpeg";
            if (imageFile != null && !imageFile.isEmpty()) {
                base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
                contentType = imageFile.getContentType();
            }

            // ── 3. 프롬프트 구성 ──────────────────────────────────────────────
            String prompt = "schedule".equals(mode)
                    ? buildSchedulePrompt(userContext, historyJson, question,
                                          scheduleTitle, currentStep, stepIndex, totalSteps,
                                          specialNote, stepsJson)
                    : buildChatPrompt(userContext, historyJson, question);

            // ── 4. Gemini 호출 ────────────────────────────────────────────────
            String rawResponse = geminiAdapter.sendRequest(prompt, base64Image, contentType);

            // ── 5. 응답 파싱 ──────────────────────────────────────────────────
            String cleanJson = rawResponse.replaceAll("(?s)```json|```", "").trim();
            Map<String, Object> aiResult = objectMapper.readValue(cleanJson, Map.class);

            Map<String, Object> result = new HashMap<>(aiResult);
            result.put("userId", userId);
            result.put("transcribedText", question.equals("....") ? "" : question);
            return result;

        } catch (Exception e) {
            System.err.println("[Orchestrator Error] " + e.getMessage());
            return createErrorResponse();
        }
    }

    // ── 일반 채팅 프롬프트 ────────────────────────────────────────────────────
    private String buildChatPrompt(String userContext, String historyJson, String question) {
        return """
                ## 역할
                당신은 지적 장애인의 일상생활을 돕는 따뜻한 AI 친구 '똘똘이'입니다.

                ## 말투 규칙
                - 한 문장은 15자 이내로 짧게
                - 어려운 단어 절대 사용 금지
                - 따뜻하고 응원하는 말투
                - 한 번에 한 가지만 말하기

                ## 사진 요청 규칙
                - 상황을 직접 봐야 더 잘 도울 수 있다고 판단되면 photoRequest를 true로 설정

                ## 사용자 정보
                """
                + (userContext != null && !userContext.isBlank() ? userContext : "(정보 없음)")
                + """


                ## 이전 대화
                """
                + safeHistory(historyJson)
                + """


                ## 지금 사용자가 한 말
                "
                """ + question + """
                "

                반드시 아래 JSON 형식으로만 응답해. 다른 말 하지 마:
                {
                  "answer": "짧고 따뜻한 답변",
                  "suggestedQuestions": ["질문1", "질문2", "질문3"],
                  "stepComplete": false,
                  "photoRequest": false
                }
                """;
    }

    // ── 일정 실행 프롬프트 ────────────────────────────────────────────────────
    private String buildSchedulePrompt(
            String userContext, String historyJson, String question,
            String scheduleTitle, String currentStep,
            int stepIndex, int totalSteps,
            String specialNote, String stepsJson) {

        // 단계 목록 텍스트 변환
        String stepsList = parseStepsList(stepsJson, scheduleTitle);
        String stepStr   = (currentStep != null && !currentStep.isBlank()) ? currentStep : scheduleTitle;

        // 특이사항 + 사용자 컨텍스트 합치기
        String contextSection = "";
        if (userContext != null && !userContext.isBlank()) {
            contextSection += "\n## 사용자 정보\n" + userContext + "\n";
        }
        if (specialNote != null && !specialNote.isBlank()) {
            contextSection += "\n## 이번 일정 특이사항\n" + specialNote + "\n";
        }

        return "## 역할\n"
                + "당신은 지적 장애인이 일정을 스스로 완수할 수 있도록 단계별로 안내하는 생활 보조 AI입니다.\n\n"
                + "## 핵심 행동 원칙\n"
                + "### 단계 안내\n"
                + "- 반드시 한 번에 하나의 단계만 안내합니다.\n"
                + "- 사용자가 완료 신호를 보내기 전까지 절대 다음 단계로 넘어가지 않습니다.\n"
                + "- 완료 신호 예시: \"다 했어\", \"했어\", \"완료\", \"끝났어\", \"됐어\", \"다 했어요\"\n"
                + "- 완료 신호가 명확하면 → stepComplete: true, 짧게 칭찬 후 다음 단계 예고\n"
                + "- 완료 신호가 없거나 불분명하면 → stepComplete: false, 현재 단계 재안내\n"
                + "- 마지막 단계 완료 시 → 전체 완료 축하 메시지\n\n"
                + "### 관련 없는 질문 대응\n"
                + "- \"지금은 [현재 단계]를 먼저 해볼까요?\" 라고 유도\n\n"
                + "## 말투 규칙\n"
                + "- 한 문장 15자 이내\n"
                + "- 어려운 단어 금지\n"
                + "- 단계 안내 시 마지막에 반드시 \"다 하면 말해줘!\" 붙이기\n"
                + contextSection + "\n"
                + "## 오늘 일정\n"
                + "일정명: " + scheduleTitle + "\n\n"
                + "전체 단계:\n" + stepsList + "\n\n"
                + "## 지금 진행 중인 단계\n"
                + (stepIndex + 1) + "/" + totalSteps + " 단계: " + stepStr + "\n\n"
                + "## 이전 대화\n"
                + safeHistory(historyJson) + "\n\n"
                + "## 지금 사용자가 한 말\n"
                + "\"" + question + "\"\n\n"
                + "반드시 아래 JSON 형식으로만 응답해. 다른 말 하지 마:\n"
                + "{\n"
                + "  \"answer\": \"안내 메시지\",\n"
                + "  \"stepComplete\": true 또는 false,\n"
                + "  \"suggestedQuestions\": [],\n"
                + "  \"photoRequest\": false\n"
                + "}";
    }

    /** stepsJson 배열 → "1. xxx\n2. xxx" 문자열 변환 */
    private String parseStepsList(String stepsJson, String fallback) {
        if (stepsJson == null || stepsJson.isBlank() || stepsJson.equals("[]")) return fallback;
        try {
            com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(stepsJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                sb.append(i + 1).append(". ").append(arr.get(i).asText()).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** historyJson이 비어있거나 파싱 불가면 "(대화 없음)" 반환 */
    private String safeHistory(String historyJson) {
        if (historyJson == null || historyJson.isBlank() || historyJson.equals("[]")) {
            return "(대화 없음)";
        }
        return historyJson;
    }

    private Map<String, Object> createErrorResponse() {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("answer", "잠깐, 다시 한 번 말해줄래?");
        errorMap.put("suggestedQuestions", java.util.Arrays.asList("다시 말해볼게요"));
        errorMap.put("stepComplete", false);
        errorMap.put("photoRequest", false);
        return errorMap;
    }
}
