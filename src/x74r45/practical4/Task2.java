package x74r45.practical4;

import mpi.MPI;

import java.util.Arrays;
import java.util.Random;

/**
 * A simple gather() program.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Task2 {
    public static void main(String[] args) throws Exception{
        MPI.Init(args);

        // Initial data
        int myRank = MPI.COMM_WORLD.getRank();
        int np = MPI.COMM_WORLD.getSize();
        int n = Integer.parseInt(args[0]);
        int[] arr = new int[n];

        // Generating input
        Random rand = new Random();
        for (int i = 0; i < n; i++) arr[i] = rand.nextInt(100);
        System.out.print("rank = " + myRank + ": arr = " + Arrays.toString(arr) + '\n');

        // Sending the input from all processors to one (with rank == 1)
        int[] received = new int[n * np];
        MPI.COMM_WORLD.gather(arr, n, MPI.INT, received, n, MPI.INT, 1);
        if (myRank == 1) {
            System.out.print("rank = " + myRank + ": received = " + Arrays.toString(received) + '\n');
        }

        MPI.Finalize();
    }
}
