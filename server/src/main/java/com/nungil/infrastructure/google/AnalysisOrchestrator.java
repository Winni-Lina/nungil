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
    public Map<String, Object> execute(String userId, String historyJson, MultipartFile voiceFile, MultipartFile imageFile, String textPrompt,
                                        String mode, String scheduleTitle, String currentStep, int stepIndex, int totalSteps, String specialNote, String stepsJson) {
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
            }

            // 3. Gemini 호출 (mode별 프롬프트 분기)
            boolean isScheduleMode = "schedule".equals(mode);
            String prompt;

            if (isScheduleMode) {
                String noteStr = (specialNote != null && !specialNote.isBlank()) ? "\n## 사용자 특이사항\n" + specialNote + "\n" : "";
                String stepStr = (currentStep != null && !currentStep.isBlank()) ? currentStep : scheduleTitle;

                // stepsJson을 번호 목록으로 변환
                String stepsList = "";
                if (stepsJson != null && !stepsJson.isBlank() && !stepsJson.equals("[]")) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(stepsJson);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.size(); i++) {
                            sb.append(i + 1).append(". ").append(arr.get(i).asText()).append("\n");
                        }
                        stepsList = sb.toString().trim();
                    } catch (Exception ignored) {
                        stepsList = stepsJson;
                    }
                }
                if (stepsList.isBlank()) stepsList = scheduleTitle;

                prompt = String.format("""
                    ## 역할
                    당신은 지적 장애인이 일상 일정을 스스로 수행할 수 있도록 돕는 생활 보조 AI입니다.

                    ## 핵심 행동 원칙
                    ### 1. 단계 안내 방식
                    - 반드시 한 번에 하나의 단계만 안내합니다.
                    - 사용자가 완료 신호를 보내기 전까지 다음 단계로 넘어가지 않습니다.
                    - 완료 신호: "다 했어요", "했어", "완료", "끝났어", "됐어" 등
                    - 완료 신호를 받으면 stepComplete를 true로 응답하고, 짧게 칭찬 후 다음 단계 안내
                    - 완료 신호가 없으면 stepComplete는 false로 응답하세요.
                    - 모든 단계가 끝나면 전체 완료 축하 메시지

                    ### 2. 질문 대응 방식
                    - 일정 관련 질문: 간단히 답변 후 현재 단계로 복귀
                    - 관련 없는 질문: "지금은 [현재 단계]을 먼저 해볼까요?" 유도

                    ## 말투 규칙
                    - 15자 이내 짧은 문장
                    - 어려운 단어 사용 금지
                    - 따뜻하고 응원하는 말투
                    - 단계를 안내할 때는 반드시 마지막에 "다 하면 말해줘!" 를 붙이세요.
                    %s
                    ## 일정명
                    %s

                    ## 일정 목록
                    %s

                    ## 현재 진행 중인 단계
                    %d/%d 단계: %s

                    ## 이전 대화
                    %s

                    ## 사용자 말
                    "%s"

                    반드시 아래 JSON 형식으로만 응답해:
                    {
                      "answer": "안내 메시지",
                      "stepComplete": true 또는 false,
                      "suggestedQuestions": [],
                      "photoRequest": false
                    }
                    """, noteStr, scheduleTitle, stepsList, stepIndex + 1, totalSteps, stepStr, historyJson, finalQuestion);
            } else {
                prompt = String.format("""
                    ## 역할
                    당신은 지적 장애인의 일상생활을 돕는 친절한 AI 도우미입니다.
                    사용자가 이해하기 쉽도록 아주 간단하고 따뜻하게 설명해주세요.

                    ## 말투 규칙
                    - 15자 이내 짧은 문장
                    - 어려운 단어 사용 금지
                    - 따뜻하고 응원하는 말투

                    ## 사진 요청
                    상황을 직접 봐야 더 잘 도울 수 있다고 판단되면 photoRequest를 true로 보내주세요.

                    ## 사용자 정보 및 이전 대화
                    %s

                    ## 사용자 질문
                    "%s"

                    반드시 아래 JSON 형식으로만 응답해:
                    {
                      "answer": "친절한 설명 내용",
                      "suggestedQuestions": ["짧은 질문1", "짧은 질문2", "짧은 질문3"],
                      "stepComplete": false,
                      "photoRequest": 필요 여부(true or false)
                    }
                    """, historyJson, finalQuestion);
            }

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
