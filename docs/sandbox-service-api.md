# Sandbox Service API（C 扩展版）

核心接口兼容原 v1.2 契约，新增 C 语言及资源边界。当前 dev 按最新要求前后端直连，不接网关或认证服务；后端保留 Nacos 注册与 MySQL。

## 网关契约（2026-11 对齐）

服务名 `sandbox-service`，网关路由 `lb://sandbox-service`。主路径 `/api/sandbox/execute`，保留 `/v1/api/sandbox/execute`。Nacos 地址采用 `${NACOS_ADDR:nacos:8848}`，本机联调设置 `NACOS_ADDR=127.0.0.1:8848`。

当前 dev 显式配置 `sandbox.gateway.require-user=false`，不要求 Token 或 X-User-Id。prod 保留原内部用户头检查（缺少时 HTTP 401 / 1003），与当前前端直连流程分开。

所有旧 demo 入口统一为 dev。前端位于 frontend，使用 BACKEND_TARGET（默认 http://127.0.0.1:8084）直接代理后端 API 与 Swagger；后端位于 backend。启动步骤见 [Windows/Linux 启动指南](startup-windows-linux.md)。

## 执行接口

`POST /v1/api/sandbox/execute`；兼容 `POST /api/sandbox/execute`。

请求头 `Content-Type: application/json`；可传入 `X-Trace-Id`。

```json
{
  "submitId": "sub-c-001",
  "language": "C",
  "code": "#include <stdio.h>\nint main(void){int a,b;if(scanf(\"%d%d\",&a,&b)!=2)return 1;printf(\"%d\\n\",a+b);return 0;}",
  "testCases": [{"input": "1 2", "expectedOutput": "3"}],
  "timeLimitMs": 3000,
  "memoryLimitMb": 128
}
```

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| submitId | 是 | 非空，最多 64 字符 |
| language | 是 | C、CPP、JAVA、PYTHON，不区分大小写 |
| code | 是 | 非空，最多 65,536 字符；完整程序 |
| testCases | 是 | 1–50 个非 null 元素 |
| testCases[].input | 否 | 最多 16,384 字符；null 视为空输入 |
| testCases[].expectedOutput | 否 | 最多 65,536 字符；null 视为空输出 |
| timeLimitMs | 否 | 100–10,000，省略默认 10,000；显式 null 无效 |
| memoryLimitMb | 否 | 16–256，省略默认 256；显式 null 无效，单位 MiB |

请求体上限 2 MiB；超过实例执行入口并发容量时立即拒绝。编译后更新容器内存限制，禁止额外 swap。Java 的堆会为 JVM 原生开销预留空间，因此请求内存不是 `-Xmx` 的同义值。

```json
{
  "code": 200,
  "msg": "执行成功",
  "data": {
    "status": "AC",
    "compileError": null,
    "executionTimeMs": 80,
    "memoryUsedMb": 2,
    "testCaseResults": [{"passed": true, "actualOutput": "3", "errorLine": null}]
  }
}
```

以上耗时与内存为格式示例。实际值取决于机器、运行时和代码。

| status | 含义 |
| --- | --- |
| AC | 已执行用例全部通过 |
| WA | 输出与预期不一致 |
| CE | 编译失败或编译超时；Python 在运行前执行 `py_compile` 语法检查，失败也返回 CE |
| TLE | 单个用例运行墙钟时间超限，强杀容器 |
| MLE | 退出码 137 或运行时内存异常（例如 Java OutOfMemoryError） |
| RE | 运行失败，或输出 / 诊断读取超过上限 |

`testCaseResults` 仅包含已执行用例；首个失败后停止执行，CE 不返回运行用例。比较前对标准输出和预期输出调用 `trim()`，不进行数值误差或特殊判题比较。

Python 语法检查只生成字节码，不执行顶层代码。`SyntaxError`、`IndentationError` 等语法问题返回 CE；语法检查成功后发生的异常（例如 `ZeroDivisionError`、`NameError`）返回 RE。编译诊断位于 `compileError`，读取上限为 16 KiB，检查超时上限为 10 秒；检查失败后不执行测试用例。

`executionTimeMs` 为执行用例中的最大 Docker exec 墙钟耗时，包含启动开销，不含源码编译和申请容器时间。`memoryUsedMb` 为各已执行程序的峰值 RSS 最大值（GNU time，KiB 向上取整到 MiB）；未采集时为 0，超时和 OOM 时可能缺失。它不是整个容器或服务内存。输出最多读取 64 KiB，错误和编译诊断最多读取 16 KiB。

`compileError` 在 CE 时为编译信息，RE 时兼容承载运行错误。Java / Python 可解析部分错误行号，C / C++ 的 `errorLine` 通常为 null，编译行号保留在诊断文本中。

## 错误与重试

原控制层保留 HTTP 200 + `ApiResponse.code` 的业务错误方式：参数错误 400、容量不足 503、系统异常 500。入口过滤器在解析前直接返回 HTTP 413（已声明请求体过大）或 HTTP 503（并发已满），同样提供 `code`、`msg`、`data`。

调用方同时检查 HTTP 状态和 `body.code`。503 可退避重试；请求无幂等去重，重试会产生新的执行。最长总耗时可能包含冷创建、最多 10 秒编译及多个串行用例，网关超时需按用例数量配置，不能把每用例时限当成整次请求时限。

## 状态接口

`GET /api/sandbox/status`

```json
{
  "code": 200,
  "msg": "执行成功",
  "data": {
    "languages": ["JAVA", "C", "CPP", "PYTHON"],
    "containers": 0,
    "maxContainers": 4,
    "warmupPerLanguage": 0,
    "heapUsedMb": 42,
    "heapCommittedMb": 50
  }
}
```

`containers` 为本实例已预留的总容量（含创建、预热、运行、清理重试），并非集群统计。堆内存只统计当前 JVM。此接口可确认服务可连接，不代表 Docker daemon 与所有语言镜像均健康。

## 调试与监控

- 工作台：前端独立服务 `http://localhost:5173/`，后端不再托管页面
- Swagger：`GET /swagger-ui.html`
- OpenAPI：`GET /v3/api-docs`
- 健康：`GET /actuator/health`
- 指标：`GET /actuator/prometheus`

本机演示见 [demo-guide.md](demo-guide.md)，启动与故障排查见 [startup-windows-linux.md](startup-windows-linux.md)。
