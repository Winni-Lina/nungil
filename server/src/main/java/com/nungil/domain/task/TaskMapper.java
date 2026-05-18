package com.nungil.domain.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMapper {

    List<TaskVO> findByKeyword(@Param("keyword") String keyword);

    TaskVO findById(@Param("taskId") Long taskId);

    List<TaskVO> findAll();
}
