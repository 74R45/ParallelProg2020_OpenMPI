package x74r45.finalTask;

/**
 * Computes the number of iterations for a complex number
 * to determine whether it's included in the Mandelbrot set.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Mandelbrot {
    public static final int MAX_ITER = 1000;

    public static int iterations(double real, double imag) {
        double x, y, x2, y2;
        x = y = x2 = y2 = 0d;
        int iter = 0;
        while (x2 + y2 <= 4 && iter < MAX_ITER) {
            y = 2*x*y + imag;
            x = x2 - y2 + real;
            x2 = x * x;
            y2 = y * y;
            iter++;
        }
        return iter;
    }
}
