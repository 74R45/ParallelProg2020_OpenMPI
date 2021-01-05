package x74r45.practical4;

import mpi.MPI;
import mpi.MPIException;

import java.util.Arrays;
import java.util.Random;

/**
 * A simple bcast() program.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Task1 {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        // Initial data
        int myRank = MPI.COMM_WORLD.getRank();
        int n = Integer.parseInt(args[0]);
        int[] arr = new int[n];

        // Generating input array
        if (myRank == 2) {
            Random rand = new Random();
            for (int i = 0; i < n; i++) arr[i] = rand.nextInt(100);
            System.out.print("rank = " + myRank + ": arr = " + Arrays.toString(arr) + '\n');
        }

        // Copying array values to all other processors
        MPI.COMM_WORLD.barrier();
        MPI.COMM_WORLD.bcast(arr, n, MPI.INT, 2);
        System.out.print("rank = " + myRank + ": arr = " + Arrays.toString(arr) + '\n');

        MPI.Finalize();
    }
}
