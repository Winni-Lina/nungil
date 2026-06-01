package com.nungil.api.guardian.report;

import com.nungil.domain.schedule.ScheduleMapper;
import com.nungil.domain.schedule.ScheduleVO;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/guardian/report")
public class ReportController {

    private final ScheduleMapper scheduleMapper;

    public ReportController(ScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * GET /api/v1/guardian/report/daily?id=xxx&idx=1&date=yyyy-MM-dd
     */
    @GetMapping("/daily")
    public Map<String, Object> getDailyReport(
            @RequestParam("id")   String id,
            @RequestParam("idx")  int    idx,
            @RequestParam("date") String date) {

        System.out.println("[API] GET /api/v1/guardian/report/daily | id=" + id + ", idx=" + idx + ", date=" + date);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScheduleVO> schedules = scheduleMapper.findByDate(id, idx, date);

            int totalSchedules      = schedules.size();
            int completedSchedules  = 0;
            int totalQuestions      = 0;
            int selfCompletedCount  = 0;
            int alertTriggeredCount = 0;

            List<Map<String, Object>> taskStats = new ArrayList<>();

            for (ScheduleVO s : schedules) {
                boolean completed      = "completed".equals(s.getStatus());
                int     qCount         = s.getQuestionCount();
                boolean selfCompleted  = completed && qCount == 0;
                boolean alertTriggered = qCount >= 3;

                if (completed)      completedSchedules++;
                totalQuestions     += qCount;
                if (selfCompleted)  selfCompletedCount++;
                if (alertTriggered) alertTriggeredCount++;

                Map<String, Object> stat = new HashMap<>();
                stat.put("scheduleId",     s.getScheduleId());
                stat.put("taskName",       s.getTaskName() != null ? s.getTaskName() : "과업");
                stat.put("status",         s.getStatus());
                stat.put("questionCount",  qCount);
                stat.put("selfCompleted",  selfCompleted);
                stat.put("alertTriggered", alertTriggered);
                taskStats.add(stat);
            }

            int completionRate = totalSchedules > 0
                    ? (int) Math.round(completedSchedules * 100.0 / totalSchedules) : 0;

            Map<String, Object> result = new HashMap<>();
            result.put("totalSchedules",     totalSchedules);
            result.put("completedSchedules", completedSchedules);
            result.put("completionRate",      completionRate);
            result.put("totalQuestions",      totalQuestions);
            result.put("selfCompletedCount",  selfCompletedCount);
            result.put("alertTriggeredCount", alertTriggeredCount);
            result.put("taskStats",           taskStats);

            System.out.println("[결과] 보고서 조회 완료 schedules=" + totalSchedules);
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
     * GET /api/v1/guardian/report/task-trends?id=xxx&idx=1&days=14
     */
    @GetMapping("/task-trends")
    public Map<String, Object> getTaskTrends(
            @RequestParam("id")                  String id,
            @RequestParam("idx")                 int    idx,
            @RequestParam(value="days", defaultValue="14") int days) {

        System.out.println("[API] GET /api/v1/guardian/report/task-trends | id=" + id + ", idx=" + idx + ", days=" + days);
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> rows = scheduleMapper.findTaskTrends(id, idx, days);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                int    total     = toInt(row.get("totalCount"));
                int    completed = toInt(row.get("completedCount"));
                double avgQ      = toDouble(row.get("avgQuestions"));
                Double recentAvgQ = toDoubleOrNull(row.get("recentAvgQ"));
                Double prevAvgQ   = toDoubleOrNull(row.get("prevAvgQ"));

                double completionRate = total > 0 ? completed * 100.0 / total : 0;

                String label;
                String trend;
                if (total < 2) {
                    label = "데이터 부족";
                    trend = "unknown";
                } else if (completionRate >= 70 && avgQ < 1.0) {
                    label = "이제 잘해요";
                    trend = "good";
                } else if (recentAvgQ != null && prevAvgQ != null && recentAvgQ < prevAvgQ - 0.5) {
                    label = "점점 나아지고 있어요";
                    trend = "improving";
                } else if (avgQ >= 2.0) {
                    label = "아직 어려워해요";
                    trend = "hard";
                } else if (completionRate < 30) {
                    label = "아직 못하겠어요";
                    trend = "struggling";
                } else {
                    label = "진행 중";
                    trend = "ongoing";
                }

                Map<String, Object> item = new HashMap<>();
                item.put("taskId",        row.get("taskId"));
                item.put("taskName",      row.get("taskName"));
                item.put("totalCount",    total);
                item.put("completedCount",completed);
                item.put("completionRate",(int) Math.round(completionRate));
                item.put("avgQuestions",  avgQ);
                item.put("recentAvgQ",    recentAvgQ);
                item.put("prevAvgQ",      prevAvgQ);
                item.put("label",         label);
                item.put("trend",         trend);
                result.add(item);
            }

            System.out.println("[결과] 추세 조회 완료 tasks=" + result.size());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }

    private Double toDoubleOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }
}
