package x74r45;

import mpi.MPI;
import mpi.MPIException;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Calculates matrix's norm in parallel.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Practical5_1 {

    /**
     * Runs the calculations and prints the result in the console.
     *
     * @param args           Contains one element - the size of the matrix.
     * @throws MPIException  If an error occurred during MPI communications.
     */
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        // Initial data
        int rank = MPI.COMM_WORLD.getRank();
        int np = MPI.COMM_WORLD.getSize();
        int ord = Integer.parseInt(args[0]);

        /*
         * I decided to represent matrix as a 1D array because this way
         * sending and receiving data would be the easiest and most efficient
         * (both time- and memory-wise).
         */
        int[] matrix = new int[ord * ord];
        if (rank == 0) {
            // Filling the matrix with random numbers
            Random rand = new Random();
            for (int i = 0; i < matrix.length; i++)
                matrix[i] = rand.nextInt(1000);

            System.out.println("Starting matrix: ");
            for (int y = 0; y < ord; y++) printPartOfArray(matrix, y * ord, (y+1) * ord);
            System.out.println();
        }

        // Calculating displacements
        float elemsPerProcessor = ((float) matrix.length) / np;
        int[] displs = IntStream.range(0, np).map(x -> Math.round(x * elemsPerProcessor)).toArray();
        int[] sendcount = IntStream.range(0, np)
                .map(x -> (x == np-1) ? matrix.length - displs[x] : displs[x + 1] - displs[x])
                .toArray();

        // Scattering equal (max diff = 1) parts of the matrix across all processors
        int[] chunk = new int[sendcount[rank]];
        MPI.COMM_WORLD.barrier();
        MPI.COMM_WORLD.scatterv(matrix, sendcount, displs, MPI.INT,
                chunk, sendcount[rank], MPI.INT, 0);
        System.out.print("rank = " + rank + "; chunk = " + Arrays.toString(chunk) + '\n');

        // Calculating sum of squared elements
        int myRes = Arrays.stream(chunk).map(x -> x * x).reduce(0, Integer::sum);
        System.out.print("rank = " + rank + "; myRes = " + myRes + '\n');

        // Sending the sum of results of all processors to the root
        int[] reduced = new int[1];
        MPI.COMM_WORLD.reduce(new int[]{myRes}, reduced, 1, MPI.INT, MPI.SUM, 0);

        if (rank == 0) {
            // Final step to calculate the norm
            double norm = Math.sqrt(reduced[0]);
            System.out.println("Reduced: " + reduced[0]);
            System.out.println("Matrix's norm is: " + norm);
        }

        MPI.Finalize();
    }

    // A helper function I used to print the 1D matrix prettier than just an array
    private static void printPartOfArray(int[] arr, int start, int end) {
        System.out.print('[');
        for (int i = start; i < end && i < arr.length; i++) {
            System.out.print(arr[i]);
            if (i != end-1 && i != arr.length-1)
                System.out.print(", ");
        }
        System.out.println(']');
    }
}
