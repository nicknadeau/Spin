#!/bin/bash

help () {
	echo 'USAGE: spin [-v] <num threads> <built-tests-dir> [dependencies]...'
	echo -e "\t"'-v [OPTIONAL]: if present this flag specifies to run in verbose logging mode.'
	echo -e "\t"'num threads: the number of threads to use to run the tests with.'
	echo -e "\t"'built-tests-dir: a directory that contains all of the built .class test files to be run.'
	echo -e "\t"'dependencies [OPTIONAL]: zero or more .class or .jar files required to run the tests.'
}

# We have 2 non-optional arguments we expect so if given less we fail out.
if [ $# -lt 2 ]
then
	help
	exit 1
fi

# If first arg is the verbose flag we update next expected arg indices and verbose property.
if [ "$1" == '-v' ]
then
	verbose=true
fi

if [ "$verbose" = true ]
then
	num_threads_index=2
	test_dir_index=3
	verbose_property='-Denable_logger=true'
else
	num_threads_index=1
	test_dir_index=2
	verbose_property='-Denable_logger=false'
fi

# Next argument must be a number, we verify that and this is the number of threads to use.
num_threads=${!num_threads_index}
if ! [[ "$num_threads" =~ ^[0-9]+$ ]]
then
	help
	exit
fi

num_threads_property="-Dnum_threads=$num_threads"
test_dir="${!test_dir_index}"
num_tests=0

# Grab all test files in the given test base directory. We consider a test file a file ending with Test.class.
for file in $(find "$test_dir" -type f)
do
	if [[ $(realpath $file) == *"Test.class" ]]
	then
		tests="$tests $(realpath $file)"
		num_tests=$((num_tests+1))
	fi
done

# Collect any given dependencies and then run the program.
for dependency in ${@:$((test_dir_index+1))}
do
	dependencies="$dependencies $(realpath $dependency)"
done

java "$verbose_property" "$num_threads_property" -cp "spin-singleuse.jar:junit-4.12.jar" spin.core.singleuse.SingleUseEntryPoint "$(realpath $test_dir)" "$num_tests" $tests $dependencies
