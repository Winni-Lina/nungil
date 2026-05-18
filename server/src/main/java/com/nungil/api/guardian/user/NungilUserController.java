package com.nungil.api.guardian.user;

import com.nungil.domain.task.TaskService;
import com.nungil.domain.task.TaskVO;
import com.nungil.domain.user.NungilUserService;
import com.nungil.domain.user.NungilUserVO;
import com.nungil.infrastructure.google.GeminiRestAdapter;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/guardian")
public class NungilUserController {

    private final NungilUserService nungilUserService;
    private final TaskService taskService;
    private final GeminiRestAdapter geminiRestAdapter;

    public NungilUserController(NungilUserService nungilUserService,
                                TaskService taskService,
                                GeminiRestAdapter geminiRestAdapter) {
        this.nungilUserService = nungilUserService;
        this.taskService = taskService;
        this.geminiRestAdapter = geminiRestAdapter;
    }

    /** 사용자 등록 POST /api/v1/guardian/users */
    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/users | guardianId=" + body.get("guardianId"));
        Map<String, Object> response = new HashMap<>();
        try {
            String guardianId = (String) body.get("guardianId");
            NungilUserVO user = nungilUserService.createUser(guardianId);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", user.getId());
            result.put("userIdx", user.getIdx());

            System.out.println("[결과] 사용자 등록 완료 userIdx=" + user.getIdx());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            String errMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            System.out.println("[ERROR] createUser 실패 → " + errMsg);
            e.printStackTrace();
            response.put("status", "ERROR");
            response.put("message", errMsg);
        }
        return response;
    }

    /** 전체 과업 목록 조회 GET /api/v1/guardian/tasks */
    @GetMapping("/tasks")
    public Map<String, Object> getAllTasks() {
        System.out.println("[API] GET /api/v1/guardian/tasks");
        Map<String, Object> response = new HashMap<>();
        try {
            List<TaskVO> allTasks = taskService.findAll();
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (TaskVO t : allTasks) {
                Map<String, Object> item = new HashMap<>();
                item.put("taskId", t.getTaskId());
                item.put("taskName", t.getName());
                tasks.add(item);
            }
            System.out.println("[결과] 전체 과업 " + tasks.size() + "개 반환");
            response.put("tasks", tasks);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("tasks", Collections.emptyList());
        }
        return response;
    }

    /** 과업 검색 (직접매칭 → Gemini 유사어 매칭)
     *  GET /api/v1/guardian/tasks/search?item=빨래
     *  응답: { "found": true, "taskId": 5, "taskName": "빨래하기" }
     */
    @GetMapping("/tasks/search")
    public Map<String, Object> searchTask(@RequestParam("item") String item) {
        System.out.println("==============================================");
        System.out.println("[API] GET /api/v1/guardian/tasks/search?item=" + item);
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

            TaskVO matchedTask = allTasks.stream()
                    .filter(t -> t.getName().equals(item)
                              || t.getName().contains(item)
                              || item.contains(t.getName()))
                    .findFirst()
                    .orElse(null);

            if (matchedTask != null) {
                System.out.println("[직접매칭] " + item + " → " + matchedTask.getName() + " (Gemini 생략)");
            } else {
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
                System.out.println("[결과] 매칭 성공 → " + matchedTask.getName());
                response.put("found", true);
                response.put("taskId", matchedTask.getTaskId());
                response.put("taskName", matchedTask.getName());
            } else {
                System.out.println("[결과] 매칭 실패 → found=false");
                response.put("found", false);
                response.put("taskId", null);
                response.put("taskName", null);
            }
            System.out.println("==============================================");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("found", false);
            response.put("taskId", null);
            response.put("taskName", null);
        }
        return response;
    }

    /** 화이트리스트 조회 GET /api/v1/guardian/settings/user/{guardianId}/{idx}/whitelist */
    @GetMapping("/settings/user/{guardianId}/{idx}/whitelist")
    public Map<String, Object> getWhitelist(@PathVariable("guardianId") String guardianId,
                                             @PathVariable("idx") int idx) {
        System.out.println("[API] GET /api/v1/guardian/settings/user/" + guardianId + "/" + idx + "/whitelist");
        Map<String, Object> response = new HashMap<>();
        try {
            List<Long> taskIds = nungilUserService.getWhiteList(guardianId, idx);
            List<Map<String, Object>> items = toItemList(taskIds);

            Map<String, Object> result = new HashMap<>();
            result.put("guardianId", guardianId);
            result.put("idx", idx);
            result.put("allowedItems", items);

            System.out.println("[결과] 화이트리스트 " + items.size() + "개");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 화이트리스트 추가 POST /api/v1/guardian/settings/user/{guardianId}/{idx}/whitelist */
    @PostMapping("/settings/user/{guardianId}/{idx}/whitelist")
    public Map<String, Object> addWhitelist(@PathVariable("guardianId") String guardianId,
                                             @PathVariable("idx") int idx,
                                             @RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/settings/user/" + guardianId + "/" + idx + "/whitelist | taskId=" + body.get("taskId"));
        Map<String, Object> response = new HashMap<>();
        try {
            Long taskId = Long.valueOf(body.get("taskId").toString());
            List<Long> taskIds = nungilUserService.addToWhiteList(guardianId, idx, taskId);

            Map<String, Object> result = new HashMap<>();
            result.put("guardianId", guardianId);
            result.put("idx", idx);
            result.put("taskId", taskId);
            result.put("registeredCount", taskIds.size());
            result.put("canComplete", taskIds.size() >= 2);
            result.put("message", "화이트리스트에 추가됐어요!");

            System.out.println("[결과] 화이트리스트 추가 완료 → 현재 " + taskIds.size() + "개");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (IllegalArgumentException e) {
            System.out.println("[결과] 화이트리스트 추가 실패 → " + e.getMessage());
            response.put("status", "ERROR");
            response.put("errorCode", e.getMessage());
            response.put("message", getErrorMessage(e.getMessage()));
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 화이트리스트 삭제 DELETE /api/v1/guardian/settings/user/{guardianId}/{idx}/whitelist/{taskId} */
    @DeleteMapping("/settings/user/{guardianId}/{idx}/whitelist/{taskId}")
    public Map<String, Object> removeWhitelist(@PathVariable("guardianId") String guardianId,
                                                @PathVariable("idx") int idx,
                                                @PathVariable("taskId") Long taskId) {
        System.out.println("[API] DELETE /api/v1/guardian/settings/user/" + guardianId + "/" + idx + "/whitelist/" + taskId);
        Map<String, Object> response = new HashMap<>();
        try {
            List<Long> remaining = nungilUserService.removeFromWhiteList(guardianId, idx, taskId);

            Map<String, Object> result = new HashMap<>();
            result.put("guardianId", guardianId);
            result.put("idx", idx);
            result.put("allowedItems", toItemList(remaining));

            System.out.println("[결과] 화이트리스트 삭제 완료 → 남은 항목 " + remaining.size() + "개");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 피보호자 이름/전화번호 저장 POST /api/v1/guardian/settings/user/{guardianId}/{idx}/userinfo */
    @PostMapping("/settings/user/{guardianId}/{idx}/userinfo")
    public Map<String, Object> saveUserInfo(@PathVariable("guardianId") String guardianId,
                                             @PathVariable("idx") int idx,
                                             @RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/settings/user/" + guardianId + "/" + idx + "/userinfo");
        Map<String, Object> response = new HashMap<>();
        try {
            String userName  = (String) body.get("userName");
            String userPhone = (String) body.get("userPhone");
            nungilUserService.updateUserInfo(guardianId, idx, userName, userPhone);
            response.put("status", "SUCCESS");
            response.put("message", "피보호자 정보가 저장됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 특이사항 저장 POST /api/v1/guardian/settings/user/{guardianId}/{idx}/profile */
    @PostMapping("/settings/user/{guardianId}/{idx}/profile")
    public Map<String, Object> saveProfile(@PathVariable("guardianId") String guardianId,
                                            @PathVariable("idx") int idx,
                                            @RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/settings/user/" + guardianId + "/" + idx + "/profile | specialNote=" + body.get("specialNote"));
        Map<String, Object> response = new HashMap<>();
        try {
            String specialNote = (String) body.get("specialNote");
            nungilUserService.updateSpecialNote(guardianId, idx, specialNote);

            Map<String, Object> result = new HashMap<>();
            result.put("guardianId", guardianId);
            result.put("idx", idx);
            result.put("message", "특이사항이 저장됐어요!");

            System.out.println("[결과] 특이사항 저장 완료");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 사용자 정보 조회 GET /api/v1/guardian/settings/user/{guardianId}/{idx} */
    @GetMapping("/settings/user/{guardianId}/{idx}")
    public Map<String, Object> getUser(@PathVariable("guardianId") String guardianId,
                                        @PathVariable("idx") int idx) {
        System.out.println("[API] GET /api/v1/guardian/settings/user/" + guardianId + "/" + idx);
        Map<String, Object> response = new HashMap<>();
        try {
            NungilUserVO user = nungilUserService.getUser(guardianId, idx);
            if (user == null) {
                System.out.println("[결과] 사용자 없음 → USER_NOT_FOUND");
                response.put("status", "ERROR");
                response.put("errorCode", "USER_NOT_FOUND");
                return response;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("guardianId", user.getId());
            result.put("idx", user.getIdx());
            result.put("specialNote", user.getSpecialNote());
            result.put("whiteList", parseWhiteListToItems(user.getWhiteList()));

            System.out.println("[결과] 사용자 조회 완료 specialNote=" + user.getSpecialNote());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    private List<Map<String, Object>> toItemList(List<Long> taskIds) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long taskId : taskIds) {
            TaskVO task = taskService.findById(taskId);
            Map<String, Object> item = new HashMap<>();
            item.put("taskId", taskId);
            item.put("item", task != null ? task.getName() : "");
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> parseWhiteListToItems(String whiteList) {
        List<Long> ids = new ArrayList<>();
        if (whiteList != null && !whiteList.trim().isEmpty()) {
            for (String s : whiteList.split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (Exception ignored) {}
            }
        }
        return toItemList(ids);
    }

    private String getErrorMessage(String errorCode) {
        if ("ITEM_EXISTS".equals(errorCode)) return "이미 등록된 과업입니다";
        return "오류가 발생했습니다";
    }
}
