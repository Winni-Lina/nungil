package com.nungil.domain.chat;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatHistoryMapper {

    /** 대화 1건 저장 */
    void insert(ChatHistoryVO history);

    /**
     * 일정별 대화 조회 — guardian_id + user_idx + schedule_id + chat_mode 일치, created_at 오름차순.
     * scheduleId가 null이면 schedule_id IS NULL 조건으로 조회한다 (GENERAL 대화).
     */
    List<ChatHistoryVO> findMessages(@Param("guardianId") String guardianId,
                                     @Param("userIdx") int userIdx,
                                     @Param("scheduleId") Long scheduleId,
                                     @Param("chatMode") String chatMode);
}
