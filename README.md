# Spin

A library for running unit tests concurrently in Java.

## Build

Run the following command to build the standalone client jar.
```shell
ant build-standalone
```

## Running The Example

An example module is provided that simulates a project with some tests to be run. To run the tests in this example using the standalone client library run the following commands:

```shell
./pack.sh
cd pack
./spin build/test build/src lib/*
```

To clean up when finished run:

```shell
cd ..
ant clean
```
