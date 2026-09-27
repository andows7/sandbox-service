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
├── docs/                             # 包含 API 文档与各阶段开发报告汇总
├── src/main/java/com/huixue/sandbox/
│   ├── api/                          # Controller 层与 DTO
│   ├── common/                       # 枚举、异常处理、拦截器、工具类
│   ├── config/                       # Docker、MyBatis、MVC 配置
│   ├── domain/                       # 核心业务领域 (Service, Model, 策略模式抽象)
│   ├── infrastructure/               # 基础设施层 (Docker客户端封装, 持久化Mapper, 容器池)
│   ├── job/                          # 定时调度任务 (僵尸容器及日志清理)
│   └── strategy/                     # 各种语言的编译及执行策略实现 (Java/Cpp/Python)
├── Dockerfile                        # 项目应用 Docker 镜像构建文件
├── docker-compose.yml                # 容器编排文件
└── pom.xml                           # Maven 依赖与插件管理
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
