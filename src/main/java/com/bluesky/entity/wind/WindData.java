package com.bluesky.entity.wind;

import com.bluesky.entity.Bounds;
import lombok.Data;

@Data
public class WindData {

    private WindComponent u;
    private WindComponent v;

    private int width;
    private int height;

    private Bounds bounds;

    public WindData() {}

    public WindData(WindComponent u, WindComponent v, int width, int height, Bounds bounds) {
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.bounds = bounds;
    }

    public WindComponent getU() {
        return u;
    }

    public void setU(WindComponent u) {
        this.u = u;
    }

    public WindComponent getV() {
        return v;
    }

    public void setV(WindComponent v) {
        this.v = v;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public void setBounds(Bounds bounds) {
        this.bounds = bounds;
    }
}
