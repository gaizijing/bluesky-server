# 低空气象飞行保障服务系统 - 后端服务

> 基于 **Spring Boot 3 + PostgreSQL + MyBatis-Plus** 构建的 RESTful API 后端服务，为低空飞行作业提供气象监测、适飞分析、风险预警等核心能力。

---

## 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [功能模块说明](#功能模块说明)
- [快速启动](#快速启动)
- [API 接口列表](#api-接口列表)
- [数据库说明](#数据库说明)
- [配置说明](#配置说明)
- [默认账号](#默认账号)

---

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.2.0 | Web 框架 |
| PostgreSQL | 14+ | 主数据库 |
| MyBatis-Plus | 3.5.5 | ORM 框架，提供分页、CRUD 封装 |
| Spring Security | 6.x | 安全框架 |
| JWT (jjwt) | 0.12.3 | 无状态身份认证 |
| SpringDoc OpenAPI | 2.3.0 | Swagger 3 API 文档 |
| Hutool | 5.8.24 | Java 工具库 |
| Lombok | latest | 简化实体类代码 |

---

## 项目结构

```
bluesky-server/
├── pom.xml                                      # Maven 依赖配置
├── README.md                                    # 项目说明（本文件）
├── .gitignore
├── sql/
│   ├── schema.sql                               # 建表脚本（13张表）
│   └── init_data.sql                            # 初始化示例数据
└── src/main/
    ├── resources/
    │   └── application.yml                      # 应用配置（数据库、JWT、日志等）
    └── java/com/bluesky/
        ├── BlueSkyApplication.java              # 启动入口
        ├── common/
        │   └── Result.java                      # 统一响应体 {code, message, data}
        ├── config/
        │   ├── CorsConfig.java                  # 跨域配置
        │   ├── MybatisPlusConfig.java           # 分页插件配置
        │   └── SecurityConfig.java              # Spring Security + BCrypt 配置
        ├── exception/
        │   ├── BusinessException.java           # 自定义业务异常
        │   └── GlobalExceptionHandler.java      # 全局异常处理（统一错误响应）
        ├── util/
        │   └── JwtUtil.java                     # JWT 生成 / 解析 / 验证
        ├── entity/                              # 数据库实体类（13个）
        │   ├── User.java
        │   ├── MonitoringPoint.java             # 重点关注区域
        │   ├── RiskWarning.java                 # 风险预警
        │   ├── WeatherRealtime.java             # 实时气象
        │   ├── WindTrend.java                   # 风向趋势
        │   ├── WindField.java                   # 3D 风场
        │   ├── VerticalProfile.java             # 垂直剖面
        │   ├── CoreIndicator.java               # 核心气象要素
        │   ├── MicroscaleWeather.java           # 微尺度天气
        │   ├── SuitabilityAnalysis.java         # 适飞分析
        │   ├── FlightTask.java                  # 飞行任务
        │   ├── AircraftModel.java               # 飞行器型号
        │   └── AircraftLimit.java               # 飞行器气象限制
        ├── mapper/                              # MyBatis-Plus Mapper（13个，对应实体）
        ├── service/                             # 业务逻辑层（6个）
        │   ├── AuthService.java                 # 认证：登录、用户信息
        │   ├── MonitoringPointService.java      # 重点关注区域 CRUD
        │   ├── RiskWarningService.java          # 预警查询与处理
        │   ├── WeatherService.java              # 气象数据（实时/趋势/风场/微尺度）
        │   ├── SuitabilityService.java          # 适飞分析/核心要素/垂直剖面
        │   └── FlightTaskService.java           # 飞行任务 + 飞行器适配分析
        ├── controller/                          # API 控制器（8个）
        │   ├── AuthController.java
        │   ├── MonitoringPointController.java
        │   ├── RiskWarningController.java
        │   ├── WeatherController.java
        │   ├── SuitabilityController.java
        │   ├── CoreIndicatorController.java
        │   ├── VerticalProfileController.java
        │   └── FlightTaskController.java
        ├── dto/
        │   └── LoginRequest.java
        └── vo/
            └── LoginResponse.java
```

---

## 功能模块说明

### 1. 认证授权
用户登录后获取 JWT Token，后续所有请求在 Header 中携带 `Authorization: Bearer <token>`。

- 密码使用 BCrypt 加密存储
- Token 默认有效期 **24 小时**（可在配置文件修改）
- 提供登录、登出、获取当前用户信息接口

---

### 2. 重点关注区域管理
管理系统中的飞行起降点和作业点（即重点监控区域）。

- 支持增删改查
- 区域带有地理坐标（经纬度）和边界框信息
- 状态分三级：`available`（可用）/ `warning`（预警）/ `unavailable`（不可用）
- 支持"选中区域"概念，记录当前用户聚焦的监控区域

---

### 3. 气象数据
提供多维度气象数据，对接 Cesium 三维可视化和 ECharts 图表。

| 子功能 | 说明 |
|--------|------|
| 实时气象 | 温度、湿度、风速风向、能见度、气压、降水等最新观测值 |
| 风向趋势 | 指定时间段内风速/风向的时序折线数据（含上下限/偏差） |
| 3D 风场 | 各高度层经纬网格的风矢量（U/V分量），供 Cesium 粒子系统渲染 |
| 微尺度天气 | 精细化网格化风险热力图数据（湍流/风切变/局部强风） |

---

### 4. 适飞分析
综合多种气象要素，逐时间点判断区域是否适合飞行，输出热力图矩阵。

- **气象因素维度**：综合 / 风 / 风切变 / 颠簸指数 / 湍流 / 降水 / 能见度
- **时间维度**：支持 6h / 12h / 24h / 48h 预测时长
- **热力图**：每个时间格子返回 `isSuitable`（适飞/不适飞）+ 异常值

---

### 5. 风险预警
汇总各类气象风险告警。

- 预警等级：`danger`（危险）/ `warning`（警告）/ `info`（提示）
- 支持时间范围筛选、区域筛选
- 提供预警处理接口（标记已处理、记录处理人和备注）

---

### 6. 核心气象要素监测
实时展示温度、湿度、风速、能见度、气压、降水等核心指标当前值及健康状态。

- 每个要素带有 `warningThreshold`（警告阈值）和 `dangerThreshold`（危险阈值）
- 状态自动计算：`normal` / `warning` / `danger`

---

### 7. 垂直剖面数据
展示从 50m 到 500m 各高度层的气象要素分布，帮助规划飞行高度。

- 按高度层返回：风速、温度、湿度、能见度、气压、湍流等级
- 支持查询当前时刻 / 1小时前 / 3小时前 / 6小时前的剖面

---

### 8. 飞行任务管理
管理当日所有飞行任务的全生命周期。

- 任务状态流转：`waiting → ongoing → completed / cancelled`
- 按日期、状态、类型筛选
- 记录起飞/降落点、计划高度、实际轨迹、执行飞行器等信息

---

### 9. 飞行器气象适配分析
根据当前实时气象条件，逐一评估各型号飞行器是否满足飞行要求。

- 对比飞行器气象限制参数（最大风速/最小能见度/最低云底高等）与当前气象数据
- 输出每台飞行器的适配结果与不适配原因

---

## 快速启动

### 环境要求

| 环境 | 要求 |
|------|------|
| JDK | 17 或以上 |
| Maven | 3.6 或以上 |
| PostgreSQL | 14 或以上 |

---

### 第一步：创建数据库

```sql
-- 连接 PostgreSQL 后执行
CREATE DATABASE bluesky
    WITH ENCODING = 'UTF8'
    LC_COLLATE = 'zh_CN.UTF-8'
    LC_CTYPE   = 'zh_CN.UTF-8'
    TEMPLATE   = template0;
```

> Windows 下若无中文 locale，可改为 `ENCODING='UTF8'` 省略后两个参数。

---

### 第二步：执行数据库脚本

```bash
# 建表（13张表 + 索引）
psql -U postgres -d bluesky -f sql/schema.sql

# 写入初始数据（管理员账号、示例区域、飞行器、预警、任务等）
psql -U postgres -d bluesky -f sql/init_data.sql
```

---

### 第三步：修改数据库连接配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bluesky
    username: postgres        # 改为你的 PostgreSQL 用户名
    password: postgres        # 改为你的 PostgreSQL 密码
```

---

### 第四步：启动项目

```bash
# 方式一：Maven 直接运行（推荐开发环境）
mvn spring-boot:run

# 方式二：先打包再运行
mvn clean package -DskipTests
java -jar target/bluesky-server-1.0.0.jar

# 方式三：指定配置文件（生产环境）
java -jar target/bluesky-server-1.0.0.jar --spring.profiles.active=prod
```

启动成功后控制台输出：

```
========================================
  BlueSky Server Started Successfully!
  Swagger UI: http://localhost:8080/api/swagger-ui.html
  API Docs:   http://localhost:8080/api/v3/api-docs
========================================
```

---

### 第五步：访问接口文档

启动成功后，推荐使用 **Knife4j** 增强版文档 UI：

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/api/doc.html` | ✅ **Knife4j UI**（推荐，界面美观，支持搜索/调试/授权） |
| `http://localhost:8080/api/swagger-ui.html` | Swagger 原生 UI（备用） |
| `http://localhost:8080/api/v3/api-docs` | OpenAPI 3 原始 JSON 数据 |

**使用 Knife4j 调试带 Token 的接口：**
1. 先调用 `POST /api/auth/login` 获取 `token`
2. 点击右上角 **Authorize 🔒** 按钮
3. 在 `Authorization` 输入框中填写 `Bearer <你的token>`（注意有空格）
4. 点击 Authorize → 后续所有调试请求自动携带 Token

---

## API 接口列表

> 所有接口统一前缀：`/api`  
> 统一响应格式：`{ "code": 200, "message": "success", "data": ... }`

### 认证授权 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 用户登录，返回 JWT Token |
| POST | `/auth/logout` | 用户登出 |
| GET  | `/auth/userInfo` | 获取当前登录用户信息 |

**登录示例：**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

### 重点关注区域 `/api/monitoring-points`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET    | `/monitoring-points` | 获取所有区域列表 |
| GET    | `/monitoring-points/selected` | 获取当前选中区域 |
| POST   | `/monitoring-points/selected` | 更新选中区域 |
| POST   | `/monitoring-points` | 新增区域 |
| PUT    | `/monitoring-points/{id}` | 更新区域信息 |
| DELETE | `/monitoring-points/{id}` | 删除区域（逻辑删除） |

---

### 气象数据 `/api/weather`

| 方法 | 路径 | 说明 | 主要参数 |
|------|------|------|----------|
| GET | `/weather/realtime` | 实时气象数据 | `pointId` |
| GET | `/weather/wind-trend` | 风向趋势（折线图） | `pointId`, `timeRange` |
| GET | `/weather/wind-field` | 3D 风场数据（Cesium） | `timeRange`, `height` |
| GET | `/weather/microscale` | 微尺度天气热力图 | `region`, `timeRange` |

---

### 风险预警 `/api/weather/risk`

| 方法 | 路径 | 说明 | 主要参数 |
|------|------|------|----------|
| GET  | `/weather/risk/report` | 获取预警列表 | `pointId`, `timeRange` |
| POST | `/weather/risk/{id}/handle` | 处理预警 | `handler`, `remark` |

---

### 适飞分析 `/api/suitability`

| 方法 | 路径 | 说明 | 主要参数 |
|------|------|------|----------|
| GET | `/suitability/status` | 适飞状态热力矩阵 | `pointId`, `factor`, `totalHours` |
| GET | `/suitability/heatmap` | 空间区域适飞热力图 | `timePoint`, `factor` |

`factor` 可选值：`综合` / `风` / `风切变` / `颠簸指数` / `湍流` / `降水` / `能见度`

---

### 核心气象要素 `/api/meteorology`

| 方法 | 路径 | 说明 | 主要参数 |
|------|------|------|----------|
| GET | `/meteorology/core-indicators` | 核心要素监测 | `pointId` |
| GET | `/meteorology/vertical-profile` | 垂直剖面各层数据 | `pointId`, `timeType` |

`timeType` 可选值：`current` / `1h` / `3h` / `6h`

---

### 飞行任务 `/api/flight`

| 方法 | 路径 | 说明 | 主要参数 |
|------|------|------|----------|
| GET   | `/flight/tasks` | 飞行任务列表 | `taskDate`, `status`, `type` |
| GET   | `/flight/tasks/{taskId}` | 任务详情 | - |
| POST  | `/flight/tasks` | 创建任务 | 任务对象 |
| PUT   | `/flight/tasks/{taskId}` | 更新任务 | 任务对象 |
| PATCH | `/flight/tasks/{taskId}/status` | 更新任务状态 | `status` |
| GET   | `/flight/aircraft-adapt` | 飞行器适配分析 | `pointId` |
| GET   | `/flight/aircraft-models` | 飞行器型号列表 | - |

---

## 数据库说明

共 **13 张核心业务表**：

| 表名 | 说明 |
|------|------|
| `users` | 用户账号 |
| `monitoring_points` | 重点关注区域 |
| `risk_warnings` | 风险预警记录 |
| `weather_realtime` | 实时气象观测 |
| `wind_trend` | 风向趋势时序数据 |
| `wind_field` | 3D 风场矢量数据 |
| `vertical_profile` | 垂直剖面各高度层数据 |
| `core_indicators` | 核心气象要素监测 |
| `microscale_weather` | 微尺度网格天气数据 |
| `suitability_analysis` | 适飞分析结果 |
| `aircraft_models` | 飞行器型号 |
| `aircraft_limits` | 飞行器气象限制参数 |
| `flight_tasks` | 飞行任务 |

详细字段设计见：[数据库设计文档](../brain/beaec82a-1dcb-4246-9f37-46403efbc590/数据库设计文档.md)

---

## 配置说明

`src/main/resources/application.yml` 关键配置项：

```yaml
server:
  port: 8080                        # 服务端口
  servlet:
    context-path: /api              # 统一接口前缀

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bluesky
    username: postgres
    password: postgres

jwt:
  secret: bluesky-weather-...       # JWT 签名密钥（生产环境务必修改！）
  expiration: 86400000              # Token 有效期，毫秒，默认 24h
  header: Authorization             # 请求头名称
  prefix: "Bearer "                 # Token 前缀

logging:
  level:
    com.bluesky: DEBUG              # 开发环境日志级别
  file:
    name: logs/bluesky-server.log   # 日志文件路径
```

> ⚠️ **生产环境注意**：务必修改 `jwt.secret` 为随机长字符串，并将日志级别调整为 `INFO`。

---

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | 系统管理员 |

> 密码使用 BCrypt 加密存储，可通过接口或直接修改数据库更改密码。

---

## 开发扩展指南

### 添加新接口

1. 在 `entity/` 创建新实体类（`@TableName` 对应表名）
2. 在 `mapper/` 创建 Mapper 接口（继承 `BaseMapper<T>`）
3. 在 `service/` 创建 Service 类（注入 Mapper，编写业务逻辑）
4. 在 `controller/` 创建 Controller（注入 Service，添加 Swagger 注解）

### 统一响应格式

```java
// 成功
return Result.success(data);

// 失败（业务异常）
throw new BusinessException(400, "参数不合法");
```

---

## License

Copyright © 2025 BlueSky Team. All rights reserved.
