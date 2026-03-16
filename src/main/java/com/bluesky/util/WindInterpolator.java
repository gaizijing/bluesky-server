package com.bluesky.util;

public class WindInterpolator {

    public static double[][] interpolateGrid(
            double[][] grid,
            int scale) {

        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            throw new IllegalArgumentException("Grid data is empty");
        }

        if (scale <= 1) {
            double[][] copy = new double[grid.length][grid[0].length];
            for (int y = 0; y < grid.length; y++) {
                System.arraycopy(grid[y], 0, copy[y], 0, grid[y].length);
            }
            return copy;
        }

        int srcH = grid.length;
        int srcW = grid[0].length;

        int dstH = (srcH - 1) * scale + 1;
        int dstW = (srcW - 1) * scale + 1;

        double[][] result = new double[dstH][dstW];

        for (int y = 0; y < dstH; y++) {

            double gy = (double) y / scale;

            int y0 = (int) Math.floor(gy);
            int y1 = Math.min(y0 + 1, srcH - 1);

            double ty = gy - y0;

            for (int x = 0; x < dstW; x++) {

                double gx = (double) x / scale;

                int x0 = (int) Math.floor(gx);
                int x1 = Math.min(x0 + 1, srcW - 1);

                double tx = gx - x0;

                double q11 = grid[y0][x0];
                double q21 = grid[y0][x1];
                double q12 = grid[y1][x0];
                double q22 = grid[y1][x1];

                result[y][x] = bilinear(q11, q21, q12, q22, tx, ty);
            }
        }

        return result;
    }

    private static double bilinear(
            double q11,
            double q21,
            double q12,
            double q22,
            double tx,
            double ty) {

        return q11 * (1 - tx) * (1 - ty)
                + q21 * tx * (1 - ty)
                + q12 * (1 - tx) * ty
                + q22 * tx * ty;
    }
}
