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

            // ── 3. 프롬프트 구성 (system / user 분리) ─────────────────────────
            String[] prompts = "schedule".equals(mode)
                    ? buildSchedulePrompt(userContext, historyJson, question,
                                          scheduleTitle, currentStep, stepIndex, totalSteps,
                                          specialNote)
                    : buildChatPrompt(userContext, historyJson, question);

            String systemInstruction = prompts[0];
            String userMessage = prompts[1];

            // ── 4. Gemini 호출 ────────────────────────────────────────────────
            String rawResponse = geminiAdapter.sendRequest(systemInstruction, userMessage, base64Image, contentType);

            // ── 5. 응답 파싱 ──────────────────────────────────────────────────
            //   responseSchema로 JSON이 보장되지만, 모델이 가끔 펜스(```json)나
            //   이중 JSON을 섞어 answer 안에 중괄호가 노출되는 경우를 방어한다.
            String cleanJson = rawResponse.replaceAll("(?s)```json|```", "").trim();
            Map<String, Object> aiResult = objectMapper.readValue(cleanJson, Map.class);

            // answer 세척: 펜스 제거 + answer가 통째로 JSON이면 안쪽 answer만 재추출
            Object rawAnswer = aiResult.get("answer");
            if (rawAnswer != null) {
                String answer = String.valueOf(rawAnswer)
                        .replaceAll("(?s)```json|```", "").trim();
                if (answer.startsWith("{") && answer.contains("\"answer\"")) {
                    try {
                        Object inner = objectMapper.readValue(answer, Map.class).get("answer");
                        if (inner != null) answer = String.valueOf(inner).trim();
                    } catch (Exception ignore) { /* 파싱 실패 시 원본 유지 */ }
                }
                aiResult.put("answer", answer);
            }

            Map<String, Object> result = new HashMap<>(aiResult);
            result.put("userId", userId);
            result.put("transcribedText", question.equals("....") ? "" : question);
            return result;

        } catch (Exception e) {
            System.err.println("[Orchestrator Error] " + e.getMessage());
            return createErrorResponse();
        }
    }

    // ── 자유 채팅 프롬프트 ────────────────────────────────────────────────────
    private String[] buildChatPrompt(String userContext, String historyJson, String question) {
        String system =
            "너는 \"똘똘이\"야. 지적장애가 있는 친구의 궁금증을 풀어주는 따뜻한 AI 친구야.\n\n"
            + "[답변 규칙]\n"
            + "- 한 번에 한 가지만, 아주 쉽고 짧게.\n"
            + "- 사물을 직접 봐야 답할 수 있으면 photoRequest를 true로 (예: \"이게 뭐야?\", \"어떤 거 먹어?\").\n"
            + "- 인사·감정 표현 등 사진 없이 답할 수 있으면 photoRequest는 false.\n\n"
            + "[말투 규칙]\n"
            + "- 한 문장 15자 이내. 어려운 단어 금지.\n"
            + "- 가끔 친구 이름을 부르며 따뜻하게, 칭찬·응원 자주.\n"
            + "- 특이사항 고려 (예: 큰 소리 무서워함 → 재촉·느낌표 남발 금지).\n\n"
            + "[안전]\n"
            + "- 보호자가 허용한 활동만 권하기. 그 외엔 \"보호자에게 물어보자\".\n"
            + "- 위급해 보이면 \"어른에게 말하자\". 직접 의료·약 판단 금지.\n"
            + "- 같은 질문을 반복해도 친절 유지.\n\n"
            + "[필드 의미]\n"
            + "- suggestedQuestions: 2~3개, 보호자 허용 활동 안에서, 누르기 쉬운 짧은 말.\n"
            + "- stepComplete: chat 모드에선 항상 false.";

        String user = (userContext != null && !userContext.isBlank() ? userContext + "\n\n" : "")
                + "[이전 대화]\n" + safeHistory(historyJson)
                + "\n\n[사용자 질문] " + question;

        return new String[]{ system, user };
    }

    // ── 일정 수행 프롬프트 ────────────────────────────────────────────────────
    // 단계 완료 판단은 클라이언트 키워드 감지가 소유. AI는 안내·질문대응만 함.
    private String[] buildSchedulePrompt(
            String userContext, String historyJson, String question,
            String scheduleTitle, String currentStep,
            int stepIndex, int totalSteps,
            String specialNote) {

        String stepStr = (currentStep != null && !currentStep.isBlank()) ? currentStep : scheduleTitle;

        String system =
            "너는 \"똘똘이\"야. 지적장애가 있는 친구가 정해진 일정을 한 단계씩 끝까지 해내도록 돕는 생활 보조 AI 친구야.\n"
            + "일정의 단계 순서와 내용은 미리 정해져 있어. 반드시 그 순서대로만, 한 번에 한 단계씩 안내해.\n"
            + "지금 단계를 마치기 전엔 절대 다음 단계로 넘어가지 마. 단계 이동은 앱이 결정해.\n\n"
            + "[매 발화마다 4가지 중 하나로 분류하고 행동]\n\n"
            + "① 완료 신호 (\"다 했어\",\"했어\",\"완료\",\"끝났어\",\"됐어\" 등)\n"
            + "   → 짧게 칭찬하고 stepComplete=true.\n\n"
            + "② 일정과 관련된 질문\n"
            + "   = 지금 단계 하는 법 / 도구·물건 / 이 일정 자체에 관한 것\n"
            + "   → 짧고 쉽게 답해주고, 다시 지금 단계로 돌아오게 해. stepComplete=false.\n"
            + "   → 사물을 직접 봐야 답할 수 있으면 photoRequest=true.\n\n"
            + "③ 일정과 관련 없는 말 (\"엄마 언제 와?\", \"심심해\" 등)\n"
            + "   → 따뜻하게 한 번 받아준 뒤, 지금 단계로 부드럽게 유도. stepComplete=false.\n\n"
            + "④ 못 하겠다·하기 싫다·힘들어함\n"
            + "   → 재촉하지 말고 응원하며 더 쉽게 다시 안내. stepComplete=false.\n\n"
            + "* 애매하면 ②로 보고 짧게 답해줘.\n"
            + "* 같은 질문을 또 해도 짜증 없이 똑같이 친절하게.\n\n"
            + "[말투 규칙]\n"
            + "- 한 문장 15자 이내. 한 번에 한 가지만. 어려운 단어 금지.\n"
            + "- 새 단계를 처음 안내할 땐 answer 마지막에 꼭 \"다 하면 말해줘!\"\n"
            + "- 친구의 특이사항을 고려해 말투 조절.\n\n"
            + "[안전] 위급해 보이면 \"어른에게 말하자\"로 안내. 직접 의료·약 판단 금지.\n\n"
            + "[필드 의미]\n"
            + "- photoRequest: 사용자가 \"이게 뭐야?\", \"이거 맞아?\" 처럼 사물을 직접 보여줘야만 답할 수 있을 때만 true. 단계 완료 확인 목적으로는 절대 true 하지 마.\n"
            + "- suggestedQuestions: 일정 수행 중엔 빈 배열([]).";

        StringBuilder user = new StringBuilder();
        if (userContext != null && !userContext.isBlank()) {
            user.append(userContext).append("\n\n");
        }
        if (specialNote != null && !specialNote.isBlank()) {
            user.append("[이번 일정 특이사항] ").append(specialNote).append("\n\n");
        }
        user.append("[일정] ").append(scheduleTitle).append("\n")
            .append("[현재 단계] ").append(stepIndex + 1).append("/").append(totalSteps)
            .append(" — ").append(stepStr).append("\n\n")
            .append("[이전 대화]\n").append(safeHistory(historyJson)).append("\n\n")
            .append("[사용자 말] ").append(question);

        return new String[]{ system, user.toString() };
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
        errorMap.put("photoRequest", false);
        return errorMap;
    }
}
