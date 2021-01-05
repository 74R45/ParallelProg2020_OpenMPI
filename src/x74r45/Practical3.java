package x74r45;

import mpi.MPI;
import mpi.MPIException;

import java.nio.IntBuffer;

/**
 * A very simple OpenMPI program that sends a number between
 * processors in form of a chain: 0 -> 1 -> ... -> np-1 -> 0
 *
 * The number is being changed depending on whether processor's
 * rank is even.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Practical3 {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.getRank();
        int np = MPI.COMM_WORLD.getSize();
        MPI.COMM_WORLD.barrier();

        if (rank == 0) {
            IntBuffer buf = MPI.newIntBuffer(1);
            buf.put(0, Integer.parseInt(args[0]));
            int receiver = (np > 1) ? 1 : 0;
            MPI.COMM_WORLD.iSend(buf, 1, MPI.INT, receiver, 74);

            IntBuffer out = MPI.newIntBuffer(1);
            MPI.COMM_WORLD.recv(out, 1, MPI.INT, np-1, 74);
            System.out.println("Final number: " + out.get(0));
        } else {
            IntBuffer num = MPI.newIntBuffer(1);
            MPI.COMM_WORLD.recv(num, 1, MPI.INT, rank-1, 74);

            int prev = num.get(0);
            int next = (rank % 2 == 0) ? prev + rank : prev + rank * 10;
            num.put(0, next);

            int receiver = (rank + 1) % np;
            MPI.COMM_WORLD.send(num, 1, MPI.INT, receiver, 74);
        }
        MPI.Finalize();
    }
}
