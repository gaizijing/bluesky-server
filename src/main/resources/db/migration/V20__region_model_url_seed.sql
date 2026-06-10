-- Region 白膜 tileset 与前端 / 管理端 modelUrl 字段对齐

UPDATE region
SET model_url = '/cesium/model/tianjin/tileset.json'
WHERE region_id = 'R1'
  AND (model_url IS NULL OR TRIM(model_url) = '');

UPDATE region
SET model_url = '/cesium/model/qingdaoshi/tileset.json'
WHERE region_id = 'R2'
  AND (model_url IS NULL OR TRIM(model_url) = '');
