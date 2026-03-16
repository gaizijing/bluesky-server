package com.bluesky.service;

import com.bluesky.common.ResultCode;
import com.bluesky.entity.Bounds;
import com.bluesky.entity.wind.WindComponent;
import com.bluesky.entity.wind.WindData;
import com.bluesky.entity.wind.WindLayer;
import com.bluesky.exception.BusinessException;
import com.bluesky.netcdf.NetcdfWindReader;
import com.bluesky.service.WindDataSourceService.WindSourceFiles;
import com.bluesky.util.WindInterpolator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WindFieldService {

    private static final DateTimeFormatter OUTPUT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WindDataSourceService windDataSourceService;
    private final NetcdfWindReader netcdfWindReader = new NetcdfWindReader();

    @Value("${wind.field.heights:10}")
    private String heightsConfig;

    @Value("${wind.field.interpolation-scale:1}")
    private int interpolationScale;

    @Value("${wind.field.output-width:64}")
    private int outputWidth;

    @Value("${wind.field.output-height:64}")
    private int outputHeight;

    @Value("${wind.field.bounds.west:119.0}")
    private double defaultWest;

    @Value("${wind.field.bounds.east:122.0}")
    private double defaultEast;

    @Value("${wind.field.bounds.south:35.0}")
    private double defaultSouth;

    @Value("${wind.field.bounds.north:37.0}")
    private double defaultNorth;

    public Map<String, Object> getWindField(String boundsParam) {
        Bounds bounds = parseBounds(boundsParam);
        List<Integer> targetHeights = parseHeights();

        WindSourceFiles sourceFiles = windDataSourceService.ensureSourceFiles();
        String uFile = sourceFiles.getUFile().toString();
        String vFile = sourceFiles.getVFile().toString();

        int timeIndex;
        try {
            timeIndex = netcdfWindReader.resolveTimeIndex(uFile, null);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Failed to resolve latest wind field time: " + e.getMessage());
        }

        LocalDateTime dataTime;
        try {
            dataTime = netcdfWindReader.readTimeAtIndex(uFile, timeIndex);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Failed to read wind field time: " + e.getMessage());
        }

        double[] latAxis;
        double[] lonAxis;
        double[] levelAxis;
        try {
            latAxis = netcdfWindReader.readLatitudeAxis(uFile);
            lonAxis = netcdfWindReader.readLongitudeAxis(uFile);
            levelAxis = netcdfWindReader.readLevelAxis(uFile);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Failed to read axis from wind file: " + e.getMessage());
        }

        List<WindLayer> layers = new ArrayList<>();
        List<Map<String, Object>> layerSources = new ArrayList<>();
        int scale = Math.max(1, interpolationScale);

        for (int height : targetHeights) {
            Integer levelIndex = null;
            Double matchedLevel = null;
            if (levelAxis != null && levelAxis.length > 0) {
                int idx = netcdfWindReader.resolveNearestLevelIndex(levelAxis, height);
                levelIndex = idx;
                matchedLevel = levelAxis[idx];
            }

            WindData windData = buildLayerData(uFile, vFile, timeIndex, levelIndex, bounds, latAxis, lonAxis, scale);
            layers.add(new WindLayer(height, windData));

            Map<String, Object> src = new HashMap<>();
            src.put("height", height);
            src.put("levelIndex", levelIndex);
            src.put("matchedLevel", matchedLevel);
            src.put("uFile", sourceFiles.getUFile().toAbsolutePath().normalize().toString());
            src.put("vFile", sourceFiles.getVFile().toAbsolutePath().normalize().toString());
            layerSources.add(src);
        }

        Map<String, Object> source = new HashMap<>();
        source.put("timeIndex", timeIndex);
        source.put("updateTime", dataTime == null ? null : dataTime.format(OUTPUT_TIME_FORMATTER));
        source.put("layers", layerSources);

        Map<String, Object> result = new HashMap<>();
        result.put("time", dataTime == null ? null : dataTime.format(OUTPUT_TIME_FORMATTER));
        result.put("timeIndex", timeIndex);
        result.put("bounds", bounds);
        result.put("heights", targetHeights);
        result.put("layers", layers);
        result.put("source", source);
        result.put("dataType", "real");
        return result;
    }

    private WindData buildLayerData(
            String uFile,
            String vFile,
            int timeIndex,
            Integer levelIndex,
            Bounds bounds,
            double[] latAxis,
            double[] lonAxis,
            int scale) {

        try {
            double[][] uGrid = netcdfWindReader.readUGrid(uFile, timeIndex, levelIndex);
            double[][] vGrid = netcdfWindReader.readVGrid(vFile, timeIndex, levelIndex);

            if (uGrid.length == 0 || uGrid[0].length == 0) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Loaded u-grid is empty");
            }
            if (uGrid.length != vGrid.length || uGrid[0].length != vGrid[0].length) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "U/V grid dimensions do not match");
            }

            double[] lat = latAxis;
            double[] lon = lonAxis;
            if (scale > 1) {
                uGrid = WindInterpolator.interpolateGrid(uGrid, scale);
                vGrid = WindInterpolator.interpolateGrid(vGrid, scale);
                lat = netcdfWindReader.interpolateAxis(latAxis, scale);
                lon = netcdfWindReader.interpolateAxis(lonAxis, scale);
            }

            GridSlice region = extractRegion(uGrid, vGrid, lat, lon, bounds);
            GridSlice output = resample(region, Math.max(2, outputWidth), Math.max(2, outputHeight));
            return buildWindData(output);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Failed to build wind layer: " + e.getMessage());
        }
    }

    private GridSlice extractRegion(
            double[][] uGrid,
            double[][] vGrid,
            double[] latAxis,
            double[] lonAxis,
            Bounds bounds) {

        if (latAxis.length != uGrid.length || lonAxis.length != uGrid[0].length) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Grid size does not match lat/lon axis");
        }

        double west360 = normalizeLon360(bounds.getWest());
        double east360 = normalizeLon360(bounds.getEast());
        if (west360 > east360) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "bounds west/east cannot cross 180 longitude");
        }

        int westIndex = nearestLonIndex(lonAxis, west360);
        int eastIndex = nearestLonIndex(lonAxis, east360);
        int xStart = Math.min(westIndex, eastIndex);
        int xEnd = Math.max(westIndex, eastIndex);
        if (xStart == xEnd) {
            if (xEnd < lonAxis.length - 1) {
                xEnd++;
            } else if (xStart > 0) {
                xStart--;
            }
        }

        int northIndex = nearestLatIndex(latAxis, bounds.getNorth());
        int southIndex = nearestLatIndex(latAxis, bounds.getSouth());
        int yStart = Math.min(northIndex, southIndex);
        int yEnd = Math.max(northIndex, southIndex);
        if (yStart == yEnd) {
            if (yEnd < latAxis.length - 1) {
                yEnd++;
            } else if (yStart > 0) {
                yStart--;
            }
        }

        int width = xEnd - xStart + 1;
        int height = yEnd - yStart + 1;
        double[][] uSlice = new double[height][width];
        double[][] vSlice = new double[height][width];
        double[] latSlice = new double[height];
        double[] lonSlice = new double[width];

        for (int x = 0; x < width; x++) {
            lonSlice[x] = normalizeLon180(lonAxis[xStart + x]);
        }
        for (int y = 0; y < height; y++) {
            latSlice[y] = latAxis[yStart + y];
            System.arraycopy(uGrid[yStart + y], xStart, uSlice[y], 0, width);
            System.arraycopy(vGrid[yStart + y], xStart, vSlice[y], 0, width);
        }

        return new GridSlice(uSlice, vSlice, latSlice, lonSlice);
    }

    private GridSlice resample(GridSlice source, int targetWidth, int targetHeight) {
        int srcHeight = source.u.length;
        int srcWidth = source.u[0].length;
        if (srcHeight == targetHeight && srcWidth == targetWidth) {
            return source;
        }

        double[][] outU = new double[targetHeight][targetWidth];
        double[][] outV = new double[targetHeight][targetWidth];
        for (int y = 0; y < targetHeight; y++) {
            double srcY = targetHeight == 1 ? 0 : ((double) y * (srcHeight - 1) / (targetHeight - 1));
            for (int x = 0; x < targetWidth; x++) {
                double srcX = targetWidth == 1 ? 0 : ((double) x * (srcWidth - 1) / (targetWidth - 1));
                outU[y][x] = bilinearWithNaN(source.u, srcX, srcY);
                outV[y][x] = bilinearWithNaN(source.v, srcX, srcY);
            }
        }

        double[] outLat = resampleAxis(source.latAxis, targetHeight);
        double[] outLon = resampleAxis(source.lonAxis, targetWidth);
        return new GridSlice(outU, outV, outLat, outLon);
    }

    private WindData buildWindData(GridSlice output) {
        double[][] speedGrid = new double[output.u.length][output.u[0].length];
        for (int y = 0; y < output.u.length; y++) {
            for (int x = 0; x < output.u[0].length; x++) {
                double u = output.u[y][x];
                double v = output.v[y][x];
                speedGrid[y][x] = (Double.isNaN(u) || Double.isNaN(v)) ? Double.NaN : Math.hypot(u, v);
            }
        }

        WindComponent uComp = buildComponent(output.u);
        WindComponent vComp = buildComponent(output.v);
        WindComponent speedComp = buildComponent(speedGrid);

        double northLat = max(output.latAxis);
        double southLat = min(output.latAxis);
        double westLon = Math.min(output.lonAxis[0], output.lonAxis[output.lonAxis.length - 1]);
        double eastLon = Math.max(output.lonAxis[0], output.lonAxis[output.lonAxis.length - 1]);
        Bounds bounds = new Bounds(westLon, southLat, eastLon, northLat);

        return new WindData(uComp, vComp, speedComp, output.u[0].length, output.u.length, bounds);
    }

    private WindComponent buildComponent(double[][] grid) {
        List<Double> values = new ArrayList<>(grid.length * grid[0].length);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (double[] row : grid) {
            for (double value : row) {
                values.add(value);
                if (Double.isNaN(value)) {
                    continue;
                }
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        if (Double.isInfinite(min)) {
            min = Double.NaN;
            max = Double.NaN;
        }
        return new WindComponent(values, min, max);
    }

    private double bilinearWithNaN(double[][] grid, double srcX, double srcY) {
        int x0 = (int) Math.floor(srcX);
        int y0 = (int) Math.floor(srcY);
        int x1 = Math.min(x0 + 1, grid[0].length - 1);
        int y1 = Math.min(y0 + 1, grid.length - 1);

        double tx = srcX - x0;
        double ty = srcY - y0;

        double q11 = grid[y0][x0];
        double q21 = grid[y0][x1];
        double q12 = grid[y1][x0];
        double q22 = grid[y1][x1];

        double w11 = (1 - tx) * (1 - ty);
        double w21 = tx * (1 - ty);
        double w12 = (1 - tx) * ty;
        double w22 = tx * ty;

        double weighted = 0.0;
        double weightSum = 0.0;
        if (!Double.isNaN(q11)) {
            weighted += q11 * w11;
            weightSum += w11;
        }
        if (!Double.isNaN(q21)) {
            weighted += q21 * w21;
            weightSum += w21;
        }
        if (!Double.isNaN(q12)) {
            weighted += q12 * w12;
            weightSum += w12;
        }
        if (!Double.isNaN(q22)) {
            weighted += q22 * w22;
            weightSum += w22;
        }
        return weightSum == 0.0 ? Double.NaN : weighted / weightSum;
    }

    private double[] resampleAxis(double[] axis, int targetSize) {
        if (axis.length == targetSize) {
            return axis;
        }
        double[] out = new double[targetSize];
        for (int i = 0; i < targetSize; i++) {
            double src = targetSize == 1 ? 0 : ((double) i * (axis.length - 1) / (targetSize - 1));
            int i0 = (int) Math.floor(src);
            int i1 = Math.min(i0 + 1, axis.length - 1);
            double t = src - i0;
            out[i] = axis[i0] * (1 - t) + axis[i1] * t;
        }
        return out;
    }

    private Bounds parseBounds(String boundsParam) {
        if (boundsParam == null || boundsParam.isBlank()) {
            return new Bounds(defaultWest, defaultSouth, defaultEast, defaultNorth);
        }

        String raw = boundsParam.trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "bounds format should be: west,south,east,north");
        }

        try {
            double west = Double.parseDouble(parts[0].trim());
            double south = Double.parseDouble(parts[1].trim());
            double east = Double.parseDouble(parts[2].trim());
            double north = Double.parseDouble(parts[3].trim());
            if (west >= east || south >= north) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "invalid bounds range");
            }
            return new Bounds(west, south, east, north);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "bounds contains invalid number");
        }
    }

    private List<Integer> parseHeights() {
        String raw = heightsConfig == null ? "" : heightsConfig.trim();
        if (raw.isEmpty()) {
            return List.of(10);
        }

        String[] parts = raw.split(",");
        Set<Integer> set = new LinkedHashSet<>();
        for (String part : parts) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                int height = Integer.parseInt(text);
                if (height <= 0) {
                    throw new NumberFormatException("height must be positive");
                }
                set.add(height);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid height config: " + text);
            }
        }

        if (set.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "wind.field.heights cannot be empty");
        }
        return List.copyOf(set);
    }

    private double normalizeLon360(double lon) {
        double out = lon % 360.0;
        return out < 0 ? out + 360.0 : out;
    }

    private double normalizeLon180(double lon) {
        double out = lon;
        while (out > 180.0) {
            out -= 360.0;
        }
        while (out <= -180.0) {
            out += 360.0;
        }
        return out;
    }

    private int nearestLonIndex(double[] lonAxis, double targetLon360) {
        int best = 0;
        double minDiff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < lonAxis.length; i++) {
            double diff = Math.abs(normalizeLon360(lonAxis[i]) - targetLon360);
            if (diff < minDiff) {
                minDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private int nearestLatIndex(double[] latAxis, double targetLat) {
        int best = 0;
        double minDiff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < latAxis.length; i++) {
            double diff = Math.abs(latAxis[i] - targetLat);
            if (diff < minDiff) {
                minDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private double min(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        for (double value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private double max(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static class GridSlice {
        private final double[][] u;
        private final double[][] v;
        private final double[] latAxis;
        private final double[] lonAxis;

        private GridSlice(double[][] u, double[][] v, double[] latAxis, double[] lonAxis) {
            this.u = u;
            this.v = v;
            this.latAxis = latAxis;
            this.lonAxis = lonAxis;
        }
    }
}
