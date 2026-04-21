package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.config.RegionConfig;
import com.bluesky.service.WindFieldService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wind-field")
public class WindFieldController {

    private final WindFieldService windFieldService;
    private final RegionConfig regionConfig;

    public WindFieldController(WindFieldService windFieldService, RegionConfig regionConfig) {
        this.windFieldService = windFieldService;
        this.regionConfig = regionConfig;
    }

    @GetMapping
    public Result<Map<String, Object>> getWindField(
            @RequestParam(required = false) String bounds
    ){
        //默认加载全市的风场数据，前端bounds传空
        String targetBounds = bounds != null ? bounds : String.format("[%s,%s,%s,%s]",
                regionConfig.getBounds().getWest(), regionConfig.getBounds().getSouth(),
                regionConfig.getBounds().getEast(), regionConfig.getBounds().getNorth());

        Map<String, Object> data = windFieldService.getWindField(targetBounds);
        return Result.success(data);
    }
}
