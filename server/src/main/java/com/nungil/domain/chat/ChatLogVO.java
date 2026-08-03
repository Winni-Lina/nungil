package com.nungil.domain.chat;

import java.time.LocalDateTime;

/**
 * 사용자 대화 로그 1건 (append-only).
 *  - role : "user" | "model"
 *  - kind : "chat" (기본) | "summary" (미래 일자별 요약용)
 *  - scheduleId : 일정 수행 대화면 해당 일정, 자유대화면 null
 */
public class ChatLogVO {

    private Long          chatId;
    private String        userId;
    private Integer       userIdx;
    private Long          scheduleId;
    private String        role;
    private String        kind;
    private String        message;
    private LocalDateTime createdAt;

    public ChatLogVO() {}

    public ChatLogVO(String userId, Integer userIdx, Long scheduleId,
                     String role, String kind, String message) {
        this.userId     = userId;
        this.userIdx    = userIdx;
        this.scheduleId = scheduleId;
        this.role       = role;
        this.kind       = kind;
        this.message    = message;
    }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Integer getUserIdx() { return userIdx; }
    public void setUserIdx(Integer userIdx) { this.userIdx = userIdx; }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
