package com.nungil.chat;

import com.nungil.domain.chat.ChatHistoryMapper;
import com.nungil.domain.chat.ChatHistoryService;
import com.nungil.domain.chat.ChatHistoryVO;
import com.nungil.domain.schedule.ScheduleMapper;
import com.nungil.domain.schedule.ScheduleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatHistoryService 단위 테스트.
 * Mockito 의존성 없이 수동 Fake Mapper 구현으로 검증한다 (다른 서비스 테스트와 동일한 컨벤션).
 */
class ChatHistoryServiceTest {

    private FakeChatHistoryMapper chatHistoryMapper;
    private FakeScheduleMapper scheduleMapper;
    private ChatHistoryService service;

    @BeforeEach
    void setUp() {
        chatHistoryMapper = new FakeChatHistoryMapper();
        scheduleMapper = new FakeScheduleMapper();
        service = new ChatHistoryService(chatHistoryMapper, scheduleMapper);
    }

    private ChatHistoryVO base() {
        ChatHistoryVO vo = new ChatHistoryVO();
        vo.setGuardianId("guard1");
        vo.setUserIdx(1);
        vo.setSender("USER");
        vo.setMessage("오늘 날씨 어때?");
        vo.setChatMode("GENERAL");
        return vo;
    }

    @Test
    @DisplayName("save_자유대화_일정번호없이저장성공")
    void save_자유대화_성공() {
        ChatHistoryVO vo = base();
        service.save(vo);
        assertEquals(1, chatHistoryMapper.inserts.size());
        assertSame(vo, chatHistoryMapper.inserts.get(0));
    }

    @Test
    @DisplayName("save_일정대화_실제존재하는일정번호면저장성공")
    void save_일정대화_존재하는일정_성공() {
        scheduleMapper.existing.add(10L);
        ChatHistoryVO vo = base();
        vo.setChatMode("SCHEDULE");
        vo.setScheduleId(10L);
        service.save(vo);
        assertEquals(1, chatHistoryMapper.inserts.size());
    }

    @Test
    @DisplayName("save_일정대화_일정번호없으면거부")
    void save_일정대화_번호없음_거부() {
        ChatHistoryVO vo = base();
        vo.setChatMode("SCHEDULE");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        assertTrue(e.getMessage().contains("scheduleId is required"));
        assertEquals(0, chatHistoryMapper.inserts.size());
    }

    @Test
    @DisplayName("save_일정대화_존재하지않는일정번호면거부")
    void save_일정대화_존재하지않는번호_거부() {
        ChatHistoryVO vo = base();
        vo.setChatMode("SCHEDULE");
        vo.setScheduleId(999L); // scheduleMapper.existing 에 없음
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        assertTrue(e.getMessage().contains("does not exist"));
        assertEquals(0, chatHistoryMapper.inserts.size());
    }

    @Test
    @DisplayName("save_guardianId없으면거부")
    void save_guardianId_없음_거부() {
        ChatHistoryVO vo = base();
        vo.setGuardianId(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        assertEquals(0, chatHistoryMapper.inserts.size());
    }

    @Test
    @DisplayName("save_빈메시지면거부")
    void save_빈메시지_거부() {
        ChatHistoryVO vo = base();
        vo.setMessage("  ");
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        assertEquals(0, chatHistoryMapper.inserts.size());
    }

    @Test
    @DisplayName("save_잘못된chatMode면거부")
    void save_잘못된모드_거부() {
        ChatHistoryVO vo = base();
        vo.setChatMode("FREE");
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    @DisplayName("save_잘못된sender면거부")
    void save_잘못된sender_거부() {
        ChatHistoryVO vo = base();
        vo.setSender("AI");
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    @DisplayName("findMessages_위임검증_Mapper그대로전달")
    void findMessages_위임() {
        service.findMessages("guard1", 1, 10L, "SCHEDULE");
        assertEquals(1, chatHistoryMapper.findCalls.size());
        FakeChatHistoryMapper.FindCall call = chatHistoryMapper.findCalls.get(0);
        assertEquals("guard1", call.guardianId);
        assertEquals(1, call.userIdx);
        assertEquals(10L, call.scheduleId);
        assertEquals("SCHEDULE", call.chatMode);
    }

    static class FakeChatHistoryMapper implements ChatHistoryMapper {
        List<ChatHistoryVO> inserts = new ArrayList<>();
        List<FindCall> findCalls = new ArrayList<>();

        static class FindCall {
            String guardianId; int userIdx; Long scheduleId; String chatMode;
        }

        @Override public void insert(ChatHistoryVO history) { inserts.add(history); }

        @Override public List<ChatHistoryVO> findMessages(String guardianId, int userIdx, Long scheduleId, String chatMode) {
            FindCall c = new FindCall();
            c.guardianId = guardianId; c.userIdx = userIdx; c.scheduleId = scheduleId; c.chatMode = chatMode;
            findCalls.add(c);
            return List.of();
        }
    }

    static class FakeScheduleMapper implements ScheduleMapper {
        List<Long> existing = new ArrayList<>();

        @Override public void insert(ScheduleVO schedule) { }
        @Override public ScheduleVO findById(Long scheduleId) {
            if (!existing.contains(scheduleId)) return null;
            ScheduleVO vo = new ScheduleVO();
            vo.setScheduleId(scheduleId);
            return vo;
        }
        @Override public List<ScheduleVO> findByUser(String id, int idx, String status) { return List.of(); }
        @Override public void updateStatus(Long scheduleId, String status) { }
        @Override public void updateSuccessAt(Long scheduleId) { }
        @Override public int updateScheduledAt(Long scheduleId, LocalDateTime scheduledAt) { return 1; }
        @Override public int deleteById(Long scheduleId) { return 1; }
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
