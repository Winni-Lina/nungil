package com.nungil.api.nungil;

import com.nungil.domain.guardian.GuardianService;
import com.nungil.domain.guardian.GuardianVO;
import com.nungil.domain.question.QuestionService;
import com.nungil.domain.question.QuestionVO;
import com.nungil.domain.schedule.ScheduleService;
import com.nungil.domain.schedule.ScheduleVO;
import com.nungil.domain.task.TaskService;
import com.nungil.domain.task.TaskVO;
import com.nungil.domain.user.NungilUserService;
import com.nungil.domain.user.NungilUserVO;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class NungilApiController {

    private final GuardianService guardianService;
    private final NungilUserService nungilUserService;
    private final TaskService taskService;
    private final ScheduleService scheduleService;
    private final QuestionService questionService;

    public NungilApiController(GuardianService guardianService,
                                NungilUserService nungilUserService,
                                TaskService taskService,
                                ScheduleService scheduleService,
                                QuestionService questionService) {
        this.guardianService = guardianService;
        this.nungilUserService = nungilUserService;
        this.taskService = taskService;
        this.scheduleService = scheduleService;
        this.questionService = questionService;
    }

    /**
     * API 1: 보호자 로그인
     * POST /api/v1/guardian/login
     * Body: { "guardianId": "guardian01", "password": "1234" }
     */
    @PostMapping("/guardian/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/login | guardianId=" + body.get("guardianId"));
        Map<String, Object> response = new HashMap<>();
        try {
            String guardianId = (String) body.get("guardianId");
            String password   = (String) body.get("password");

            GuardianVO guardian = guardianService.login(guardianId, password);

            Map<String, Object> result = new HashMap<>();
            result.put("guardianId", guardian.getId());
            result.put("name", guardian.getName());

            System.out.println("[결과] 로그인 성공 guardianId=" + guardian.getId());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (IllegalArgumentException e) {
            System.out.println("[결과] 로그인 실패 - 잘못된 자격증명");
            response.put("status", "ERROR");
            response.put("errorCode", "INVALID_CREDENTIALS");
            response.put("message", "아이디 또는 비밀번호가 올바르지 않습니다");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 2: 사용자 정보 조회 (특이사항 + 화이트리스트 과업명)
     * GET /api/v1/user/{userId}/{userIdx}
     */
    @GetMapping("/user/{userId}/{userIdx}")
    public Map<String, Object> getUser(@PathVariable("userId") String userId,
                                        @PathVariable("userIdx") int userIdx) {
        System.out.println("[API] GET /api/v1/user/" + userId + "/" + userIdx);
        Map<String, Object> response = new HashMap<>();
        try {
            NungilUserVO user = nungilUserService.getUser(userId, userIdx);
            if (user == null) {
                System.out.println("[결과] 사용자 없음 → USER_NOT_FOUND");
                response.put("status", "ERROR");
                response.put("errorCode", "USER_NOT_FOUND");
                return response;
            }

            List<String> taskNames = new ArrayList<>();
            String whiteList = user.getWhiteList();
            if (whiteList != null && !whiteList.trim().isEmpty()) {
                for (String s : whiteList.split(",")) {
                    try {
                        Long taskId = Long.parseLong(s.trim());
                        TaskVO task = taskService.findById(taskId);
                        if (task != null) taskNames.add(task.getName());
                    } catch (Exception ignored) {}
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("userIdx", userIdx);
            result.put("specialNote", user.getSpecialNote());
            result.put("whiteList", taskNames);

            System.out.println("[결과] 사용자 조회 완료 whiteList=" + taskNames.size() + "개");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 3-1: 오늘 이후 pending 일정 조회 (과업 단계 포함)
     * GET /api/v1/schedule?userId=xxx&idx=1
     */
    @GetMapping("/schedule")
    public Map<String, Object> getSchedule(@RequestParam("userId") String userId,
                                            @RequestParam("idx") int idx) {
        System.out.println("[API] GET /api/v1/schedule | userId=" + userId + ", idx=" + idx);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScheduleVO> schedules = scheduleService.findTodayPendingByUser(userId, idx);

            List<Map<String, Object>> result = new ArrayList<>();
            for (ScheduleVO s : schedules) {
                Map<String, Object> item = new HashMap<>();
                item.put("scheduleId", s.getScheduleId());
                item.put("taskId", s.getTaskId());
                item.put("taskName", s.getTaskName());
                item.put("taskProcess", s.getTaskProcess());
                item.put("scheduledAt", s.getScheduledAt());
                item.put("location", s.getLocation());
                item.put("specialNote", s.getSpecialNote());
                item.put("status", s.getStatus());
                result.add(item);
            }

            System.out.println("[결과] 일정 " + result.size() + "개 조회");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 3-2: 일정 완료 처리
     * PATCH /api/v1/schedule/{scheduleId}/complete
     */
    @PatchMapping("/schedule/{scheduleId}/complete")
    public Map<String, Object> completeSchedule(@PathVariable("scheduleId") Long scheduleId) {
        System.out.println("[API] PATCH /api/v1/schedule/" + scheduleId + "/complete");
        Map<String, Object> response = new HashMap<>();
        try {
            scheduleService.complete(scheduleId);
            System.out.println("[결과] 일정 완료 처리");
            response.put("status", "SUCCESS");
            response.put("message", "일정이 완료됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 4-1: 질문 로그 시작
     * POST /api/v1/question/log
     * Body: { "userId": "guardian01", "userIdx": 1, "content": "...", "answer": "..." }
     */
    @PostMapping("/question/log")
    public Map<String, Object> startQuestion(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/question/log | userId=" + body.get("userId") + ", userIdx=" + body.get("userIdx"));
        Map<String, Object> response = new HashMap<>();
        try {
            String userId  = (String) body.get("userId");
            int    userIdx = Integer.parseInt(body.get("userIdx").toString());

            // FK 오류 전에 유저 존재 여부 먼저 확인
            NungilUserVO user = nungilUserService.getUser(userId, userIdx);
            if (user == null) {
                System.out.println("[ERROR] NUNGIL_USER 없음 (userId=" + userId + ", userIdx=" + userIdx + ") → USER_NOT_FOUND");
                response.put("status", "ERROR");
                response.put("errorCode", "USER_NOT_FOUND");
                response.put("message", "등록된 사용자가 없어요. userId=" + userId + ", userIdx=" + userIdx);
                return response;
            }

            QuestionVO question = new QuestionVO();
            question.setId(userId);
            question.setIdx(userIdx);
            question.setContent((String) body.get("content"));
            question.setAnswer((String) body.get("answer"));

            questionService.create(question);

            Map<String, Object> result = new HashMap<>();
            result.put("questionId", question.getQuestionId());

            System.out.println("[결과] 질문 로그 생성 questionId=" + question.getQuestionId());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 5: 피보호자 신규 연동
     * POST /api/v1/user/link
     * Body: { "guardianId": "guardian01" }
     */
    @PostMapping("/user/link")
    public Map<String, Object> linkUser(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/user/link | guardianId=" + body.get("guardianId"));
        Map<String, Object> response = new HashMap<>();
        try {
            String guardianId = (String) body.get("guardianId");
            NungilUserVO user = nungilUserService.createUser(guardianId);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", user.getId());
            result.put("userIdx", user.getIdx());

            System.out.println("[결과] 피보호자 생성 완료 userIdx=" + user.getIdx());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 6: 기존 피보호자 목록 조회 (재연동용)
     * GET /api/v1/user/link/{guardianId}
     */
    @GetMapping("/user/link/{guardianId}")
    public Map<String, Object> getLinkedUsers(@PathVariable("guardianId") String guardianId) {
        System.out.println("[API] GET /api/v1/user/link/" + guardianId);
        Map<String, Object> response = new HashMap<>();
        try {
            List<NungilUserVO> users = nungilUserService.getUsersByGuardian(guardianId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (NungilUserVO u : users) {
                Map<String, Object> item = new HashMap<>();
                item.put("userId", u.getId());
                item.put("userIdx", u.getIdx());
                result.add(item);
            }

            System.out.println("[결과] 피보호자 " + result.size() + "명 조회");
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * API 4-2: 질문 완료 처리
     * PATCH /api/v1/question/log/{questionId}/complete
     */
    @PatchMapping("/question/log/{questionId}/complete")
    public Map<String, Object> completeQuestion(@PathVariable("questionId") Long questionId) {
        System.out.println("[API] PATCH /api/v1/question/log/" + questionId + "/complete");
        Map<String, Object> response = new HashMap<>();
        try {
            questionService.complete(questionId);
            System.out.println("[결과] 질문 완료 처리");
            response.put("status", "SUCCESS");
            response.put("message", "질문이 완료됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
