package com.huixue.sandbox.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sandbox_execution_audit")
public class ExecutionAuditEntity {
    @TableId
    private String traceId;
    private String submitId;
    private String containerId;
    private String language;
    private String resultStatus;
    private Integer costTimeMs;
    private LocalDateTime createTime;
}
