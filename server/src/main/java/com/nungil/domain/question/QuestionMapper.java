package com.nungil.domain.question;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface QuestionMapper {

    void insert(QuestionVO question);

    QuestionVO findById(@Param("questionId") Long questionId);

    void updateComplete(@Param("questionId") Long questionId);

    // NT-004: 최근 1시간 내 pending 질문 2개 이상인 사용자 목록
    List<Map<String, Object>> findRecentRepeatUsers();
}
