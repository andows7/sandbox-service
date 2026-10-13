<div align="center">
  <h1>慧学 一体化教学平台 - 代码执行沙箱服务 (Sandbox Service)</h1>
  <p>一个基于 Spring Boot 3 与 Docker 实现的高效、安全、多语言代码评测沙箱</p>
</div>

## 📖 项目介绍

`sandbox-service` 是“慧学”一体化教学平台中负责 **在线代码判题与执行** 的核心微服务。
系统通过接收前端学生提交的代码与测试用例，在隔离的 Docker 容器中进行编译与执行，并实时捕获标准输出、内存与时间消耗，给出准确的判题结果（AC, WA, TLE, MLE, CE, RE）。

为了解决突发流量下 Docker 容器创建的耗时问题，系统自主设计并实现了 **预热容器池（Container Pool）**，并通过 `MEMORY` 引擎对容器生命周期及执行流水进行高速存取与审计。

## ✨ 核心特性

- **🌍 多语言支持**：已完全支持 `Java`、`C++`、`Python` 三种主流编程语言的编译与执行。
- **🛡️ 严格的安全隔离 (Sandbox)**：
  - 代码运行在无权限用户下（非 root）。
  - 限制最大可用 CPU（`--cpus=1`）、内存（`--memory`）、网络（`--network none`）及进程数（`--pids-limit`）。
  - 禁止特权升级（`--security-opt no-new-privileges`）。
- **🚀 极速判题响应**：自研 `ContainerPool`，支持预热 (Pre-warm) 与异步补充 (Async Replenish)。将原本数百毫秒的容器创建开销降低至 `O(1)` 的对象获取时间。
- **📊 全链路可观测**：支持 API 链路追踪（`X-Trace-Id`），内嵌 Actuator 暴露 `/actuator/prometheus` 给监控中心进行打点，且内置定时任务自动清理僵尸容器与过期审计记录。

## 🛠️ 技术栈

- **框架**：Spring Boot 3.x, Spring MVC
- **数据库及 ORM**：MySQL 8.0 (使用 MEMORY 内存引擎), MyBatis-Plus 3.5+
- **容器与底层隔离**：Docker, docker-java API
- **服务治理**：Spring Cloud Alibaba Nacos (用于注册中心与配置中心)
- **文档与测试**：Swagger 3 (OpenAPI), JUnit 5, Mockito, JaCoCo

## 📁 目录结构

```text
sandbox-service/
├── docker-env/                       # 多语言沙箱运行环境 Dockerfile 构建目录
│   ├── cpp/Dockerfile                # C++ 环境 (Ubuntu 22.04 + GCC 11)
│   ├── java/Dockerfile               # Java 环境 (Ubuntu 22.04 + OpenJDK 17)
│   └── python/Dockerfile             # Python 环境 (Ubuntu 22.04 + Python 3.9)
├── docs/                             # 项目设计与接口规范文档
│   ├── Development_Reports.md        # 各阶段开发与重构报告汇总
│   ├── sandbox-service-api.md        # API 接口规范与在线调试说明
│   └── 慧学教学平台_代码执行沙箱服务_接口规范文档_v1.2.0.docx # Word 标准化规范文档
├── sandbox_workdirs/                 # 宿主机与容器卷挂载工作目录 (按需生成与定时清理)
├── src/
│   ├── main/
│   │   ├── java/com/huixue/sandbox/
│   │   │   ├── SandboxServiceApplication.java # Spring Boot 应用启动主类
│   │   │   ├── api/                  # 接口控制层与数据传输对象 (DTO)
│   │   │   │   ├── controller/       # SandboxController (判题接口入口)
│   │   │   │   └── dto/              # 请求、响应包装实体 (ApiResponse 等)
│   │   │   ├── common/               # 全局通用组件
│   │   │   │   ├── enums/            # 状态字典 (JudgeStatus: AC, WA, TLE, MLE, RE, CE 等)
│   │   │   │   ├── exception/        # 业务异常及全局异常捕获 (GlobalExceptionHandler)
│   │   │   │   ├── interceptor/      # TraceId 链路追踪拦截器
│   │   │   │   └── util/             # 路径脱敏工具类与 TraceId 工具
│   │   │   ├── config/               # 自动配置类 (Docker、MyBatis-Plus、WebMvc)
│   │   │   ├── domain/               # 核心业务领域层
│   │   │   │   ├── model/            # 核心业务模型与领域实体
│   │   │   │   ├── service/          # SandboxExecutionService 判题核心编排服务
│   │   │   │   └── strategy/         # 编译与执行策略接口抽象
│   │   │   ├── infrastructure/       # 基础设施层
│   │   │   │   ├── docker/           # DockerContainerManager 容器运行时交互与执行
│   │   │   │   ├── persistence/      # 数据访问层与 MyBatis-Plus Mapper (审计流水)
│   │   │   │   └── pool/             # 自研 ContainerPool 预热容器池与池化管理
│   │   │   ├── job/                  # 定时任务 (僵尸容器与过期审计记录自动清理)
│   │   │   └── strategy/             # 多语言策略具体实现
│   │   │       ├── cpp/              # C++ 编译与隔离执行策略
│   │   │       ├── java/             # Java 编译与隔离执行策略
│   │   │       └── python/           # Python 脚本校验与隔离执行策略
│   │   └── resources/
│   │       ├── application.yml       # 核心服务配置 (Docker连接、容器池参数、Nacos、数据源)
│   │       └── schema.sql            # 数据库初始化脚本 (MEMORY 引擎审计表结构)
│   └── test/java/com/huixue/sandbox/ # 单元测试套件
│       ├── JavaStrategyTest.java     # 6 种判题状态 (AC/WA/TLE/MLE/RE/CE) 策略级覆盖测试
│       ├── api/                      # 接口与参数校验测试 (MockMvc)
│       ├── common/                   # 路径脱敏等基础工具测试
│       ├── domain/service/           # 判题核心调度服务集成单元测试
│       └── infrastructure/           # Docker 客户端封装与容器池并发吞吐测试
├── Dockerfile                        # 沙箱主服务镜像构建文件
├── docker-compose.yml                # 容器化编排部署文件
└── pom.xml                           # Maven 依赖与构建配置
```

## 🚀 部署步骤

### 环境要求
- JDK 17
- Maven 3.9+
- Docker Engine (必须)
- MySQL 8.0 与 Nacos 2.x (可通过网关统一提供)

### 方式一：Windows/Mac 本地源码启动 (开发调试)

1. 克隆代码仓库并进入项目根目录：
   ```bash
   cd sandbox-service
   ```
2. 确保本地 `application.yml` 中的 Nacos 和 MySQL 地址已指向可用环境。
3. 执行 Maven 编译与打包：
   ```bash
   mvn clean package -DskipTests
   ```
4. 启动应用：
   ```bash
   java -jar target/sandbox-service-1.0.0-SNAPSHOT.jar
   ```

### 方式二：部署到 Ubuntu 2C2G 服务器 (轻量级生产环境)

由于 2C2G (2核2G) 的服务器资源非常紧张，必须对服务进行内存压榨和连接数限制，否则容易触发 Linux OOM (Out Of Memory) 导致服务被强杀。

**核心建议**：
- 请确保 MySQL 和 Nacos 部署在**另外的服务器**上，不要将它们与 Sandbox 服务挤在同一台 2C2G 服务器内。
- Docker 的 Sandbox 预热容器池默认占用内存较多，在资源极度受限时，可以通过配置文件缩减预热容器数量。

#### 1. 环境准备 (安装 JDK 与 Docker)
确保 2C2G 服务器已安装 `JDK 17` 和 `Docker`：
```bash
# 更新 apt 缓存
sudo apt-get update

# 安装 OpenJDK 17
sudo apt-get install -y openjdk-17-jdk

# 安装 Docker Engine
sudo apt-get install -y docker.io docker-compose-plugin
sudo systemctl enable --now docker

# 将当前用户加入 docker 组 (避免 sudo)
sudo usermod -aG docker $USER
newgrp docker
```

#### 2. 提前拉取基础镜像
沙箱执行代码依赖基础环境，建议提前通过清华镜像源或者默认源拉取，避免首次判题超时：
```bash
sudo docker pull ubuntu:22.04
```

#### 3. 编译打包与上传
在**本地开发机**上，打包出 jar 文件：
```bash
mvn clean package -DskipTests
```
将 `target/sandbox-service-1.0.0-SNAPSHOT.jar` 以及项目根目录的 `docker-env` 文件夹，一起上传到 Ubuntu 2C2G 服务器上的同一个目录下（例如 `/opt/sandbox/`）。

#### 4. 限制内存启动服务
登录到 Ubuntu 2C2G 服务器，进入上传目录，使用 JVM 内存参数严格限制 Java 进程内存（最大 512MB）：
```bash
cd /opt/sandbox/

# 覆盖配置项：指定 Nacos/MySQL 地址，并把容器池的核心数量从 10 降到 3，最大数量降到 10，节省 Docker 内存开销
java -Xms256m -Xmx512m -jar sandbox-service-1.0.0-SNAPSHOT.jar \
  --spring.cloud.nacos.discovery.server-addr=192.168.1.100:8848 \
  --spring.cloud.nacos.config.server-addr=192.168.1.100:8848 \
  --spring.datasource.url="jdbc:mysql://192.168.1.100:3306/huixue_sandbox?useUnicode=true&characterEncoding=utf-8" \
  --sandbox.docker.pool.core-size=3 \
  --sandbox.docker.pool.max-size=10
```
*(注意：请将 `192.168.1.100` 替换为你真实的 Nacos 和 MySQL 服务器地址)*

启动后，访问以下链接即可查看 API 接口调试页面：
`http://<你的服务器IP>:8084/swagger-ui/`

## 📖 文档指南

- [API 接口文档](docs/sandbox-service-api.md)
- [完整阶段开发报告汇总](docs/Development_Reports.md)
