package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.WindFieldService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wind-field")
public class WindFieldController {

    private final WindFieldService windFieldService;

    public WindFieldController(WindFieldService windFieldService) {
        this.windFieldService = windFieldService;
    }

    @GetMapping
    public Result<Map<String, Object>> getWindField(
            @RequestParam(required = false) String bounds
    ){
        Map<String, Object> data = windFieldService.getWindField(bounds);
        return Result.success(data);
    }
}
