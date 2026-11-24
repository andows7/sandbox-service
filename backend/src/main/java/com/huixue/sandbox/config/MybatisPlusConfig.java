package com.huixue.sandbox.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.huixue.sandbox.infrastructure.persistence.mapper")
public class MybatisPlusConfig {
}
