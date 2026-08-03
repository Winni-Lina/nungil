package com.nungil.api.nungil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nungil.domain.chat.ChatLogMapper;
import com.nungil.domain.chat.ChatLogVO;
import com.nungil.infrastructure.google.AnalysisOrchestrator;
import com.nungil.infrastructure.google.GeminiRestAdapter;

@RestController
@RequestMapping("/api/v1/question")
public class NungilAnalyzeController {

    private final AnalysisOrchestrator orchestrator;
    private final GeminiRestAdapter geminiAdapter;
    private final ChatLogMapper chatLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NungilAnalyzeController(AnalysisOrchestrator orchestrator,
                                   GeminiRestAdapter geminiAdapter,
                                   ChatLogMapper chatLogMapper) {
        this.orchestrator = orchestrator;
        this.geminiAdapter = geminiAdapter;
        this.chatLogMapper = chatLogMapper;
    }

    /** 통합 분석 API - 음성/이미지/텍스트 중 있는 것만 전송 */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestPart(value = "userId",        required = false) String id,
            @RequestPart(value = "userIdx",       required = false) String userIdxStr,
            @RequestPart(value = "historyJson",   required = false) String historyJson,
            @RequestPart(value = "userContext",   required = false) String userContext,
            @RequestPart(value = "voiceFile",     required = false) MultipartFile voiceFile,
            @RequestPart(value = "imageFile",     required = false) MultipartFile imageFile,
            @RequestParam(value = "textPrompt",   required = false) String textPrompt,
            @RequestParam(value = "mode",         required = false, defaultValue = "chat") String mode,
            @RequestParam(value = "scheduleId",   required = false, defaultValue = "-1") long scheduleId,
            @RequestParam(value = "scheduleTitle",required = false, defaultValue = "") String scheduleTitle,
            @RequestParam(value = "currentStep",  required = false, defaultValue = "") String currentStep,
            @RequestParam(value = "stepIndex",    required = false, defaultValue = "0") int stepIndex,
            @RequestParam(value = "totalSteps",   required = false, defaultValue = "0") int totalSteps,
            @RequestParam(value = "specialNote",  required = false, defaultValue = "") String specialNote,
            @RequestParam(value = "stepsJson",    required = false, defaultValue = "") String stepsJson) {

        System.out.println("[API] POST /api/v1/question/analyze | mode=" + mode);
        Map<String, Object> response = new HashMap<>();
        String userId = "unknown";

        try {
            if (id != null && !id.isEmpty()) {
                userId = id;
            }
            // userIdx: 폼 파트 없거나 파싱 실패 시 1 (구버전 클라 하위호환)
            int userIdx = 1;
            try {
                if (userIdxStr != null && !userIdxStr.isBlank()) userIdx = Integer.parseInt(userIdxStr.trim());
            } catch (NumberFormatException ignore) {}

            Map<String, Object> result = orchestrator.execute(
                    userId, userIdx, scheduleId, historyJson, userContext, voiceFile, imageFile, textPrompt,
                    mode, scheduleTitle, currentStep, stepIndex, totalSteps, specialNote, stepsJson);

            System.out.println("[결과] 분석 완료 userId=" + userId + ", mode=" + mode);
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", "요청 처리 중 오류 발생: " + e.getMessage());
        }
        return response;
    }

    /** 일정 완료 요약 POST /api/v1/question/summarize
     *  Body: { userId, scheduleTitle, historyJson }
     *  Return: { status: "SUCCESS", message: "완료 요약 메시지" }
     */
    @PostMapping("/summarize")
    public Map<String, Object> summarize(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/question/summarize | userId=" + body.get("userId"));
        Map<String, Object> response = new HashMap<>();
        try {
            String scheduleTitle = body.get("scheduleTitle") != null ? body.get("scheduleTitle").toString() : "일정";
            // 대화 기록: scheduleId 있으면 DB에서 조회, 없으면(구버전 클라) historyJson 폴백
            long scheduleId = -1;
            try {
                if (body.get("scheduleId") != null) scheduleId = Long.parseLong(body.get("scheduleId").toString());
            } catch (NumberFormatException ignore) {}

            String historyText;
            if (scheduleId > 0) {
                historyText = buildHistoryText(chatLogMapper.findBySchedule(scheduleId));
            } else {
                String historyJson = body.get("historyJson") != null ? body.get("historyJson").toString() : "";
                historyText = historyJson.isBlank() ? "(기록 없음)" : historyJson;
            }

            String system = "## 역할\n"
                    + "당신은 지적 장애인이 일정을 잘 마쳤을 때 따뜻하게 칭찬하고\n"
                    + "오늘 한 일을 아주 쉽게 요약해주는 AI 친구입니다.\n\n"
                    + "## 말투 규칙\n"
                    + "- 한 문장 15자 이내, 어려운 단어 금지\n"
                    + "- 먼저 칭찬, 그다음 무엇을 했는지 1~2문장 요약\n"
                    + "- 따뜻하고 신나는 말투\n\n"
                    + "JSON이나 다른 형식 없이, 읽어줄 완료 메시지 텍스트만 출력해.";

            String user = "## 완료한 일정\n" + scheduleTitle + "\n\n"
                    + "## 진행 대화 기록\n"
                    + historyText + "\n\n"
                    + "위 일정을 다 마쳤어. 칭찬과 함께 짧게 요약해줘.";

            String message = geminiAdapter.generateText(system, user);
            if (message != null) {
                // 혹시 모델이 펜스/대괄호를 붙였을 때 방어
                message = message.replaceAll("(?s)```json|```", "").trim();
                if (message.startsWith("[") && message.endsWith("]")) {
                    message = message.substring(1, message.length() - 1)
                            .replaceAll("^\\s*\"|\"\\s*$", "").trim();
                }
            }
            if (message == null || message.isBlank()) {
                message = scheduleTitle + " 다 했어요! 정말 잘했어요!";
            }

            System.out.println("[결과] 요약 생성 완료");
            response.put("status", "SUCCESS");
            response.put("message", message);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", "오늘 일정 다 했어요! 잘했어요!");
        }
        return response;
    }

    /** CHAT_LOG 목록을 "사용자: …\n똘똘이: …" 형태 텍스트로 조립 (완료 요약용) */
    private String buildHistoryText(List<ChatLogVO> logs) {
        if (logs == null || logs.isEmpty()) return "(기록 없음)";
        StringBuilder sb = new StringBuilder();
        for (ChatLogVO log : logs) {
            String who = "user".equals(log.getRole()) ? "사용자" : "똘똘이";
            sb.append(who).append(": ").append(log.getMessage()).append("\n");
        }
        return sb.toString().trim();
    }
}
