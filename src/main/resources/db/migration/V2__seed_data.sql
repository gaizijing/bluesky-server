-- V2 初始示例数据（整合原 V2~V31，空库打包部署用）
-- 默认账号：admin / 123456（SUPER_ADMIN）
-- 默认 Region：R1 天津宁河区、R2 青岛

INSERT INTO users (id, username, password, name, email, status, role, created_at, updated_at)
VALUES ('U001', 'admin', '$2a$10$2enCWfyf4FjHFpecHPgl1eLWoqko//fuR9IGRkWpmcwAsy5ng.RaK', '系统管理员', 'admin@bluesky.local', 'active', 'SUPER_ADMIN', NOW(), NOW());

INSERT INTO region (region_id, name, center_lng, center_lat, boundary_url, adcode, map_lift_json, model_url, enabled, is_default, created_at, updated_at)
VALUES
('R1', '天津宁河区', 117.75, 39.30, '/cesium/shp/R1_ninghe.geojson', '120117',
 '{"longitude":117.75,"latitude":39.30,"height":25000,"pitch":-35,"heading":0,"terrainExaggeration":1.2}'::jsonb,
 '/cesium/model/tianjin/tileset.json', TRUE, TRUE, NOW(), NOW()),
('R2', '青岛', 120.5, 36.5, '/cesium/shp/R2_qingdao.geojson', '370200',
 '{"longitude":120.5,"latitude":36.5,"height":25000,"pitch":-35,"heading":0,"terrainExaggeration":1.2}'::jsonb,
 '/cesium/model/qingdaoshi/tileset.json', TRUE, FALSE, NOW(), NOW());

INSERT INTO user_region_rel (user_id, region_id, created_at) SELECT 'U001', region_id, NOW() FROM region;

INSERT INTO landing_point (landing_point_id, region_id, name, code, type, address, longitude, latitude, altitude, bbox_min_lng, bbox_min_lat, bbox_max_lng, bbox_max_lat, enabled, created_at, updated_at) VALUES
('LP001', 'R1', '宁河主起降场', 'NH-01', 'takeoff', '天津市宁河区', 117.820000, 39.330000, 10.00, 117.800000, 39.310000, 117.840000, 39.350000, TRUE, NOW(), NOW()),
('LP002', 'R1', '宁河备降场', 'NH-02', 'operation', '天津市宁河区东部', 117.900000, 39.280000, 8.00, 117.880000, 39.260000, 117.920000, 39.300000, TRUE, NOW(), NOW()),
('LP003', 'R2', '青岛起降场', 'QD-01', 'takeoff', '青岛市', 120.450000, 36.250000, 5.00, 120.430000, 36.230000, 120.470000, 36.270000, TRUE, NOW(), NOW()),
('LP004', 'R2', '崂山低空枢纽', 'QD-02', 'takeoff', '青岛市崂山区', 120.468000, 36.107000, 12.00, 120.448000, 36.087000, 120.488000, 36.127000, TRUE, NOW(), NOW()),
('LP005', 'R2', '西海岸起降场', 'QD-03', 'takeoff', '青岛市黄岛区', 120.192000, 35.960000, 8.00, 120.172000, 35.940000, 120.212000, 35.980000, TRUE, NOW(), NOW()),
('LP006', 'R2', '古城建设发展有限公司', 'QD-04', 'operation', '青岛市城阳区', 120.396000, 36.307000, 6.00, 120.376000, 36.287000, 120.416000, 36.327000, TRUE, NOW(), NOW()),
('LP007', 'R2', '流亭物流起降点', 'QD-05', 'takeoff', '青岛市城阳区流亭', 120.374000, 36.266000, 10.00, 120.354000, 36.246000, 120.394000, 36.286000, TRUE, NOW(), NOW()),
('LP008', 'R2', '即墨滨海作业点', 'QD-06', 'operation', '青岛市即墨区', 120.447000, 36.389000, 5.00, 120.427000, 36.369000, 120.467000, 36.409000, TRUE, NOW(), NOW());

INSERT INTO flyability_rule_set (rule_set_id, name, version_no, status, effective_from, rules_json, created_at, updated_at) VALUES (
'FS001', '默认适飞规则', 1, 'PUBLISHED', NOW(), '{
  "windSpeedMs": {"medium": 8, "high": 12},
  "visibilityKm": {"medium": 3, "low": 1},
  "precipMmH": {"medium": 2, "high": 5},
  "temperatureC": {"low": -10, "high": 40},
  "cloudBaseM": {"medium": 300, "low": 150},
  "windShearMs": {"medium": 3, "high": 5},
  "turbulenceIndex": {"medium": 0.35, "high": 0.6},
  "turbulence": {"medium": 0.35, "high": 0.6},
  "rMet": {"factors": [{"name": "windSpeedMs", "weight": 0.4}, {"name": "windShearMs", "weight": 0.3}, {"name": "visibilityKm", "weight": 0.3}], "outputCap": 100}
}'::jsonb, NOW(), NOW());

INSERT INTO risk_rule_set (rule_set_id, name, version_no, status, effective_from, rules_json, created_at, updated_at) VALUES (
'RS001', '默认R_met规则', 1, 'PUBLISHED', NOW(), '{
  "factors": [{"name": "windSpeedMs", "weight": 0.4}, {"name": "windShearMs", "weight": 0.3}, {"name": "visibilityKm", "weight": 0.3}],
  "outputCap": 100
}'::jsonb, NOW(), NOW());

INSERT INTO warning_rule_set (rule_set_id, name, version_no, status, effective_from, rules_json, enable_llm, created_at, updated_at) VALUES (
'WS001', '默认预警规则', 1, 'PUBLISHED', NOW(), '{
  "l1Rules": [
    {"factor": "windSpeedMs", "operator": "gte", "threshold": 10, "level": "medium"},
    {"factor": "visibilityKm", "operator": "lte", "threshold": 2, "level": "medium"},
    {"factor": "windSpeedMs", "operator": "gte", "threshold": 12, "level": "high"}
  ]
}'::jsonb, FALSE, NOW(), NOW());

-- ========== 演示航路（R2） ==========

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-huangdao', 'R2', '顺丰-黄岛保税', '西海岸起降场', '黄岛保税港区', 23.50, 'available', 0.38, TRUE, 300, 'rv-sf-huangdao-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-huangdao-v1', 'route-sf-huangdao', 1, 480, 4, 23500, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-huangdao-01', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 1, '起点', 120.192000000, 35.960000000, 120, NOW()),
    ('wp-sf-huangdao-02', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 2, '途经点1', 120.250000000, 36.030000000, 350, NOW()),
    ('wp-sf-huangdao-03', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 3, '途经点2', 120.320000000, 36.090000000, 480, NOW()),
    ('wp-sf-huangdao-04', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 4, '终点', 120.400000000, 36.160000000, 120, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-jiaodong', 'R2', '顺丰-青岛胶东', '胶东低空枢纽', '即墨滨海作业点', 26.80, 'available', 0.32, TRUE, 320, 'rv-sf-jiaodong-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-jiaodong-v1', 'route-sf-jiaodong', 1, 450, 3, 26800, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-jiaodong-01', 'route-sf-jiaodong', 'rv-sf-jiaodong-v1', 1, '起点', 120.350000000, 36.080000000, 120, NOW()),
    ('wp-sf-jiaodong-02', 'route-sf-jiaodong', 'rv-sf-jiaodong-v1', 2, '途经点1', 120.465000000, 36.155000000, 420, NOW()),
    ('wp-sf-jiaodong-03', 'route-sf-jiaodong', 'rv-sf-jiaodong-v1', 3, '终点', 120.580000000, 36.260000000, 120, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-laoshan', 'R2', '顺丰-崂山高新', '崂山低空枢纽', '崂山高新园区', 18.60, 'available', 0.29, TRUE, 300, 'rv-sf-laoshan-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-laoshan-v1', 'route-sf-laoshan', 1, 400, 3, 18600, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-laoshan-01', 'route-sf-laoshan', 'rv-sf-laoshan-v1', 1, '起点', 120.468000000, 36.107000000, 120, NOW()),
    ('wp-sf-laoshan-02', 'route-sf-laoshan', 'rv-sf-laoshan-v1', 2, '途经点1', 120.540000000, 36.175000000, 380, NOW()),
    ('wp-sf-laoshan-03', 'route-sf-laoshan', 'rv-sf-laoshan-v1', 3, '终点', 120.620000000, 36.250000000, 120, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-liuting', 'R2', '顺丰-城阳流亭', '流亭物流起降点', '城阳北部枢纽', 16.40, 'available', 0.31, TRUE, 280, 'rv-sf-liuting-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-liuting-v1', 'route-sf-liuting', 1, 360, 3, 16400, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-liuting-01', 'route-sf-liuting', 'rv-sf-liuting-v1', 1, '起点', 120.374000000, 36.266000000, 100, NOW()),
    ('wp-sf-liuting-02', 'route-sf-liuting', 'rv-sf-liuting-v1', 2, '途经点1', 120.455000000, 36.298000000, 320, NOW()),
    ('wp-sf-liuting-03', 'route-sf-liuting', 'rv-sf-liuting-v1', 3, '终点', 120.540000000, 36.330000000, 100, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-jimo', 'R2', '顺丰-即墨温泉', '即墨滨海作业点', '即墨温泉度假区', 22.30, 'available', 0.27, TRUE, 300, 'rv-sf-jimo-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-jimo-v1', 'route-sf-jimo', 1, 400, 4, 22300, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-jimo-01', 'route-sf-jimo', 'rv-sf-jimo-v1', 1, '起点', 120.447000000, 36.389000000, 100, NOW()),
    ('wp-sf-jimo-02', 'route-sf-jimo', 'rv-sf-jimo-v1', 2, '途经点1', 120.520000000, 36.405000000, 280, NOW()),
    ('wp-sf-jimo-03', 'route-sf-jimo', 'rv-sf-jimo-v1', 3, '途经点2', 120.610000000, 36.425000000, 380, NOW()),
    ('wp-sf-jimo-04', 'route-sf-jimo', 'rv-sf-jimo-v1', 4, '终点', 120.700000000, 36.440000000, 100, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-jiaozhou', 'R2', '顺丰-胶州少海', '胶州西部枢纽', '少海湿地北侧', 28.50, 'available', 0.41, TRUE, 350, 'rv-sf-jiaozhou-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-jiaozhou-v1', 'route-sf-jiaozhou', 1, 440, 3, 28500, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-jiaozhou-01', 'route-sf-jiaozhou', 'rv-sf-jiaozhou-v1', 1, '起点', 120.020000000, 36.180000000, 120, NOW()),
    ('wp-sf-jiaozhou-02', 'route-sf-jiaozhou', 'rv-sf-jiaozhou-v1', 2, '途经点1', 120.130000000, 36.235000000, 400, NOW()),
    ('wp-sf-jiaozhou-03', 'route-sf-jiaozhou', 'rv-sf-jiaozhou-v1', 3, '终点', 120.260000000, 36.300000000, 120, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-laixi', 'R2', '顺丰-莱西姜山', '莱西南部枢纽', '姜山湿地东侧', 24.20, 'available', 0.36, TRUE, 320, 'rv-sf-laixi-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-laixi-v1', 'route-sf-laixi', 1, 400, 3, 24200, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-laixi-01', 'route-sf-laixi', 'rv-sf-laixi-v1', 1, '起点', 120.400000000, 36.720000000, 120, NOW()),
    ('wp-sf-laixi-02', 'route-sf-laixi', 'rv-sf-laixi-v1', 2, '途经点1', 120.520000000, 36.780000000, 360, NOW()),
    ('wp-sf-laixi-03', 'route-sf-laixi', 'rv-sf-laixi-v1', 3, '终点', 120.640000000, 36.840000000, 120, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-pingdu', 'R2', '顺丰-平度南村', '平度西部枢纽', '南村物流园', 23.80, 'available', 0.33, TRUE, 310, 'rv-sf-pingdu-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-pingdu-v1', 'route-sf-pingdu', 1, 390, 3, 23800, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-pingdu-01', 'route-sf-pingdu', 'rv-sf-pingdu-v1', 1, '起点', 120.120000000, 36.680000000, 120, NOW()),
    ('wp-sf-pingdu-02', 'route-sf-pingdu', 'rv-sf-pingdu-v1', 2, '途经点1', 120.220000000, 36.735000000, 350, NOW()),
    ('wp-sf-pingdu-03', 'route-sf-pingdu', 'rv-sf-pingdu-v1', 3, '终点', 120.360000000, 36.800000000, 120, NOW());

INSERT INTO routes (id, region_id, name, start_name, end_name, distance, status, average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at)
VALUES ('route-sf-coastal-bypass', 'R2', '西海岸-流亭', '西海岸起降场', '流亭物流起降点', 20.50, 'available', 0.42, TRUE, 300, 'rv-sf-coastal-bypass-v1', 0, NOW(), NOW());

INSERT INTO route_version (route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at)
VALUES ('rv-sf-coastal-bypass-v1', 'route-sf-coastal-bypass', 1, 360, 3, 20500, 'ACTIVE', NOW());

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-coastal-bypass-01', 'route-sf-coastal-bypass', 'rv-sf-coastal-bypass-v1', 1, '起点', 120.192000000, 35.960000000, 100, NOW()),
    ('wp-sf-coastal-bypass-02', 'route-sf-coastal-bypass', 'rv-sf-coastal-bypass-v1', 2, '途经点1', 120.280000000, 36.120000000, 320, NOW()),
    ('wp-sf-coastal-bypass-03', 'route-sf-coastal-bypass', 'rv-sf-coastal-bypass-v1', 3, '终点', 120.374000000, 36.266000000, 100, NOW());

INSERT INTO warning_records (warning_id, warning_type, display_region_id, target_type, target_id, level, title, content, status, dedupe_key, occurrence_count, bucket_time, rule_version, last_triggered_at, created_at, updated_at) VALUES
('WR001', 'L2', 'R1', 'LANDING_POINT', 'LP001', 'YELLOW', '宁河主起降场风速预警', '当前风速接近黄色阈值，请关注放飞决策。', 'NEW', 'L2:W1:LANDING_POINT:LP001:R1:seed', 1, NOW(), 'WS001-v1', NOW(), NOW(), NOW()),
('WR101', 'L2', 'R2', 'LANDING_POINT', 'LP004', 'RED', '崂山低空枢纽阵风超限', '当前阵风 14.2 m/s，超过 L2 阈值 12 m/s，建议暂停放飞。', 'NEW', 'seed:WR101', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '5 minutes', NOW(), NOW()),
('WR102', 'L2', 'R2', 'ROUTE', 'route-sf-huangdao', 'YELLOW', '顺丰-黄岛保税航路能见度偏低', '航路沿线能见度约 2.8 km，低于适飞建议值，注意低云影响。', 'NEW', 'seed:WR102', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '18 minutes', NOW(), NOW()),
('WR103', 'L1', 'R2', 'LANDING_POINT', 'LP007', 'YELLOW', '流亭物流起降点侧风预警', '侧风分量持续偏高，建议调整起降方向或延迟出港。', 'ACKNOWLEDGED', 'seed:WR103', 2, NOW(), 'WS001-v1', NOW() - INTERVAL '42 minutes', NOW(), NOW()),
('WR104', 'L1', 'R2', 'AIRSPACE', 'R2', 'GREEN', '城阳区域低云增多', '云底高度下降，对低空目视飞行有一般影响，持续监测。', 'NEW', 'seed:WR104', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '65 minutes', NOW(), NOW()),
('WR105', 'L2', 'R2', 'LANDING_POINT', 'LP005', 'RED', '西海岸起降场强降水', '未来 1 小时有中到大雨，不建议执行物流放飞任务。', 'NEW', 'seed:WR105', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '90 minutes', NOW(), NOW()),
('WR201', 'L2', 'R1', 'LANDING_POINT', 'LP001', 'RED', '宁河主起降场阵风超限', '阵风达 13.5 m/s，超过放飞上限，建议停飞。', 'NEW', 'seed:WR201', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '10 minutes', NOW(), NOW()),
('WR202', 'L1', 'R1', 'LANDING_POINT', 'LP002', 'YELLOW', '宁河备降场能见度下降', '能见度约 3.2 km，低于理想值，注意目视条件。', 'ACKNOWLEDGED', 'seed:WR202', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '35 minutes', NOW(), NOW()),
('WR203', 'L1', 'R1', 'ROUTE', 'route-nh-logistics', 'GREEN', '宁河物流航线低云提示', '航路中段云底略低，对一般任务影响有限。', 'HANDLED', 'seed:WR203', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '120 minutes', NOW(), NOW()),
('WR301', 'L2', 'R2', 'ROUTE', 'route-sf-huangdao', 'RED', '顺丰航路风切变告警', '航路中段监测到中度风切变（水平风速变化 6 m/s），建议暂停联飞任务。', 'NEW', 'seed:WR301', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '2 minutes', NOW(), NOW()),
('WR302', 'L2', 'R2', 'LANDING_POINT', 'LP006', 'RED', '古城建设起降场湍流预警', '垂直气流速度达 4.2 m/s，对低空起降有显著影响，建议延迟放飞。', 'NEW', 'seed:WR302', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '8 minutes', NOW(), NOW()),
('WR303', 'L1', 'R2', 'ROUTE', 'route-sf-huangdao', 'YELLOW', '顺丰航路侧风偏大', '航路沿线侧风分量 8.5 m/s，接近机型限制，注意姿态修正。', 'NEW', 'seed:WR303', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '15 minutes', NOW(), NOW()),
('WR304', 'L1', 'R2', 'LANDING_POINT', 'LP008', 'YELLOW', '即墨滨海作业点湿度超标', '当前湿度 88%，超过农业无人机安全阈值，建议缩短单次作业时间。', 'ACKNOWLEDGED', 'seed:WR304', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '28 minutes', NOW(), NOW()),
('WR305', 'L2', 'R2', 'AIRSPACE', 'R2', 'RED', '城阳空域雷暴临近', '雷达显示 30 分钟后有雷暴云团移入，建议所有低空任务立即返航。', 'NEW', 'seed:WR305', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '3 minutes', NOW(), NOW()),
('WR306', 'L1', 'R2', 'LANDING_POINT', 'LP004', 'GREEN', '崂山枢纽电池低温提示', '气温降至 2℃，电池放电效率可能下降 15%，建议缩短续航预期。', 'NEW', 'seed:WR306', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '50 minutes', NOW(), NOW()),
('WR307', 'L2', 'R2', 'ROUTE', 'route-sf-huangdao', 'RED', '顺丰航路进入禁飞缓冲区', '当前位置距禁飞区边界不足 500 m，请立即调整航向。', 'NEW', 'seed:WR307', 2, NOW(), 'WS001-v1', NOW() - INTERVAL '1 minute', NOW(), NOW());

INSERT INTO no_fly_zone (zone_id, region_id, name, zone_type, geometry_json, enabled, created_at, updated_at) VALUES
('NFZ_R1_001', 'R1', '宁河东部禁飞区', 'PERMANENT', '{"type":"Polygon","coordinates":[[[117.860,39.310],[117.900,39.310],[117.900,39.350],[117.860,39.350],[117.860,39.310]]]}'::jsonb, TRUE, NOW(), NOW()),
('NFZ_R2_001', 'R2', '崂山禁飞演示区', 'PERMANENT', '{"type":"Polygon","coordinates":[[[120.580,36.120],[120.650,36.120],[120.650,36.180],[120.580,36.180],[120.580,36.120]]]}'::jsonb, TRUE, NOW(), NOW());

INSERT INTO cameras (id, name, location, point_id, longitude, latitude, status, resolution, preview_url, stream_url, last_heartbeat, is_active, created_at, updated_at) VALUES
('CAM-LP004-01', '东侧全景', '崂山区东侧围栏', 'LP004', 120.470000, 36.108000, 'online', '1920x1080', 'https://picsum.photos/seed/lp004-east/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP004-02', '跑道入口', '起降区主入口', 'LP004', 120.468500, 36.107200, 'online', '2560x1440', 'https://picsum.photos/seed/lp004-runway/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP004-03', '机库门口', '机库南侧', 'LP004', 120.466800, 36.106500, 'offline', '1920x1080', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 3600000, TRUE, NOW(), NOW()),
('CAM-LP004-04', '北侧监控', '北侧道路', 'LP004', 120.469200, 36.109100, 'online', '1280x720', 'https://picsum.photos/seed/lp004-north/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP005-01', '塔台视角', '观测塔', 'LP005', 120.193000, 35.961000, 'online', '1920x1080', 'https://picsum.photos/seed/lp005-tower/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP005-02', '停机坪', '停机坪南侧', 'LP005', 120.191500, 35.959800, 'online', '1920x1080', 'https://picsum.photos/seed/lp005-pad/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP005-03', '门岗', '主门岗', 'LP005', 120.192800, 35.960500, 'offline', '1920x1080', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 7200000, FALSE, NOW(), NOW()),
('CAM-LP005-04', '西侧围栏', '西侧围栏', 'LP005', 120.190800, 35.960200, 'online', '1920x1080', 'https://picsum.photos/seed/lp005-west/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-01', '物流区入口', '入口卡口', 'LP007', 120.375000, 36.267000, 'online', '1920x1080', 'https://picsum.photos/seed/lp007-gate/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-02', '装卸区', '装卸平台', 'LP007', 120.373800, 36.265800, 'online', '1920x1080', 'https://picsum.photos/seed/lp007-dock/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-03', '仓储区', '仓储区通道', 'LP007', 120.374500, 36.266500, 'online', '1280x720', 'https://picsum.photos/seed/lp007-store/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-04', '备用监控', '备用位', 'LP007', 120.373200, 36.265200, 'offline', '1920x1080', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 1800000, TRUE, NOW(), NOW()),
('CAM-LP001-01', '主起降场全景', '宁河主起降场', 'LP001', 117.700000, 39.400000, 'online', '1920x1080', 'https://picsum.photos/seed/lp001-main/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP001-02', '备降场监视', '东部备降方向', 'LP001', 117.702000, 39.401000, 'online', '1920x1080', 'https://picsum.photos/seed/lp001-back/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP001-03', '通道监控', '进场通道', 'LP001', 117.699000, 39.399000, 'offline', '1280x720', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 5400000, TRUE, NOW(), NOW()),
('CAM-LP001-04', '围界监控', '围界东北角', 'LP001', 117.701500, 39.402000, 'online', '1920x1080', 'https://picsum.photos/seed/lp001-fence/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW());

-- ========== 风险场缓存 ==========

INSERT INTO risk_field_cache (region_id, bucket_time, height_m, lng, lat, value, level, reason, factors_json, rule_version, computed_at)
SELECT 'R1', date_trunc('hour', NOW()), h.height_m,
    117.4 + (118.1 - 117.4) * c.col / 9.0, 39.1 + (39.5 - 39.1) * r.row / 9.0,
    ROUND(v.raw::numeric, 2),
    CASE WHEN v.raw >= 70 THEN 'HIGH' WHEN v.raw >= 40 THEN 'MEDIUM' ELSE 'LOW' END,
    CASE WHEN v.raw >= 70 THEN '风速偏大，建议暂缓放飞' WHEN v.raw >= 40 THEN '综合风险中等，请关注风切变' ELSE '综合风险一般' END,
    '{"wind":0.4,"windShear":0.3,"visibility":0.3}'::jsonb, 'RS001-v1-seed', NOW()
FROM generate_series(0, 9) AS r(row)
CROSS JOIN generate_series(0, 9) AS c(col)
CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
CROSS JOIN LATERAL (
    SELECT LEAST(100, GREATEST(5, 28 + 22 * sin(r.row * 0.85 + c.col * 0.55 + h.height_m * 0.002) + 18 * cos(c.col * 0.72 - r.row * 0.4) + (r.row + c.col) * 2.5)) AS raw
) AS v;


INSERT INTO risk_field_cache (region_id, bucket_time, height_m, lng, lat, value, level, reason, factors_json, rule_version, computed_at)
SELECT 'R2', date_trunc('hour', NOW()), h.height_m,
    120.12 + (120.48 - 120.12) * c.col / 11.0, 35.96 + (36.22 - 35.96) * r.row / 11.0,
    ROUND(v.raw::numeric, 2),
    CASE WHEN v.raw >= 70 THEN 'HIGH' WHEN v.raw >= 40 THEN 'MEDIUM' ELSE 'LOW' END,
    CASE WHEN v.raw >= 70 THEN '强对流核心，建议暂缓放飞' WHEN v.raw >= 40 THEN '回波中等，关注风切变' ELSE '背景回波较弱' END,
    '{"wind":0.45,"windShear":0.3,"visibility":0.25}'::jsonb, 'RS001-v1-seed', NOW()
FROM generate_series(0, 11) AS r(row)
CROSS JOIN generate_series(0, 11) AS c(col)
CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
CROSS JOIN LATERAL (
    SELECT 120.12 + (120.48 - 120.12) * c.col / 11.0 AS lng, 35.96 + (36.22 - 35.96) * r.row / 11.0 AS lat
) AS g
CROSS JOIN LATERAL (
    SELECT LEAST(100, GREATEST(6, 8 + 6 * sin(r.row * 0.82 + c.col * 0.58)
        + 52 * exp(-(power((g.lng - 120.252) / 0.032, 2) + power((g.lat - 36.065) / 0.028, 2))) * (0.32 + h.height_m / 4200.0)
        + 44 * exp(-(power((g.lng - 120.368) / 0.038, 2) + power((g.lat - 36.105) / 0.034, 2))) * (0.28 + h.height_m / 3600.0)
        + h.height_m * 0.014)) AS raw
) AS v;


-- ========== 气象格点缓存 ==========

-- MetViz 全要素格点种子：64×64 · 5 高度层 · R1 + R2 · 当前 15 分钟桶
WITH bucket AS (
    SELECT (
        date_trunc('minute', timezone('Asia/Shanghai', CURRENT_TIMESTAMP))
        - make_interval(mins => (EXTRACT(MINUTE FROM timezone('Asia/Shanghai', CURRENT_TIMESTAMP))::int % 15))
    )::timestamp AS t
),
cell_grid AS (
    SELECT
        r.region_id, b.t AS bucket_time, h.height_m, p.product,
        r.west, r.east, r.south, r.north,
        jsonb_agg(
            jsonb_build_object(
                'lng', r.west + (r.east - r.west) * c.col / 63.0,
                'lat', r.south + (r.north - r.south) * rrow.row / 63.0,
                'value', ROUND(
                    CASE p.product
                        WHEN 'temperature' THEN 18 + 10 * sin(rrow.row * 0.11 + c.col * 0.08 + h.height_m * 0.0007) + 5 * cos(c.col * 0.1 - rrow.row * 0.06) - h.height_m * 0.002
                        WHEN 'wind' THEN 3 + 9 * abs(sin(rrow.row * 0.09 + c.col * 0.07)) + 3 * cos(rrow.row * 0.04 - c.col * 0.05) + h.height_m * 0.002
                        WHEN 'visibility' THEN 8000 + 6000 * sin(rrow.row * 0.08 + c.col * 0.06) + 2000 * cos(c.col * 0.09 - rrow.row * 0.05) - h.height_m * 0.8
                        WHEN 'precip' THEN GREATEST(0, 2 + 8 * abs(sin(rrow.row * 0.13 + c.col * 0.1)) - h.height_m * 0.001)
                        WHEN 'humidity' THEN 55 + 30 * sin(rrow.row * 0.1 + c.col * 0.07 + h.height_m * 0.0003) + 10 * cos(c.col * 0.08 - rrow.row * 0.04) - h.height_m * 0.004
                        WHEN 'cloud' THEN 30 + 50 * abs(sin(rrow.row * 0.09 + c.col * 0.11)) + 15 * cos(rrow.row * 0.05 - c.col * 0.06)
                        WHEN 'pressure' THEN 1013 - h.height_m * 0.012 + 8 * sin(rrow.row * 0.07 + c.col * 0.05)
                        ELSE 0
                    END::numeric, 2
                )
            ) ORDER BY rrow.row, c.col
        ) AS cells
    FROM bucket b
    CROSS JOIN (VALUES ('R1', 117.4::float8, 118.1::float8, 39.1::float8, 39.5::float8), ('R2', 120.0::float8, 121.0::float8, 36.0::float8, 37.0::float8)) AS r(region_id, west, east, south, north)
    CROSS JOIN generate_series(0, 63) AS rrow(row)
    CROSS JOIN generate_series(0, 63) AS c(col)
    CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
    CROSS JOIN (VALUES ('temperature'), ('wind'), ('visibility'), ('precip'), ('humidity'), ('cloud'), ('pressure')) AS p(product)
    GROUP BY r.region_id, b.t, h.height_m, p.product, r.west, r.east, r.south, r.north
)
INSERT INTO weather_grid_cache (region_id, bucket_time, height_m, product, grid_json, data_source_time, computed_at, expires_at)
SELECT region_id, bucket_time, height_m, product,
    jsonb_build_object('source', 'seed', 'west', west, 'east', east, 'south', south, 'north', north, 'width', 64, 'height', 64, 'product', product, 'cells', cells),
    bucket_time, NOW(), NOW() + interval '7 days'
FROM cell_grid;

