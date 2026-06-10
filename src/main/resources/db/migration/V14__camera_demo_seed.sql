-- 下钻「实时监控」面板演示数据（每起降点 2~4 路摄像头）

INSERT INTO cameras (
    id, name, location, point_id, longitude, latitude,
    status, resolution, preview_url, stream_url, last_heartbeat, is_active, created_at, updated_at
) VALUES
-- LP004 崂山低空枢纽
('CAM-LP004-01', '东侧全景', '崂山区东侧围栏', 'LP004', 120.470000, 36.108000,
 'online', '1920x1080', 'https://picsum.photos/seed/lp004-east/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP004-02', '跑道入口', '起降区主入口', 'LP004', 120.468500, 36.107200,
 'online', '2560x1440', 'https://picsum.photos/seed/lp004-runway/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP004-03', '机库门口', '机库南侧', 'LP004', 120.466800, 36.106500,
 'offline', '1920x1080', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 3600000, TRUE, NOW(), NOW()),
('CAM-LP004-04', '北侧监控', '北侧道路', 'LP004', 120.469200, 36.109100,
 'online', '1280x720', 'https://picsum.photos/seed/lp004-north/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),

-- LP005 西海岸起降场
('CAM-LP005-01', '塔台视角', '观测塔', 'LP005', 120.193000, 35.961000,
 'online', '1920x1080', 'https://picsum.photos/seed/lp005-tower/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP005-02', '停机坪', '停机坪南侧', 'LP005', 120.191500, 35.959800,
 'online', '1920x1080', 'https://picsum.photos/seed/lp005-pad/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP005-03', '门岗', '主门岗', 'LP005', 120.192800, 35.960500,
 'offline', '1920x1080', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 7200000, FALSE, NOW(), NOW()),
('CAM-LP005-04', '西侧围栏', '西侧围栏', 'LP005', 120.190800, 35.960200,
 'online', '1920x1080', 'https://picsum.photos/seed/lp005-west/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),

-- LP007 流亭物流
('CAM-LP007-01', '物流区入口', '入口卡口', 'LP007', 120.375000, 36.267000,
 'online', '1920x1080', 'https://picsum.photos/seed/lp007-gate/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-02', '装卸区', '装卸平台', 'LP007', 120.373800, 36.265800,
 'online', '1920x1080', 'https://picsum.photos/seed/lp007-dock/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-03', '仓储区', '仓储区通道', 'LP007', 120.374500, 36.266500,
 'online', '1280x720', 'https://picsum.photos/seed/lp007-store/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP007-04', '备用监控', '备用位', 'LP007', 120.373200, 36.265200,
 'offline', '1920x1080', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 1800000, TRUE, NOW(), NOW()),

-- LP001 宁河（R1 演示）
('CAM-LP001-01', '主起降场全景', '宁河主起降场', 'LP001', 117.700000, 39.400000,
 'online', '1920x1080', 'https://picsum.photos/seed/lp001-main/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP001-02', '备降场监视', '东部备降方向', 'LP001', 117.702000, 39.401000,
 'online', '1920x1080', 'https://picsum.photos/seed/lp001-back/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW()),
('CAM-LP001-03', '通道监控', '进场通道', 'LP001', 117.699000, 39.399000,
 'offline', '1280x720', NULL, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000 - 5400000, TRUE, NOW(), NOW()),
('CAM-LP001-04', '围界监控', '围界东北角', 'LP001', 117.701500, 39.402000,
 'online', '1920x1080', 'https://picsum.photos/seed/lp001-fence/640/360', NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
