package com.huixue.sandbox.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huixue.sandbox.infrastructure.persistence.entity.ExecutionAuditEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExecutionAuditMapper extends BaseMapper<ExecutionAuditEntity> {
}
