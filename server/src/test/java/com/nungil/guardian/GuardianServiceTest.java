package com.nungil.guardian;

import com.nungil.domain.guardian.GuardianMapper;
import com.nungil.domain.guardian.GuardianService;
import com.nungil.domain.guardian.GuardianVO;
import com.nungil.domain.schedule.ScheduleMapper;
import com.nungil.domain.schedule.ScheduleVO;
import com.nungil.domain.user.NungilUserMapper;
import com.nungil.domain.user.NungilUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GuardianService 단위 테스트.
 * Mockito 의존성이 없어 수동 Fake Mapper 구현으로 검증한다.
 */
class GuardianServiceTest {

    private FakeGuardianMapper guardianMapper;
    private GuardianService service;

    @BeforeEach
    void setUp() {
        guardianMapper = new FakeGuardianMapper();
        service = new GuardianService(guardianMapper, new NoopUserMapper(), new NoopScheduleMapper());
    }

    private GuardianVO sample(String id, String email) {
        GuardianVO g = new GuardianVO();
        g.setId(id);
        g.setPw("1234");
        g.setEmail(email);
        g.setName("테스터");
        g.setPhone("010-0000-0000");
        return g;
    }

    @Test
    @DisplayName("join_성공_정상가입_DB에insert호출됨")
    void join_성공_정상가입() {
        GuardianVO g = sample("guard1", "g@a.com");
        service.join(g);
        assertEquals(1, guardianMapper.inserts.size());
        assertEquals("guard1", guardianMapper.inserts.get(0).getId());
    }

    @Test
    @DisplayName("join_실패_ID중복_IllegalArgumentException(ID_EXISTS)")
    void join_실패_ID중복() {
        guardianMapper.byId.put("guard1", sample("guard1", "other@a.com"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.join(sample("guard1", "new@a.com")));
        assertEquals("ID_EXISTS", ex.getMessage());
    }

    @Test
    @DisplayName("join_실패_이메일중복_IllegalArgumentException(EMAIL_EXISTS)")
    void join_실패_이메일중복() {
        guardianMapper.byEmail.put("dup@a.com", sample("other", "dup@a.com"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.join(sample("newId", "dup@a.com")));
        assertEquals("EMAIL_EXISTS", ex.getMessage());
    }

    @Test
    @DisplayName("isIdAvailable_true_ID없음")
    void isIdAvailable_true() {
        assertTrue(service.isIdAvailable("nope"));
    }

    @Test
    @DisplayName("isIdAvailable_false_ID존재")
    void isIdAvailable_false() {
        guardianMapper.byId.put("guard1", sample("guard1", "g@a.com"));
        assertFalse(service.isIdAvailable("guard1"));
    }

    @Test
    @DisplayName("login_실패_없는ID_INVALID_CREDENTIALS")
    void login_실패_없는ID() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("nope", "1234"));
        assertEquals("INVALID_CREDENTIALS", ex.getMessage());
    }

    @Test
    @DisplayName("login_실패_비밀번호불일치_INVALID_CREDENTIALS")
    void login_실패_비밀번호불일치() {
        guardianMapper.byId.put("guard1", sample("guard1", "g@a.com"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("guard1", "wrong"));
        assertEquals("INVALID_CREDENTIALS", ex.getMessage());
    }

    @Test
    @DisplayName("login_성공_VO반환")
    void login_성공() {
        guardianMapper.byId.put("guard1", sample("guard1", "g@a.com"));
        GuardianVO out = service.login("guard1", "1234");
        assertNotNull(out);
        assertEquals("guard1", out.getId());
    }

    @Test
    @DisplayName("resetPassword_실패_USER_NOT_FOUND")
    void resetPassword_실패_userNotFound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword("nope", "g@a.com"));
        assertEquals("USER_NOT_FOUND", ex.getMessage());
    }

    @Test
    @DisplayName("resetPassword_실패_EMAIL_MISMATCH")
    void resetPassword_실패_emailMismatch() {
        guardianMapper.byId.put("guard1", sample("guard1", "real@a.com"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword("guard1", "wrong@a.com"));
        assertEquals("EMAIL_MISMATCH", ex.getMessage());
    }

    // ── Fakes ────────────────────────────────────────────────
    static class FakeGuardianMapper implements GuardianMapper {
        Map<String, GuardianVO> byId = new HashMap<>();
        Map<String, GuardianVO> byEmail = new HashMap<>();
        java.util.List<GuardianVO> inserts = new java.util.ArrayList<>();

        @Override public void insert(GuardianVO g) { inserts.add(g); byId.put(g.getId(), g); }
        @Override public GuardianVO findById(String id) { return byId.get(id); }
        @Override public GuardianVO findByEmail(String email) { return byEmail.get(email); }
        @Override public void updateFcmToken(String id, String fcmToken) { }
        @Override public void updatePw(String id, String pw) {
            GuardianVO g = byId.get(id); if (g != null) g.setPw(pw);
        }
        @Override public void deleteById(String id) { byId.remove(id); }
    }

    static class NoopUserMapper implements NungilUserMapper {
        @Override public void insert(NungilUserVO user) { }
        @Override public int getNextIdx(String id) { return 0; }
        @Override public List<NungilUserVO> findByGuardianId(String id) { return List.of(); }
        @Override public NungilUserVO findByIdAndIdx(String id, int idx) { return null; }
        @Override public void updateWhiteList(String id, int idx, String whiteList) { }
        @Override public void updateSpecialNote(String id, int idx, String specialNote) { }
        @Override public void updateUserInfo(String id, int idx, String userName, String userPhone) { }
        @Override public void updateFcmToken(String id, int idx, String fcmToken) { }
        @Override public void deleteByGuardianId(String id) { }
    }

    static class NoopScheduleMapper implements ScheduleMapper {
        @Override public void insert(ScheduleVO schedule) { }
        @Override public ScheduleVO findById(Long scheduleId) { return null; }
        @Override public List<ScheduleVO> findByUser(String id, int idx, String status) { return List.of(); }
        @Override public void updateStatus(Long scheduleId, String status) { }
        @Override public void updateSuccessAt(Long scheduleId) { }
        @Override public void updateScheduledAt(Long scheduleId, LocalDateTime scheduledAt) { }
        @Override public void deleteById(Long scheduleId) { }
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
