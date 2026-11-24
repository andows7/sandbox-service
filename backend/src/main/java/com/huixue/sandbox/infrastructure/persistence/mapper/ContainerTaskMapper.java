package com.huixue.sandbox.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huixue.sandbox.infrastructure.persistence.entity.ContainerTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContainerTaskMapper extends BaseMapper<ContainerTaskEntity> {
}
