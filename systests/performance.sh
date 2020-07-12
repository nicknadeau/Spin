#!/bin/bash

filename='performance'
spin='spin-singleuse'
spin_cmd='./spin-singleuse'
spin_jar='spin-singleuse.jar'
junit_jar='junit-4.12.jar'
hamcrest_jar='hamcrest-all-1.3.jar'
spin_log_dir='spin_logs'
log='systests.log'
autogen_cmd='python3 spin/suite_autogen.py'
gen_file='gen_file'
curr_dir="$(pwd)"

magnitudes='1 10 100 1000 10000'

function print_help () {
	echo "USAGE: $filename.sh <action>"
	echo -e "\taction:"
	echo -e "\t--speed: runs the Spin speed performance tests. Outputs time as seconds and milliseconds for a project"
	echo -e "\t\tdefined as K=(N x M), where K is num tests, N is num test classes, M is num tests per class."
}

function clean_for_run() {
	ant clean &> /dev/null
	rm $gen_file &> /dev/null
}

function clean() {
	echo -e "\n[$filename]: Cleaning up the workspace."
	clean_for_run
	rm *.jar &> /dev/null
	rm $spin &> /dev/null
	rm -rf "$spin_log_dir" &> /dev/null
	rm $log &> /dev/null
}

function setup() {
	echo '============== Performance (Setup: Begin) =============='
	clean
	echo "[$filename]: Building the single-use Spin project."
	cd .. && \
	ant build-singleuse &> "$curr_dir/$log" && \
	cd $curr_dir && \
	echo "[$filename]: Fetching all dependencies." && \
	cp ../client/$spin . && \
	cp ../Core/dist/$spin_jar . && \
	cp ../lib/$junit_jar . && \
	cp ../Example/lib/$hamcrest_jar . && \
	cd $curr_dir && \
	echo "[$filename]: Setting PYTHONPATH to current directory: $curr_dir" && \
	export PYTHONPATH=$curr_dir && \
	mkdir $spin_log_dir
	echo -e "============== Performance (Setup: Complete) ==============\n"
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

# Runs a speed test
# args:: <num classes> <num tests per class>
# num classes: the number of test classes to generate
# num tests per class: the number of test methods per generated test class
function speed_test() {
	if [ "$#" -ne 2 ]
	then
		echo "[$filename]: USAGE:: speed_test <num classes> <num tests per class>"
		return 1
	fi

	eval $autogen_cmd --gen_file -new . a "$1" "$2" '[0]' && \
	generate_test_dir_only_project "speed_perf"
	if [ "$?" -ne 0 ]
	then
		print_gen_fail_msg "$name"
		return 1
	fi

	start_time="$(date +%s%N)"
	eval $spin_cmd 4 'build/speed_perf/test' '.class' "$spin_log_dir/speed_perf" "$junit_jar" "$hamcrest_jar" &> /dev/null
	end_time="$(date +%s%N)"

	total_time_millis="$(((end_time - start_time) / 1000000))"
	num_seconds="$((total_time_millis / 1000))"
	num_milliseconds="$((total_time_millis - (num_seconds * 1000)))"

	num_classes="$1"
	tests_per_class="$2"
	echo -e "Total time $((num_classes * tests_per_class))=($1 x $2): $num_seconds sec. $num_milliseconds millis."
}

if [ "$#" -ne 1 ]
then
	print_help
else
	if [ "$1" == '--speed' ]
	then
		echo '============== Performance (Speed: Begin) =============='
		setup

		for magnitude in $magnitudes
		do
			if [ "$magnitude" -eq 1 ]
			then
				speed_test $magnitude 1
				clean_for_run
			else
				speed_test $magnitude 1
				clean_for_run
				speed_test 1 $magnitude
				clean_for_run
			fi
		done

		clean
		echo '============== Performance (Speed: Complete) =============='
	else
		print_help
	fi
fi

