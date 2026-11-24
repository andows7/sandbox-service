package com.huixue.sandbox.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sandbox_container_task")
public class ContainerTaskEntity {
    @TableId
    private String containerId;
    private String status;
    private LocalDateTime allocateTime;
}
