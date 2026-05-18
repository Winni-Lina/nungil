package com.nungil.infrastructure.scheduler;

import com.nungil.domain.guardian.GuardianService;
import com.nungil.domain.guardian.GuardianVO;
import com.nungil.domain.question.QuestionMapper;
import com.nungil.domain.schedule.ScheduleService;
import com.nungil.domain.schedule.ScheduleVO;
import com.nungil.domain.user.NungilUserService;
import com.nungil.domain.user.NungilUserVO;
import com.nungil.infrastructure.firebase.FcmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NungilScheduler {

    private final ScheduleService scheduleService;
    private final GuardianService guardianService;
    private final NungilUserService nungilUserService;
    private final QuestionMapper questionMapper;
    private final FcmService fcmService;

    // 이미 알림 보낸 ID 추적 (서버 재시작 시 초기화)
    private final Set<Long> notifiedScheduleIds = new HashSet<>();
    private final Set<String> notifiedRepeatKeys = new HashSet<>(); // "id:idx"

    public NungilScheduler(ScheduleService scheduleService,
                            GuardianService guardianService,
                            NungilUserService nungilUserService,
                            QuestionMapper questionMapper,
                            FcmService fcmService) {
        this.scheduleService = scheduleService;
        this.guardianService = guardianService;
        this.nungilUserService = nungilUserService;
        this.questionMapper = questionMapper;
        this.fcmService = fcmService;
    }

    /**
     * NT-003: 일정 지연 알림 — 1분마다 체크
     * 예약 시간이 지났는데 아직 pending인 일정 → 보호자에게 FCM
     */
    @Scheduled(fixedRate = 60000)
    public void checkOverdueSchedules() {
        try {
            List<ScheduleVO> overdueList = scheduleService.findOverdue();
            for (ScheduleVO s : overdueList) {
                if (notifiedScheduleIds.contains(s.getScheduleId())) continue;

                GuardianVO guardian = guardianService.findById(s.getId());
                if (guardian == null || guardian.getFcmToken() == null || guardian.getFcmToken().isEmpty()) continue;

                NungilUserVO user = nungilUserService.getUser(s.getId(), s.getIdx());
                String userName  = (user != null && user.getUserName()  != null) ? user.getUserName()  : "사용자";
                String userPhone = (user != null && user.getUserPhone() != null) ? user.getUserPhone() : "";

                fcmService.sendScheduleOverdue(guardian.getFcmToken(), userName, userPhone, s.getScheduleId());
                notifiedScheduleIds.add(s.getScheduleId());
                System.out.println("[NT-003] 알림 발송 scheduleId=" + s.getScheduleId() + " guardianId=" + s.getId());
            }
        } catch (Exception e) {
            System.out.println("[NT-003] 스케줄러 오류: " + e.getMessage());
        }
    }

    /**
     * NT-004: 반복 질문 알림 — 5분마다 체크
     * 최근 1시간 내 같은 사용자가 질문 2회 이상 → 보호자에게 FCM
     */
    @Scheduled(fixedRate = 300000)
    public void checkRepeatQuestions() {
        try {
            List<Map<String, Object>> repeatUsers = questionMapper.findRecentRepeatUsers();
            for (Map<String, Object> row : repeatUsers) {
                String id  = String.valueOf(row.get("ID"));
                int    idx = ((Number) row.get("IDX")).intValue();
                String key = id + ":" + idx;

                if (notifiedRepeatKeys.contains(key)) continue;

                GuardianVO guardian = guardianService.findById(id);
                if (guardian == null || guardian.getFcmToken() == null || guardian.getFcmToken().isEmpty()) continue;

                NungilUserVO user = nungilUserService.getUser(id, idx);
                String userName  = (user != null && user.getUserName()  != null) ? user.getUserName()  : "사용자";
                String userPhone = (user != null && user.getUserPhone() != null) ? user.getUserPhone() : "";

                fcmService.sendRepeatQuestion(guardian.getFcmToken(), userName, userPhone);
                notifiedRepeatKeys.add(key);
                System.out.println("[NT-004] 알림 발송 id=" + id + " idx=" + idx);
            }
        } catch (Exception e) {
            System.out.println("[NT-004] 스케줄러 오류: " + e.getMessage());
        }
    }
}
