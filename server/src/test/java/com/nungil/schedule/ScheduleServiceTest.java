package com.nungil.schedule;

import com.nungil.domain.schedule.ScheduleMapper;
import com.nungil.domain.schedule.ScheduleService;
import com.nungil.domain.schedule.ScheduleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleServiceTest {

    private SpyMapper mapper;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        mapper = new SpyMapper();
        service = new ScheduleService(mapper);
    }

    @Test
    @DisplayName("create_위임검증_Mapper_insert호출")
    void create_위임() {
        ScheduleVO vo = new ScheduleVO();
        vo.setScheduleId(10L);
        service.create(vo);
        assertEquals(1, mapper.inserts.size());
        assertSame(vo, mapper.inserts.get(0));
    }

    @Test
    @DisplayName("complete_위임검증_updateSuccessAt호출")
    void complete_위임() {
        service.complete(42L);
        assertEquals(List.of(42L), mapper.successAtIds);
    }

    @Test
    @DisplayName("updateScheduledAt_위임검증_파라미터그대로전달")
    void updateScheduledAt_위임() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 7, 13, 0);
        service.updateScheduledAt(7L, t);
        assertEquals(1, mapper.scheduledAtCalls.size());
        assertEquals(7L, mapper.scheduledAtCalls.get(0).id);
        assertEquals(t, mapper.scheduledAtCalls.get(0).at);
    }

    @Test
    @DisplayName("delete_위임검증_deleteById호출")
    void delete_위임() {
        service.delete(99L);
        assertEquals(List.of(99L), mapper.deletedIds);
    }

    static class SpyMapper implements ScheduleMapper {
        List<ScheduleVO> inserts = new ArrayList<>();
        List<Long> successAtIds = new ArrayList<>();
        List<Long> deletedIds = new ArrayList<>();
        List<ScheduledAtCall> scheduledAtCalls = new ArrayList<>();

        static class ScheduledAtCall { Long id; LocalDateTime at; }

        @Override public void insert(ScheduleVO schedule) { inserts.add(schedule); }
        @Override public ScheduleVO findById(Long scheduleId) { return null; }
        @Override public List<ScheduleVO> findByUser(String id, int idx, String status) { return List.of(); }
        @Override public void updateStatus(Long scheduleId, String status) { }
        @Override public void updateSuccessAt(Long scheduleId) { successAtIds.add(scheduleId); }
        @Override public void updateScheduledAt(Long scheduleId, LocalDateTime scheduledAt) {
            ScheduledAtCall c = new ScheduledAtCall(); c.id = scheduleId; c.at = scheduledAt;
            scheduledAtCalls.add(c);
        }
        @Override public void deleteById(Long scheduleId) { deletedIds.add(scheduleId); }
        @Override public List<ScheduleVO> findTodayPendingByUser(String id, int idx) { return List.of(); }
        @Override public List<ScheduleVO> findOverdue() { return List.of(); }
        @Override public void deleteByGuardianId(String id) { }
        @Override public List<ScheduleVO> findByDate(String id, int idx, String date) { return List.of(); }
        @Override public void incrementQuestionCount(Long scheduleId) { }
        @Override public void updateCustomSteps(Long scheduleId, String customSteps) { }
        @Override public List<Map<String, Object>> findRecentRepeatUsers() { return List.of(); }
        @Override public List<Map<String, Object>> findTaskTrends(String id, int idx, int days) { return List.of(); }
    }
}
