#!/bin/bash

# ==================================================================
# This file is divided into the following sections:
# Section 1: variable declarations [where all shared variables are defined; most are constants.]
# Section 2: test definitions [a special subsection of section 1. These are test names. Their names must map to a
#     function defined within this file which, when run, executes the test.]
# Section 3: helper functions [functions that contain common code.]
# Section 4: test functions [the functions that are called which execute tests (see Section 2).]
# Section 5: entry point [where we actually handle the --run argument and invoke the tests.]
# ==================================================================

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
# >>>>>>>>>>>>>>>>>>>  Section 1  <<<<<<<<<<<<<<<<<<<<
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
filename='systests'
spin='spin-singleuse'
spin_cmd='./spin-singleuse'
spin_jar='spin-core.jar'
json_parser='json_parser.py'
json_parser_orig='spin/json_parser.py'
dependencies='dependencies'
db_config='db_config.txt'
config_dir='config'
spin_log_dir='spin_logs'
log='systests.log'
autogen_cmd='python3 spin/suite_autogen.py'
gen_file='gen_file'
any_class_matcher='.*\\\\.class'
curr_dir="$(pwd)"
num_tests=0
num_success=0

if [ "$#" -eq 2 ] && [ "$2" == '-v' ]
then
	is_verbose=true
else
	is_verbose=false
fi

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
# >>>>>>>>>>>>>>>>>>>  Section 2  <<<<<<<<<<<<<<<<<<<<
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

# This is a space-separated list of test/function names that will be run by this script.
one='test_multi_class_single_test test_multi_class_multi_test test_multi_package_single_test test_multi_package_multi_test '
two='test_some_failures test_all_failures test_writes_to_stdout test_writes_to_stderr test_single_class_single_test '
three='test_single_class_multi_test test_single_class_single_test_failure test_single_class_multi_test_all_fail '
four='test_single_class_multi_test_some_fail test_single_class_zero_tests test_multi_class_some_empty test_multi_class_zero_tests '
five='test_zero_test_classes test_project_with_src_files test_some_fail_out_err_empty_src test_missing_dependency'
tests="$one$two$three$four$five"

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
# >>>>>>>>>>>>>>>>>>>  Section 3  <<<<<<<<<<<<<<<<<<<<
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
function print_help () {
	echo "USAGE: $filename.sh <action>"
	echo -e "\taction:"
	echo -e "\t--run [flag]: runs the Spin system tests."
	echo -e "\t\tflag:"
	echo -e "\t\t-v: outputs Spin to the console rather than redirecting to a log file."
	echo -e "\t\t-c: forces this program to clean up after itself when it is done."
	echo -e "\t--clean: cleans up the auto-generated system tests."
}

# Args:: <name>
# name: the name of the test
function print_success_msg() {
	echo "[$filename]: SUCCESS | test: $1"
}

# Args:: <name>
# name: the name of the test
function print_gen_fail_msg() {
	echo "[$filename]: FAILURE | test: $1 (project generation failure)"
}

# Args:: <name>
# name: the name of the test
function print_spin_fail_msg() {
	echo "[$filename]: FAILURE | test: $1 (failure running Spin)"
}

# Args:: <name>
# name: the name of the test
function print_validate_fail_msg() {
	echo "[$filename]: FAILURE | test: $1 (project validation failure)"
}

function clean_for_test() {
	ant clean &> /dev/null
	rm $gen_file &> /dev/null
}

function clean() {
	echo "[$filename]: Cleaning up the workspace."
	clean_for_test
	rm output.txt &> /dev/null
	rm $json_parser &> /dev/null
	rm $spin &> /dev/null
	rm *.jar &> /dev/null
	rm -rf $dependencies &> /dev/null
	rm -rf $config_dir &> /dev/null
	rm -rf "$spin_log_dir" &> /dev/null
	rm $log &> /dev/null
}

function setup() {
	echo '============== System Tests (Setup: Begin) =============='
	clean
	echo "[$filename]: Building the single-use Spin project."
	cd .. && \
	ant build-core &> "$curr_dir/$log" && \
	cd $curr_dir && \
	echo "[$filename]: Fetching all dependencies." && \
	cp ../client/$spin . && \
	cp $json_parser_orig . && \
	cp ../Core/dist/$spin_jar . && \
	mkdir $dependencies && \
	cp ../Core/lib/* $dependencies &&\
	cp ../lib/* $dependencies && \
	mkdir $config_dir && \
	cp ../config/$db_config $config_dir && \
	cd $curr_dir && \
	echo "[$filename]: Setting PYTHONPATH to current directory: $curr_dir" && \
	export PYTHONPATH=$curr_dir && \
	mkdir $spin_log_dir
	echo '============== System Tests (Setup: Complete) =============='
}

# Generates a project from a gen file. Assumption: project contains only test files no source files.
# args:: <name>
# name: the name of the project to generate
function generate_test_dir_only_project() {
	if [ "$#" -ne 1 ]
	then
		echo "[$filename]: USAGE:: generate_project <name>"
		return 1
	fi
	eval $autogen_cmd --generate $gen_file "$1" && \
	ant -Dproject_name="$1" build_tests &> $curr_dir/$log
	rm -rf "$1"
}

# Generates a project from a gen file. Assumption: project contains both test & source files.
# args:: <name>
# name: the name of the project to generate
function generate_test_and_src_project() {
        if [ "$#" -ne 1 ]
        then
                echo "[$filename]: USAGE:: generate_project <name>"
                return 1
        fi
        eval $autogen_cmd --generate $gen_file "$1" && \
        ant -Dproject_name="$1" build_tests_and_src &> $curr_dir/$log
        rm -rf "$1"
}

# Args:: <test dir> <class matcher> <name> [<dependency>...]
# test dir: the directory of the compiled .class test class files
# class matcher: the file name suffix to match against to determine which class files to submit
# name: the project name
# dependency: any dependencies that the project requires to be run
function run_spin() {
	if [ "$#" -lt 3 ]
	then
		echo "[$filename]: USAGE:: run_spin <test dir> <class matcher> <name> [<dependency>...]"
		return 1
	fi
	echo `eval $spin_cmd -v -p 4 "$1" "$2" ${@:4}`
}

# Args:: <suite id>
# suite id: the id of the suite in the postgres database to validate
function validate_results() {
	if [ "$#" -ne 1 ]
	then
		echo "[$filename]: USAGE:: validate_results <spin result>"
		return 1
	fi
	eval $autogen_cmd --validate $gen_file ./$config_dir/$db_config "$1"
}

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
# >>>>>>>>>>>>>>>>>>>  Section 4  <<<<<<<<<<<<<<<<<<<<
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
function test_multi_class_single_test() {
	num_tests=$((num_tests + 1))
	name='test_multi_class_single_test'
	echo "[$filename]: Running test $name"
	eval $autogen_cmd --gen_file -new . a 4 1 '[0]' && \
	generate_test_dir_only_project "$name"
	if [ "$?" -ne 0 ]
	then
	  print_gen_fail_msg "$name"
		return 1
	fi

	suite_result="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" $dependencies/*)"
	if [ "$?" -ne 0 ]
	then
		print_spin_fail_msg "$name"
		return 1
	fi

	validate_results "$suite_result"
	if [ "$?" -eq 0 ]
	then
		print_success_msg "$name"
		num_success=$((num_success + 1))
	else
		print_validate_fail_msg "$name"
	fi
}

function test_multi_class_multi_test() {
	num_tests=$((num_tests + 1))
	name='test_multi_class_multi_test'
	echo "[$filename]: Running test $name"
	eval $autogen_cmd --gen_file -new . a 8 6 '[0]' && \
	generate_test_dir_only_project "$name"
	if [ "$?" -ne 0 ]
	then
		print_gen_fail_msg "$name"
		return 1
	fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
	if [ "$?" -ne 0 ]
	then
		print_spin_fail_msg "$name"
		return 1
	fi

	validate_results $suite_id
	if [ "$?" -eq 0 ]
	then
		print_success_msg "$name"
		num_success=$((num_success + 1))
	else
		print_validate_fail_msg "$name"
	fi
}

function test_multi_package_single_test() {
        num_tests=$((num_tests + 1))
        name='test_multi_package_single_test'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 5 1 '[0]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file b 7 1 '[1]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_multi_package_multi_test() {
        num_tests=$((num_tests + 1))
        name='test_multi_package_multi_test'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 6 4 '[0]' && \
        eval $autogen_cmd --gen_file -append_classes $gen_file b 5 12 '[0]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_some_failures() {
        num_tests=$((num_tests + 1))
        name='test_some_failures'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 3 4 '[2]' && \
        eval $autogen_cmd --gen_file -append_classes $gen_file b 5 2 '[0]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_all_failures() {
        num_tests=$((num_tests + 1))
        name='test_all_failures'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 7 5 '[2]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_writes_to_stdout() {
        num_tests=$((num_tests + 1))
        name='test_writes_to_stdout'
        echo "[$filename]: Running test $name"
	# We increment some counters and print to stdout with codes 7 & 9. Code 3 is a basic stdout print.
        eval $autogen_cmd --gen_file -new . a 7 5 '[3,5,6,7,5,6,5,7,9]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_writes_to_stderr() {
        num_tests=$((num_tests + 1))
        name='test_writes_to_stderr'
        echo "[$filename]: Running test $name"
        # We increment some counters and print to stdout with codes 8 & 10. Code 4 is a basic stdout print.
        eval $autogen_cmd --gen_file -new . a 4 3 '[4,5,6,8,6,5,8,10]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_single_class_single_test() {
        num_tests=$((num_tests + 1))
        name='test_single_class_single_test'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 1 1 '[0]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_single_class_multi_test() {
        num_tests=$((num_tests + 1))
        name='test_single_class_multi_test'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 1 7 '[3]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_single_class_single_test_failure() {
        num_tests=$((num_tests + 1))
        name='test_single_class_single_test_failure'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 1 1 '[2]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_single_class_multi_test_all_fail() {
        num_tests=$((num_tests + 1))
        name='test_single_class_multi_test_all_fail'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 1 4 '[2]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_single_class_multi_test_some_fail() {
        num_tests=$((num_tests + 1))
        name='test_single_class_multi_test_some_fail'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 1 3 '[0]' && \
	eval $autogen_cmd --gen_file -append_tests $gen_file a 0 3 '[2]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_single_class_zero_tests() {
        num_tests=$((num_tests + 1))
        name='test_single_class_zero_tests'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 1 0 '[]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_multi_class_zero_tests() {
        num_tests=$((num_tests + 1))
        name='test_multi_class_zero_tests'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 3 0 '[]' && \
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_multi_class_some_empty() {
        num_tests=$((num_tests + 1))
        name='test_multi_class_some_empty'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 3 4 '[0]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file a 5 0 '[]'
        generate_test_dir_only_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_zero_test_classes() {
        num_tests=$((num_tests + 1))
        name='test_zero_test_classes'
        echo "[$filename]: Running test $name"
	
	# We don't have anything to generate here, instead we just create the empty test dir.
	# This also means we do not verify. What we are testing here is that Spin exits normally.
	mkdir build
	mkdir "build/$name"
	mkdir "build/$name/test"
	run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar" &> /dev/null
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_project_with_src_files() {
        num_tests=$((num_tests + 1))
        name='test_project_with_src_files'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 6 4 '[11,12,13]' && \
        generate_test_and_src_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "build/$name/src"  "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

function test_some_fail_out_err_empty_src() {
	num_tests=$((num_tests + 1))
	name='test_some_fail_out_err_empty_src'
	echo "[$filename]: Running test $name"
	eval $autogen_cmd --gen_file -new . a 2 3 '[0]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file a 4 1 '[2]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file b 3 2 '[3,5,6,7,9]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file b 3 2 '[4,5,6,8,10]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file c 4 3 '[11,12,13]' && \
	eval $autogen_cmd --gen_file -append_classes $gen_file c 4 0 '[]' && \
	generate_test_and_src_project "$name"
	if [ "$?" -ne 0 ]
	then
		print_gen_fail_msg "$name"
		return 1
	fi

	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "build/$name/src" "$junit_jar" "$hamcrest_jar")"
	if [ "$?" -ne 0 ]
	then
		print_spin_fail_msg "$name"
		return 1
	fi

	validate_results $suite_id
	if [ "$?" -eq 0 ]
	then
		print_success_msg "$name"
		num_success=$((num_success + 1))
	else
		print_validate_fail_msg "$name"
	fi
}

function test_missing_dependency() {
        num_tests=$((num_tests + 1))
        name='test_missing_dependency'
        echo "[$filename]: Running test $name"
        eval $autogen_cmd --gen_file -new . a 6 4 '[14]' && \
        generate_test_and_src_project "$name"
        if [ "$?" -ne 0 ]
        then
                print_gen_fail_msg "$name"
                return 1
        fi

	# We omit the src dependencies.
	suite_id="$(run_spin "build/$name/test" "$any_class_matcher" "$spin_log_dir/$name" "$junit_jar" "$hamcrest_jar")"
        if [ "$?" -ne 0 ]
        then
                print_spin_fail_msg "$name"
                return 1
        fi

        validate_results $suite_id
        if [ "$?" -eq 0 ]
        then
                print_success_msg "$name"
                num_success=$((num_success + 1))
        else
                print_validate_fail_msg "$name"
        fi
}

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
# >>>>>>>>>>>>>>>>>>>  Section 5  <<<<<<<<<<<<<<<<<<<<
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
if [ "$1" == '--run' ]
then
	echo '============== System Tests (Run: Begin) =============='
	setup
	if [ "$?" -ne 0 ]
	then
		echo "[$filename]: setup failed!"
		exit 1
	fi

	echo '============== System Tests (Testing: Begin) =============='
	start_time="$(date +%s)"
	for test in $tests
	do
		$test
		clean_for_test
	done
	end_time="$(date +%s)"

	echo '============== System Tests (Testing: Complete) =============='
	echo -e "\n[$filename]: Tests: $num_tests | Successes: $num_success | Failed: $((num_tests - num_success))"
	echo -e "Total time: $((end_time - start_time)) sec.\n"
	echo '============== System Tests (Run: Complete) =============='
	cd $curr_dir

	if [ "$#" -eq 2 ] && [ "$2" == '-c' ]
	then
		clean
	fi

elif [ "$1" == '--clean' ]
then
	clean
else
	print_help
	exit 1
fi
