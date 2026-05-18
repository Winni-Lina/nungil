package com.nungil.domain.schedule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduleMapper {

    void insert(ScheduleVO schedule);

    ScheduleVO findById(@Param("scheduleId") Long scheduleId);

    List<ScheduleVO> findByUser(@Param("id") String id, @Param("idx") int idx,
                                @Param("status") String status);

    void updateStatus(@Param("scheduleId") Long scheduleId, @Param("status") String status);

    void updateSuccessAt(@Param("scheduleId") Long scheduleId);

    // SC-007: 드래그로 시간 변경
    void updateScheduledAt(@Param("scheduleId") Long scheduleId,
                           @Param("scheduledAt") LocalDateTime scheduledAt);

    void deleteById(@Param("scheduleId") Long scheduleId);

    List<ScheduleVO> findTodayPendingByUser(@Param("id") String id, @Param("idx") int idx);

    List<ScheduleVO> findOverdue();
}
