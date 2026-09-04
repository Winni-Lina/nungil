package com.nungil.google;

import com.nungil.infrastructure.google.AnalysisOrchestrator;
import com.nungil.infrastructure.google.GeminiRestAdapter;
import com.nungil.infrastructure.google.GoogleSttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnalysisOrchestrator 단위 테스트.
 * private 메서드는 reflection으로 호출. Gemini/STT는 수동 stub.
 */
class AnalysisOrchestratorTest {

    private StubGeminiAdapter geminiStub;
    private AnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        geminiStub = new StubGeminiAdapter();
        orchestrator = new AnalysisOrchestrator(null, geminiStub);
    }

    @Test
    @DisplayName("buildChatPrompt가 system_user String[] 길이2_반환")
    void buildChatPrompt_길이2() throws Exception {
        Method m = AnalysisOrchestrator.class.getDeclaredMethod(
                "buildChatPrompt", String.class, String.class, String.class);
        m.setAccessible(true);
        String[] result = (String[]) m.invoke(orchestrator, "사용자정보", "[]", "안녕");
        assertEquals(2, result.length);
        assertNotNull(result[0]);
        assertNotNull(result[1]);
        assertTrue(result[0].contains("똘똘이"));
        assertTrue(result[1].contains("안녕"));
    }

    @Test
    @DisplayName("buildSchedulePrompt_system에_단계이동은앱결정_명시_user에_단계명포함")
    void buildSchedulePrompt_단계안내구조검증() throws Exception {
        Method m = AnalysisOrchestrator.class.getDeclaredMethod(
                "buildSchedulePrompt", String.class, String.class, String.class,
                String.class, String.class, int.class, int.class,
                String.class);
        m.setAccessible(true);
        String[] result = (String[]) m.invoke(orchestrator,
                "사용자정보", "[]", "안녕",
                "빨래하기", "세제 넣기", 1, 5,
                "");
        assertEquals(2, result.length, "system + user 2개 반환");
        // system: AI는 단계 이동을 직접 결정하지 않음을 명시
        assertTrue(result[0].contains("앱이 결정"), "system 프롬프트에 '앱이 결정' 명시 필요");
        // user: 현재 단계명과 일정명이 포함되어야 함
        assertTrue(result[1].contains("빨래하기"), "user 메시지에 일정명 포함 필요");
        assertTrue(result[1].contains("세제 넣기"), "user 메시지에 현재 단계명 포함 필요");
        // user: 발화 내용도 포함
        assertTrue(result[1].contains("안녕"), "user 메시지에 사용자 발화 포함 필요");
    }

    @Test
    @DisplayName("safeHistory가_빈historyJson을_대화없음으로_변환")
    void safeHistory_빈입력_대화없음() throws Exception {
        Method m = AnalysisOrchestrator.class.getDeclaredMethod("safeHistory", String.class);
        m.setAccessible(true);
        assertEquals("(대화 없음)", m.invoke(orchestrator, (Object) null));
        assertEquals("(대화 없음)", m.invoke(orchestrator, ""));
        assertEquals("(대화 없음)", m.invoke(orchestrator, "[]"));
        assertEquals("[{\"q\":\"hi\"}]", m.invoke(orchestrator, "[{\"q\":\"hi\"}]"));
    }

    @Test
    @DisplayName("execute_mode_schedule_voiceFile_null_textPrompt_질문_GeminiRestAdapter_호출검증")
    void execute_schedule모드_Gemini호출검증() {
        geminiStub.responseToReturn = "{\"answer\":\"네\",\"suggestedQuestions\":[],\"photoRequest\":false}";
        Map<String, Object> result = orchestrator.execute(
                "user1", "[]", "사용자정보",
                null, null,
                "질문", "schedule",
                "빨래하기", "세제 넣기",
                1, 5,
                "", "[\"빨래모으기\",\"세제넣기\"]");
        assertNotNull(result);
        assertEquals("네", result.get("answer"));
        assertEquals("user1", result.get("userId"));
        assertEquals("질문", result.get("transcribedText"));
        assertEquals(1, geminiStub.callCount, "Gemini가 정확히 1번 호출되어야 함");
        assertNotNull(geminiStub.lastSystem);
        assertTrue(geminiStub.lastUser.contains("질문"));
    }

    // ── Intent 검증 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("execute_Gemini가_5종Intent를_반환하면_그대로_유지")
    void execute_허용된intent_유지() {
        for (String intent : new String[]{
                "STEP_DONE", "STEP_QUESTION", "HELP_REQUEST", "OFF_TOPIC", "OTHER"}) {
            geminiStub.responseToReturn =
                    "{\"intent\":\"" + intent + "\",\"answer\":\"네\","
                    + "\"stepComplete\":false,\"suggestedQuestions\":[],\"photoRequest\":false}";
            Map<String, Object> result = execSchedule("발화");
            assertEquals(intent, result.get("intent"), intent + "은 그대로 유지되어야 함");
        }
    }

    @Test
    @DisplayName("execute_intent가_없거나_null이면_OTHER로_처리")
    void execute_intent없음_OTHER() {
        // intent 필드 자체가 없는 경우
        geminiStub.responseToReturn =
                "{\"answer\":\"네\",\"stepComplete\":false,\"suggestedQuestions\":[],\"photoRequest\":false}";
        assertEquals("OTHER", execSchedule("발화").get("intent"));

        // intent가 JSON null인 경우
        geminiStub.responseToReturn =
                "{\"intent\":null,\"answer\":\"네\",\"stepComplete\":false,"
                + "\"suggestedQuestions\":[],\"photoRequest\":false}";
        assertEquals("OTHER", execSchedule("발화").get("intent"));
    }

    @Test
    @DisplayName("execute_빈문자열_공백_허용외값은_모두_OTHER로_처리")
    void execute_잘못된intent_OTHER() {
        for (String bad : new String[]{"", "   ", "STEP_UNKNOWN", "완료", "123"}) {
            geminiStub.responseToReturn =
                    "{\"intent\":\"" + bad + "\",\"answer\":\"네\",\"stepComplete\":false,"
                    + "\"suggestedQuestions\":[],\"photoRequest\":false}";
            assertEquals("OTHER", execSchedule("발화").get("intent"),
                    "허용되지 않은 값 [" + bad + "]은 OTHER여야 함");
        }
    }

    @Test
    @DisplayName("execute_소문자_공백포함_intent는_정규화되어_인식")
    void execute_소문자intent_정규화() {
        geminiStub.responseToReturn =
                "{\"intent\":\" step_done \",\"answer\":\"잘했어요\",\"stepComplete\":true,"
                + "\"suggestedQuestions\":[],\"photoRequest\":false}";
        assertEquals("STEP_DONE", execSchedule("다 했어").get("intent"));
    }

    @Test
    @DisplayName("execute_Gemini응답이_깨진JSON이면_intent_OTHER_stepComplete_false_반환")
    void execute_깨진JSON_기본값반환() {
        geminiStub.responseToReturn = "이건 JSON이 아님";
        Map<String, Object> result = execSchedule("발화");
        assertEquals("OTHER", result.get("intent"), "파싱 실패 시 intent는 OTHER");
        assertEquals(false, result.get("stepComplete"), "파싱 실패 시 stepComplete는 false");
        assertNotNull(result.get("answer"), "기본 안내 문구가 있어야 함");
    }

    @Test
    @DisplayName("일정_프롬프트에_5종_Intent와_stepComplete_기준이_명시됨")
    void buildSchedulePrompt_intent규칙_포함() throws Exception {
        Method m = AnalysisOrchestrator.class.getDeclaredMethod(
                "buildSchedulePrompt", String.class, String.class, String.class,
                String.class, String.class, int.class, int.class, String.class);
        m.setAccessible(true);
        String system = ((String[]) m.invoke(orchestrator,
                "사용자정보", "[]", "안녕", "빨래하기", "세제 넣기", 1, 5, ""))[0];

        for (String intent : new String[]{
                "STEP_DONE", "STEP_QUESTION", "HELP_REQUEST", "OFF_TOPIC", "OTHER"}) {
            assertTrue(system.contains(intent), "system 프롬프트에 " + intent + " 명시 필요");
        }
        assertTrue(system.contains("stepComplete는 intent=STEP_DONE일 때만 true"),
                "stepComplete 기준이 프롬프트에 명시되어야 함");
    }

    /** schedule 모드 execute 호출 헬퍼 */
    private Map<String, Object> execSchedule(String question) {
        return orchestrator.execute(
                "user1", "[]", "사용자정보", null, null,
                question, "schedule", "빨래하기", "세제 넣기", 1, 5,
                "", "[\"빨래모으기\",\"세제넣기\"]");
    }

    // ── Stubs ──────────────────────────────
    static class StubGeminiAdapter extends GeminiRestAdapter {
        int callCount = 0;
        String lastSystem;
        String lastUser;
        String responseToReturn = "{\"answer\":\"ok\",\"suggestedQuestions\":[],\"photoRequest\":false}";

        @Override
        public String sendRequest(String systemInstruction, String userMessage,
                                  String base64Image, String contentType) {
            callCount++;
            lastSystem = systemInstruction;
            lastUser = userMessage;
            return responseToReturn;
        }
    }
}
