package com.nungil.domain.chat;

import java.util.List;

import org.springframework.stereotype.Service;

import com.nungil.domain.schedule.ScheduleMapper;

/**
 * 대화 History 저장/조회 서비스.
 *  - 저장 전 입력값을 검증한다 (허용값·필수값). 잘못된 요청은 IllegalArgumentException.
 *  - 조회는 일정 번호 등 조건으로 시간순 목록을 반환한다.
 */
@Service
public class ChatHistoryService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final ScheduleMapper scheduleMapper;

    public ChatHistoryService(ChatHistoryMapper chatHistoryMapper, ScheduleMapper scheduleMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.scheduleMapper = scheduleMapper;
    }

    /** 대화 1건 저장. 검증 실패 시 IllegalArgumentException(사유). */
    public void save(ChatHistoryVO vo) {
        validate(vo);
        chatHistoryMapper.insert(vo);
    }

    /** 일정별(또는 GENERAL) 대화 조회 — created_at 오름차순. */
    public List<ChatHistoryVO> findMessages(String guardianId, int userIdx,
                                            Long scheduleId, String chatMode) {
        return chatHistoryMapper.findMessages(guardianId, userIdx, scheduleId, chatMode);
    }

    // ── 입력값 검증 (문서 5.6 / 5.9-4단계) ──────────────────────────────
    private void validate(ChatHistoryVO vo) {
        if (vo == null) throw new IllegalArgumentException("EMPTY_BODY");

        if (isBlank(vo.getGuardianId())) throw new IllegalArgumentException("guardianId is required");
        if (vo.getUserIdx() == null)     throw new IllegalArgumentException("userIdx is required");

        String mode = vo.getChatMode();
        if (!"GENERAL".equals(mode) && !"SCHEDULE".equals(mode)) {
            throw new IllegalArgumentException("chatMode must be GENERAL or SCHEDULE");
        }

        String sender = vo.getSender();
        if (!"USER".equals(sender) && !"ASSISTANT".equals(sender) && !"SYSTEM".equals(sender)) {
            throw new IllegalArgumentException("sender must be USER, ASSISTANT or SYSTEM");
        }

        if (isBlank(vo.getMessage())) {
            throw new IllegalArgumentException("message must not be empty");
        }

        if ("SCHEDULE".equals(mode)) {
            if (vo.getScheduleId() == null) {
                throw new IllegalArgumentException("scheduleId is required for SCHEDULE chat");
            }
            if (scheduleMapper.findById(vo.getScheduleId()) == null) {
                throw new IllegalArgumentException("scheduleId does not exist: " + vo.getScheduleId());
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
