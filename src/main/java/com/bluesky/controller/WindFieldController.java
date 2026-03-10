package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.WindFieldService;
import com.bluesky.vo.WindFieldResponse;
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
            @RequestParam String time
    ){
        Map<String, Object> data = windFieldService.getWindField(time);
        return Result.success(data);
    }
}