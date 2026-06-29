# 低空气象飞行保障服务系统 - 后端服务

> 基于 **Spring Boot 3 + PostgreSQL + Flyway + MyBatis-Plus** 的 RESTful API，为低空飞行提供气象监测、适飞分析、风险预警、航路分析与 ISIM 模拟机集成。

---

## 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [功能模块](#功能模块)
- [快速启动](#快速启动)
- [API 文档](#api-文档)
- [配置说明](#配置说明)
- [默认账号](#默认账号)

---

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS |
| Spring Boot | 3.2.x | Web 框架 |
| PostgreSQL | 14+ | 主数据库 |
| Flyway | — | 数据库迁移与种子数据 |
| MyBatis-Plus | 3.5.x | ORM |
| Spring Security + JWT | — | 认证 |
| Knife4j / SpringDoc | — | API 文档 |
| NetCDF | — | 风场文件解析 |

---

## 项目结构

```
server/
├── pom.xml
├── src/main/
│   ├── resources/
│   │   ├── application.yml          # 数据库、JWT、风场、ISIM、调度
│   │   └── db/migration/            # Flyway 迁移（V1 建表 + V2 示例数据）
│   └── java/com/bluesky/
│       ├── controller/              # REST 控制器（24+）
│       ├── service/                 # 业务逻辑
│       ├── scheduler/               # 格点/风险/适飞定时任务
│       ├── entity/ / mapper/        # 实体与 MyBatis
│       └── isim/                    # ISIM UDP + WebSocket
├── doc/
│   ├── 后端接口文档.md               # V2 接口契约（本仓库维护）
│   ├── API-接口与数据来源.md         # 真实/演示数据对照
│   └── ISIM_INTEGRATION_IMPLEMENTATION.md
└── sql/                             # 历史手工脚本（开发以 Flyway 为准）
```

---

## 功能模块

| 模块 | 路径前缀 | 说明 |
|------|----------|------|
| 认证 | `/auth` | JWT 登录 |
| Region / 起降点 | `/regions`, `/landing-points` | 多 Region 数据隔离 |
| 气象 | `/weather` | Open-Meteo 实况/预报 + 格点缓存 |
| 风场 | `/wind-field` | NetCDF 三维风场 |
| 风险 | `/risk` | 风险场热力图 / 单点 |
| 适飞 | `/flyability` | 起降点 / 航路适飞矩阵 |
| 预警 | `/warnings` | L1/L2 预警记录与处置 |
| 航路 | `/routes` | CRUD + 风险分析 |
| 禁飞区 | `/no-fly-zones` | GeoJSON 多边形 |
| 摄像头 | `/cameras` | 监控点配置 |
| 设备 | `/devices` | 监测统计与管理 |
| 飞行任务 | `/flight` | 任务 CRUD + 机型适配 |
| AI 解读 | `/ai` | 模板降级（LLM 未接入） |
| 模拟飞行 | `/sim/sessions` | 模拟会话 |
| ISIM | `/isim`, `/ws/isim-data` | 模拟机 UDP 集成 |
| 调度 | `/scheduler` | 缓存重算与健康检查 |
| 规则集 | `/*-rule-sets` | 风险 / 适飞 / 预警规则 |

---

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.6+
- PostgreSQL 14+

### 1. 创建数据库

```sql
CREATE DATABASE bluesky WITH ENCODING = 'UTF8';
```

### 2. 配置连接

编辑 `src/main/resources/application.yml` 中的 `spring.datasource`。

### 3. 启动（Flyway 自动建表 + 种子）

```bash
cd server
mvn spring-boot:run
```

空库重建步骤见 [docs/db/RESET-空库重建.md](../docs/db/RESET-空库重建.md)。

### 4. 访问文档

| 地址 | 说明 |
|------|------|
| http://localhost:8080/api/doc.html | Knife4j（推荐） |
| http://localhost:8080/api/swagger-ui.html | Swagger UI |

调试带 Token 接口：先 `POST /auth/login`，再在 Knife4j 中 Authorize 填入 `Bearer <token>`。

### 5. 刷新真实气象缓存（可选）

```bash
curl -X POST "http://localhost:8080/api/scheduler/recompute?regionId=R1" \
  -H "Authorization: Bearer <token>"
```

---

## API 文档

详细接口与**真实/演示数据来源**说明请参阅：

- **[doc/后端接口文档.md](doc/后端接口文档.md)** — V2 接口索引与示例
- **[doc/API-接口与数据来源.md](doc/API-接口与数据来源.md)** — 各接口数据来源对照（🟢真实 / 🟡混合 / 🟠演示）
- **[web/docs/route-risk-api.md](../web/docs/route-risk-api.md)** — 航线风险分析契约

### 接口速览

> 统一前缀 `/api`

| 模块 | 代表接口 |
|------|----------|
| 气象 | `GET /weather/realtime`, `/point`, `/grid-field`, `/forecast-trend` |
| 风场 | `GET /wind-field` |
| 风险 | `GET /risk/heatmap`, `/point` |
| 适飞 | `GET /flyability/landing-matrix`, `/route-matrix` |
| 预警 | `GET /warnings` |
| 航路 | `GET /routes`, `POST /routes/{id}/analyze` |
| ISIM | `GET /isim/status`, `WS /ws/isim-data` |

### 已废弃（V1，勿再使用）

| 旧路径 | V2 替代 |
|--------|---------|
| `/monitoring-points` | `/landing-points` + `/regions` |
| `/weather/microscale` | 无 HTTP；内部读 `risk_field_cache` |
| `/suitability/*` | `/flyability/*` |
| `/meteorology/core-indicators` | 无 V2 接口 |

---

## 配置说明

`application.yml` 关键项：

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  flyway:
    enabled: true
    locations: classpath:db/migration

wind.field:
  u-file: data/uwnd.nc
  v-file: data/vwnd.nc
  auto-download-if-missing: true   # 从 NOAA 自动下载

isim:
  enabled: true
  websocket-path: /ws/isim-data

scheduler:
  enabled: true                    # 定时写格点/风险/适飞缓存
```

生产环境务必修改 `jwt.secret`。

---

## 默认账号

Flyway V2 种子见 [docs/db/RESET-空库重建.md](../docs/db/RESET-空库重建.md)：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | SUPER_ADMIN |

默认 Region：`R1` 天津宁河区（default）、`R2` 青岛。

---

## License

Copyright © 2025 BlueSky Team. All rights reserved.
