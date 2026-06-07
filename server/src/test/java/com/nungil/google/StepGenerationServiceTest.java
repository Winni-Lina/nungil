package com.nungil.google;

import com.nungil.infrastructure.google.GeminiRestAdapter;
import com.nungil.infrastructure.google.StepGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StepGenerationServiceTest {

    @Test
    @DisplayName("Gemini응답_json펜스로_감싼배열_파싱성공")
    void parseSteps_json펜스파싱() {
        StubGemini stub = new StubGemini("```json\n[\"세제 가져오기\", \"세탁기 열기\", \"옷 넣기\"]\n```");
        StepGenerationService svc = new StepGenerationService(stub);
        List<String> result = svc.generatePersonalizedSteps("(기본)", "집", "", "");
        assertEquals(3, result.size());
        assertEquals("세제 가져오기", result.get(0));
        assertEquals("옷 넣기", result.get(2));
    }

    @Test
    @DisplayName("응답이_줄바꿈텍스트일때_fallback파싱이_taskProcess기반으로_동작")
    void parseSteps_fallback줄바꿈() {
        // Gemini 응답이 파싱 불가 → taskProcess fallback 사용
        StubGemini stub = new StubGemini("죄송해요 응답을 만들 수 없어요");
        StepGenerationService svc = new StepGenerationService(stub);
        String taskProcess = "1. 빨래 모으기\n2. 세제 넣기\n3. 시작 버튼 누르기";
        List<String> result = svc.generatePersonalizedSteps(taskProcess, "집", "", "");
        assertEquals(3, result.size());
        assertEquals("빨래 모으기", result.get(0));
        assertEquals("시작 버튼 누르기", result.get(2));
    }

    @Test
    @DisplayName("Gemini빈응답_처리_taskProcess없으면_빈리스트반환")
    void parseSteps_빈응답_빈리스트() {
        StubGemini stub = new StubGemini("");
        StepGenerationService svc = new StepGenerationService(stub);
        List<String> result = svc.generatePersonalizedSteps(null, null, null, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fallbackSteps_JSON배열문자열을_정상파싱")
    void fallbackSteps_JSON배열문자열() {
        StepGenerationService svc = new StepGenerationService(new StubGemini(""));
        List<String> result = svc.fallbackSteps("[\"A\",\"B\",\"C\"]");
        assertEquals(List.of("A", "B", "C"), result);
    }

    static class StubGemini extends GeminiRestAdapter {
        final String response;
        StubGemini(String r) { this.response = r; }
        @Override
        public String generateSteps(String systemInstruction, String userMessage) {
            return response;
        }
        @Override
        public String sendRequest(String systemInstruction, String userMessage,
                                  String base64Image, String contentType) {
            return response;
        }
    }
}
