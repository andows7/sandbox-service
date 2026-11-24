# Windows / Linux 平台联调启动

## 启动前检查

当前所有启动入口统一为 dev 联调。需启动 Docker（Linux 容器）、Nacos、MySQL，并创建 MySQL 数据库 `huixue_sandbox`。默认端口：前端 5173、后端 8084、Nacos 8848（还需开放客户端使用的 9848）、MySQL 3306。

项目要求 JDK 17+、Maven 3.9+、Node.js 18+。前端没有第三方运行依赖，无需 npm install。

## Windows

终端一，在根目录：

```powershell
cd D:\Code\sandbox-service
$env:NACOS_ADDR='127.0.0.1:8848'
$env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/huixue_sandbox?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
$env:SPRING_DATASOURCE_USERNAME='你的数据库用户'
$env:SPRING_DATASOURCE_PASSWORD='你的数据库密码'
./scripts/start-dev.ps1 -BuildImages
```

终端二：

```powershell
cd D:\Code\sandbox-service\frontend
$env:BACKEND_TARGET='http://127.0.0.1:8084'
npm.cmd run dev
```

后续运行可省略 `-BuildImages`。Windows 重新打包前先停止旧 JAR，避免文件锁。前后端分别在各自终端按 Ctrl+C 停止。

## Linux

终端一：

```bash
cd /你的项目路径/sandbox-service
export NACOS_ADDR='127.0.0.1:8848'
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/huixue_sandbox?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
export SPRING_DATASOURCE_USERNAME='你的数据库用户'
export SPRING_DATASOURCE_PASSWORD='你的数据库密码'
sh scripts/start-dev.sh --build-images
```

终端二：

```bash
cd /你的项目路径/sandbox-service/frontend
BACKEND_TARGET=http://127.0.0.1:8084 npm run dev
```

后续可省略 `--build-images`。前端默认只绑定 127.0.0.1；远程服务器可用 SSH 转发 `ssh -L 5173:127.0.0.1:5173 用户@服务器`。如需监听其他网卡，设置前端 HOST，按平台访问控制要求开放。

## 浏览器与后端

打开 http://localhost:5173，直接运行或提交代码，无需登录或 Token。请求通过同源代理转发到沙箱后端 8084，Swagger 入口 http://localhost:5173/swagger-ui.html 同样直连后端。Nacos 负责后端注册，不负责签发 Token。

`BACKEND_TARGET` 默认为 `http://127.0.0.1:8084`，应设置在前端服务进程中。`.env.example` 是变量示例，不自动加载。原 API_TARGET 网关地址不再使用。

502：检查沙箱后端是否启动及 BACKEND_TARGET。若仍返回 401，停止旧后端并使用最新 dev 配置重新运行；当前 dev 已关闭网关用户头检查。生产 prod 保留原检查，不属于当前直连演示流程。

## IDEA

重新加载根 pom.xml，后端模块位于 backend。运行 `Sandbox Dev (Nacos)`，或直接运行主类（默认 dev）。工作目录设为 `$PROJECT_DIR$/backend`，环境中配置实际 NACOS_ADDR 和数据库凭据。前端在独立终端运行 npm。

旧 `start-demo` 脚本、旧 Demo IDEA 配置现同样启动 dev；显式 `demo` profile 也激活 dev，没有 H2 离线数据库；dev 允许前端直接调用，仍正常注册 Nacos。

## API 与测试

主接口 `POST /api/sandbox/execute`，兼容 `/v1/api/sandbox/execute`，服务名 `sandbox-service`。请求包含 submitId、language、完整程序、testCases、timeLimitMs、memoryLimitMb，响应仍为 code/msg/data。

```powershell
mvn test package
npm.cmd run check --prefix frontend
python scripts/smoke-test.py http://127.0.0.1:8084
```
