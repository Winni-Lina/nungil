package com.nungil.domain.task;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public TaskVO searchByKeyword(String keyword) {
        List<TaskVO> results = taskMapper.findByKeyword(keyword);
        System.out.println("[DB] TASK 검색 (keyword=" + keyword + ") → " + results.size() + "건");
        return results.isEmpty() ? null : results.get(0);
    }

    public TaskVO findById(Long taskId) {
        TaskVO task = taskMapper.findById(taskId);
        System.out.println("[DB] TASK 조회 (task_id=" + taskId + ") → " + (task != null ? task.getName() : "없음"));
        return task;
    }

    public List<TaskVO> findAll() {
        List<TaskVO> list = taskMapper.findAll();
        System.out.println("[DB] TASK 전체 조회 → " + list.size() + "건");
        return list;
    }
}
