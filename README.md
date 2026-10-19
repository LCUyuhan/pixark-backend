# PixArk Backend

PixArk 云图库的后端服务，提供用户、图片、空间、审核、AI 图片编辑、以图搜图和图库分析等接口。

技术栈：Java 11 + Spring Boot 2.7.6 + MyBatis-Plus 3.5.9 + MySQL 8 + Redis 7 + RabbitMQ 3.11 + 腾讯云 COS + 阿里云百炼。

## 功能

- 用户注册、登录、退出、头像上传、个人中心
- 图片上传（本地文件 / URL 抓取）、编辑、删除、审核
- 公共图库与私有空间，支持空间配额和空间分析
- AI 图片编辑：扩图、去水印、高清放大、上色、风格化、局部重绘等
- 以图搜图：基于 RabbitMQ 的异步任务、结果缓存、失败重试与冷却
- 图库分析：分类、标签、大小、用量等统计
- 管理员：用户管理、图片审核、空间管理、AI 额度调整
- 登录失败限流、AI 编辑频率/额度/并发控制、游客浏览限制

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 / 框架 | Java 11、Spring Boot 2.7.6 |
| 数据访问 | MyBatis-Plus 3.5.9、MySQL 8.0 |
| 缓存 / 会话 | Redis 7、Spring Session、Caffeine |
| 消息队列 | RabbitMQ 3.11 |
| 对象存储 | 腾讯云 COS |
| AI | 阿里云百炼 DashScope（万相 `wanx2.1-imageedit`） |
| 接口文档 | Knife4j（本地默认开启，生产默认关闭） |
| 部署 | Docker Compose、Nginx |

## 项目结构

```text
src/main/java/com/yeyuchunlan/pixarkbackend/
├── api/            # 第三方接口封装（COS、百炼、以图搜图）
├── common/         # 通用响应、常量、工具
├── config/         # Spring、Redis、RabbitMQ、CORS 等配置
├── controller/     # REST 接口
├── exception/      # 业务异常与全局异常处理
├── manager/        # 上传模板、缓存、文件管理
├── mapper/         # MyBatis-Plus Mapper
├── model/          # 实体、DTO、VO、枚举
├── mq/             # 以图搜图消息生产与消费
├── service/        # 业务接口与实现
└── utils/          # 通用工具
```

## 本地运行

最短流程：

1. 启动 MySQL、Redis、RabbitMQ
2. 建库并执行 `sql/create_table.sql`
3. 复制 `application-local.example.yml` 为 `application-local.yml`，填 MySQL、RabbitMQ、COS、百炼四组值
4. 执行 `mvn spring-boot:run -Dspring-boot.run.profiles=local`

### 1. 环境要求

- JDK 11
- Maven 3.6+
- MySQL 8.0
- Redis 7（本机默认 `127.0.0.1:6379`）
- RabbitMQ 3.11（以图搜图功能需要）
- 腾讯云 COS 桶与密钥（当前代码启动时会初始化 COS 客户端，必须配置）
- 阿里云百炼 API Key 与业务空间 ID（当前代码启动时会初始化百炼客户端，必须配置）

### 2. 初始化数据库

`sql/create_table.sql` 不会创建数据库，需要先建库再执行：

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS pixark DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p pixark < sql/create_table.sql
```

脚本中的 `ALTER TABLE ... ADD COLUMN` 不是幂等的，**空库只执行一次**。已有库升级时请单独写迁移脚本。

### 3. 配置 `application-local.yml`

仓库里提供了一个**只含占位符**的模板 `application-local.example.yml`。复制成 `application-local.yml` 后填写自己的值：

```bash
cp application-local.example.yml application-local.yml
```

`application-local.yml` 已在 `.gitignore` 中，不会被提交；模板文件本身可以提交。

必须填写的配置：

| 配置 | 说明 |
| --- | --- |
| `spring.datasource.password` | 本机 MySQL 密码 |
| `spring.rabbitmq.username/password` | 本机 RabbitMQ 账号；默认 `guest/guest`，用项目容器时改成 `.env` 里的账号 |
| `cos.client.host/secretId/secretKey/region/bucket` | 腾讯云 COS 的域名、密钥、地域和桶名 |
| `ai-image-edit.api-key/workspace-id` | 阿里云百炼 API Key 和业务空间 ID |

注意：

- `application-local.yml` 必须放在 `pixark-backend/` 目录下，且运行时的 working directory 也是这个目录，`spring.config.import: optional:file:./application-local.yml` 才能找到它。
- 文件里的 `on-profile: local` 表示只有激活 `local` profile 时才生效。
- **当前代码在启动时会构造 COS 和百炼客户端，所以这两组配置不能留空**；即使暂时不用图片上传或 AI 编辑，也要填真实值。若希望“没有云服务密钥也能启动”，需要改代码做条件装配，属于另一项改动。
- 不要把真实密钥写进 `application.yml`、`application-local.example.yml` 或任何会被提交的文件；生产环境使用环境变量，见 `deploy/.env.example`。

### 4. 启动

在 `pixark-backend/` 目录执行：

```bash
# 方式一：命令行指定 profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 方式二：先设置环境变量
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

如果在 IDEA 中运行，把 Run Configuration 的 Active profiles 设为 `local`，并确认 working directory 是 `pixark-backend`。

启动后：

- 服务端口：`8123`
- 接口前缀：`/api`
- 健康检查：`GET http://localhost:8123/api/health`
- 本地接口文档：`http://localhost:8123/api/doc.html`（Knife4j 开启时）

### 5. 与前端联调

前端默认运行在 `http://localhost:5173`，Vite 会把 `/api` 代理到 `http://localhost:8123`。两个项目分别启动即可，不需要额外配置跨域。

## 配置说明

| 配置 | 作用 |
| --- | --- |
| `spring.datasource.*` | MySQL 连接 |
| `spring.redis.*` | Redis 连接与会话存储 |
| `spring.rabbitmq.*` | RabbitMQ 连接与消费者参数 |
| `cos.client.*` | COS 域名、地域、桶名和密钥 |
| `ai-image-edit.*` | 百炼模型、功能白名单、频率/并发/额度 |
| `login-security.*` | 登录失败限流 |
| `guest-picture.view-limit` | 游客每次查询返回的图片数量 |
| `knife4j.enable` | 是否开放接口文档 |

## 部署

部署相关文件在 `deploy/`：

- `deploy/docker-compose.yml`：MySQL、Redis、RabbitMQ、后端四个容器
- `deploy/.env.example`：环境变量模板，复制为 `.env` 后填真实值
- `deploy/nginx/pixark.conf`：Nginx 站点配置模板

生产环境使用 `prod` profile，配置见 `src/main/resources/application-prod.yml`，密钥全部从环境变量读取。`.env` 和 `application-local.yml` 都不要提交到仓库。

## 已知限制

- AI 图片编辑的百炼账号全局并发上限为 2，超限会直接拒绝，不会排队。
- 以图搜图消费者当前为单线程，任务提交后需要前端轮询结果。
- `sql/create_table.sql` 只适合空库首次初始化，不是通用迁移工具。
- 项目目前没有自动化测试，`mvn test` 只能验证构建，不能替代接口回归。

## License

本项目基于 MIT License 开源，详见 [LICENSE](LICENSE)。
