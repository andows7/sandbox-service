# Sandbox Service API 接口文档

## 1. 概述
本服务为内部微服务，不对外直接暴露，由网关 (`api-gateway`) 负责路由和鉴权转发。本服务主要负责执行学生提交的代码片段并返回执行结果。

## 2. 接口列表

### 2.1 提交代码执行评测

**路径**: `POST /v1/api/sandbox/execute`
*(别名路径: `POST /api/sandbox/execute`，用于兼容可能存在的旧网关配置)*

**请求头 (Header)**:
- `X-Trace-Id`: (String, Optional) 链路追踪ID，供微服务全链路排查问题使用。

**请求格式 (Request Body)**:
`application/json`

```json
{
  "submitId": "sub-123456",
  "language": "JAVA",
  "code": "public class Main { public static void main(String[] args) { System.out.println(1); } }",
  "testCases": [
    {
      "input": "",
      "expectedOutput": "1"
    }
  ],
  "timeLimitMs": 1000,
  "memoryLimitMb": 256
}
```

**请求参数说明**:
| 字段 | 类型 | 必填 | 描述 | 限制 |
| --- | --- | --- | --- | --- |
| submitId | String | 是 | 判题请求的唯一业务流水号 | 长度 <= 64 |
| language | String | 是 | 编程语言 | `JAVA`, `CPP`, `PYTHON` (不区分大小写) |
| code | String | 是 | 用户提交的源码内容 | 不为空 |
| testCases | List | 否 | 测试用例列表 | - |
| - input | String | 否 | 用例标准输入 (STDIN) | - |
| - expectedOutput | String | 否 | 期望的标准输出 (STDOUT) | - |
| timeLimitMs | Integer | 否 | 时间限制(毫秒) | 100 ~ 10000 之间，默认 10000 |
| memoryLimitMb | Integer | 否 | 内存限制(MB) | 16 ~ 256 之间，默认 256 |

**响应格式 (Response Body)**:
`application/json`

```json
{
  "code": 200,
  "msg": "执行成功",
  "data": {
    "status": "AC",
    "compileError": null,
    "executionTimeMs": 50,
    "memoryUsedMb": 20,
    "testCaseResults": [
      {
        "passed": true,
        "actualOutput": "1",
        "errorLine": null
      }
    ]
  }
}
```

**响应状态枚举 (`status`)**:
- `AC` - 答案正确 (Accepted)
- `WA` - 答案错误 (Wrong Answer)
- `TLE` - 运行超时 (Time Limit Exceeded)
- `MLE` - 内存超限 (Memory Limit Exceeded)
- `RE` - 运行时异常 (Runtime Error)
- `CE` - 编译错误 (Compile Error)

**错误响应示例 (校验未通过)**:
```json
{
  "code": 400,
  "msg": "submitId 不能为空, 时间限制不能低于 100ms",
  "data": null
}
```

**错误响应示例 (编译错误)**:
```json
{
  "code": 200,
  "msg": "执行成功",
  "data": {
    "status": "CE",
    "compileError": "Main.java:2: error: ';' expected\n",
    "executionTimeMs": 0,
    "memoryUsedMb": 0,
    "testCaseResults": []
  }
}
```

---

## 3. 监控端点 (Actuator)

**路径**: `GET /actuator/prometheus`

**描述**: 提供给 Prometheus 抓取的标准指标数据。应用内部配置了相关 Tag `application: sandbox-service`，方便看板聚合。
