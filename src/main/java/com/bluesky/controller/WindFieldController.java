package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.Region;
import com.bluesky.service.RegionService;
import com.bluesky.service.WindFieldService;
import com.bluesky.util.TimeBucketUtil;
import com.bluesky.vo.RegionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/wind-field")
@RequiredArgsConstructor
public class WindFieldController {

    private final WindFieldService windFieldService;
    private final RegionService regionService;

    @GetMapping
    public Result<Map<String, Object>> getWindField(
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "100") int heightM,
            @RequestParam(required = false) String bounds
    ) {
        String targetBounds = bounds;
        if (targetBounds == null || targetBounds.isBlank()) {
            RegionVO regionVo = regionId != null && !regionId.isBlank()
                    ? regionService.getById(regionId)
                    : regionService.getDefault();
            Region entity = regionService.getEntity(regionVo.getRegionId());
            targetBounds = String.format("[%s,%s,%s,%s]",
                    entity.getWest(), entity.getSouth(),
                    entity.getEast(), entity.getNorth());
        }

        Map<String, Object> data = windFieldService.getWindField(targetBounds);
        data.put("regionId", regionId);
        data.put("heightM", heightM);
        data.put("bucketTime", TimeBucketUtil.toBucket(TimeBucketUtil.parseOrNow(time)));
        data.put("isStale", true);
        return Result.success(data);
    }
}
