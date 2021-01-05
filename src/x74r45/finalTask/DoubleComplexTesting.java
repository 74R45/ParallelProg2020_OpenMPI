package x74r45.finalTask;

import mpi.DoubleComplex;
import mpi.MPI;
import mpi.MPIException;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * This class tests mpi.DoubleComplex and shows an example of its usage.
 * Testing involves running all available methods and printing the results.
 * The example is a program that computes in parallel a fragment of the
 * Mandelbrot set and renders it. Can be run with any amount of processors.
 * Run this class with the following arguments:
 * <ul>
 *     <li>real0 - The real value of the bottom left coordinate.</li>
 *     <li>imag0 - The imaginary value of the bottom left coordinate.</li>
 *     <li>size  - The size of the fragment of the Mandelbrot set to render.</li>
 * </ul>
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class DoubleComplexTesting {
    private static final int IMAGE_SIZE = 200;
    private static final boolean DEBUG_MODE = false;
    private static int rank;
    private static int np;
    private static double real0;
    private static double imag0;
    private static double size;

    /**
     * Runs the program. Prints the results in the console and renders the
     * Mandelbrot set in a new window.
     *
     * @param args           Coordinates of the bottom left corner of the
     *                       fragment of the Mandelbrot set to render and
     *                       its size: {real0, imag0, size}.
     * @throws MPIException  If an error occurred during MPI communications.
     */
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        // Initial data
        rank = MPI.COMM_WORLD.getRank();
        np = MPI.COMM_WORLD.getSize();
        real0 = Double.parseDouble(args[0]);
        imag0 = Double.parseDouble(args[1]);
        size = Double.parseDouble(args[2]);

        if (rank == 0) {
            testDoubleComplex();
            System.out.println("------------------------------------");
        }
        runMandelbrot();

        MPI.Finalize();
    }

    private static void runMandelbrot() throws MPIException {
        // Generating a 1D matrix with the complex numbers
        DoubleBuffer matrixBuf = DoubleBuffer.allocate(IMAGE_SIZE*IMAGE_SIZE*2);
        DoubleComplex[] matrix = new DoubleComplex[IMAGE_SIZE*IMAGE_SIZE];
        if (rank == 0) {
            System.out.println("Using class DoubleComplex to calculate a section of a Mandelbrot set in parallel.");

            double step = size / IMAGE_SIZE;
            for (int y = 0; y < IMAGE_SIZE; y++)
                for (int x = 0; x < IMAGE_SIZE; x++) {
                    matrixBuf.put(real0 + step * x).put(imag0 + step * y);
                    int i = y * IMAGE_SIZE + x;
                    // Using method get(DoubleBuffer buffer, int index) to set the corresponding
                    // complex numbers to their locations in the buffer
                    matrix[i] = DoubleComplex.get(matrixBuf, i);
                }

            if (DEBUG_MODE) {
                System.out.println("Starting matrix: ");
                for (int y = 0; y < IMAGE_SIZE; y++) printPartOfArray(matrix, y * IMAGE_SIZE, (y + 1) * IMAGE_SIZE);
                System.out.println();
            } else System.out.println("Finished generating the matrix.");
        }

        // Calculating displacements
        float elemsPerProcessor = ((float) matrix.length) / np;
        int[] displs = IntStream.range(0, np).map(x -> Math.round(x * elemsPerProcessor)).toArray();
        int[] sendcount = IntStream.range(0, np)
                .map(x -> (x == np-1) ? matrix.length - displs[x] : displs[x + 1] - displs[x]).toArray();

        // Scattering equal (max diff = 1) parts of the matrix across all processors
        DoubleBuffer chunkBuf = DoubleBuffer.allocate(sendcount[rank]*2);
        DoubleComplex[] chunk = new DoubleComplex[sendcount[rank]];
        MPI.COMM_WORLD.barrier();
        MPI.COMM_WORLD.scatterv(matrixBuf, sendcount, displs, MPI.DOUBLE_COMPLEX,
                chunkBuf, sendcount[rank], MPI.DOUBLE_COMPLEX, 0);
        // Just as before, using the same get method to fill the array of DoubleComplex
        for (int i = 0; i < sendcount[rank]; i++) chunk[i] = DoubleComplex.get(chunkBuf, i);

        if (DEBUG_MODE) System.out.print("rank = " + rank + "; chunk = " + Arrays.toString(chunkBuf.array()) + '\n');
        else System.out.print("Pr" + rank + " received a chunk.\n");

        // Calculating mandelbrot iterations on each processor
        // Using methods getReal() and getImag() to retrieve real and imaginary
        // parts of complex numbers and pass them to Mandelbrot::iterations
        int[] myRes = Arrays.stream(chunk).mapToInt(c -> Mandelbrot.iterations(c.getReal(), c.getImag())).toArray();

        if (DEBUG_MODE) System.out.print("rank = " + rank + "; myRes = " + Arrays.toString(myRes) + '\n');
        else System.out.print("Pr" + rank + " calculated iterations.\n");

        // Sending the results back to the root
        int[] pixelIters = new int[IMAGE_SIZE*IMAGE_SIZE];
        MPI.COMM_WORLD.gatherv(myRes, myRes.length, MPI.INT,
                pixelIters, sendcount, displs, MPI.INT, 0);

        if (rank == 0) {
            System.out.println("Results received.");

            // Rendering the result
            int[] pixels = Arrays.stream(pixelIters).map(m -> Color.HSBtoRGB(
                    ((float)m)/Mandelbrot.MAX_ITER, 1f, (m < Mandelbrot.MAX_ITER)?1f:0f)).toArray();
            BufferedImage img = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < IMAGE_SIZE; y++)
                for (int x = 0; x < IMAGE_SIZE; x++)
                    img.setRGB(x, IMAGE_SIZE - y - 1, pixels[y*IMAGE_SIZE + x]);

            System.out.println("Rendering...");
            renderImage(img, "(" + real0 + ", " + imag0 + ", " + size + ')');
        }
    }

    // Rendering Mandelbrot set using Java Swing
    private static void renderImage(BufferedImage img, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        JComponent component = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(img, 0, 0, null);
            }
        };
        component.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        frame.add(component, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
        System.out.println("Close the window to stop.");
    }

    private static void testDoubleComplex() {
        System.out.println("Testing DoubleComplex.");
        // Generating input
        double[] numbers = {1d, -1d, 2d, -2d};
        DoubleBuffer dBuffer = DoubleBuffer.wrap(numbers);
        ByteBuffer bBuffer = ByteBuffer.allocate(numbers.length * 8);
        Arrays.stream(numbers).forEach(bBuffer::putDouble);
        bBuffer.rewind();
        System.out.println("Input: " + Arrays.toString(numbers));

        System.out.println("\nTesting get methods.");
        // Using variations of method get to retrieve the number in different ways.
        printDC("get(double[] array): ", DoubleComplex.get(numbers));
        printDC("get(double[] array, int index): ", DoubleComplex.get(numbers, 1));
        printDC("get(DoubleBuffer buffer): ", DoubleComplex.get(dBuffer));
        printDC("get(DoubleBuffer buffer, int index): ", DoubleComplex.get(dBuffer, 1));
        printDC("get(ByteBuffer buffer): ", DoubleComplex.get(bBuffer));
        printDC("get(ByteBuffer buffer, int index): ", DoubleComplex.get(bBuffer, 1));

        System.out.println("\nTesting putReal, putImag, getBuffer.");
        // Taking the original number (from previous section),
        // changing its values and getting the buffer.
        DoubleComplex dc = DoubleComplex.get(numbers);
        printDC("Before: ", dc);
        dc.putReal(13d);
        dc.putImag(37d);
        printDC("After: ", dc);
        System.out.println("dc's buffer: " + Arrays.toString(dc.getBuffer().array()));
    }

    // A helper function to print the 1D matrix prettier
    private static void printPartOfArray(DoubleComplex[] arr, int start, int end) {
        System.out.print('[');
        for (int i = start; i < end && i < arr.length; i++) {
            System.out.print("(" + arr[i].getReal() + ',' + arr[i].getImag() + ')');
            if (i != end-1 && i != arr.length-1)
                System.out.print(", ");
        }
        System.out.println(']');
    }

    private static void printDC(String prefix, DoubleComplex dc) {
        System.out.print(prefix);
        printDC(dc);
    }

    private static void printDC(DoubleComplex dc) {
        System.out.print("(" + dc.getReal() + ", " + dc.getImag() + ")\n");
    }
}

/* --------- Output example ---------
 * mpirun --hostfile /home/x74r45/hostfile -np 16 java -cp /home/x74r45/IdeaProjects/OpenMPI_Homeworks/target/classes/ x74r45/finalTask/DoubleComplexTesting -0.168 1.0407 0.0013
 * Testing DoubleComplex.
 * Input: [1.0, -1.0, 2.0, -2.0]
 *
 * Testing get methods.
 * get(double[] array): (1.0, -1.0)
 * get(double[] array, int index): (2.0, -2.0)
 * get(DoubleBuffer buffer): (1.0, -1.0)
 * get(DoubleBuffer buffer, int index): (2.0, -2.0)
 * get(ByteBuffer buffer): (1.0, -1.0)
 * get(ByteBuffer buffer, int index): (2.0, -2.0)
 *
 * Testing putReal, putImag, getBuffer.
 * Before: (1.0, -1.0)
 * After: (13.0, 37.0)
 * dc's buffer: [13.0, 37.0, 2.0, -2.0]
 * ------------------------------------
 * Using class DoubleComplex to calculate a section of a Mandelbrot set in parallel.
 * Finished generating the matrix.
 * Pr1 received a chunk.
 * Pr2 received a chunk.
 * Pr3 received a chunk.
 * Pr4 received a chunk.
 * Pr5 received a chunk.
 * Pr1 calculated iterations.
 * Pr6 received a chunk.
 * Pr2 calculated iterations.
 * Pr7 received a chunk.
 * Pr3 calculated iterations.
 * Pr4 calculated iterations.
 * Pr8 received a chunk.
 * Pr9 received a chunk.
 * Pr5 calculated iterations.
 * Pr10 received a chunk.
 * Pr11 received a chunk.
 * Pr12 received a chunk.
 * Pr8 calculated iterations.
 * Pr13 received a chunk.
 * Pr14 received a chunk.
 * Pr7 calculated iterations.
 * Pr6 calculated iterations.
 * Pr9 calculated iterations.
 * Pr15 received a chunk.
 * Pr12 calculated iterations.
 * Pr10 calculated iterations.
 * Pr11 calculated iterations.
 * Pr13 calculated iterations.
 * Pr15 calculated iterations.
 * Pr14 calculated iterations.
 * Pr0 received a chunk.
 * Pr0 calculated iterations.
 * Results received.
 * Rendering...
 * Close the window to stop.
 */