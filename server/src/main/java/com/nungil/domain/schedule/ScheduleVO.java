package com.nungil.domain.schedule;

import java.time.LocalDateTime;

public class ScheduleVO {

    private Long scheduleId;
    private Long taskId;
    private String id;          // 보호자 id
    private int idx;            // 사용자 순번
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime successAt;
    private String location;    // SC-004: 장소 (세탁실, 부엌, 거실, 화장실, 방, 기타)
    private String specialNote; // SC-005: 일정 특이사항

    // JOIN 조회용
    private String taskName;
    private String taskProcess;

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getIdx() { return idx; }
    public void setIdx(int idx) { this.idx = idx; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSuccessAt() { return successAt; }
    public void setSuccessAt(LocalDateTime successAt) { this.successAt = successAt; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getSpecialNote() { return specialNote; }
    public void setSpecialNote(String specialNote) { this.specialNote = specialNote; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getTaskProcess() { return taskProcess; }
    public void setTaskProcess(String taskProcess) { this.taskProcess = taskProcess; }
}
