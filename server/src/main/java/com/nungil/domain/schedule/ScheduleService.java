package com.nungil.domain.schedule;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;

    public ScheduleService(ScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    public void create(ScheduleVO schedule) {
        scheduleMapper.insert(schedule);
        System.out.println("[DB] SCHEDULE INSERT → schedule_id=" + schedule.getScheduleId()
                + ", task_id=" + schedule.getTaskId() + ", 시간=" + schedule.getScheduledAt());
    }

    public ScheduleVO findById(Long scheduleId) {
        ScheduleVO s = scheduleMapper.findById(scheduleId);
        System.out.println("[DB] SCHEDULE 조회 (schedule_id=" + scheduleId + ") → "
                + (s != null ? s.getTaskName() + " / " + s.getStatus() : "없음"));
        return s;
    }

    public List<ScheduleVO> findByUser(String guardianId, int idx, String status) {
        List<ScheduleVO> list = scheduleMapper.findByUser(guardianId, idx, status);
        System.out.println("[DB] SCHEDULE JOIN TASK 조회 (id=" + guardianId + ", idx=" + idx
                + ", status=" + status + ") → " + list.size() + "건");
        return list;
    }

    public void complete(Long scheduleId) {
        scheduleMapper.updateSuccessAt(scheduleId);
        System.out.println("[DB] SCHEDULE UPDATE status=completed, success_at=NOW (schedule_id=" + scheduleId + ")");
    }

    public void updateScheduledAt(Long scheduleId, LocalDateTime scheduledAt) {
        scheduleMapper.updateScheduledAt(scheduleId, scheduledAt);
        System.out.println("[DB] SCHEDULE UPDATE scheduled_at=" + scheduledAt + " (schedule_id=" + scheduleId + ")");
    }

    public void delete(Long scheduleId) {
        scheduleMapper.deleteById(scheduleId);
        System.out.println("[DB] SCHEDULE DELETE (schedule_id=" + scheduleId + ")");
    }

    public void updateStatus(Long scheduleId, String status) {
        scheduleMapper.updateStatus(scheduleId, status);
        System.out.println("[DB] SCHEDULE UPDATE status=" + status + " (schedule_id=" + scheduleId + ")");
    }

    public List<ScheduleVO> findOverdue() {
        List<ScheduleVO> list = scheduleMapper.findOverdue();
        System.out.println("[DB] SCHEDULE 지연 일정 조회 → " + list.size() + "건");
        return list;
    }

    public List<ScheduleVO> findTodayPendingByUser(String id, int idx) {
        List<ScheduleVO> list = scheduleMapper.findTodayPendingByUser(id, idx);
        System.out.println("[DB] SCHEDULE JOIN TASK 조회 오늘이후 pending (id=" + id + ", idx=" + idx + ") → " + list.size() + "건");
        return list;
    }
}
