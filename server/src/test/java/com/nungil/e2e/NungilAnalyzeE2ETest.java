package com.nungil.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nungil.api.nungil.NungilAnalyzeController;
import com.nungil.infrastructure.google.AnalysisOrchestrator;
import com.nungil.infrastructure.google.GeminiRestAdapter;
import com.nungil.infrastructure.google.GoogleSttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E 테스트 — HTTP 요청 → 컨트롤러 → Orchestrator → (Gemini 가짜 응답) → HTTP 응답
 *
 * 실제 AI API 호출 없이, 앱이 서버로 보내는 요청과 서버가 돌려주는 응답 형식이
 * 올바른지를 8가지 시나리오로 검증합니다.
 */
class NungilAnalyzeE2ETest {

    private MockMvc mockMvc;
    private AnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Gemini·STT는 가짜로 대체 — 실제 API 키 불필요
        orchestrator = mock(AnalysisOrchestrator.class);
        GeminiRestAdapter gemini = mock(GeminiRestAdapter.class);
        GoogleSttClient   stt    = mock(GoogleSttClient.class);

        NungilAnalyzeController controller =
                new NungilAnalyzeController(orchestrator, gemini);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 시나리오별 AI 가짜 응답 생성 헬퍼 */
    private Map<String, Object> ai(String answer, boolean stepComplete, boolean photo) {
        Map<String, Object> m = new HashMap<>();
        m.put("answer",             answer);
        m.put("stepComplete",       stepComplete);
        m.put("suggestedQuestions", List.of());
        m.put("photoRequest",       photo);
        m.put("userId",             "user1");
        m.put("transcribedText",    "");
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 채팅 모드 시나리오
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E-01  채팅 | 일반 질문 → 답변 반환")
    void chatMode_일반질문_답변반환() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                eq("오늘 뭐 먹어?"), eq("chat"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(ai("맛있는 거 먹자!", false, false));

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt", "오늘 뭐 먹어?")
                .param("mode", "chat"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.result.answer").value("맛있는 거 먹자!"))
            .andExpect(jsonPath("$.result.stepComplete").value(false));
    }

    @Test
    @DisplayName("E2E-02  채팅 | '이게 뭐야?' → 카메라 요청(photoRequest=true)")
    void chatMode_사진요청() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                eq("이게 뭐야?"), eq("chat"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(ai("사진 보여줘!", false, true));

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt", "이게 뭐야?")
                .param("mode", "chat"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.photoRequest").value(true));
    }

    @Test
    @DisplayName("E2E-03  채팅 | 응답 JSON에 필수 필드 4개가 반드시 있어야 함")
    void chatMode_응답필드_완전성검증() throws Exception {
        Map<String, Object> full = new HashMap<>();
        full.put("answer",             "안녕!");
        full.put("stepComplete",       false);
        full.put("suggestedQuestions", List.of("뭐 먹을까?", "심심해"));
        full.put("photoRequest",       false);
        full.put("userId",             "u1");
        full.put("transcribedText",    "");

        when(orchestrator.execute(any(), any(), any(), any(), any(),
                any(), eq("chat"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(full);

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt", "안녕")
                .param("mode", "chat"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.answer").exists())
            .andExpect(jsonPath("$.result.stepComplete").exists())
            .andExpect(jsonPath("$.result.suggestedQuestions").isArray())
            .andExpect(jsonPath("$.result.photoRequest").exists());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 일정 수행 모드 시나리오
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E-04  일정 | 첫 단계 시작 → 안내 문구 반환, 미완료")
    void scheduleMode_첫단계시작() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                any(), eq("schedule"), any(), any(), eq(0), anyInt(), any(), any()))
            .thenReturn(ai("세탁기에 옷 넣어줘! 다 하면 말해줘!", false, false));

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt",    "일정 시작")
                .param("mode",          "schedule")
                .param("scheduleTitle", "빨래하기")
                .param("currentStep",   "세탁기에 옷 넣기")
                .param("stepIndex",     "0")
                .param("totalSteps",    "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.stepComplete").value(false))
            .andExpect(jsonPath("$.result.answer").isNotEmpty());
    }

    @Test
    @DisplayName("E2E-05  일정 | '다 했어' → stepComplete=true, 칭찬 응답")
    void scheduleMode_완료신호_stepCompleteTrue() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                eq("다 했어"), eq("schedule"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(ai("잘했어! 정말 대단해!", true, false));

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt",    "다 했어")
                .param("mode",          "schedule")
                .param("scheduleTitle", "빨래하기")
                .param("currentStep",   "세탁기에 옷 넣기")
                .param("stepIndex",     "1")
                .param("totalSteps",    "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.stepComplete").value(true))
            .andExpect(jsonPath("$.result.answer").isNotEmpty());
    }

    @Test
    @DisplayName("E2E-06  일정 | '어떻게 해?' → 설명 후 stepComplete=false")
    void scheduleMode_방법질문_설명반환() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                eq("어떻게 해?"), eq("schedule"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(ai("세탁기 문 열고 옷 넣으면 돼!", false, false));

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt", "어떻게 해?")
                .param("mode",       "schedule")
                .param("stepIndex",  "1")
                .param("totalSteps", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.stepComplete").value(false))
            .andExpect(jsonPath("$.result.answer").isNotEmpty());
    }

    @Test
    @DisplayName("E2E-07  일정 | '못 하겠어' → 재촉 없이 응원, stepComplete=false")
    void scheduleMode_힘들어함_응원응답() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                eq("못 하겠어"), eq("schedule"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(ai("괜찮아, 천천히 해도 돼!", false, false));

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .param("textPrompt", "못 하겠어")
                .param("mode",       "schedule")
                .param("stepIndex",  "0")
                .param("totalSteps", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.stepComplete").value(false))
            .andExpect(jsonPath("$.result.answer").value("괜찮아, 천천히 해도 돼!"));
    }

    @Test
    @DisplayName("E2E-08  일정 | 음성 파일 첨부 경로 — 정상 처리")
    void scheduleMode_음성파일첨부() throws Exception {
        when(orchestrator.execute(any(), any(), any(), any(), any(),
                isNull(), eq("schedule"), any(), any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(ai("잘했어!", true, false));

        MockMultipartFile voice = new MockMultipartFile(
                "voiceFile", "voice.wav", "audio/wav", "dummy-audio".getBytes());

        mockMvc.perform(multipart("/api/v1/question/analyze")
                .file(voice)
                .param("mode",          "schedule")
                .param("scheduleTitle", "빨래하기")
                .param("currentStep",   "세탁기 돌리기")
                .param("stepIndex",     "2")
                .param("totalSteps",    "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
