package com.huixue.sandbox.domain.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceLimit {
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
}
