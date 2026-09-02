# 从零实现一个 AI API 自动化测试平台：Java 17 + Vue 3 + Docker Compose + 硅基流动

> 项目源码：https://github.com/mJIAYI1/AutoTest-AI
>
> 技术栈：Java 17、Spring Boot 3.5、Vue 3、TypeScript、MySQL、Docker Compose、硅基流动

做接口测试时，我们经常会在多个工具之间来回切换：用 Swagger 看接口、用 Postman 调请求、在 Excel 里维护用例、再手工整理测试报告。接口数量一多，环境变量、前后置依赖和历史结果就很难统一管理。

因此，我从零实现了 **AutoTest AI**：一个覆盖“接口导入 → 用例设计 → 自动执行 → 工作流编排 → 测试报告 → AI 辅助”的 Web API 自动化测试平台。

![CI](https://github.com/mJIAYI1/AutoTest-AI/actions/workflows/ci.yml/badge.svg)

项目目前已经开源，后端固定使用 **JDK 17**，前后端、MySQL 和独立 Demo API 均可通过 Docker Compose 一键启动。

## 一、最终效果

平台登录页面：

![AutoTest AI 登录页面](https://raw.githubusercontent.com/mJIAYI1/AutoTest-AI/main/docs/images/login.png)

为了让任何人都能稳定复现测试流程，项目还内置了一个独立 Demo API：

![AutoTest AI Demo API Swagger](https://raw.githubusercontent.com/mJIAYI1/AutoTest-AI/main/docs/images/demo-api-swagger.png)

项目已经实现的主要能力包括：

- 用户注册、登录和 JWT 鉴权；
- 项目与多测试环境管理；
- 从 URL 或文件导入 OpenAPI 3.x / Swagger 2.0；
- 测试用例的新建、编辑、删除和乐观锁控制；
- 状态码、JSONPath、类型、响应时间和正文内容断言；
- 异步执行单条测试用例；
- 有顺序的测试套件和接口依赖；
- JSONPath 响应变量提取及 `{{variable}}` 模板传递；
- 测试历史、汇总报告、失败详情和 Dashboard；
- 使用硅基流动生成候选用例；
- 使用硅基流动对失败结果进行辅助诊断；
- Docker Compose 一键启动和 GitHub Actions 自动验证。

## 二、系统架构

整套系统由四个容器组成：

```text
浏览器
  │
  ▼
Vue 3 + Nginx  ───────────────┐
  │ /api、/actuator           │
  ▼                           │
Spring Boot 平台后端           │
  ├── MySQL 8.4               │
  ├── Java HTTP 执行器         │
  ├── 断言与变量提取引擎        │
  ├── OpenAPI 解析器            │
  ├── 测试报告与 Dashboard      │
  ├── 硅基流动（可选）           │
  └── Demo API ────────────────┘
```

技术栈如下：

| 层次 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Axios、Element Plus |
| 后端 | Java 17、Spring Boot 3.5、Spring Security、MyBatis、Flyway |
| API 解析 | Swagger Parser，支持 OpenAPI 3.x 和 Swagger 2.0 |
| 测试执行 | Spring RestClient、独立线程池、JSONPath、自研断言引擎 |
| 数据库 | MySQL 8.4 |
| AI | Spring AI、硅基流动 OpenAI 兼容接口 |
| 工程化 | Maven Wrapper、npm、Docker Compose、GitHub Actions |
| 测试 | JUnit 5、MockMvc、Testcontainers |

项目目录：

```text
AutoTest-AI/
├── .github/workflows/     # CI 与远程回归工作流
├── backend/               # 自动化测试平台后端
├── demo-api/              # 独立的被测 Demo API
├── docs/                  # 截图和发布文章
├── frontend/              # Vue 3 管理端
├── docker-compose.yml     # 四服务编排
├── .env.example           # 环境变量模板
└── README.md
```

## 三、为什么要单独设计 Demo API

自动化测试项目最麻烦的并不是“能不能发送 HTTP 请求”，而是怎样稳定展示正常、边界和异常场景。

如果依赖第三方公开接口，对方的数据、限流策略或返回格式随时可能变化。因此我为项目实现了一个独立的 Spring Boot Demo API，覆盖注册、登录、用户、商品和订单接口，并公开 `/v3/api-docs`。

Demo API 启动后自带：

```text
用户名：demo
密码：demo123456
默认商品：id = 1
```

同时，它故意保留了四个可复现问题：

1. 查询不存在的用户返回 `500`，而不是 `404`；
2. 创建商品时接受负数价格；
3. 创建订单时接受数量 `0`；
4. 无效 Bearer Token 返回 `500`，而不是 `401`。

这些缺陷不是平台本身的 Bug，而是专门留给自动化测试平台发现的被测问题。Demo 数据保存在内存中，重启容器就会恢复初始状态。

## 四、从 OpenAPI 到测试用例

平台支持两种导入方式：

- 输入 OpenAPI / Swagger 文档 URL；
- 上传 `.json`、`.yaml` 或 `.yml` 文件。

解析完成后，每个 Operation 会保存以下信息：

```text
HTTP Method
Path
OperationId
Summary / Description
Tags
Parameters
Request Body
Responses
Security
```

重复导入相同的 `method + path` 时更新原记录，避免接口资产越导越多。

在 Docker 环境中，可以直接导入内置 Demo API：

```text
http://demo-api:8081/v3/api-docs
```

随后即可为某个 API 创建正常、边界、异常、缺参、类型错误和认证类用例。

## 五、确定性的 Java 测试执行器

我把“AI 辅助”和“测试判定”做了明确隔离。

真实 HTTP 请求由 Java 执行器发送，断言由确定性代码计算，最终状态只能由执行器产生：

```text
PENDING → RUNNING → PASS / FAIL / ERROR
```

支持的断言类型包括：

- HTTP 状态码；
- JSONPath 是否存在；
- JSONPath 值是否相等；
- JSON 值类型；
- 响应时间上限；
- 响应正文包含指定内容。

执行结果会记录实际请求、脱敏后的请求头、请求体、响应状态、响应头、响应体、耗时、断言结果、提取变量和错误信息。

这里有一个重要区别：

- 接口正常返回，但断言不满足，记为 `FAIL`；
- 网络超时、变量缺失、响应体超限等无法完成测试的情况，记为 `ERROR`。

这样报告不会把“业务不符合预期”和“测试本身无法执行”混在一起。

## 六、测试套件与接口依赖

真实业务接口经常存在前后依赖，例如：

```text
登录 → 提取 token → 查询用户
创建商品 → 提取 productId → 创建订单
```

AutoTest AI 支持将多个测试用例按顺序加入套件。前序接口可以通过 JSONPath 提取变量：

```json
{
  "name": "token",
  "expression": "$.token"
}
```

后续请求可在 URL、请求头、查询参数和 JSON 请求体中引用：

```text
Authorization: Bearer {{token}}
/products/{{productId}}
```

执行时，当前 Test Run 的运行时变量优先级高于环境变量，因此同一个套件中的新值可以自然覆盖旧值。

套件还支持：

- 步骤排序；
- 单步骤启用或禁用；
- 首次失败后停止；
- 失败后继续运行并汇总结果；
- 保存每一步的完整请求、响应和断言结果。

## 七、AI 在平台中做什么

平台的模型供应商选择了硅基流动，通过 OpenAI 兼容接口接入。

AI 当前负责两类任务：

### 1. 生成候选测试用例

后端会把接口定义、Schema、安全要求和已有用例摘要发送给模型，让模型生成正常、边界、异常、缺参、类型错误和认证类候选用例。

模型返回结果不会直接入库。后端会先做结构校验、Bean Validation 和执行配置校验，前端展示候选结果，只有用户确认后才保存。

### 2. 解释失败结果

对于 `FAIL` 或 `ERROR`，后端从数据库读取已经保存的请求、期望断言、实际响应和错误信息，再让模型输出：

- 问题摘要；
- 严重程度；
- 可能原因；
- 建议检查位置；
- 修复建议。

AI 诊断只是辅助排查建议，不会修改 Java 执行器生成的测试状态。

如果不配置 API Key，项目的导入、编辑、执行、断言、工作流和报告功能仍然可以完整运行。

## 八、安全与隔离设计

自动化测试平台可以向外发送请求，因此安全控制不能缺失。

项目当前实现了以下约束：

- JWT 无状态鉴权；
- 用户密码只保存 BCrypt 哈希；
- 项目、环境、API、用例、套件和报告按用户隔离；
- 跨用户访问统一返回资源不存在，避免泄露资源是否存在；
- OpenAPI URL 导入采用目标主机白名单；
- 测试执行使用独立的目标主机白名单；
- 只允许 HTTP / HTTPS，拒绝 URL 中的账号信息和片段；
- 不自动跟随重定向；
- 限制连接超时、读取超时和最大响应体；
- Authorization、Cookie、API Key、Token、Secret 等请求头入库前脱敏；
- 调用 AI 前再次对请求体、响应体和错误信息脱敏、截断；
- `.env`、数据库密码、JWT 密钥和硅基流动 API Key 不进入 Git。

## 九、Docker Compose 一键运行

首先克隆项目：

```bash
git clone https://github.com/mJIAYI1/AutoTest-AI.git
cd AutoTest-AI
```

复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

Linux / macOS 可以使用：

```bash
cp .env.example .env
```

至少修改 `.env` 中的数据库密码和 JWT 密钥：

```dotenv
MYSQL_PASSWORD=请替换为数据库密码
MYSQL_ROOT_PASSWORD=请替换为Root密码
JWT_SECRET=请替换为至少32字节的随机字符串
```

如需启用 AI，再填写：

```dotenv
SILICONFLOW_API_KEY=你的硅基流动APIKey
SILICONFLOW_MODEL=硅基流动模型广场中的完整模型名称
```

启动全部服务：

```bash
docker compose up -d --build
docker compose ps
```

默认地址：

| 服务 | 地址 |
| --- | --- |
| 前端 | http://127.0.0.1:5173 |
| 后端 | http://127.0.0.1:18080 |
| 后端健康检查 | http://127.0.0.1:18080/actuator/health |
| Demo API Swagger | http://127.0.0.1:8081/swagger-ui.html |
| Demo API OpenAPI | http://127.0.0.1:8081/v3/api-docs |
| MySQL | 127.0.0.1:3307 |

停止服务但保留数据库数据：

```bash
docker compose down
```

## 十、五分钟跑通完整流程

1. 打开 `http://127.0.0.1:5173`，注册并登录；
2. 创建项目，Base URL 填写 `http://demo-api:8081`；
3. 创建测试环境，Base URL 同样填写 `http://demo-api:8081`；
4. 从 `http://demo-api:8081/v3/api-docs` 导入接口；
5. 为 `/login`、`/products`、`/orders` 等接口创建用例；
6. 配置状态码、JSONPath 或响应时间断言；
7. 运行单条用例并查看请求、响应和断言；
8. 创建测试套件，配置变量提取和接口依赖；
9. 运行套件并在测试报告中查看汇总结果；
10. 如果已配置模型，可生成候选用例或为失败步骤生成 AI 诊断。

需要注意：平台后端运行在 Docker 容器里时，`localhost` 指向后端容器自身。因此访问 Compose 中的 Demo API 应使用服务名 `demo-api`，访问 Windows 主机上的接口则使用 `host.docker.internal`。

## 十一、自动化测试与 GitHub Actions

平台发布前进行了完整验证：

```text
平台后端：54 / 54 tests passed
Demo API：4 / 4 tests passed
Vue TypeScript 检查：passed
Vue 生产构建：passed
Docker Compose 四服务冒烟测试：passed
```

GitHub Actions 在每次推送和 Pull Request 时执行四个任务：

1. Java 17 平台后端测试；
2. Java 17 Demo API 测试；
3. Vue 类型检查和生产构建；
4. 完整 Compose 构建、健康等待及公开入口冒烟测试。

仓库还提供了手动触发的远程回归工作流。配置平台 Token、项目 ID、套件 ID 和环境 ID 后，GitHub Actions 可以启动 AutoTest AI 中的套件并轮询结果，最终状态不是 `PASS` 时流水线直接失败。

这让测试平台不只可以在页面里手工使用，也能继续接入实际 CI/CD 流程。

## 十二、项目中的几个工程实践

### 1. HTTP 等待期间不持有数据库事务

测试运行先使用短事务写入 `PENDING / RUNNING`，真实 HTTP 请求在事务外执行，完成后再用短事务持久化结果，避免慢接口长期占用数据库连接。

### 2. 用例更新采用乐观锁

测试用例和测试套件均带有版本号。用户编辑旧版本时返回 `409`，避免多人或多个页面同时修改时静默覆盖新数据。

### 3. 测试历史直接生成报告

报告复用已经持久化的执行数据，不会为了查看报告而再次运行接口。汇总包含通过数、失败数、错误数、通过率和平均响应时间，并可按 API 展开详细结果。

### 4. 数据库结构只通过 Flyway 演进

所有表结构变化都放在版本化迁移脚本中，应用启动时自动校验和升级，不依赖人工执行散落的 SQL。

### 5. AI 不成为系统的单点依赖

AI 是增强能力，而不是测试平台的核心运行条件。即使模型不可用，确定性的测试闭环仍然正常工作。

## 十三、后续计划

当前版本已经具备完整 MVP 闭环，后续准备继续增加：

- 定时执行任务；
- 邮件和 Webhook 通知；
- Redis 缓存和多执行节点；
- 参数化测试数据集；
- 测试报告导出；
- 团队、角色、权限和审计日志。

## 总结

AutoTest AI 的重点不是简单封装一次 HTTP 请求，而是把接口资产、环境、测试用例、执行、依赖、结果、报告和 AI 辅助串成一条可复现的测试流水线。

如果你正在学习 Java 全栈、自动化测试、OpenAPI、Docker 或 AI 工程化，希望这个项目能提供一些参考。

项目地址：

**https://github.com/mJIAYI1/AutoTest-AI**

如果项目对你有帮助，欢迎 Star、Fork 或提出 Issue。

---

推荐 CSDN 标签：`Java`、`Spring Boot`、`Vue.js`、`Docker`、`自动化测试`、`人工智能`
