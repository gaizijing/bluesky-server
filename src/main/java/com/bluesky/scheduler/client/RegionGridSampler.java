package com.bluesky.scheduler.client;

import com.bluesky.entity.Region;
import com.bluesky.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 Region 边界采样格点气象（复用 WeatherService / 和风），供 P2 格点缓存写入。
 */
@Component
@RequiredArgsConstructor
public class RegionGridSampler {

    private final WeatherService weatherService;

    public Map<String, Object> sample(Region region, int rows, int cols, String product, long intervalMs) {
        double west = region.getWest();
        double east = region.getEast();
        double south = region.getSouth();
        double north = region.getNorth();
        int safeRows = Math.max(2, rows);
        int safeCols = Math.max(2, cols);

        List<Double> lngs = new ArrayList<>();
        List<Double> lats = new ArrayList<>();
        for (int c = 0; c < safeCols; c++) {
            lngs.add(west + (east - west) * c / (safeCols - 1.0));
        }
        for (int r = 0; r < safeRows; r++) {
            lats.add(south + (north - south) * r / (safeRows - 1.0));
        }

        List<Map<String, Object>> cells = new ArrayList<>();
        for (double lat : lats) {
            for (double lng : lngs) {
                Double value = fetchProductValue(lng, lat, product);
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("lng", lng);
                cell.put("lat", lat);
                cell.put("value", value);
                cells.add(cell);
                sleep(intervalMs);
            }
        }

        Map<String, Object> grid = new LinkedHashMap<>();
        grid.put("west", west);
        grid.put("east", east);
        grid.put("south", south);
        grid.put("north", north);
        grid.put("lngs", lngs);
        grid.put("lats", lats);
        grid.put("product", product);
        grid.put("cells", cells);
        return grid;
    }

    @SuppressWarnings("unchecked")
    private Double fetchProductValue(double lng, double lat, String product) {
        Map<String, Object> resp = weatherService.getWeatherByCoordinates(lng, lat);
        if (Boolean.TRUE.equals(resp.get("error"))) {
            return null;
        }
        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return null;
        }
        return switch (product) {
            case "wind" -> toDouble(data.get("windSpeed"));
            case "visibility" -> toDouble(data.get("visibility"));
            case "precip" -> toDouble(data.get("precipitation"));
            case "humidity" -> toDouble(data.get("humidity"));
            case "temperature" -> toDouble(data.get("temperature"));
            default -> toDouble(data.get("temperature"));
        };
    }

    private void sleep(long intervalMs) {
        if (intervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
