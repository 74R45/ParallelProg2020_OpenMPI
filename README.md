This project is a collection of labs I've done for Parallel Programming 2020 course, particularly those that are to do with OpenMPI.
It includes various examples of using OpenMPI library, some of them being very simple and others a bit more complex.

Here's one of them. It's a program that computes a fragment from the Mandelbrot set in parallel, and renders it in a new window:

![](.//media/img1.png)

## Installation
In order to launch this project, you need to install [OpenMPI](https://open-mpi.org/software/ompi/v4.1) and reference it in your project dependencies.

**Warning: `Practical5_2.java` uses an implementation from [mathpar/dap](https://bitbucket.org/mathpar/dap), so you would need to clone that repo in order to use it.**
