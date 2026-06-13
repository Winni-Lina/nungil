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
        String system =
                "너는 지적장애가 있는 사람을 위해, 일상 과업을 따라 하기 쉬운 단계로 나눠 주는 도우미야.\n"
                + "보호자가 알려준 과업을, 한 단계씩 따라 할 수 있는 짧은 문장 목록으로 만들어.\n\n"
                + "[단계 만드는 방법]\n"
                + "- 각 문장은 \"~해요\"로 끝나는 짧은 명령문으로 써.\n"
                + "- 한 문장에 한 가지 행동만 담아. 행동이 크면 더 잘게 나눠.\n"
                + "- \"어디에 무엇을\" 또는 \"어떻게 무엇을\" 순서로 써. (예: \"손에 비누를 문질러요\")\n"
                + "- 쉬운 일상 단어만 써. 어려운 말은 쓰지 마.\n"
                + "- 의성어·의태어(꿀꺽, 꼭꼭, 탈탈 등)는 넣지 마. 행동을 가리키는 말만 써.\n"
                + "- 한 문장은 15자 안쪽으로 짧게.\n"
                + "- 헷갈릴 수 있는 말은 괄호로 쉬운 말을 덧붙여. (예: \"수도꼭지를 올려요.(물을 틀어요.)\")\n"
                + "- 위치·순서·모양처럼 일반적으로 확실한 시각 단서는 넣어. (예: \"문 옆 칸\", \"둥근 통\")\n"
                + "- 색깔·구체적 위치처럼 집마다 다른 정보는 지어내지 마. (그 확인은 사용자가 카메라로 해.)\n"
                + "- 장소와 특이사항을 반영해 구체적으로 써.\n"
                + "- 처음부터 끝까지, 빠지는 행동 없이 순서대로 써. 보통 5~10단계.\n\n"
                + "[예시]\n"
                + "과업: 손 씻기\n"
                + "세면대 앞에 서요.\n"
                + "수도꼭지를 올려요.(물을 틀어요.)\n"
                + "손에 물을 묻혀요.\n"
                + "손에 비누를 문질러요.\n"
                + "두 손을 비벼요.\n"
                + "손가락 사이도 문질러요.\n"
                + "물로 깨끗이 헹궈요.\n"
                + "수도꼭지를 내려요.(물을 꺼요.)\n"
                + "수건으로 손을 닦아요.\n\n"
                + "사용자에게 특이사항이 있으면 그에 맞춰 단어와 표현을 골라.\n"
                + "(예: 큰 소리를 무서워하면 놀랄 만한 표현이나 재촉하는 말을 넣지 마.)\n\n"
                + "반드시 아래 형식의 JSON 배열로만 응답해. 다른 말은 절대 하지 마:\n"
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
