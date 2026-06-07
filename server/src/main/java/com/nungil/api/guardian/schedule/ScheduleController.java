package com.nungil.api.guardian.schedule;

import com.nungil.domain.schedule.ScheduleService;
import com.nungil.domain.schedule.ScheduleVO;
import com.nungil.domain.task.TaskService;
import com.nungil.domain.task.TaskVO;
import com.nungil.domain.user.NungilUserService;
import com.nungil.domain.user.NungilUserVO;
import com.nungil.infrastructure.firebase.FcmService;
import com.nungil.infrastructure.google.StepGenerationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/guardian/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final NungilUserService nungilUserService;
    private final FcmService fcmService;
    private final TaskService taskService;
    private final StepGenerationService stepGenerationService;

    public ScheduleController(ScheduleService scheduleService,
                               NungilUserService nungilUserService,
                               FcmService fcmService,
                               TaskService taskService,
                               StepGenerationService stepGenerationService) {
        this.scheduleService = scheduleService;
        this.nungilUserService = nungilUserService;
        this.fcmService = fcmService;
        this.taskService = taskService;
        this.stepGenerationService = stepGenerationService;
    }

    /** 일정 등록 POST /api/v1/guardian/schedules
     *  Body: { guardianId, idx, taskId, scheduledAt, location, specialNote }
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/schedules | guardianId=" + body.get("guardianId") + ", taskId=" + body.get("taskId"));
        Map<String, Object> response = new HashMap<>();
        try {
            ScheduleVO schedule = new ScheduleVO();
            schedule.setId((String) body.get("guardianId"));
            schedule.setIdx(Integer.parseInt(body.get("idx").toString()));
            schedule.setTaskId(Long.valueOf(body.get("taskId").toString()));
            schedule.setScheduledAt(LocalDateTime.parse((String) body.get("scheduledAt")));
            schedule.setLocation((String) body.get("location"));         // SC-004
            schedule.setSpecialNote((String) body.get("specialNote"));   // SC-005

            // AI 생성/편집된 맞춤 단계 (List<String> 또는 JSON 문자열, nullable)
            Object customStepsObj = body.get("customSteps");
            if (customStepsObj instanceof List) {
                schedule.setCustomSteps(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(customStepsObj));
            } else if (customStepsObj instanceof String) {
                String cs = ((String) customStepsObj).trim();
                schedule.setCustomSteps(cs.isEmpty() ? null : cs);
            }

            scheduleService.create(schedule);

            // FCM으로 사용자 앱에 알림
            String fcmToken = nungilUserService.getFcmToken(schedule.getId(), schedule.getIdx());
            fcmService.sendScheduleUpdated(fcmToken);

            System.out.println("[결과] 일정 등록 완료 scheduleId=" + schedule.getScheduleId());
            response.put("status", "SUCCESS");
            response.put("message", "일정이 등록됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** AI 맞춤 단계 생성 POST /api/v1/guardian/schedules/generate-steps
     *  Body: { taskId, location, scheduleNote, guardianId, idx }
     *  Return: { status: "SUCCESS", steps: ["단계1", "단계2", ...] }
     */
    @PostMapping("/generate-steps")
    public Map<String, Object> generateSteps(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/schedules/generate-steps | taskId=" + body.get("taskId"));
        Map<String, Object> response = new HashMap<>();
        try {
            Long taskId = Long.valueOf(body.get("taskId").toString());
            String location = (String) body.get("location");
            String scheduleNote = (String) body.get("scheduleNote");
            String guardianId = (String) body.get("guardianId");
            int idx = body.get("idx") != null ? Integer.parseInt(body.get("idx").toString()) : 0;

            TaskVO task = taskService.findById(taskId);
            String taskProcess = task != null ? task.getProcess() : null;

            String userSpecialNote = null;
            if (guardianId != null && !guardianId.isEmpty()) {
                NungilUserVO user = nungilUserService.getUser(guardianId, idx);
                if (user != null) userSpecialNote = user.getSpecialNote();
            }

            List<String> steps = stepGenerationService.generatePersonalizedSteps(
                    taskProcess, location, scheduleNote, userSpecialNote);

            System.out.println("[결과] 맞춤 단계 " + steps.size() + "개 생성");
            response.put("status", "SUCCESS");
            response.put("steps", steps);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** SM-001: 일정 목록 조회 Path Variable GET /api/v1/guardian/schedules/{guardianId}/{idx} */
    @GetMapping("/{guardianId}/{idx}")
    public Map<String, Object> getListByPath(@PathVariable("guardianId") String guardianId,
                                              @PathVariable("idx") int idx,
                                              @RequestParam(value = "status", required = false) String status) {
        System.out.println("[API] GET /api/v1/guardian/schedules/" + guardianId + "/" + idx + " status=" + status);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScheduleVO> schedules = scheduleService.findByUser(guardianId, idx, status);
            System.out.println("[결과] 일정 " + schedules.size() + "개 조회");
            response.put("status", "SUCCESS");
            response.put("schedules", schedules);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 일정 목록 조회 Query Param GET /api/v1/guardian/schedules?guardianId=xxx&idx=1 */
    @GetMapping
    public Map<String, Object> getList(@RequestParam("guardianId") String guardianId,
                                        @RequestParam("idx") int idx,
                                        @RequestParam(value = "status", required = false) String status) {
        System.out.println("[API] GET /api/v1/guardian/schedules | guardianId=" + guardianId + ", idx=" + idx + ", status=" + status);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScheduleVO> schedules = scheduleService.findByUser(guardianId, idx, status);
            System.out.println("[결과] 일정 " + schedules.size() + "개 조회");
            response.put("status", "SUCCESS");
            response.put("schedules", schedules);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 일정 완료 PUT /api/v1/guardian/schedules/{scheduleId}/complete */
    @PutMapping("/{scheduleId}/complete")
    public Map<String, Object> complete(@PathVariable("scheduleId") Long scheduleId) {
        System.out.println("[API] PUT /api/v1/guardian/schedules/" + scheduleId + "/complete");
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

    /** SC-007: 드래그로 시간 변경 PUT /api/v1/guardian/schedules/{scheduleId}/time
     *  Body: { "scheduledAt": "2026-05-03T14:30" }
     */
    @PutMapping("/{scheduleId}/time")
    public Map<String, Object> updateTime(@PathVariable("scheduleId") Long scheduleId,
                                           @RequestBody Map<String, Object> body) {
        System.out.println("[API] PUT /api/v1/guardian/schedules/" + scheduleId + "/time | scheduledAt=" + body.get("scheduledAt"));
        Map<String, Object> response = new HashMap<>();
        try {
            LocalDateTime scheduledAt = LocalDateTime.parse((String) body.get("scheduledAt"));
            ScheduleVO schedule = scheduleService.findById(scheduleId);
            scheduleService.updateScheduledAt(scheduleId, scheduledAt);

            if (schedule != null) {
                String fcmToken = nungilUserService.getFcmToken(schedule.getId(), schedule.getIdx());
                fcmService.sendScheduleUpdated(fcmToken);
            }

            System.out.println("[결과] 일정 시간 변경 완료");
            response.put("status", "SUCCESS");
            response.put("message", "시간이 변경됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** NT-003: 일정 상태 변경 PATCH /api/v1/guardian/schedules/{scheduleId}/status
     *  Body: { "status": "abandoned" }
     */
    @PatchMapping("/{scheduleId}/status")
    public Map<String, Object> updateStatus(@PathVariable("scheduleId") Long scheduleId,
                                             @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        System.out.println("[API] PATCH /api/v1/guardian/schedules/" + scheduleId + "/status | status=" + status);
        Map<String, Object> response = new HashMap<>();
        try {
            scheduleService.updateStatus(scheduleId, status);
            response.put("status", "SUCCESS");
            response.put("message", "일정 상태가 변경됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 일정 삭제 DELETE /api/v1/guardian/schedules/{scheduleId} */
    @DeleteMapping("/{scheduleId}")
    public Map<String, Object> delete(@PathVariable("scheduleId") Long scheduleId) {
        System.out.println("[API] DELETE /api/v1/guardian/schedules/" + scheduleId);
        Map<String, Object> response = new HashMap<>();
        try {
            scheduleService.delete(scheduleId);
            System.out.println("[결과] 일정 삭제 완료");
            response.put("status", "SUCCESS");
            response.put("message", "일정이 삭제됐어요!");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
