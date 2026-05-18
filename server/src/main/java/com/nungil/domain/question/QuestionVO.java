package com.nungil.domain.question;

import java.time.LocalDateTime;

public class QuestionVO {

    private Long questionId;
    private String id;
    private int idx;
    private String content;
    private String answer;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime successAt;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getIdx() { return idx; }
    public void setIdx(int idx) { this.idx = idx; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSuccessAt() { return successAt; }
    public void setSuccessAt(LocalDateTime successAt) { this.successAt = successAt; }
}
