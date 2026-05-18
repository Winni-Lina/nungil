package com.nungil.domain.question;

import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    private final QuestionMapper questionMapper;

    public QuestionService(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    public void create(QuestionVO question) {
        System.out.println("[DB] QUESTION INSERT 시도 (id=" + question.getId() + ", idx=" + question.getIdx() + ")");
        questionMapper.insert(question);
        System.out.println("[DB] QUESTION INSERT 완료 → question_id=" + question.getQuestionId());
    }

    public QuestionVO findById(Long questionId) {
        QuestionVO q = questionMapper.findById(questionId);
        System.out.println("[DB] QUESTION 조회 (question_id=" + questionId + ") → " + (q != null ? q.getStatus() : "없음"));
        return q;
    }

    public void complete(Long questionId) {
        questionMapper.updateComplete(questionId);
        System.out.println("[DB] QUESTION UPDATE status=completed, success_at=NOW (question_id=" + questionId + ")");
    }
}
