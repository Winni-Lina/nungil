package com.nungil.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nungil.infrastructure.google.AnalysisOrchestrator;
import com.nungil.infrastructure.google.GeminiRestAdapter;
import com.nungil.infrastructure.google.StepGenerationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini 실제 호출 통합 테스트.
 *
 * <p>평소엔 스킵된다. 발표 직전 캡처용으로 실행하려면:
 * <pre>
 *   set RUN_LIVE_AI=true
 *   mvn test -Dtest=GeminiLiveIntegrationTest -DfailIfNoTests=false
 * </pre>
 *
 * <p>주의: 네트워크와 API 키(application.properties: google.ai.api-key)가 필요하다.
 *
 * <p>이 프로젝트는 spring-boot-starter-test 의존성이 없어 @SpringBootTest 대신
 * Bean 을 직접 인스턴스화하고, @Value 필드는 reflection 으로 주입한다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI", matches = "true")
class GeminiLiveIntegrationTest {

    private static GeminiRestAdapter geminiAdapter;
    private static AnalysisOrchestrator orchestrator;
    private static StepGenerationService stepGenerationService;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void boot() throws Exception {
        // 1) application.properties 에서 api key 로드
        Properties props = new Properties();
        try (InputStream is = GeminiLiveIntegrationTest.class
                .getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(is, "application.properties 가 classpath 에 없음");
            props.load(is);
        }
        String apiKey = props.getProperty("google.ai.api-key");
        assertNotNull(apiKey, "google.ai.api-key 가 비어있음");
        assertFalse(apiKey.isBlank(), "google.ai.api-key 가 공백");

        // 2) 어댑터 인스턴스 + @Value 필드 reflection 주입
        geminiAdapter = new GeminiRestAdapter();
        Field f = GeminiRestAdapter.class.getDeclaredField("apiKey");
        f.setAccessible(true);
        f.set(geminiAdapter, apiKey);

        // 3) STT 없이 orchestrator (텍스트 경로만 사용)
        orchestrator = new AnalysisOrchestrator(null, geminiAdapter);
        stepGenerationService = new StepGenerationService(geminiAdapter);
    }

    @Test
    @DisplayName("[LIVE] 채팅 프롬프트 실제 호출 - JSON 응답 파싱 가능")
    void 채팅_프롬프트_실제호출_JSON응답_파싱가능() throws Exception {
        Map<String, Object> result = orchestrator.execute(
                "live-user", "[]", "이름: 이눈길 / 나이: 22",
                null, null,
                "안녕 똘똘이야 오늘 뭐 할까", "chat",
                null, null, 0, 0,
                "", "");

        System.out.println("\n===== [LIVE 1: chat] =====");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        System.out.println("==========================\n");

        assertNotNull(result);
        assertTrue(result.containsKey("answer"));
        assertNotNull(result.get("answer"));
        assertFalse(result.get("answer").toString().isBlank());
    }

    @Test
    @DisplayName("[LIVE] 일정 단계 생성 실제 호출 - 빈 배열 아님")
    void 일정_단계생성_실제호출_빈배열아님() {
        String taskProcess = "[\"화장실 가기\",\"수도꼭지 돌리기\",\"손에 물 묻히기\",\"비누 칠하기\",\"손가락 사이 닦기\",\"물로 헹구기\",\"수건으로 닦기\"]";
        List<String> steps = stepGenerationService.generatePersonalizedSteps(
                taskProcess,
                "화장실",
                "혼자서 처음 해봐요. 천천히.",
                "자폐 성향이 있어서 칭찬을 좋아해요.");

        System.out.println("\n===== [LIVE 2: step generation] =====");
        for (int i = 0; i < steps.size(); i++) {
            System.out.println((i + 1) + ". " + steps.get(i));
        }
        System.out.println("====================================\n");

        assertNotNull(steps);
        assertFalse(steps.isEmpty(), "단계 배열이 비어있으면 안 됨");
        assertTrue(steps.size() >= 3, "최소 3단계 이상 기대");
    }

    @Test
    @DisplayName("[LIVE] 요약 실제 호출 - 문자열 반환")
    void 요약_실제호출_문자열반환() {
        String system = "다음 일정 진행 기록을 한 문장으로 따뜻하게 요약해주세요. 보호자가 보는 글입니다.";
        String user = "이눈길 님이 오늘 14:00에 손 씻기를 8단계 모두 완료했습니다. 중간에 비누 거품 단계에서 한 번 멈췄지만 다시 진행했습니다.";

        String response = geminiAdapter.sendRequest(system, user, null, "image/jpeg");

        System.out.println("\n===== [LIVE 3: summarize] =====");
        System.out.println(response);
        System.out.println("===============================\n");

        assertNotNull(response);
        assertFalse(response.isBlank());
    }
}
