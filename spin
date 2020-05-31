#!/bin/bash

help () {
	echo 'USAGE: spin [-v] <built-tests-dir> [dependencies]...'
	echo -e "\t"'-v [OPTIONAL]: if present this flag specifies to run in verbose logging mode'
	echo -e "\t"'built-tests-dir: a directory that contains all of the built .class test files to be run.'
	echo -e "\t"'dependencies [OPTIONAL]: zero or more .class or .jar files required to run the tests.'
}

if [ $# -lt 1 ]
then
	help
	exit 1
fi

if [ "$1" == '-v' ]
then
	verbose=true
fi

if [ "$verbose" = true ]
then
	test_dir_index=2
	properties='-Denable_logger=true'
else
	test_dir_index=1
	properties='-Denable_logger=false'
fi

test_dir="${!test_dir_index}"
num_tests=0

for file in $(find "$test_dir" -type f)
do
	if [[ $(realpath $file) == *"Test.class" ]]
	then
		tests="$tests $(realpath $file)"
		num_tests=$((num_tests+1))
	fi
done

for dependency in ${@:$((test_dir_index+1))}
do
	dependencies="$dependencies $(realpath $dependency)"
done

java "$properties" -cp "spin-standalone.jar:junit-4.12.jar" spin.client.standalone.StandaloneClient "$(realpath $test_dir)" "$num_tests" $tests $dependencies
