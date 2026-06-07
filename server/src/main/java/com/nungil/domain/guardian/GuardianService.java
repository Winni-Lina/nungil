package com.nungil.domain.guardian;

import com.nungil.domain.schedule.ScheduleMapper;
import com.nungil.domain.user.NungilUserMapper;
import org.springframework.stereotype.Service;

@Service
public class GuardianService {

    private final GuardianMapper  guardianMapper;
    private final NungilUserMapper nungilUserMapper;
    private final ScheduleMapper  scheduleMapper;

    public GuardianService(GuardianMapper guardianMapper,
                           NungilUserMapper nungilUserMapper,
                           ScheduleMapper scheduleMapper) {
        this.guardianMapper   = guardianMapper;
        this.nungilUserMapper = nungilUserMapper;
        this.scheduleMapper   = scheduleMapper;
    }

    public void join(GuardianVO guardian) {
        GuardianVO existing = guardianMapper.findById(guardian.getId());
        System.out.println("[DB] GUARDIAN 조회 (id=" + guardian.getId() + ") → " + (existing != null ? "이미 존재" : "없음"));
        if (existing != null) throw new IllegalArgumentException("ID_EXISTS");

        GuardianVO byEmail = guardianMapper.findByEmail(guardian.getEmail());
        if (byEmail != null) throw new IllegalArgumentException("EMAIL_EXISTS");

        guardianMapper.insert(guardian);
        System.out.println("[DB] GUARDIAN INSERT 완료 (id=" + guardian.getId() + ")");
    }

    /** 아이디 중복 여부 확인 */
    public boolean isIdAvailable(String id) {
        return guardianMapper.findById(id) == null;
    }

    public GuardianVO login(String id, String pw) {
        GuardianVO guardian = guardianMapper.findById(id);
        System.out.println("[DB] GUARDIAN 조회 (id=" + id + ") → " + (guardian != null ? "찾음" : "없음"));
        if (guardian == null || !guardian.getPw().equals(pw)) {
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }
        return guardian;
    }

    public GuardianVO findById(String id) {
        GuardianVO guardian = guardianMapper.findById(id);
        System.out.println("[DB] GUARDIAN 조회 (id=" + id + ") → " + (guardian != null ? guardian.getName() : "없음"));
        return guardian;
    }

    public void updateFcmToken(String id, String fcmToken) {
        guardianMapper.updateFcmToken(id, fcmToken);
        System.out.println("[DB] GUARDIAN UPDATE fcm_token (id=" + id + ")");
    }

    /** 비밀번호 찾기: id + email 확인 후 임시 비밀번호 발급 */
    public String resetPassword(String id, String email) {
        GuardianVO guardian = guardianMapper.findById(id);
        if (guardian == null) throw new IllegalArgumentException("USER_NOT_FOUND");
        if (!email.equalsIgnoreCase(guardian.getEmail())) throw new IllegalArgumentException("EMAIL_MISMATCH");

        String tempPw = generateTempPassword();
        guardianMapper.updatePw(id, tempPw);
        System.out.println("[DB] GUARDIAN UPDATE pw (id=" + id + ") → 임시 비밀번호 발급");
        return tempPw;
    }

    /** 계정 삭제: 연관 데이터 순서대로 삭제 (QUESTION → SCHEDULE → NUNGIL_USER → GUARDIAN) */
    public void deleteAccount(String id, String pw) {
        GuardianVO guardian = guardianMapper.findById(id);
        if (guardian == null) throw new IllegalArgumentException("USER_NOT_FOUND");
        if (!guardian.getPw().equals(pw)) throw new IllegalArgumentException("INVALID_CREDENTIALS");

        scheduleMapper.deleteByGuardianId(id);
        nungilUserMapper.deleteByGuardianId(id);
        guardianMapper.deleteById(id);
        System.out.println("[DB] 계정 삭제 완료 (id=" + id + ")");
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }
}
