package com.nungil.api.nungil;

import com.nungil.domain.schedule.ScheduleService;
import com.nungil.domain.schedule.ScheduleVO;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/schedule")
public class NungilScheduleController {

    private final ScheduleService scheduleService;

    public NungilScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 일정 등록 POST /api/schedule
     *  Body: { guardianId, idx, taskId, date, time, location, specialNote }
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/schedule | guardianId=" + body.get("guardianId") + ", taskId=" + body.get("taskId"));
        Map<String, Object> response = new HashMap<>();
        try {
            String guardianId = (String) body.get("guardianId");
            int    idx        = Integer.parseInt(body.get("idx").toString());
            Long   taskId     = Long.valueOf(body.get("taskId").toString());
            String date       = (String) body.get("date");
            String time       = (String) body.get("time");

            LocalDateTime scheduledAt = LocalDateTime.parse(
                date + "T" + time,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            );

            // 중복 일정 확인
            List<ScheduleVO> existing = scheduleService.findByUser(guardianId, idx, "pending");
            boolean conflict = existing.stream()
                .anyMatch(s -> s.getScheduledAt() != null && s.getScheduledAt().equals(scheduledAt));

            if (conflict) {
                System.out.println("[결과] 일정 중복 → CONFLICT");
                response.put("status", "CONFLICT");
                response.put("conflict", true);
                response.put("message", "같은 시간에 이미 일정이 있어요");
                return response;
            }

            ScheduleVO schedule = new ScheduleVO();
            schedule.setId(guardianId);
            schedule.setIdx(idx);
            schedule.setTaskId(taskId);
            schedule.setScheduledAt(scheduledAt);
            schedule.setLocation((String) body.get("location"));         // SC-004
            schedule.setSpecialNote((String) body.get("specialNote"));   // SC-005

            scheduleService.create(schedule);
            System.out.println("[결과] 일정 등록 완료");

            response.put("status", "SUCCESS");
            response.put("conflict", false);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 일정 목록 조회 GET /api/schedule?guardianId=xxx&idx=1&status=pending */
    @GetMapping
    public Map<String, Object> getList(@RequestParam("guardianId") String guardianId,
                                        @RequestParam("idx") int idx,
                                        @RequestParam(value = "status", required = false) String status) {
        System.out.println("[API] GET /api/schedule | guardianId=" + guardianId + ", idx=" + idx + ", status=" + status);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScheduleVO> schedules = scheduleService.findByUser(guardianId, idx, status);
            System.out.println("[결과] 일정 " + schedules.size() + "개");
            response.put("status", "SUCCESS");
            response.put("schedules", schedules);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 일정 목록 조회 GET /api/schedule/{guardianId}/{idx} */
    @GetMapping("/{guardianId}/{idx}")
    public Map<String, Object> getListByPath(@PathVariable("guardianId") String guardianId,
                                              @PathVariable("idx") int idx,
                                              @RequestParam(value = "status", required = false) String status) {
        System.out.println("[API] GET /api/schedule/" + guardianId + "/" + idx + " status=" + status);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScheduleVO> schedules = scheduleService.findByUser(guardianId, idx, status);
            System.out.println("[결과] 일정 " + schedules.size() + "개");
            response.put("status", "SUCCESS");
            response.put("schedules", schedules);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
