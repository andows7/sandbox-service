<div align="center">
  <h1>慧学一体化教学平台 · 代码执行沙箱服务</h1>
  <p>基于 Spring Boot 3 与 Docker 的多语言代码执行、判题及演示工作台</p>
</div>

## 项目介绍

`sandbox-service` 接收完整程序、标准输入、预期输出及资源限制，在隔离 Docker 容器中完成编译、执行和结果比较，返回 AC、WA、CE、TLE、MLE、RE 六类状态。

项目从 Java 单语言执行扩展为 **C、C++、Java、Python 四语言**。当前采用前后端分离结构：`frontend/` 提供独立工作台，`backend/` 提供 REST API、语言策略、容器管理和审计。

默认启动模式为 **dev**：后端使用 MySQL，并向 Nacos 注册为 `sandbox-service`；前端直接代理后端接口，无需网关或登录 Token。旧 `demo` profile、启动脚本及 IDEA 入口兼容到 dev。生产 `prod` 保留用户头检查。

## 核心功能

- **四语言执行**：C 使用 GCC C17，C++ 使用 G++ C++17，Java 使用 OpenJDK 17，Python 使用 Python 3。Python 先进行 `py_compile` 语法检查，语法错误返回 CE，运行时异常返回 RE。
- **资源隔离**：执行容器使用非 root 用户、无网络、只读根文件系统、受限 tmpfs、移除 capabilities 及禁止提权，并设置 CPU、内存、进程数和超时限制。
- **按需创建与容量限制**：默认零预热，单实例全局最多 4 个容器、每语言最多 2 个。每次提交独占容器，结束后销毁并释放工作目录，清理失败保留容量重试。
- **有界内存使用**：请求体、源码和输出读取设有上限；启动脚本采用 `-Xms32m -Xmx192m -XX:+UseSerialGC` 等参数，限制线程池、数据库连接池和执行容量。
- **独立演示工作台**：提供三道题、四语言模板、语法高亮、简单补全、测试用例编辑、当前用例运行和全部用例提交。
- **草稿与提交记录**：打开或刷新页面默认展示正确模板，通过“恢复草稿”载入已保存代码；本浏览器保留最近 20 次评测，可查看当时提交的源码、用例和资源限制，查看记录不会覆盖当前编辑器。
- **可观测性**：支持 `X-Trace-Id`、Nacos 注册、MySQL 审计、Swagger / OpenAPI、Actuator 健康检查与 Prometheus 指标。

容器配额是资源上限，响应中的 `memoryUsedMb` 是用户程序峰值 RSS；它们都不是整个平台的总内存。MySQL 当前两张表采用 MEMORY 引擎，数据库重启后数据清空，长期审计持久化需另行配置。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.1.5、Spring MVC、Bean Validation |
| 数据访问 | MySQL、MyBatis-Plus 3.5.4、HikariCP |
| 服务注册与配置 | Spring Cloud Alibaba Nacos |
| 代码执行 | Docker、docker-java、Ubuntu 22.04 语言镜像 |
| 前端 | HTML / CSS / JavaScript、Node.js 18+ 内置 HTTP 服务，无第三方运行依赖 |
| 接口与验证 | Springdoc OpenAPI、JUnit 5、Mockito、JaCoCo、真实 HTTP / Docker 冒烟测试 |

## 目录结构

```text
sandbox-service/
├── backend/
│   ├── src/main/java/com/huixue/sandbox/
│   │   ├── api/                  # REST 控制器与请求 / 响应 DTO
│   │   ├── common/               # 状态、异常、链路追踪和有界文件读取
│   │   ├── config/               # Docker、Web、请求容量与用户头检查
│   │   ├── domain/               # 执行编排、领域模型及策略接口
│   │   ├── infrastructure/       # Docker、容器池、Mapper 与审计实体
│   │   ├── job/                  # 监控与清理任务
│   │   └── strategy/             # c / cpp / java / python 编译与执行策略
│   ├── src/main/resources/
│   │   ├── application.yml       # 公共配置，默认 profile 为 dev
│   │   ├── application-dev.yml   # 本机 Nacos、前端直连配置
│   │   └── schema.sql            # MySQL 表初始化
│   ├── src/test/                 # 单元与 MVC 测试
│   ├── docker-env/               # c / cpp / java / python 隔离镜像
│   ├── Dockerfile                # 后端服务镜像
│   └── pom.xml                   # 后端 Maven 工程
├── frontend/
│   ├── public/                   # 工作台、编辑器、高亮、补全与 API 调用
│   ├── server.mjs                # 静态文件服务与后端同源代理
│   ├── .env.example              # 环境变量示例，不自动加载
│   ├── Dockerfile
│   └── package.json
├── scripts/
│   ├── start-dev.ps1             # Windows：构建镜像、打包并启动后端
│   ├── start-dev.sh              # Linux：构建镜像、打包并启动后端
│   ├── start-demo.ps1 / .sh      # 兼容入口，同样启动 dev
│   └── smoke-test.py             # 真实 HTTP / Docker 检查
├── docs/
│   ├── startup-windows-linux.md  # 启动与故障排查
│   ├── sandbox-service-api.md    # 接口约束与状态说明
│   └── demo-guide.md             # 工作台演示操作
├── .run/                        # 可共享的 IDEA 运行配置
├── .gitignore
└── pom.xml                      # Maven 聚合入口，构建 backend 模块
```

`target/`、`sandbox_workdirs/`、本机 IDE 设置与日志不属于源码。工作目录由执行任务按需创建。

## 启动前准备

1. 安装 JDK 17+、Maven 3.9+、Node.js 18+ 与 Docker。Windows 的 Docker Desktop 需使用 Linux 容器。
2. 启动 Nacos 和 MySQL，创建数据库 `huixue_sandbox`。后端按 `schema.sql` 初始化表。
3. 设置实际 Nacos 地址和数据库凭据。开发默认地址为本机 Nacos `127.0.0.1:8848`、MySQL `127.0.0.1:3306`。
4. 首次启动构建四语言执行镜像，后续启动可省略镜像构建参数。前端没有第三方依赖，无需执行 `npm install`。

### Windows

终端一，在项目根目录启动后端：

```powershell
cd D:\Code\sandbox-service
$env:NACOS_ADDR='127.0.0.1:8848'
$env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/huixue_sandbox?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
$env:SPRING_DATASOURCE_USERNAME='你的数据库用户'
$env:SPRING_DATASOURCE_PASSWORD='你的数据库密码'
./scripts/start-dev.ps1 -BuildImages
```

终端二，启动前端：

```powershell
cd D:\Code\sandbox-service\frontend
$env:BACKEND_TARGET='http://127.0.0.1:8084'
npm.cmd run dev
```

后续启动后端省略 `-BuildImages`。重新打包前先停止正在使用同一个 JAR 的进程，避免 Windows 文件锁。

### Linux

终端一，在项目根目录启动后端：

```bash
cd /你的项目路径/sandbox-service
export NACOS_ADDR='127.0.0.1:8848'
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/huixue_sandbox?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
export SPRING_DATASOURCE_USERNAME='你的数据库用户'
export SPRING_DATASOURCE_PASSWORD='你的数据库密码'
sh scripts/start-dev.sh --build-images
```

终端二，启动前端：

```bash
cd /你的项目路径/sandbox-service/frontend
BACKEND_TARGET=http://127.0.0.1:8084 npm run dev
```

后续启动后端省略 `--build-images`。前端默认仅监听 `127.0.0.1`，远程访问方式和环境变量见启动指南。

### IDEA 与手动启动

重新加载根 `pom.xml`，选择 `Sandbox Dev (Nacos)`；也可以直接运行 `SandboxServiceApplication`，默认启用 dev。工作目录设置为 `$PROJECT_DIR$/backend`，环境中配置 Nacos 和数据库凭据。前端仍在独立终端运行。

手动打包并启动后端：

```bash
mvn test package
cd backend
java -Xms32m -Xmx192m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Xss512k -jar target/sandbox-service-1.0.0-SNAPSHOT.jar
```

默认 dev 连接真实 MySQL 和 Nacos。`demo` 是 dev 的兼容别名，不提供 H2 离线数据库。

## 页面与接口地址

| 用途 | 地址 |
| --- | --- |
| 浏览器工作台 | [http://127.0.0.1:5173/](http://127.0.0.1:5173/) |
| 工作台代理的接口文档 | [http://127.0.0.1:5173/swagger-ui.html](http://127.0.0.1:5173/swagger-ui.html) |
| 后端 Swagger | [http://127.0.0.1:8084/swagger-ui.html](http://127.0.0.1:8084/swagger-ui.html) |
| 健康检查 | [http://127.0.0.1:8084/actuator/health](http://127.0.0.1:8084/actuator/health) |
| 容量与 JVM 堆状态 | [http://127.0.0.1:8084/api/sandbox/status](http://127.0.0.1:8084/api/sandbox/status) |

浏览器工作台位于 **5173**。后端 **8084** 的根路径 `/` 没有页面，访问它返回 404；请通过工作台或 Swagger 地址访问。

## 执行接口

主接口为 `POST /api/sandbox/execute`，保留 `POST /v1/api/sandbox/execute`。语言值为 `C`、`CPP`、`JAVA`、`PYTHON`，不区分大小写。

```json
{
  "submitId": "example-sum-001",
  "language": "PYTHON",
  "code": "a, b = map(int, input().split())\nprint(a + b)\n",
  "testCases": [{"input": "1 2", "expectedOutput": "3"}],
  "timeLimitMs": 3000,
  "memoryLimitMb": 128
}
```

| 状态 | 含义 |
| --- | --- |
| AC | 已执行用例输出符合预期 |
| WA | 程序正常退出，但输出与预期不一致 |
| CE | 编译失败或编译超时，包含 Python 语法检查失败 |
| TLE | 单个用例运行超时 |
| MLE | 命中内存限制相关退出状态或运行时内存异常 |
| RE | 运行异常，或输出 / 诊断读取超过上限 |

首个失败后停止执行后续用例。完整字段、响应结构、限制及错误码见 [API 接口文档](docs/sandbox-service-api.md)。

## 构建与验证

在项目根目录运行：

```bash
mvn test package
npm run check --prefix frontend
```

后端及语言镜像启动后，可以执行真实冒烟测试：

```bash
python scripts/smoke-test.py http://127.0.0.1:8084
```

该脚本检查四语言正确执行、Python CE / RE 分类、C 六类状态、输出与参数边界及并发后的容器回收，结果写入本地 `outputs/smoke-results.json`。启动脚本会打包后端；代码修改后需重启后端才能加载新策略。

## 文档与仓库

- [Windows / Linux 启动指南](docs/startup-windows-linux.md)
- [API 接口文档](docs/sandbox-service-api.md)
- [工作台演示指南](docs/demo-guide.md)
- [Gitee 仓库](https://gitee.com/ggs_19_bigdata/huixue-sandbox)
- [GitHub 仓库](https://github.com/andows7/sandbox-service)
