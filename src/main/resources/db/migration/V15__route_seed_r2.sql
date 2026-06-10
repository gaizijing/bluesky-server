-- R2 演示航路（docs/data/routes-r2/02-顺丰-黄岛保税.geojson）
-- 供航路下钻、剖面、预警 WR102 关联使用

DELETE FROM route_waypoints WHERE route_id = 'route-sf-huangdao';
DELETE FROM route_version WHERE route_id = 'route-sf-huangdao';
DELETE FROM routes WHERE id = 'route-sf-huangdao';

INSERT INTO routes (
    id, region_id, name, start_name, end_name, distance, status,
    average_risk, is_active, flight_height, current_version_id, deleted, created_at, updated_at
) VALUES (
    'route-sf-huangdao', 'R2', '顺丰-黄岛保税', '起点', '终点', 21.20,
    'available', 0.35, TRUE, 300, 'rv-sf-huangdao-v1', 0, NOW(), NOW()
);

INSERT INTO route_version (
    route_version_id, route_id, version_no, cruise_height_m, waypoint_count, distance_m, status, created_at
) VALUES (
    'rv-sf-huangdao-v1', 'route-sf-huangdao', 1, 300, 4, 21200, 'ACTIVE', NOW()
);

INSERT INTO route_waypoints (id, route_id, route_version_id, sequence, name, longitude, latitude, altitude, created_at) VALUES
    ('wp-sf-huangdao-1', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 1, '起点',   120.220000000, 36.040000000, 300, NOW()),
    ('wp-sf-huangdao-2', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 2, '途经点1', 120.280000000, 36.080000000, 300, NOW()),
    ('wp-sf-huangdao-3', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 3, '途经点2', 120.340000000, 36.120000000, 300, NOW()),
    ('wp-sf-huangdao-4', 'route-sf-huangdao', 'rv-sf-huangdao-v1', 4, '终点',   120.400000000, 36.160000000, 300, NOW());
