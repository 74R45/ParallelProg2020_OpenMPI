package x74r45.practical4;

import mpi.MPI;
import mpi.MPIException;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * A simple scatterv() program.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Task4 {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int myRank = MPI.COMM_WORLD.getRank();
        int np = MPI.COMM_WORLD.getSize();

        // Generating input and calculating sendcount and displs
        int[] sendcount = IntStream.range(0, np).map(x -> (int) Math.pow(2, x)).toArray();
        int[] displs = IntStream.range(0, np).map(x -> {
            int displ = 0;
            for (int i = 0; i < x; i++) displ += sendcount[i];
            return displ;
        }).toArray();
        int[] arr = new int[Arrays.stream(sendcount).sum()];
        if (myRank == 2) {
            Random rand = new Random();
            for (int i = 0; i < arr.length; i++) arr[i] = rand.nextInt(100);
            System.out.print("rank = " + myRank + ": a = " + Arrays.toString(arr) + '\n');
        }

        // Scattering the input between all processors
        int[] received = new int[sendcount[myRank]];
        MPI.COMM_WORLD.barrier();
        MPI.COMM_WORLD.scatterv(arr, sendcount, displs, MPI.INT, received,
                sendcount[myRank], MPI.INT, 2);
        System.out.print("rank = " + myRank + ": received = " + Arrays.toString(received) + '\n');

        MPI.Finalize();
    }
}
