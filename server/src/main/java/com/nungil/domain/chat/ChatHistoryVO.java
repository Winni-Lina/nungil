package com.nungil.domain.chat;

import java.time.LocalDateTime;

/**
 * CHAT_HISTORY 한 행 (사용자-AI 대화 1건).
 *  - chatMode : "GENERAL" | "SCHEDULE"
 *  - sender   : "USER" | "ASSISTANT" | "SYSTEM"
 *  - scheduleId/stepIndex : SCHEDULE 대화에서만 채워지고 GENERAL은 null 가능
 *  - intent   : 사용자 말의 목적 (AI 의도 분류 기능이 채움, 없으면 null)
 */
public class ChatHistoryVO {

    private Long          messageId;
    private String        guardianId;
    private Integer       userIdx;
    private Long          scheduleId;
    private Integer       stepIndex;
    private String        chatMode;
    private String        sender;
    private String        message;
    private String        intent;
    private LocalDateTime createdAt;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public String getGuardianId() { return guardianId; }
    public void setGuardianId(String guardianId) { this.guardianId = guardianId; }

    public Integer getUserIdx() { return userIdx; }
    public void setUserIdx(Integer userIdx) { this.userIdx = userIdx; }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }

    public String getChatMode() { return chatMode; }
    public void setChatMode(String chatMode) { this.chatMode = chatMode; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
