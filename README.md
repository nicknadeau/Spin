# Spin

A library for running unit tests concurrently in Java.

Spin picks up on JUnit annotations so that if you've already got a suite of JUnit tests written then you've already got a suite of Spin tests written.

## Contents
* [1. What's The Current Status Of Spin?](#status)
* [2. Running The Example](#example)
* [3. How To Build Spin](#build)
* [4. Running The Tests](#tests)
* [5. Running The Performance Scripts](#perf)

### <a name="status">1. What's The Current Status Of Spin?</a>
Spin is still in its early stages of development. It's just me, in what little spare time I have. Spin is to the point where it can run a suite of tests concurrently and output the results to you, in at least some halfway readable format. But the JUnit-related support is still very weak: it essentilly only recognizes the `@Test` annotation (with no parameters) right now and nothing else. This is my "MVP" scope, if you will.

What's the current effort? I am currently in the middle of making Spin a long-lived process that handles any number of test suites thrown at it. I'll always have a "singleuse" client hanging around, I suppose, for people interested in always starting up a new instance of Spin to run some tests, but I'm more interested in the long-lived aspect: it will allow me to begin making headway towards some benchmarking and the long-lived process has the kind of flexibility that ultimately I'd like to have (plus, besides the first run, should be noticeably faster).

What's after that? The two big milestones on the horizon after getting the system life-cycling sorted out will be a) improving how tests get distributed through the system so that the application can actually scale. Right now, we have the entire suite all in memory at once travelling through each part of the system, we're going to have to batch tests together in a much smarter way. b) I'd like to reduce the test execution logic down to a simple functional interface ideally, so that the executing threads know nothing about what constitutes a test, and from there to also create a module of any necessary components someone would need to create their own custom implementation, much like a custom JUnit Runner, that can be used instead. This is all still within the "MVP" scope... that is, only supporting a bare bones `@Test` annotation. From this point on, I can begin to focus on robustness, integ & unit tests, and additional JUnit support.

### <a name="example">2. Running The Example</a>

#### Requirements:
- JDK 11
- Ant 1.10
- Ubuntu 18.04

A small Example Java project can be found in the `Example/` directory. This project defines the JUnit tests that will make up the test suite we will send off for Spin to run.

There's already a script in place to make running this example fairly easy. Just run the following commands:
```shell script
cd demo/
./example-singleuse.sh
cd example/
./spin-singleuse 4 build/test/ '.*Test\\.class' build/src/ lib/*
```
The final command calls into the `spin-singleuse` client, which is a client that runs a single test suite and then exits. The command is telling it to run Spin using 4 test executor threads. It then passes in the base directory in which all of the compiled .class test files can be found: `build/test/`. Of all the .class files found in this base directory, only the tests whose filename matches the Java regular expression (`.*Test\\.class`) will be selected to be run, all others will be ignored. Finally, all of the trailing arguments are paths to all of the dependencies that are required to run the tests, the project's compiled source files in `build/src/` and the other .jar files found in `lib/`.

The output of the client will be `-1`: the client outputs the suite id that it uses to write its results to a database but only if it is told to persist those results. In the above case, we do not persist anything and so no suite id exists and `-1` is returned as such.

After running the example you can delete the generated `example` directory inside `demo/`.

### <a name="build">3. How To Build Spin</a>

#### Requirements:
- JDK 11
- Ant 1.10
- Ubuntu 18.04

To build the Spin jar file, run the following command:
```shell script
ant build-core
```

The clients used to interact with the running Spin instance can be found in the `client/` directory. Currently, there is only a "single-use" client, which runs a single test suite and then exits.

### <a name="tests">4. Running The Tests</a>

#### Requirements:
- JDK 11
- Ant 1.10
- Python 3.6
- PostgresQL 12.3
- Ubuntu 18.04

The only tests that exist right now are system or end-to-end tests. They can be found in the `systests` directory. To run them, you'll need to have a few things configured.

1. Make sure you've got the python `psycopg2` package installed.
2. Make sure you've got a postgresql server running, you may need to change the `config/db_config.txt` file to match your configurations.
3. Make sure you've got a database named `spin_results` created (again, you could instead modify the config file).

If you've done all of that run:
```shell script
cd systests
./systests.sh --run -c
```

There is also a `ci.sh` file in the project root directory which will run these tests as well (in addition to building Spin and running the example). This file assumes you've got a correctly configured postgresql server running as well as a python virtual environment located at `systests/venv` (with the `psycopg2` package). If so, run:
```shell script
./ci.sh
```

### <a name="perf">5. Running The Performance Scripts</a>

#### Requirements:
- JDK 11
- Ant 1.10
- Python 3.6
- Ubuntu 18.04

There are also a few speed and memory related performance 'tests' that can be run. These are mostly just here for interest's sake and to provide a starting point to eventually seriously measure against. To execute them, run:
```shell script
cd systests
./performance.sh --speed
./performance.sh --memory
```
