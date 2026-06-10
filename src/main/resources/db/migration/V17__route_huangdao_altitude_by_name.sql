-- 按航路名称更新高度（含后台 GeoJSON 导入生成的 ASSIGN_ID 航路，如 2062101031604191234）
-- V16 仅更新 route-sf-huangdao 固定 ID，与列表/下钻实际使用的导入航路不是同一条记录

UPDATE route_waypoints rw
SET altitude = CASE rw.sequence
    WHEN 1 THEN 120
    WHEN 2 THEN 280
    WHEN 3 THEN 480
    WHEN 4 THEN 200
    ELSE rw.altitude
END
FROM routes r
WHERE rw.route_id = r.id
  AND r.region_id = 'R2'
  AND r.name = '顺丰-黄岛保税'
  AND r.deleted = 0
  AND rw.sequence BETWEEN 1 AND 4;

UPDATE route_version rv
SET cruise_height_m = 480
FROM routes r
WHERE rv.route_id = r.id
  AND r.region_id = 'R2'
  AND r.name = '顺丰-黄岛保税'
  AND r.deleted = 0;

UPDATE routes
SET flight_height = 300,
    updated_at = NOW()
WHERE region_id = 'R2'
  AND name = '顺丰-黄岛保税'
  AND deleted = 0;
