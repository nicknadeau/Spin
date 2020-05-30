#!/bin/bash

help () {
	echo 'USAGE: spin [built-tests-dir] [dependencies]...'
	echo -e "\t"'built-tests-dir: a directory that contains all of the built .class test files to be run.'
	echo -e "\t"'dependencies: zero or more .class or .jar files required to run the tests.'
}

if [ $# -lt 1 ]
then
	help
	exit 1
fi

test_dir="$1"
num_tests=0

for file in $(find "$test_dir" -type f)
do
	if [[ $(realpath $file) == *"Test.class" ]]
	then
		tests="$tests $(realpath $file)"
		num_tests=$((num_tests+1))
	fi
done

for dependency in ${@:2}
do
	dependencies="$dependencies $(realpath $dependency)"
done

java -cp "spin-standalone.jar:junit-4.12.jar" spin.client.standalone.StandaloneClient "$(realpath $test_dir)" "$num_tests" $tests $dependencies
