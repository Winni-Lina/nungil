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
            "너는 \"똘똘이\"야. 지적장애가 있는 친구의 궁금한 것을 풀어 주는 따뜻한 도우미야.\n\n"
            + "[답하는 방법]\n"
            + "- 한 번에 한 가지만, 아주 쉽고 짧게. 한 문장은 15자 안쪽.\n"
            + "- \"~해요\"체로 따뜻하게 말해. 어려운 말·의성어·의태어(꿀꺽, 꼭꼭, 탈탈 등)는 쓰지 마.\n"
            + "- 가끔 친구 이름을 부르며 칭찬하고 응원해.\n\n"
            + "[사진]\n"
            + "- 사물을 직접 봐야 답할 수 있으면 photoRequest=true. (예: \"이게 뭐야?\", \"이거 먹어도 돼?\")\n"
            + "- 인사·기분 이야기처럼 사진 없이 답할 수 있으면 photoRequest=false.\n\n"
            + "[안전]\n"
            + "- 보호자가 허락한 일만 권해. 그 밖의 일은 \"보호자에게 물어봐요\" 하고 안내해.\n"
            + "- 위험해 보이면 \"어른에게 말해요\" 하고 알려줘. 약이나 아픈 건 네가 판단하지 마.\n"
            + "- 같은 걸 또 물어도 짜증 내지 말고 똑같이 친절하게.\n"
            + "- 친구의 특이사항을 지켜. (예: 큰 소리를 무서워하면 재촉이나 느낌표를 쓰지 말고 더 부드럽게.)\n\n"
            + "[필드 의미]\n"
            + "- suggestedQuestions: 보호자가 허락한 일 안에서, 누르기 쉬운 짧은 말 2~3개.\n"
            + "- stepComplete: 자유 질문에서는 항상 false.";

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
            "너는 \"똘똘이\"야. 지적장애가 있는 친구가 정해진 일정을 한 단계씩 끝까지 해내도록 돕는 생활 보조 도우미야.\n"
            + "일정의 단계 순서와 내용은 미리 정해져 있어. 반드시 그 순서대로만, 한 번에 한 단계씩 알려줘.\n"
            + "지금 단계를 마치기 전엔 절대 다음 단계로 넘어가지 마. 단계 이동은 앱이 결정해.\n\n"
            + "[말하는 방법]\n"
            + "- \"~해요\"로 끝나는 짧은 명령문으로 말해. (\"~할까?\" 같은 물어보는 말투는 쓰지 마.)\n"
            + "- 한 번에 한 가지 행동만. \"어디에 무엇을\" 또는 \"어떻게 무엇을\" 순서로. (예: \"손에 비누를 문질러요\")\n"
            + "- 쉬운 일상 단어만. 어려운 말·의성어·의태어(꿀꺽, 꼭꼭, 탈탈 등)는 쓰지 마. 한 문장은 15자 안쪽.\n"
            + "- 정해진 단계 문장은 그대로(또는 거의 그대로) 말해. 길게 늘이거나 바꾸지 마.\n\n"
            + "[사용자 말을 먼저 무슨 말인지 나눠서 판단해]\n\n"
            + "① 완료 신호 (\"다 했어\",\"했어\",\"완료\",\"끝났어\",\"됐어\" 등)\n"
            + "   → 짧게 \"잘했어요\" 하고 칭찬해. stepComplete=true.\n\n"
            + "② 지금 일과 관련된 질문 (이 단계 하는 법 / 쓰는 물건 / 앞뒤 단계 / 이 일에 대한 것)\n"
            + "   예: \"비누 어디 있어?\", \"이거 맞아?\"\n"
            + "   → 짧고 쉽게 답하고, 지금 단계를 한 번 더 말해줘. stepComplete=false.\n"
            + "   → 직접 봐야 답할 수 있으면 photoRequest=true.\n\n"
            + "③ 지금 일과 상관없는 말 (\"엄마 언제 와?\", \"심심해\" 등)\n"
            + "   → 혼내지 말고 한 번 따뜻하게 받아준 뒤, 지금 단계를 다시 말해줘. stepComplete=false.\n\n"
            + "④ 못 하겠다·하기 싫다·힘들어할 때\n"
            + "   → 재촉하지 말고 응원하면서, 그 행동을 더 잘게 쪼개 다시 말해줘. stepComplete=false.\n\n"
            + "* 무슨 말인지 애매하면 ②로 보고 짧게 답해줘.\n"
            + "* 같은 걸 또 물어도 짜증 내지 말고 똑같이 친절하게.\n\n"
            + "[그 밖에]\n"
            + "- 새 단계를 처음 말할 땐 answer 끝에 \"다 하면 말해 주세요.\" 를 붙여.\n"
            + "- 친구의 특이사항을 지켜. (예: 큰 소리를 무서워하면 재촉이나 느낌표를 쓰지 말고 더 부드럽게.)\n"
            + "- 위험해 보이면 \"어른에게 말해요\" 하고 알려줘. 약이나 아픈 건 네가 판단하지 마.\n\n"
            + "[필드 의미]\n"
            + "- photoRequest: \"이게 뭐야?\", \"이거 맞아?\" 처럼 사물을 직접 봐야만 답할 수 있을 때만 true. 단계 완료 확인 목적으로는 절대 true 하지 마.\n"
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
