# Spin

A library for running unit tests concurrently in Java.

## Build

Run the following command to build the single-use jar.

This jar is the Spin program that runs all of the tests in a single test suite and then exits. It is not long-lived. 
 
```shell
ant build-singleuse
```

The client used to interact with this single-use jar is the `client/spin-singleuse` file.

## Running The Examples

An example module is provided that simulates a project with some tests to be run. 

To run the tests on the single-use version of Spin run the following commands:

```shell
cd demo/
./example-singleuse.sh
cd example/
./spin-singleuse 4 build/test build/src lib/*
```

The above command will run Spin using 4 threads. It will run all of the tests in the given test directory `build/test` and it supplies all of the dependency `.class` and `.jar` files required to run these tests, located in the `build/src` and `lib` directories.

To clean up when finished run:

```shell
cd ../..
ant clean
```
