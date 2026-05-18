package com.nungil.domain.guardian;

import org.springframework.stereotype.Service;

@Service
public class GuardianService {

    private final GuardianMapper guardianMapper;

    public GuardianService(GuardianMapper guardianMapper) {
        this.guardianMapper = guardianMapper;
    }

    public void join(GuardianVO guardian) {
        GuardianVO existing = guardianMapper.findById(guardian.getId());
        System.out.println("[DB] GUARDIAN 조회 (id=" + guardian.getId() + ") → " + (existing != null ? "이미 존재" : "없음"));
        if (existing != null) throw new IllegalArgumentException("ID_EXISTS");
        guardianMapper.insert(guardian);
        System.out.println("[DB] GUARDIAN INSERT 완료 (id=" + guardian.getId() + ")");
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
}
