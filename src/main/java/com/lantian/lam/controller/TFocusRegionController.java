package com.lantian.lam.controller;

import com.lantian.lam.annotation.ResponseWrapper;
import com.lantian.lam.model.entity.TFocusRegion;
import com.lantian.lam.service.ITFocusRegionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/lam/region")
public class TFocusRegionController {
    @Resource
    private ITFocusRegionService tFocusRegionService;

    @GetMapping("getAll")
    @ResponseWrapper
    public List<TFocusRegion> getAll() {
        return tFocusRegionService.getAll();
    }
}
