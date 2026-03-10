package com.bluesky.service;

import com.bluesky.common.Result;
import com.bluesky.entity.Bounds;
import com.bluesky.entity.wind.WindComponent;
import com.bluesky.entity.wind.WindData;
import com.bluesky.entity.wind.WindLayer;
import com.bluesky.vo.WindFieldResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WindFieldService {

    public Map<String, Object> getWindField(String time){

        int width = 32;
        int height = 32;

        List<WindLayer> layers = new ArrayList<>();

        int[] heights = {50,150,200};

        for(int h : heights){

            WindData windData = generateWindData(width,height);

            layers.add(new WindLayer(h,windData));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("time", time);
        result.put("layers", layers);
        return result;
    }


    private WindData generateWindData(int width,int height){

        List<Double> uArray = new ArrayList<>();
        List<Double> vArray = new ArrayList<>();

        double minU = Double.MAX_VALUE;
        double maxU = Double.MIN_VALUE;

        double minV = Double.MAX_VALUE;
        double maxV = Double.MIN_VALUE;

        Random random = new Random();

        for(int i=0;i<width*height;i++){

            double u = random.nextDouble()*6-3;
            double v = random.nextDouble()*6-3;

            uArray.add(u);
            vArray.add(v);

            minU = Math.min(minU,u);
            maxU = Math.max(maxU,u);

            minV = Math.min(minV,v);
            maxV = Math.max(maxV,v);
        }

        WindComponent uComp = new WindComponent(uArray,minU,maxU);
        WindComponent vComp = new WindComponent(vArray,minV,maxV);

        Bounds bounds = new Bounds(
                120.30,
                36.05,
                120.45,
                36.20
        );

        return new WindData(uComp,vComp,width,height,bounds);
    }
}