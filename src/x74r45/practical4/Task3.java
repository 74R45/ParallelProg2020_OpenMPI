package x74r45.practical4;

import mpi.MPI;
import mpi.MPIException;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * A simple gatherv() program.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Task3 {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        // Initial data
        int myRank = MPI.COMM_WORLD.getRank();
        int np = MPI.COMM_WORLD.getSize();
        int n = (myRank + 1) * 5;
        int[] arr = new int[n];

        // Generating input
        Random rand = new Random();
        for (int i = 0; i < n; i++) arr[i] = rand.nextInt(100);
        System.out.print("rank = " + myRank + ": arr = " + Arrays.toString(arr) + '\n');

        // Gathering different sizes of input from different processors
        int[] recvcount = IntStream.range(1, np + 1).map(x -> 5 * x).toArray();
        int[] displs = IntStream.range(0, np).map(x -> {
            int displ = 0;
            for (int i = 0; i < x; i++) displ += recvcount[i];
            return displ;
        }).toArray();
        int[] received = new int[Arrays.stream(recvcount).sum()];
        MPI.COMM_WORLD.gatherv(arr, n, MPI.INT, received, recvcount, displs, MPI.INT, 3);

        if (myRank == 3) {
            System.out.print("rank = " + myRank + ": received = " + Arrays.toString(received) + '\n');
        }

        MPI.Finalize();
    }
}
