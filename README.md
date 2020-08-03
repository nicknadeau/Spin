# Spin

A library for running unit tests concurrently in Java.

## Build

#### Requirements:
- JDK 11
- Ant 1.10
- Ubuntu 16.04

Currently, the only way the project can be built is as a so-called "single-use" project. That is, Spin is started up and given some tests to run and once it completes running them the program exits. It is not a long-lived process.

The single-use program can be built as a jar file using the following command:
 
```shell script
ant build-core
```

The client used to interact with this single-use jar is a simple shell script named `spin-singleuse`, which can be found in the `client` directory.

## Running The Example

#### Requirements:
- JDK 11
- Ant 1.10
- Ubuntu 16.04

An example module is provided that simulates a project with some tests to be run.

To run the tests on the single-use version of Spin run the following commands:

```shell script
cd demo/
./example-singleuse.sh
cd example/
./spin-singleuse 4 build/test/ '.*Test\\.class' build/src/ lib/*
```

The above command will run Spin using 4 threads. It will run all of the tests whose file names end in the pattern 'Test.class' (eg. ThisTest.class) in the given test directory `build/test` and it supplies all of the dependency `.class` and `.jar` files required to run these tests, located in the `build/src` and `lib` directories.

Note that the matching expression we must pass in is a Java regular expression.

To clean up when finished run:

```shell script
cd ../..
ant clean
```

## Running The Tests

#### Requirements:
- JDK 11
- Ant 1.10
- Python 3.5
- PostgresQL 9.5
- Ubuntu 16.04

Currently, the only tests on Spin are end-to-end or system tests located in the `systests` directory. To run them:

```shell script
cd systests
./systests.sh --run -c
```

## System Performance

#### Requirements:
- JDK 11
- Ant 1.10
- Python 3.5
- Ubuntu 16.04

We have some speed and memory performance scripts. Spin is not yet intended to be optimal in either regard. We are at an early stage where writing a skeleton with some demonstrable correctness properties was the key goal of the project. These results are most just here for interest's sake and also to give us a starting point to measure against.

```shell script
cd systests
./performance.sh --speed
./performance.sh --memory
```
