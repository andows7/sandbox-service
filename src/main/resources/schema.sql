SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sandbox_container_task` (
  `container_id` VARCHAR(64) NOT NULL COMMENT 'Docker 容器 ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'IDLE' COMMENT '状态: IDLE, RUNNING, DEAD',
  `allocate_time` DATETIME DEFAULT NULL COMMENT '最新分配时间',
  PRIMARY KEY (`container_id`),
  INDEX `idx_status` (`status`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COMMENT='容器池运行期调度状态表(重启清空)';

CREATE TABLE IF NOT EXISTS `sandbox_execution_audit` (
  `trace_id` VARCHAR(64) NOT NULL COMMENT '链路追踪 TraceId',
  `submit_id` VARCHAR(64) NOT NULL COMMENT '评测服务传递的 SubmitID',
  `container_id` VARCHAR(64) NOT NULL COMMENT '实际执行的容器 ID',
  `language` VARCHAR(20) NOT NULL COMMENT '执行语言',
  `result_status` VARCHAR(20) NOT NULL COMMENT '执行结束状态(AC,CE,TLE,MLE,RE,WA)',
  `cost_time_ms` INT NOT NULL DEFAULT 0 COMMENT '执行总耗时(ms)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计创建时间',
  PRIMARY KEY (`trace_id`),
  INDEX `idx_submit_id` (`submit_id`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COMMENT='单次代码执行审计流水(重启清空)';
