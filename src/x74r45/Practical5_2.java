package x74r45;

import com.mathpar.matrix.MatrixS;
import com.mathpar.number.NumberZp32;
import com.mathpar.number.Ring;
import com.mathpar.parallel.utils.MPITransport;
import mpi.MPI;
import mpi.MPIException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Implements Strassen's algorithm of multiplying matrices in parallel.
 * Uses mathpar's implementation of Matrices, which you can find <a href="https://bitbucket.org/mathpar/dap">here</a>.
 *
 * @author Taras Kreshchenko
 * @version 1.0
 */
public class Practical5_2 {
    static boolean debug_mode = true; // enables printing of matrices, should be used with matrices that aren't too large.

    /**
     * Runs the calculations and prints the results in the console.
     *
     * @param args                     Contains one element - the size of the matrix.
     * @throws MPIException            If an error occurred during MPI communications.
     * @throws IOException             If MPITransport threw this exception.
     * @throws ClassNotFoundException  If MPITransport threw this exception.
     */
    public static void main(String[] args) throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);

        // Initial data
        Ring ring = new Ring("R64[x]");
        int rank = MPI.COMM_WORLD.getRank();
        if (MPI.COMM_WORLD.getSize() < 7) throw new IllegalStateException("Number of processors is less than 7.");
        int ord = Integer.parseInt(args[0]);
        int mod = 13;
        ring.setMOD32(mod);

        if (rank == 0) {
            // Filling the initial matrices
            Random rnd = new Random();
            MatrixS A = new MatrixS(ord, ord, 10000, new int[]{5, 5}, rnd, NumberZp32.ONE, ring);
            MatrixS B = new MatrixS(ord, ord, 10000, new int[]{5, 5}, rnd, NumberZp32.ONE, ring);
            if (debug_mode) {
                System.out.print("Matrix A:" + A.toString() + '\n');
                System.out.print("Matrix B:" + B.toString() + "\n\n");
            }

            MatrixS[] CC = new MatrixS[4];
            MatrixS[] MM = new MatrixS[7];
            // Splitting the matrices
            MatrixS[] AA = A.split();
            MatrixS[] BB = B.split();
            // Sending parts of matrices to other processors
            MPITransport.sendObjectArray(new Object[]{AA[0].add(AA[3], ring), BB[0].add(BB[3], ring)},0, 2, 1, 1);
            MPITransport.sendObjectArray(new Object[]{AA[2].add(AA[3], ring), BB[0]}, 0, 2, 2, 2);
            MPITransport.sendObjectArray(new Object[]{AA[0], BB[1].subtract(BB[3], ring)}, 0, 2, 3, 3);
            MPITransport.sendObjectArray(new Object[]{AA[3], BB[2].subtract(BB[0], ring)}, 0, 2, 4, 4);
            MPITransport.sendObjectArray(new Object[]{AA[0].add(AA[1], ring), BB[3]}, 0, 2, 5, 5);
            MPITransport.sendObjectArray(new Object[]{AA[2].subtract(AA[0], ring), BB[0].add(BB[1], ring)}, 0, 2, 6, 6);

            // Calculating processor 0's part
            MM[6] = multiplySeq(AA[1].subtract(AA[3], ring), BB[2].add(BB[3], ring), ring);

            // Receiving other parts from other processors
            MM[0] = (MatrixS) MPITransport.recvObject(1, 1);
            MM[1] = (MatrixS) MPITransport.recvObject(2, 2);
            MM[2] = (MatrixS) MPITransport.recvObject(3, 3);
            MM[3] = (MatrixS) MPITransport.recvObject(4, 4);
            MM[4] = (MatrixS) MPITransport.recvObject(5, 5);
            MM[5] = (MatrixS) MPITransport.recvObject(6, 6);
            if (debug_mode) {
                System.out.print("\nReceived matrices:");
                Arrays.stream(MM).forEach(System.out::print);
                System.out.println();
            }

            // Combining the results
            CC[0] = MM[0].add(MM[3], ring).subtract(MM[4], ring).add(MM[6], ring);
            CC[1] = MM[2].add(MM[4], ring);
            CC[2] = MM[1].add(MM[3], ring);
            CC[3] = MM[0].subtract(MM[1], ring).add(MM[2], ring).add(MM[5], ring);
            MatrixS C = MatrixS.join(CC);
            if (debug_mode) System.out.print("\nResult:" + C.toString() + '\n');
        } else {
            // Receiving matrices
            Object[] n = new Object[2];
            MPITransport.recvObjectArray(n, 0, 2, 0, rank);
            if (debug_mode) System.out.print("rank = " + rank + "; received matrices:" + n[0] + n[1] + '\n');

            // Multiplying them
            MatrixS res = multiplySeq((MatrixS) n[0], (MatrixS) n[1], ring);

            // Sending the result
            if (debug_mode) System.out.print("rank = " + rank + "; res = " + res + '\n');
            MPITransport.sendObject(res, 0, rank);
        }
        MPI.Finalize();
    }

    // Sequential multiplication (using Strassen's algorithm)
    public static MatrixS multiplySeq(MatrixS A, MatrixS B, Ring ring) {
        if (A.size != B.size) throw new IllegalArgumentException("Matrices have different sizes.");
        if (A.size == 1) return A.multiply(B, ring);

        MatrixS[] AA = A.split();
        MatrixS[] BB = B.split();
        MatrixS[] CC = new MatrixS[4];
        MatrixS[] MM = new MatrixS[7];

        // Building helping matrices
        MM[0] = multiplySeq(AA[0].add(AA[3], ring), BB[0].add(BB[3], ring), ring);
        MM[1] = multiplySeq(AA[2].add(AA[3], ring), BB[0], ring);
        MM[2] = multiplySeq(AA[0], BB[1].subtract(BB[3], ring), ring);
        MM[3] = multiplySeq(AA[3], BB[2].subtract(BB[0], ring), ring);
        MM[4] = multiplySeq(AA[0].add(AA[1], ring), BB[3], ring);
        MM[5] = multiplySeq(AA[2].subtract(AA[0], ring), BB[0].add(BB[1], ring), ring);
        MM[6] = multiplySeq(AA[1].subtract(AA[3], ring), BB[2].add(BB[3], ring), ring);

        // Combining them and returning the result
        CC[0] = MM[0].add(MM[3], ring).subtract(MM[4], ring).add(MM[6], ring);
        CC[1] = MM[2].add(MM[4], ring);
        CC[2] = MM[1].add(MM[3], ring);
        CC[3] = MM[0].subtract(MM[1], ring).add(MM[2], ring).add(MM[5], ring);
        return MatrixS.join(CC);
    }
}

/* Test with matrix size 4 (and 7 processors)
 * > mpirun --hostfile /home/x74r45/hostfile -np 7 java -cp /home/x74r45/IdeaProjects/OpenMPI_Homeworks/target/classes/ x74r45/Practical5_2 4
 *
 * Matrix A:
 * [[0.63, 0.27, 0.32, 0.68]
 *  [0.23, 0.28, 0.81, 0.74]
 *  [0.71, 0.15, 0.68, 0.64]
 *  [0.67, 0.66, 0.39, 0.65]]
 * Matrix B:
 * [[0.13, 0.9,  0.12, 0.92]
 *  [0.76, 0.57, 0.04, 0.91]
 *  [0.94, 0.56, 0.62, 0.31]
 *  [0.52, 0.57, 0.86, 0.15]]
 *
 * rank = 1; received matrices:
 * [[1.31, 0.91]
 *  [0.62, 0.93]]
 * [[0.75, 1.2 ]
 *  [1.62, 0.73]]
 * rank = 1; res =
 * [[2.45, 2.24]
 *  [1.97, 1.42]]
 * rank = 4; received matrices:
 * [[0.68, 0.64]
 *  [0.39, 0.65]]
 * [[0.81,  -0.34]
 *  [-0.24, -0.01]]
 * rank = 4; res =
 * [[0.39, -0.23]
 *  [0.16, -0.14]]
 * rank = 3; received matrices:
 * [[0.63, 0.27]
 *  [0.23, 0.28]]
 * [[-0.5,  0.61]
 *  [-0.82, 0.76]]
 * rank = 3; res =
 * [[-0.54, 0.59]
 *  [-0.34, 0.35]]
 * rank = 5; received matrices:
 * [[0.95, 0.95]
 *  [1.04, 1.02]]
 * [[0.62, 0.31]
 *  [0.86, 0.15]]
 * rank = 5; res =
 * [[1.41, 0.44]
 *  [1.52, 0.48]]
 * rank = 2; received matrices:
 * [[1.39, 0.79]
 *  [1.05, 1.31]]
 * [[0.13, 0.9 ]
 *  [0.76, 0.57]]
 * rank = 2; res =
 * [[0.78, 1.7]
 *  [1.13, 1.7]]
 * rank = 6; received matrices:
 * [[0.08, -0.12]
 *  [0.43, 0.38 ]]
 * [[0.25, 1.81]
 *  [0.8,  1.49]]
 * rank = 6; res =
 * [[-0.07, -0.03]
 *  [0.41,  1.35 ]]
 *
 * Received matrices:
 * [[2.45, 2.24]
 *  [1.97, 1.42]]
 * [[0.78, 1.7]
 *  [1.13, 1.7]]
 * [[-0.54, 0.59]
 *  [-0.34, 0.35]]
 * [[0.39, -0.23]
 *  [0.16, -0.14]]
 * [[1.41, 0.44]
 *  [1.52, 0.48]]
 * [[-0.07, -0.03]
 *  [0.41,  1.35 ]]
 * [[-0.5, -0.28]
 *  [0.79, 0.43 ]]
 *
 * Result:
 * [[0.94, 1.29, 0.87, 1.03]
 *  [1.39, 1.24, 1.18, 0.83]
 *  [1.17, 1.47, 1.06, 1.1 ]
 *  [1.29, 1.56, 0.91, 1.43]]
 */
