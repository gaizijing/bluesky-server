package com.bluesky.entity.wind;

import lombok.Data;

@Data
public class WindLayer {

    private int height;
    private WindData windData;

    public WindLayer() {}

    public WindLayer(int height, WindData windData) {
        this.height = height;
        this.windData = windData;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public WindData getWindData() {
        return windData;
    }

    public void setWindData(WindData windData) {
        this.windData = windData;
    }
}
