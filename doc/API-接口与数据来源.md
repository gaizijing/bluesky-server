# API 接口与数据来源说明

> 对应当前 V2 后端代码（Spring Boot 3 + Flyway + PostgreSQL）。  
> 统一前缀：`/api`；响应格式：`{ "code": 200, "message": "success", "data": ... }`  
> 在线调试：启动后访问 `http://localhost:8080/api/doc.html`（Knife4j）

---

## 1. 数据来源图例

| 标记 | 含义 |
|------|------|
| 🟢 **真实** | 直接调用外部 API 或解析真实文件（Open-Meteo、NetCDF、ISIM UDP） |
| 🟡 **混合** | 优先读调度缓存；无缓存时用真实 API 计算，或回退到 Flyway 种子/派生公式 |
| 🟠 **演示** | Flyway 种子、占位 URL、硬编码默认值，或 LLM 未接入时的模板文案 |
| ⚪ **配置** | 纯数据库 CRUD，数据由库内记录决定（含种子初始化） |

---

## 2. 外部数据源

| 数据源 | 用途 | 配置位置 |
|--------|------|----------|
| **Open-Meteo** | 实况、15 分钟粒度预报、适飞未来时间桶 | `WeatherService.callOpenMeteoCurrentAPI` / `fetchOpenMeteoForecastSeries` |
| **NOAA Reanalysis2 NetCDF** | 3D 风场 U/V 分量 | `application.yml` → `wind.field.u-file/v-file` |
| **ISIM 模拟机 UDP** | 飞机位置、重定位、风场回灌 | `application.yml` → `isim.*` |
| **WebSocket** | 前端实时飞机数据 | `/ws/isim-data` |

调度任务（`scheduler.enabled=true`）会周期性调用 Open-Meteo API，写入：

- `weather_grid_cache` — 气象格点
- `risk_field_cache` — 风险场格点
- `osi_landing_cache` / `osi_route_cache` — 适飞矩阵

手动触发重算：`POST /api/scheduler/recompute?regionId=R1`

---

## 3. Flyway 演示种子（开发/验收用）

| 迁移脚本 | 内容 |
|----------|------|
| `V2__seed_data.sql` | 用户、Region R1/R2、起降点、规则集 |
| `V2__seed_data.sql` | 演示预警、风险场、气象格点、航路、禁飞区、摄像头等示例数据 |
| `V14__camera_demo_seed.sql` | 摄像头记录，预览图为 picsum.photos 占位 |
| `V15__route_seed_r2.sql` | 演示航路「顺丰-黄岛保税」 |

**判断是否为种子数据：**

- 风险场：`risk_field_cache.rule_version LIKE '%-seed'`
- 气象格点：`weather_grid_cache.grid_json->>'source'` 含 `seed`
- 预警：`warning_records.dedupe_key` 含 `seed:`

---

## 4. 接口清单（按模块）

### 4.1 认证 `/auth` ⚪

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录，返回 JWT |
| POST | `/auth/logout` | 登出 |
| GET | `/auth/userInfo` | 当前用户信息 |

默认账号见 `docs/db/RESET-空库重建.md`（Flyway V2 种子：`admin`）。

---

### 4.2 Region / 起降点 ⚪

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/regions` | Region 列表（按用户权限） |
| GET | `/regions/default` | 默认 Region |
| GET/POST/PUT/DELETE | `/regions/{regionId}` | Region CRUD |
| GET | `/landing-points` | 起降点列表（`?regionId=`） |
| GET/POST/PUT/DELETE | `/landing-points/{id}` | 起降点 CRUD |

> V1 的 `/monitoring-points` 已废弃，请改用上述接口。

---

### 4.3 气象 `/weather`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/weather/realtime` | 🟢 Open-Meteo | 按起降点 ID 返回实况（含 TemporalMeta） |
| GET | `/weather/point` | 🟢 Open-Meteo | 单点查询（`lng`, `lat`, 可选 `includeRisk`） |
| POST | `/weather/by-coords/batch` | 🟢 Open-Meteo | 批量坐标查询，4 位小数去重 |
| GET | `/weather/forecast-trend` | 🟢 Open-Meteo | 趋势预报折线图 |
| GET | `/weather/grid-field` | 🟡 | 读 `weather_grid_cache`；无缓存返回空 grid + `cacheMiss` |
| GET | `/weather/heatmap/citywide` | 🟡 | 从 `risk_field_cache` 源点 + IDW 插值 |
| GET | `/weather/vertical-profile` | 🟡 | 有表数据则用；否则基于 Open-Meteo 按高度公式派生 |
| GET | `/meteorology/vertical-profile` | 🟡 | **已废弃**，兼容旧路径，同上 |

**未暴露的 HTTP 接口：** `WeatherService.getMicroscaleWeather()` 仍读 `risk_field_cache`，但无 Controller 映射。

---

### 4.4 风场 `/wind-field`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/wind-field` | 🟢 NetCDF | 解析 `data/uwnd.nc`、`data/vwnd.nc`（可自动从 NOAA 下载） |

参数：`regionId`、`time`、`heightM`、`bounds`（可选 bbox）。

---

### 4.5 风险 `/risk`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/risk/heatmap` | 🟡 | 读 `risk_field_cache`；种子或调度写入 |
| GET | `/risk/point` | 🟡 | 同上；无缓存时用 Open-Meteo 临时算，`isStale: true` |

---

### 4.6 适飞 `/flyability`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/flyability/landing-matrix` | 🟡 | 优先 `osi_landing_cache`；否则实时 Open-Meteo |
| GET | `/flyability/route-matrix` | 🟡 | 优先 `osi_route_cache`；否则实时计算 |

> V1 的 `/suitability/*` 已废弃，请改用上述接口。

---

### 4.7 预警 `/warnings`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/warnings` | 🟠 | 读 `warning_records`（当前为 Flyway 测试种子，**无实时规则引擎自动触发**） |
| GET | `/warnings/{warningId}` | 🟠 | 单条详情 |
| POST | `/warnings/{id}/ack` | ⚪ | NEW → ACKNOWLEDGED |
| POST | `/warnings/{id}/handle` | ⚪ | ACKNOWLEDGED → HANDLED |
| POST | `/warnings/{id}/close` | ⚪ | → CLOSED |

---

### 4.8 航路 `/routes`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/routes` | ⚪ | 航路列表（含 V15 演示航路种子） |
| GET | `/routes/{routeId}` | ⚪ | 航路详情 |
| GET | `/routes/{routeId}/versions` | ⚪ | 版本列表 |
| POST | `/routes` | ⚪ | 创建航路 |
| POST | `/routes/import` | ⚪ | 导入 GeoJSON |
| POST | `/routes/{routeId}/analyze` | 🟡 | 沿航线从 `risk_field_cache` 插值采样风险；见下文限制 |
| DELETE | `/routes` | ⚪ | 按 Region 清空 |
| DELETE | `/routes/{routeId}` | ⚪ | 删除单条 |

**航线分析 `analyze` 字段说明：**

- `risk` / `averageRisk`：来自 `risk_field_cache` 双线性插值（0~1）
- `windDir`、`rainfall`：**固定 0**（未接入独立格点）
- `windSpeed`、`windShear`、`turbulence`：格点快照中**未填充**，当前为 **0**
- `alternativeRoutes`：算法绕飞，非数据库查询
- 详见 `web/docs/route-risk-api.md`

---

### 4.9 禁飞区 `/no-fly-zones`

| 方法 | 路径 | 数据来源 |
|------|------|----------|
| GET/POST/PUT/DELETE | `/no-fly-zones` | ⚪ 配置 + V23 演示种子 |
| POST | `/no-fly-zones/import` | ⚪ GeoJSON 导入 |

---

### 4.10 摄像头 `/cameras`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/cameras` | 🟠 | V14 种子；预览 URL 为 picsum 占位图 |
| GET | `/cameras/{id}/preview` | 🟠 | 返回 DB 中的占位图 URL |
| GET | `/cameras/{id}/stream` | 🟠 | 多数为 NULL，无真实 RTSP |

---

### 4.11 设备 `/devices`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/devices/count` | ⚪ | 读 `devices` 表统计 |
| GET | `/devices/alarms` | ⚪ | 读 `device_alarms` |
| GET | `/devices/history` | 🟡 | 读 `device_history_data`；不足时用默认值；雷达图风向/气压写死 |
| GET/POST/PUT/DELETE | `/devices/list` 等 | ⚪ | 设备管理 CRUD |

---

### 4.12 飞行任务 `/flight`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET/POST/PUT/PATCH | `/flight/tasks` | ⚪ | 任务 CRUD（无 Flyway 种子，通常为空） |
| GET | `/flight/aircraft-adapt` | 🟡 | 读 `weather_realtime`；`cloudBase` 写死 500 |
| GET | `/flight/aircraft-models` | ⚪ | 机型列表 |

---

### 4.13 AI 解读 `/ai`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| POST | `/ai/conclusion` | 🟠 | LLM 未配置，超时后返回固定模板（`source: "template"`） |

---

### 4.14 模拟飞行 `/sim/sessions`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET/POST/PUT | `/sim/sessions` | ⚪ | 模拟会话 CRUD（DB 真实，场景为模拟） |

---

### 4.15 ISIM 集成 `/isim`

| 方法 | 路径 | 数据来源 | 说明 |
|------|------|----------|------|
| GET | `/isim/status` | 🟢 | UDP 连接状态 |
| POST | `/isim/control` | 🟢 | START_SENDING / STOP_SENDING |
| POST | `/isim/disconnect` | 🟢 | 断开 UDP |
| POST | `/isim/send-body-wind` | 🟢 | 手动发 U/V/W |
| POST | `/isim/update-target` | 🟢 | 更新 IP/端口，可选重定位 |
| POST | `/isim/relocate` | 🟢 | 飞机重定位 |
| WS | `/ws/isim-data` | 🟢 | 飞机位置（UDP）+ 风场（NetCDF） |

ISIM 气象查询失败时 fallback 硬编码默认值（风速 5 m/s、温度 20°C 等）。

---

### 4.16 规则集与调度

| 前缀 | 说明 | 数据来源 |
|------|------|----------|
| `/risk-rule-sets` | 风险规则 CRUD / 发布 | ⚪ |
| `/flyability-rule-sets` | 适飞规则 CRUD / 发布 | ⚪ |
| `/warning-rule-sets` | 预警规则 CRUD / 发布 | ⚪ |
| `/scheduler/health` | 调度健康检查 | ⚪ |
| `/scheduler/recompute` | 手动触发格点/风险/适飞重算 | 🟢 触发 Open-Meteo 采样 |
| `/scheduler/cleanup` | 清理过期缓存 | ⚪ |

---

### 4.17 用户 `/users`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/users/register` | 注册 |
| GET/POST/PUT/DELETE | `/users/*` | 用户管理 |

---

## 5. 已废弃接口（文档/前端勿再使用）

| 旧路径 | 替代 |
|--------|------|
| `/monitoring-points/*` | `/landing-points` + `/regions` |
| `/weather/microscale` | 无 HTTP 暴露；内部读 `risk_field_cache` |
| `/weather/wind-trend` | 未在 V2 Controller 暴露 |
| `/weather/wind-field` | 改为 `/wind-field` |
| `/weather/risk/*` | 改为 `/warnings` + `/risk/*` |
| `/suitability/*` | `/flyability/*` |
| `/meteorology/core-indicators` | 无 V2 Controller |

---

## 6. 开发环境切换到真实数据

1. 确保网络可访问 Open-Meteo API。
2. 启动后端，确认 `scheduler.enabled=true`。
3. 执行 `POST /api/scheduler/recompute?regionId=R1`（及 R2）。
4. 检查缓存：`risk_field_cache` 中 `rule_version` 不含 `-seed`，且 `computed_at` 为近期时间。
5. 风场：确认 `server/data/uwnd.nc` 存在或允许自动下载。

---

## 7. 相关文档

- [后端接口文档（V2 契约）](./后端接口文档.md)
- [航线风险分析接口](../../web/docs/route-risk-api.md)
- [ISIM 集成说明](./ISIM_INTEGRATION_IMPLEMENTATION.md)
- [数据库 V2 对照](../docs/db/SCHEMA-现状与V2迁移对照.md)
- [空库重建步骤](../docs/db/RESET-空库重建.md)
