package com.nungil.infrastructure.google;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 보호자가 입력한 일정 정보(과업 기본 단계 + 장소 + 특이사항)를 바탕으로
 * 사용자 맞춤 단계 목록을 Gemini로 생성한다.
 */
@Service
public class StepGenerationService {

    private final GeminiRestAdapter geminiAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StepGenerationService(GeminiRestAdapter geminiAdapter) {
        this.geminiAdapter = geminiAdapter;
    }

    /**
     * @param taskProcess     과업의 기본 단계 (JSON 배열 또는 줄바꿈 텍스트, nullable)
     * @param location        장소
     * @param scheduleNote    이번 일정 특이사항
     * @param userSpecialNote 사용자 고유 특이사항
     * @return 맞춤 단계 리스트
     */
    public List<String> generatePersonalizedSteps(String taskProcess, String location,
                                                  String scheduleNote, String userSpecialNote) {
        String system = "## 역할\n"
                + "당신은 지적 장애인이 일정을 스스로 완수하도록, 보호자가 입력한 정보를 바탕으로\n"
                + "쉽고 구체적인 단계 목록을 만드는 AI입니다.\n\n"
                + "## 작성 규칙\n"
                + "- 각 단계는 한 문장, 15자 내외로 아주 짧고 쉽게\n"
                + "- 어려운 단어 금지, 한 단계에 한 가지 행동만\n"
                + "- 장소와 특이사항을 반영해 구체적으로\n"
                + "- 5~10개 단계로\n\n"
                + "반드시 아래 형식의 JSON 배열로만 응답해. 다른 말 절대 하지 마:\n"
                + "[\"단계1\", \"단계2\", \"단계3\"]";

        StringBuilder user = new StringBuilder();
        user.append("## 과업 기본 단계\n")
            .append(taskProcess != null && !taskProcess.isBlank() ? taskProcess : "(기본 단계 없음)")
            .append("\n\n");
        if (location != null && !location.isBlank()) {
            user.append("## 장소\n").append(location).append("\n\n");
        }
        if (scheduleNote != null && !scheduleNote.isBlank()) {
            user.append("## 이번 일정 특이사항\n").append(scheduleNote).append("\n\n");
        }
        if (userSpecialNote != null && !userSpecialNote.isBlank()) {
            user.append("## 사용자 특이사항\n").append(userSpecialNote).append("\n\n");
        }
        user.append("위 정보를 반영해 맞춤 단계 목록을 JSON 배열로 만들어줘.");

        String raw = geminiAdapter.generateSteps(system, user.toString());
        return parseSteps(raw, taskProcess);
    }

    /** Gemini 응답(JSON 배열 문자열) 파싱. 실패 시 taskProcess fallback. */
    private List<String> parseSteps(String raw, String fallback) {
        List<String> result = new ArrayList<>();
        if (raw == null) raw = "";
        String clean = raw.replaceAll("(?s)```json|```", "").trim();

        // 1) JSON 배열 시도
        try {
            JsonNode arr = objectMapper.readTree(clean);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String s = n.asText().trim();
                    if (!s.isEmpty()) result.add(s);
                }
            }
        } catch (Exception ignored) { }

        if (!result.isEmpty()) return result;

        // 2) fallback: taskProcess 파싱 (JSON 배열 또는 줄바꿈)
        return fallbackSteps(fallback);
    }

    /** taskProcess가 JSON 배열이면 파싱, 아니면 줄바꿈 분리 */
    public List<String> fallbackSteps(String taskProcess) {
        List<String> result = new ArrayList<>();
        if (taskProcess == null || taskProcess.isBlank()) return result;
        String s = taskProcess.trim();
        try {
            JsonNode arr = objectMapper.readTree(s);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String v = n.asText().trim();
                    if (!v.isEmpty()) result.add(v);
                }
                if (!result.isEmpty()) return result;
            }
        } catch (Exception ignored) { }

        for (String line : s.split("\\r?\\n")) {
            String v = line.trim().replaceAll("^\\d+[.)]\\s*", "");
            if (!v.isEmpty()) result.add(v);
        }
        return result;
    }
}
