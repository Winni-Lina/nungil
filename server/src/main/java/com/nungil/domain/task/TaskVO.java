package com.nungil.domain.task;

public class TaskVO {

    private Long taskId;
    private String name;
    private String process; // 단계별 가이드 (CLOB)

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }
}
