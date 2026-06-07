package com.nungil.api.nungil;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nungil.infrastructure.google.AnalysisOrchestrator;
import com.nungil.infrastructure.google.GeminiRestAdapter;

@RestController
@RequestMapping("/api/v1/question")
public class NungilAnalyzeController {

    private final AnalysisOrchestrator orchestrator;
    private final GeminiRestAdapter geminiAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NungilAnalyzeController(AnalysisOrchestrator orchestrator,
                                   GeminiRestAdapter geminiAdapter) {
        this.orchestrator = orchestrator;
        this.geminiAdapter = geminiAdapter;
    }

    /** 통합 분석 API - 음성/이미지/텍스트 중 있는 것만 전송 */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestPart(value = "userId",        required = false) String id,
            @RequestPart(value = "historyJson",   required = false) String historyJson,
            @RequestPart(value = "userContext",   required = false) String userContext,
            @RequestPart(value = "voiceFile",     required = false) MultipartFile voiceFile,
            @RequestPart(value = "imageFile",     required = false) MultipartFile imageFile,
            @RequestParam(value = "textPrompt",   required = false) String textPrompt,
            @RequestParam(value = "mode",         required = false, defaultValue = "chat") String mode,
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

            Map<String, Object> result = orchestrator.execute(
                    userId, historyJson, userContext, voiceFile, imageFile, textPrompt,
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
            String historyJson = body.get("historyJson") != null ? body.get("historyJson").toString() : "";

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
                    + (historyJson.isBlank() ? "(기록 없음)" : historyJson) + "\n\n"
                    + "위 일정을 다 마쳤어. 칭찬과 함께 짧게 요약해줘.";

            String message = geminiAdapter.generateSteps(system, user);
            if (message != null) {
                message = message.replaceAll("(?s)```json|```", "").trim();
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
}
