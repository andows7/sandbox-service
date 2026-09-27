<div align="center">
  <h1>慧学 一体化教学平台 - 代码执行沙箱服务 (Sandbox Service)</h1>
  <p>一个基于 Spring Boot 3 与 Docker 实现的高效、安全、多语言代码评测沙箱</p>
</div>

## 📖 项目介绍

`sandbox-service` 是“慧学”一体化教学平台中负责**在线代码判题与执行**的核心微服务。
系统通过接收前端学生提交的代码与测试用例，在隔离的 Docker 容器中进行编译与执行，并实时捕获标准输出、内存与时间消耗，给出准确的判题结果（AC, WA, TLE, MLE, CE, RE）。

为了解决突发流量与 Docker 容器创建的耗时问题，系统自主设计并实现了**预热容器池（Container Pool）**，并通过 `MEMORY` 引擎对容器生命周期及执行流水进行高速存取与审计。

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
- MySQL 8.0 及 Nacos 2.x (可通过网关统一提供)

### 方式一：Windows/Mac 本地源码启动 (开发调试)

1. 克隆代码仓库并进入项目根目录：
   ```bash
   cd sandbox-service
   ```
2. 确保本地 `application.yml` 中的 Nacos 和 MySQL 地址已指向可用环境。
3. **重要（针对 Windows 用户）**：请打开 Docker Desktop 设置，在 General 选项卡中勾选 `Expose daemon on tcp://localhost:2375 without TLS` 并重启 Docker。
4. 执行 Maven 编译与打包：
   ```bash
   mvn clean package -DskipTests
   ```
5. 启动应用：
   ```bash
   java -jar target/sandbox-service-1.0.0-SNAPSHOT.jar
   ```

### 方式二：Linux 服务器完整部署指南 (生产环境)

本指南适用于在 Ubuntu/CentOS 等标准 Linux 服务器上完整部署沙箱服务，采用 Docker Compose 及 DooD (Docker-out-of-Docker) 架构。

#### 1. 环境准备与依赖安装
确保服务器已安装 `Docker` 及 `Docker Compose`（V2 版本）。
```bash
# Ubuntu/Debian 示例
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin

# 启动 Docker 并设置开机自启
sudo systemctl enable --now docker
```

#### 2. 基础镜像预热 (防超时拦截)
为防止首次收到学生代码执行请求时，因临时下载镜像耗时过长导致判题超时 (TLE)，建议在 Linux 宿主机上提前拉取环境基础镜像：
```bash
sudo docker pull openjdk:17-jdk-slim
sudo docker pull gcc:11.4.0
sudo docker pull python:3.9-slim
```

#### 3. 拉取项目与配置修改
```bash
# 下载源码到服务器，或直接上传预编译的 jar 包及 docker-compose.yml
git clone <your-repository-url> sandbox-service
cd sandbox-service

# 根据生产环境实际情况，修改 docker-compose.yml 中的环境变量
# 务必替换：SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR 等
vi docker-compose.yml
```

#### 4. 授权 Docker Socket (关键步骤)
沙箱服务本身运行在容器内，并需要调用宿主机的 Docker Daemon 来创建“兄弟容器”执行不可信代码，因此必须挂载 `/var/run/docker.sock`。
如果容器启动后报 `Permission denied` 错误，需确保宿主机 socket 权限足够：
```bash
sudo chmod 666 /var/run/docker.sock
```

#### 5. 构建与后台运行服务
在项目根目录下，使用 Compose 进行镜像构建与后台部署：
```bash
# 编译并以后台模式启动服务
sudo docker compose up -d --build

# 检查服务运行状态及端口映射 (默认暴露宿主机的 8084 端口)
sudo docker compose ps
```

#### 6. 日志监控与日常维护
```bash
# 实时追踪沙箱服务运行日志，确认成功注册到 Nacos 且无报错
sudo docker compose logs -f sandbox-service

# 重启沙箱服务
sudo docker compose restart sandbox-service

# 停止并移除沙箱容器
sudo docker compose down
```
http://localhost:8084/swagger-ui/

## 📖 文档指南

- [API 接口文档](docs/sandbox-service-api.md)
- [完整阶段开发报告汇总](docs/Development_Reports.md)
