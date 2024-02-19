package de.labystudio.game.util;

public class MathHelper {

    /**
     * Returns the greatest integer less than or equal to the double argument
     */
    public static int floor_double(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /**
     * Linearly interpolates between two values.
     *
     * @param start   The starting value.
     * @param end     The ending value.
     * @param percent The interpolation factor (between 0.0 and 1.0).
     * @return The interpolated value.
     */
    public static float lerp(float start, float end, float percent) {
        return start + percent * (end - start);
    }

    /**
     * Linearly interpolates between two values.
     *
     * @param start   The starting value.
     * @param end     The ending value.
     * @param percent The interpolation factor (between 0.0 and 1.0).
     * @return The interpolated value.
     */
    public static double lerp(double start, double end, double percent) {
        return start + percent * (end - start);
    }
}