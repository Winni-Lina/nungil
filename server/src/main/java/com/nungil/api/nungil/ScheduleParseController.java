package com.nungil.api.nungil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nungil.infrastructure.google.GeminiRestAdapter;
import com.nungil.infrastructure.google.GoogleSttClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleParseController {

    private final GeminiRestAdapter geminiAdapter;
    private final GoogleSttClient   sttClient;
    private final ObjectMapper      objectMapper = new ObjectMapper();

    public ScheduleParseController(GeminiRestAdapter geminiAdapter, GoogleSttClient sttClient) {
        this.geminiAdapter = geminiAdapter;
        this.sttClient     = sttClient;
    }

    /**
     * SC-010: 자연어 → 일정 정보 파싱
     * POST /api/v1/schedule/parse
     * multipart: voiceFile(optional), textPrompt(optional), tasks(허용 과업 목록, 쉼표 구분)
     */
    @PostMapping("/parse")
    public Map<String, Object> parse(
            @RequestPart(value = "voiceFile",   required = false) MultipartFile voiceFile,
            @RequestParam(value = "textPrompt", required = false) String textPrompt,
            @RequestParam(value = "tasks",      required = false, defaultValue = "") String tasks) {

        System.out.println("[API] POST /api/v1/schedule/parse");
        Map<String, Object> response = new HashMap<>();
        try {
            String input = (voiceFile != null && !voiceFile.isEmpty())
                    ? sttClient.transcribe(voiceFile)
                    : textPrompt;

            if (input == null || input.trim().isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "입력이 없습니다.");
                return response;
            }

            String today = LocalDate.now().toString();
            String prompt = String.format("""
                아래 텍스트에서 일정 정보를 추출해서 JSON으로만 응답해줘.
                허용된 과업 목록: %s
                오늘 날짜: %s
                입력: "%s"

                규칙:
                - taskName은 위 허용 목록 중 가장 가까운 것으로 선택해. 없으면 빈 문자열.
                - date는 오늘 기준으로 계산 (예: "내일" → 오늘+1일, "모레" → 오늘+2일)
                - time은 24시간 HH:mm 형식
                - 언급 없으면 location, note는 빈 문자열

                반드시 아래 JSON 형식으로만 응답해:
                {
                  "taskName": "과업명",
                  "date": "yyyy-MM-dd",
                  "time": "HH:mm",
                  "location": "장소",
                  "note": "특이사항"
                }
                """, tasks, today, input);

            String raw = geminiAdapter.sendRequest(prompt, null, null);
            String clean = raw.replaceAll("```json|```", "").trim();
            Map<String, Object> parsed = objectMapper.readValue(clean, Map.class);

            response.put("status", "SUCCESS");
            response.put("result", parsed);
            response.put("transcribedText", input);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", "파싱 실패: " + e.getMessage());
        }
        return response;
    }
}
