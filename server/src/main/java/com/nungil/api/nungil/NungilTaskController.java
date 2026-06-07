package com.nungil.api.nungil;

import com.nungil.domain.task.TaskService;
import com.nungil.domain.task.TaskVO;
import com.nungil.infrastructure.google.GeminiRestAdapter;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class NungilTaskController {

    private final TaskService taskService;
    private final GeminiRestAdapter geminiRestAdapter;

    public NungilTaskController(TaskService taskService, GeminiRestAdapter geminiRestAdapter) {
        this.taskService = taskService;
        this.geminiRestAdapter = geminiRestAdapter;
    }

    /**
     * 과업 검색 (직접매칭 → Gemini 유사어 매칭)
     * GET /api/tasks/search?item=빨래
     * 응답: { "found": true, "taskId": 5, "taskName": "빨래하기" }
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("item") String item) {
        System.out.println("==============================================");
        System.out.println("[API] GET /api/tasks/search?item=" + item);
        Map<String, Object> response = new HashMap<>();
        try {
            List<TaskVO> allTasks = taskService.findAll();
            System.out.println("[DB] 전체 과업 수: " + (allTasks != null ? allTasks.size() : 0));

            if (allTasks == null || allTasks.isEmpty()) {
                response.put("found", false);
                response.put("taskId", null);
                response.put("taskName", null);
                return response;
            }

            // 1. Java 직접 매칭
            TaskVO matchedTask = allTasks.stream()
                    .filter(t -> t.getName().equals(item)
                              || t.getName().contains(item)
                              || item.contains(t.getName()))
                    .findFirst()
                    .orElse(null);

            if (matchedTask != null) {
                System.out.println("[직접매칭] " + item + " → " + matchedTask.getName());
            } else {
                // 2. Gemini 유사어 매칭 (fallback)
                String taskListStr = allTasks.stream()
                        .map(TaskVO::getName)
                        .collect(Collectors.joining(", "));

                String prompt = "다음 과업 목록 중에서 '" + item + "'와 의미가 같거나 매우 유사한 항목을 딱 1개만 골라줘.\n"
                        + "단순히 집안일이라서 비슷한 게 아니라, 정말 같은 행동을 의미하는 것만 골라야 해.\n"
                        + "예) '세탁' → '빨래하기' (같은 의미 ✅), '세탁' → '설거지하기' (다른 행동 ❌)\n"
                        + "의미가 같거나 매우 유사한 항목이 없으면 반드시 '없음'이라고만 답해.\n"
                        + "목록에 있는 항목 이름 그대로 반환해줘. 다른 설명은 절대 하지 마.\n"
                        + "목록: " + taskListStr;

                System.out.println("[Gemini] 요청 중...");
                String geminiAnswer = geminiRestAdapter.sendRequest(prompt, null, null);
                System.out.println("[Gemini] 응답: " + geminiAnswer);

                if (geminiAnswer != null && !geminiAnswer.isBlank()) {
                    String matched = geminiAnswer.trim();
                    if (!matched.equals("없음")) {
                        matchedTask = allTasks.stream()
                                .filter(t -> matched.contains(t.getName()))
                                .findFirst()
                                .orElse(null);
                    }
                }
            }

            if (matchedTask != null) {
                System.out.println("[결과] 매칭 성공 → " + matchedTask.getTaskId() + " / " + matchedTask.getName());
                response.put("found", true);
                response.put("taskId", matchedTask.getTaskId());
                response.put("taskName", matchedTask.getName());
            } else {
                System.out.println("[결과] 매칭 실패 → found=false");
                response.put("found", false);
                response.put("taskId", null);
                response.put("taskName", null);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("found", false);
            response.put("message", e.getMessage());
        }
        System.out.println("==============================================");
        return response;
    }

}
