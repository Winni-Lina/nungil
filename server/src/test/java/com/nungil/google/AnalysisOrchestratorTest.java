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
    @DisplayName("buildSchedulePrompt가_stepComplete_단어를_포함하지않음")
    void buildSchedulePrompt_stepComplete없음() throws Exception {
        Method m = AnalysisOrchestrator.class.getDeclaredMethod(
                "buildSchedulePrompt", String.class, String.class, String.class,
                String.class, String.class, int.class, int.class,
                String.class, String.class);
        m.setAccessible(true);
        String[] result = (String[]) m.invoke(orchestrator,
                "사용자정보", "[]", "안녕",
                "빨래하기", "세제 넣기", 1, 5,
                "", "[\"빨래모으기\",\"세제넣기\"]");
        assertEquals(2, result.length);
        assertFalse(result[0].contains("stepComplete"), "system에 stepComplete 필드가 남아있으면 안 됨");
        assertFalse(result[1].contains("stepComplete"), "user에 stepComplete 필드가 남아있으면 안 됨");
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
