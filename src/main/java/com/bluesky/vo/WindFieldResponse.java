package com.bluesky.vo;

import com.bluesky.entity.wind.WindLayer;
import lombok.Data;

import java.util.List;
@Data
public class WindFieldResponse {

    private String time;
    private List<WindLayer> layers;

    public WindFieldResponse() {}

    public WindFieldResponse(String time, List<WindLayer> layers) {
        this.time = time;
        this.layers = layers;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public List<WindLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<WindLayer> layers) {
        this.layers = layers;
    }
}