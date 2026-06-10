-- Region 绑定行政区划 GeoJSON（bounds 仍保留，由 GeoJSON 包络自动计算）

ALTER TABLE region
    ADD COLUMN IF NOT EXISTS boundary_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS adcode VARCHAR(20);

UPDATE region
SET boundary_url = '/cesium/shp/R1_ninghe.geojson',
    adcode = '120117'
WHERE region_id = 'R1';

UPDATE region
SET boundary_url = '/cesium/shp/R2_qingdao.geojson',
    adcode = '370200'
WHERE region_id = 'R2';
