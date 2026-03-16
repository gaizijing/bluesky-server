package com.bluesky.netcdf;

import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Index;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.units.DateUnit;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class NetcdfWindReader {

    private static final List<String> U_VARIABLE_CANDIDATES = Arrays.asList("uwnd", "UGRD", "u10", "u");
    private static final List<String> V_VARIABLE_CANDIDATES = Arrays.asList("vwnd", "VGRD", "v10", "v");
    private static final List<String> LAT_VARIABLE_CANDIDATES = Arrays.asList("lat", "latitude", "LAT");
    private static final List<String> LON_VARIABLE_CANDIDATES = Arrays.asList("lon", "longitude", "LON");
    private static final List<String> TIME_DIM_CANDIDATES = Arrays.asList("time", "Time");
    private static final List<String> LEVEL_DIM_CANDIDATES = Arrays.asList(
            "level", "lev", "isobaric", "isobaricInhPa", "pressure", "height", "altitude"
    );

    public double[][] readUGrid(String filePath, int timeIndex, Integer levelIndex) throws Exception {
        return readGrid(filePath, U_VARIABLE_CANDIDATES, timeIndex, levelIndex);
    }

    public double[][] readVGrid(String filePath, int timeIndex, Integer levelIndex) throws Exception {
        return readGrid(filePath, V_VARIABLE_CANDIDATES, timeIndex, levelIndex);
    }

    public int resolveTimeIndex(String filePath, LocalDateTime targetTime) throws Exception {
        try (NetcdfFile ncFile = NetcdfFile.open(filePath)) {
            Variable timeVar = ncFile.findVariable("time");
            if (timeVar == null || timeVar.getRank() == 0) {
                return 0;
            }

            int timeLength = timeVar.getShape()[0];
            if (timeLength <= 0) {
                return 0;
            }
            if (targetTime == null) {
                return timeLength - 1;
            }

            String units = timeVar.getUnitsString();
            if (units == null || units.isBlank()) {
                return timeLength - 1;
            }

            DateUnit dateUnit = new DateUnit(units);
            Array timeArray = timeVar.read();
            Index index = timeArray.getIndex();

            long targetMillis = targetTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long minDiff = Long.MAX_VALUE;
            int nearestIndex = 0;

            for (int i = 0; i < timeLength; i++) {
                double rawTime = timeArray.getDouble(index.set(i));
                Date date = dateUnit.makeDate(rawTime);
                long diff = Math.abs(date.getTime() - targetMillis);
                if (diff < minDiff) {
                    minDiff = diff;
                    nearestIndex = i;
                }
            }

            return nearestIndex;
        }
    }

    public LocalDateTime readTimeAtIndex(String filePath, int timeIndex) throws Exception {
        try (NetcdfFile ncFile = NetcdfFile.open(filePath)) {
            Variable timeVar = ncFile.findVariable("time");
            if (timeVar == null || timeVar.getRank() == 0) {
                return null;
            }

            int timeLength = timeVar.getShape()[0];
            if (timeLength <= 0) {
                return null;
            }

            int safeTimeIndex = clamp(timeIndex, 0, timeLength - 1);
            String units = timeVar.getUnitsString();
            if (units == null || units.isBlank()) {
                return null;
            }

            DateUnit dateUnit = new DateUnit(units);
            Array timeArray = timeVar.read();
            Index index = timeArray.getIndex();
            double rawTime = timeArray.getDouble(index.set(safeTimeIndex));
            Date date = dateUnit.makeDate(rawTime);
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
    }

    public double[] readLatitudeAxis(String filePath) throws Exception {
        return readAxis(filePath, LAT_VARIABLE_CANDIDATES);
    }

    public double[] readLongitudeAxis(String filePath) throws Exception {
        return readAxis(filePath, LON_VARIABLE_CANDIDATES);
    }

    public double[] readLevelAxis(String filePath) throws Exception {
        try (NetcdfFile ncFile = NetcdfFile.open(filePath)) {
            Variable direct = findVariable(ncFile, LEVEL_DIM_CANDIDATES);
            if (direct != null && direct.getRank() == 1) {
                return readAxisValues(direct);
            }

            Variable uVar = findVariable(ncFile, U_VARIABLE_CANDIDATES);
            if (uVar == null) {
                return new double[0];
            }

            int levelDimIndex = findDimensionIndexByName(uVar, LEVEL_DIM_CANDIDATES);
            if (levelDimIndex < 0) {
                return new double[0];
            }

            Dimension levelDim = uVar.getDimension(levelDimIndex);
            Variable levelVar = ncFile.findVariable(levelDim.getShortName());
            if (levelVar != null && levelVar.getRank() == 1) {
                return readAxisValues(levelVar);
            }

            double[] out = new double[levelDim.getLength()];
            for (int i = 0; i < out.length; i++) {
                out[i] = i;
            }
            return out;
        }
    }

    public int resolveNearestLevelIndex(double[] levelAxis, double targetLevel) {
        if (levelAxis == null || levelAxis.length == 0) {
            return 0;
        }
        int best = 0;
        double minDiff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < levelAxis.length; i++) {
            double diff = Math.abs(levelAxis[i] - targetLevel);
            if (diff < minDiff) {
                minDiff = diff;
                best = i;
            }
        }
        return best;
    }

    public double[] interpolateAxis(double[] axis, int scale) {
        if (axis == null || axis.length == 0) {
            throw new IllegalArgumentException("Axis data is empty");
        }
        if (scale <= 1 || axis.length == 1) {
            return Arrays.copyOf(axis, axis.length);
        }

        int outLength = (axis.length - 1) * scale + 1;
        double[] out = new double[outLength];
        for (int i = 0; i < outLength; i++) {
            double srcIndex = i / (double) scale;
            int i0 = (int) Math.floor(srcIndex);
            int i1 = Math.min(i0 + 1, axis.length - 1);
            double t = srcIndex - i0;
            out[i] = axis[i0] * (1 - t) + axis[i1] * t;
        }
        return out;
    }

    private double[][] readGrid(String filePath, List<String> variableCandidates, int timeIndex, Integer levelIndex)
            throws IOException, InvalidRangeException {

        try (NetcdfFile ncFile = NetcdfFile.open(filePath)) {
            Variable var = findVariable(ncFile, variableCandidates);
            if (var == null) {
                throw new IllegalArgumentException(
                        "Cannot find variable " + variableCandidates + " in " + filePath);
            }

            int rank = var.getRank();
            if (rank < 2) {
                throw new IllegalArgumentException(
                        "Variable " + var.getShortName() + " rank is less than 2: " + rank);
            }

            int latDim = findDimensionIndexByName(var, LAT_VARIABLE_CANDIDATES);
            int lonDim = findDimensionIndexByName(var, LON_VARIABLE_CANDIDATES);
            if (latDim < 0 || lonDim < 0) {
                latDim = rank - 2;
                lonDim = rank - 1;
            }
            if (latDim == lonDim) {
                throw new IllegalArgumentException("Cannot resolve latitude/longitude dimensions");
            }

            int timeDim = findDimensionIndexByName(var, TIME_DIM_CANDIDATES);
            int levelDim = findDimensionIndexByName(var, LEVEL_DIM_CANDIDATES);

            int[] fullShape = var.getShape();
            int[] origin = new int[rank];
            int[] section = Arrays.copyOf(fullShape, rank);

            for (int i = 0; i < rank; i++) {
                if (i == latDim || i == lonDim) {
                    origin[i] = 0;
                    section[i] = fullShape[i];
                } else if (i == timeDim) {
                    origin[i] = clamp(timeIndex, 0, fullShape[i] - 1);
                    section[i] = 1;
                } else if (i == levelDim) {
                    int safeLevel = levelIndex == null ? 0 : clamp(levelIndex, 0, fullShape[i] - 1);
                    origin[i] = safeLevel;
                    section[i] = 1;
                } else {
                    origin[i] = 0;
                    section[i] = 1;
                }
            }

            Array reduced = var.read(origin, section).reduce();
            if (reduced.getRank() != 2) {
                throw new IllegalArgumentException(
                        "Variable " + var.getShortName() + " slice rank is not 2, actual: " + reduced.getRank());
            }

            boolean latFirst = latDim < lonDim;
            int latSize = fullShape[latDim];
            int lonSize = fullShape[lonDim];
            double[][] grid = new double[latSize][lonSize];

            double missingValue = getAttributeAsDouble(var, "missing_value");
            double fillValue = getAttributeAsDouble(var, "_FillValue");

            Index index = reduced.getIndex();
            if (latFirst) {
                for (int y = 0; y < latSize; y++) {
                    for (int x = 0; x < lonSize; x++) {
                        double value = reduced.getDouble(index.set(y, x));
                        grid[y][x] = sanitize(value, missingValue, fillValue);
                    }
                }
            } else {
                for (int y = 0; y < latSize; y++) {
                    for (int x = 0; x < lonSize; x++) {
                        double value = reduced.getDouble(index.set(x, y));
                        grid[y][x] = sanitize(value, missingValue, fillValue);
                    }
                }
            }

            return grid;
        }
    }

    private Variable findVariable(NetcdfFile ncFile, List<String> candidates) {
        for (String candidate : candidates) {
            Variable variable = ncFile.findVariable(candidate);
            if (variable != null) {
                return variable;
            }
        }
        return null;
    }

    private int findDimensionIndexByName(Variable var, List<String> candidates) {
        for (int i = 0; i < var.getRank(); i++) {
            String dimName = var.getDimension(i).getShortName();
            if (matchesAny(dimName, candidates)) {
                return i;
            }
        }
        return -1;
    }

    private boolean matchesAny(String name, List<String> candidates) {
        String n = name == null ? "" : name.trim().toLowerCase();
        for (String candidate : candidates) {
            String c = candidate.toLowerCase();
            if (n.equals(c) || n.contains(c)) {
                return true;
            }
        }
        return false;
    }

    private double[] readAxis(String filePath, List<String> variableCandidates) throws Exception {
        try (NetcdfFile ncFile = NetcdfFile.open(filePath)) {
            Variable axisVar = findVariable(ncFile, variableCandidates);
            if (axisVar == null) {
                throw new IllegalArgumentException(
                        "Cannot find axis variable " + variableCandidates + " in " + filePath);
            }
            if (axisVar.getRank() != 1) {
                throw new IllegalArgumentException(
                        "Axis variable " + axisVar.getShortName() + " rank is not 1: " + axisVar.getRank());
            }
            return readAxisValues(axisVar);
        }
    }

    private double[] readAxisValues(Variable axisVar) throws IOException {
        Array axisArray = axisVar.read();
        Index index = axisArray.getIndex();
        int length = axisVar.getShape()[0];
        double[] axis = new double[length];
        for (int i = 0; i < length; i++) {
            axis[i] = axisArray.getDouble(index.set(i));
        }
        return axis;
    }

    private double getAttributeAsDouble(Variable variable, String attributeName) {
        Attribute attribute = variable.findAttributeIgnoreCase(attributeName);
        if (attribute == null || attribute.getNumericValue() == null) {
            return Double.NaN;
        }
        return attribute.getNumericValue().doubleValue();
    }

    private double sanitize(double value, double missingValue, double fillValue) {
        if (isMissing(value, missingValue, fillValue)) {
            return Double.NaN;
        }
        return value;
    }

    private boolean isMissing(double value, double missingValue, double fillValue) {
        if (Math.abs(value) > 1.0E35) {
            return true;
        }
        return isApproximatelyEqual(value, missingValue) || isApproximatelyEqual(value, fillValue);
    }

    private boolean isApproximatelyEqual(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }
        double tolerance = 1.0E-6 * Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= tolerance;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
