# Spin

A library for running unit tests concurrently in Java.

## Build

Run the following command to build the standalone client jar.
```shell
ant build-singleuse
```

## Running The Example

An example module is provided that simulates a project with some tests to be run. To run the tests in this example using the standalone client library run the following commands:

```shell
./pack.sh
cd pack
./spin 4 build/test build/src lib/*
```

The above command will run Spin using 4 threads. It will run all of the tests in the given test directory 'build/test' and it supplies all of the dependency .class and .jar files required to run these tests, located in the 'build/src' and 'lib' directories.

To clean up when finished run:

```shell
cd ..
ant clean
```
