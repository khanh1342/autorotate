package com.autorotate.solver;

public record Bias(Double x, Double y, Double z) {

    public static final Bias NONE = new Bias(null, null, null);

    public static Bias ofX(double x) {
        return new Bias(x, null, null);
    }

    public static Bias ofY(double y) {
        return new Bias(null, y, null);
    }

    public static Bias ofZ(double z) {
        return new Bias(null, null, z);
    }

    public boolean isEmpty() {
        return x == null && y == null && z == null;
    }
}
