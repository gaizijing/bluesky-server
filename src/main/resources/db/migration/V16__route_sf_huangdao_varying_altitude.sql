-- 顺丰-黄岛保税：各航点不同高度，供航路剖面演示（起飞爬升 → 巡航 → 下降）
UPDATE routes
SET flight_height = 300,
    updated_at = NOW()
WHERE id = 'route-sf-huangdao';

UPDATE route_version
SET cruise_height_m = 480
WHERE route_version_id = 'rv-sf-huangdao-v1';

UPDATE route_waypoints SET altitude = 120 WHERE id = 'wp-sf-huangdao-1';
UPDATE route_waypoints SET altitude = 280 WHERE id = 'wp-sf-huangdao-2';
UPDATE route_waypoints SET altitude = 480 WHERE id = 'wp-sf-huangdao-3';
UPDATE route_waypoints SET altitude = 200 WHERE id = 'wp-sf-huangdao-4';
