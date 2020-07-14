# System Tests
This directory contains end-to-end or system tests to be run against the Spin codebase as a means of testing it.

### Contents
* [1. Running The Tests](#run)
* [2. How The System Tests Work](#how)
* [3. The Gen File](#gen_file)
* [4. Performance Tests](#perf)

---
### <a name="run">1. Running The Tests</a>
To run the tests (assuming you are in the `systests` directory):
```shell script
./systests.sh --run -c
```

### <a name="how">2. How The System Tests Work</a>
Spin requires a suite of Java unit tests written in one or more files distributed across one or more packages in order to be run. We could create a side project where we throw all of the test files we want to include and we could run Spin against this but there are a few disadvantages to this approach. Namely, its flexibility is restricted and it would require taking up a considerable amount of extra disk space for testing purposes.

Instead, the idea is to automatically generate mock Java unit test suites on the fly as needed and to run Spin against these. Sometimes this may even require generating the project's corresponding mock source code.

Here's the high-level flow of how these tests work:
1. create a test suite description using our `suite_autogen.py` script.
2. generate the mock project from the suite description using our `suite_autogen.py` script.
3. build `Spin` and run it against the newly generated project. Configure `Spin` to write the suite results to a PostgresQL database.
4. evaluate the results written to the database against the suite description generated in step 1, ensuring all of the results are as expected.

Each test goes through this 4-step process, and the tests themselves are defined in the `systests.sh` script.

### <a name="gen_file">3. The Gen File</a>
The gen file is the file that describes a test suite. This file can be constructed using the `suite_autogen.py` script and once constructed acts as the canonical representation of the test suite. That same script can then be used to read this file and determine how to generate all of the required Java classes and their tests, and using this same file the tool can determine exactly what the results of running these tests should have been, which is what allows us to verify `Spin` is working correctly.

To create a new gen file, or to add more description to an existent one, run `suite_autogen.py --gen_file`

The representation of this file is not succinct. I didn't want to over-think it at this stage. The file is able to describe a test suite consisting of 100,000 tests in a variety of ways in less than 5MB, which is good enough. Typical tests define less than 100 tests.

### <a name="perf">4. Performance Tests</a>
There are both speed and memory performance "tests" written for Spin in the `performance.sh` script. Currently, we are not actually using the results in any sort of hard sense (ie. to detect regressions via CI). They are more there to loosely keep our eye on and notice regressions if they occur, but more importantly to provide a starting point to begin improving upon.

The system was not designed with any sort of performance in mind and naturally it does quite poorly at this early stage. The speed bottleneck is the client shell script, which accounts for the overwhelming amount of run time. When we get on the order of running 10,000 tests with some fairly decent hardware it can take over a minute (these are empty tests). Likely when we get up to 10,000 tests we also see our memory begin to grow very noticeably.

To run the performance tests:
```shell script
./performance.sh --speed
./performance.sh --memory
```
