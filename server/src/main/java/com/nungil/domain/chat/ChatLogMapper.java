package com.nungil.domain.chat;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatLogMapper {

    /** 대화 1턴 저장 (append-only) */
    void insert(ChatLogVO log);

    /** 자유대화(schedule_id IS NULL) 최근 N턴 — created_at DESC */
    List<ChatLogVO> findRecentChat(@Param("userId") String userId,
                                   @Param("userIdx") int userIdx,
                                   @Param("limit") int limit);

    /** 특정 일정의 최근 N턴 — created_at DESC */
    List<ChatLogVO> findRecentBySchedule(@Param("scheduleId") long scheduleId,
                                         @Param("limit") int limit);

    /** 특정 일정의 전체 대화 — created_at ASC (완료 요약용) */
    List<ChatLogVO> findBySchedule(@Param("scheduleId") long scheduleId);
}
