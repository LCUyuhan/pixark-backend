# 单元测试用例设计文档

> 项目：yu-picture-backend（智能协同云图库后端）
> 技术栈：Spring Boot 2.7.6 / Java 11 / MyBatis Plus / JUnit 5 / Mockito / JaCoCo

## 1. 测试目标与范围

- **目标**：验证各功能模块的核心业务逻辑、边界条件与异常处理，保证行为正确、可独立运行、可回归。
- **策略**：采用 Mockito 纯单元测试，模拟 Mapper、Service、第三方组件（腾讯云 COS）与 HTTP 请求，不依赖真实数据库和外部服务。
- **覆盖率目标**：核心业务代码行覆盖率 ≥ 80%（由 JaCoCo 的 `check` 规则在 `mvn verify` 阶段强制校验，排除了纯配置类、应用启动类与注解）。

## 2. 测试框架与命名规范

- 框架：JUnit 5（`org.junit.jupiter`）+ Mockito（`org.mockito`）。
- 命名规范：
  - 测试类：`{被测类名}Test`，与被测类同包（`src/test/java` 下对应包结构）。
  - 测试方法：`{被测方法名}_{场景}_{预期结果}`，例如 `userRegister_shortAccount_throws`。
  - Mock 数据在 `@BeforeEach` 中准备，测试后由 JUnit 自动回收，无需显式清理数据库。

## 3. 模块测试设计

### 3.1 异常模块（exception）
| 类 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `ThrowUtils` | 三种 `throwIf` 重载 | 条件为真抛出指定异常/BusinessException；条件为假不抛异常 |
| `BusinessException` | 三个构造器 | 校验 `code` 与 `message` 正确设置 |
| `ErrorCode` | 枚举量 | 校验各状态码与文案 |
| `GlobalExceptionHandler` | 全局异常处理 | BusinessException 返回对应 code/message；RuntimeException 返回 SYSTEM_ERROR |

### 3.2 公共模块（common）
| 类 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `ResultUtils` | 成功/失败响应封装 | `success` 返回 code=0+data；`error` 返回正确 code/message，data 为 null |
| `BaseResponse` | 构造器 | 三个构造器各自字段赋值正确 |

### 3.3 枚举模块（model/enums）
| 类 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `UserRoleEnum` | 枚举解析 | 空值/未知值返回 null；user/admin 正确解析；text/value 正确 |

### 3.4 VO 模块（model/vo）
| 类 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `PictureVO` | 实体↔VO 双向转换 | objToVo/voToObj 的 null 处理、tags 在 JSON 字符串与 List 之间的转换 |

### 3.5 用户服务（UserServiceImpl）
| 方法 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `userRegister` | 注册校验与落库 | 空参/账号过短/密码过短/两次密码不一致/账号重复均抛错；成功返回新用户 id |
| `getEncryptPassword` | 密码加密 | 返回 (盐值+密码) 的 MD5 |
| `userLogin` | 登录 | 参数校验、用户不存在抛错、成功返回脱敏信息并写入会话 |
| `getLoginUserVO` / `getUserVO` | 脱敏 | null 返回 null；非 null 复制字段 |
| `getLoginUser` | 获取当前登录用户 | 未登录/无 id/数据库不存在抛 NOT_LOGIN；成功返回用户 |
| `userLogout` | 注销 | 未登录抛错；成功移除会话 |
| `getUserVOList` | 列表脱敏 | 空列表返回空；非空逐项转换 |
| `getQueryWrapper` | 查询条件构建 | null 请求抛错；各种筛选条件正确构建 |
| `isAdmin` | 权限判断 | null/user/admin 分别返回预期布尔值 |

### 3.6 图片服务（PictureServiceImpl）
| 方法 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `uploadPicture` | 图片上传 | 未登录抛 NO_AUTH；更新不存在图片抛 NOT_FOUND；新增成功返回 VO；保存失败抛错；更新成功带 id 与 editTime |
| `getQueryWrapper` | 查询条件构建 | null 返回空 wrapper；全量筛选条件（含 tags、searchText）正常构建 |
| `getPictureVO` | 单图脱敏 | userId 为 null 不查用户；有 userId 时填充 UserVO |
| `getPictureVOPage` | 分页脱敏 | 空分页返回空；非空批量填充用户信息 |
| `validPicture` | 图片校验 | null/id 缺失/url 过长/简介过长均抛错；合法数据不抛错 |

### 3.7 文件管理（FileManager / CosManager）
| 类/方法 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `FileManager.validPicture` | 文件校验 | null/超过 2M/非法后缀抛错；合法文件通过 |
| `FileManager.uploadPicture` | 上传 | 上传成功返回图片信息；COS 异常抛 SYSTEM_ERROR |
| `FileManager.deleteTempFile` | 清理临时文件 | null 与不存在文件均不抛异常 |
| `CosManager.putObject/getObject/putPictureObject` | COS 委托 | 正确委托 COSClient 并返回结果 |

### 3.8 控制器模块（Controller）
| 类 | 测试目标 | 关键用例与预期 |
| --- | --- | --- |
| `UserController` | 用户接口 | 各接口参数校验、成功返回、服务调用委托 |
| `PictureController` | 图片接口 | 增删改查、分页、编辑的权限与异常分支、标签分类列表 |
| `FileController` | 文件接口 | 上传/下载成功与异常分支 |
| `MainController` | 健康检查 | 返回 ok |

## 4. 测试数据准备与清理机制

- **准备**：每个测试类在 `@BeforeEach` 中使用 Mockito 创建被模拟的 Mapper/Service/Controller 依赖，并通过 `ReflectionTestUtils` 注入或直接构造被测对象；测试数据在用例内通过 DTO/实体构建。
- **清理**：由于采用纯 Mock 方式不写库，无数据库残留；临时文件在 `FileManager.uploadPicture` 的 `finally` 中由业务逻辑删除；测试运行结束后由 JVM 自动回收 Mock 对象。

## 5. 自动化测试集成配置

- 已将 `maven-surefire-plugin`（2.22.2）与 `jacoco-maven-plugin`（0.8.8）加入 `pom.xml`：
  - `mvn test`：编译并执行全部单元测试，运行后生成覆盖率报告（`target/site/jacoco/index.html`）。
  - `mvn verify`：在测试基础上执行 JaCoCo `check`，强制行覆盖率 ≥ 80%（低于阈值构建失败）。
- 本次按用户选择采用「仅本地 Maven」，CI/CD 管道可直接调用 `mvn verify` 完成自动化验证。

## 6. 独立运行方式

```bash
# 执行全部单元测试（含覆盖率报告）
mvn test

# 执行全部单元测试并强制校验 80% 覆盖率
mvn verify

# 单独运行某个测试类
mvn test -Dtest=UserServiceImplTest
```