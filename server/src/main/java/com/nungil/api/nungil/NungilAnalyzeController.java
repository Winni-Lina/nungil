package com.nungil.api.nungil;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nungil.infrastructure.google.AnalysisOrchestrator;

@RestController
@RequestMapping("/api/v1/question")
public class NungilAnalyzeController {

    private final AnalysisOrchestrator orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NungilAnalyzeController(AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** 통합 분석 API - 음성/이미지/텍스트 중 있는 것만 전송 */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestPart(value = "userId",        required = false) String id,
            @RequestPart(value = "historyJson",   required = false) String historyJson,
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
                    userId, historyJson, voiceFile, imageFile, textPrompt,
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
}
