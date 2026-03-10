package com.bluesky.entity.wind;
import lombok.Data;

import java.util.List;
@Data
public class WindComponent {

    private List<Double> array;
    private double min;
    private double max;

    public WindComponent() {}

    public WindComponent(List<Double> array, double min, double max) {
        this.array = array;
        this.min = min;
        this.max = max;
    }

    public List<Double> getArray() {
        return array;
    }

    public void setArray(List<Double> array) {
        this.array = array;
    }

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }
}
