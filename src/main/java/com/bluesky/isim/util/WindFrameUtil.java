package com.bluesky.isim.util;

/**
 * 风场坐标转换：NetCDF 格点为东/北向 U/V，ISIM WeatherBridge 需要机体轴 X/Y/Z。
 */
public final class WindFrameUtil {

    private WindFrameUtil() {
    }

    /** 东/北/上 (m/s) + 真航向(°) → 机体轴 X/Y/Z (m/s) */
    public static double[] enuToBody(double uEast, double vNorth, double wUp, double headingDeg) {
        double h = Math.toRadians(headingDeg);
        double sin = Math.sin(h);
        double cos = Math.cos(h);
        double bodyX = vNorth * cos + uEast * sin;
        double bodyY = -vNorth * sin + uEast * cos;
        return new double[]{bodyX, bodyY, wUp};
    }
}
