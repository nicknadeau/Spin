#!/bin/bash

function print_help () {
	echo 'USAGE: systests.sh <action>'
	echo -e "\t"'action:'
	echo -e "\t\t"'--run [-v]: runs the Spin system tests.'
	echo -e "\t\t\t"'-v: outputs Spin to the console rather than redirecting to a log file.'
	echo -e "\t\t"'--clean: cleans up the auto-generated system tests.'
}

function clean() {
	ant clean
	rm *.jar
	rm spin-singleuse
	rm -rf config
	rm gen_file
	rm spin.log
}

if [ "$1" == '--run' ]
then
	curr_dir=$(pwd)
	clean

	echo '============== System Tests (Prepare) ==============' && \
	echo '>>>> Building the single-use Spin project.' && \
	cd .. && \
	ant build-singleuse && \
	cd $curr_dir && \
	echo '>>>> Pulling in all dependencies.' && \
	cp ../client/spin-singleuse . && \
	cp ../Core/dist/spin-singleuse.jar . && \
	cp ../Core/lib/postgresql-42.2.12.jar . && \
	cp ../lib/junit-4.12.jar . && \
	cp ../Example/lib/hamcrest-all-1.3.jar . && \
	mkdir config && \
	cp ../config/db_config.txt config/ && \
	echo '>>>> Creating gen file.' && \
	python3 spin/suite_autogen.py --gen_file -new . a 4 3 '[6,7,8,9,10,6,6,10,6,9]' && \
	echo '>>>> Auto-generating the project to test.' && \
	python3 spin/suite_autogen.py --generate gen_file example_gen && \
	echo '>>>> Compiling the auto-generated project.' && \
	ant -Dproject_name="example_gen" build_tests && \
	rm -rf example_gen && \
	echo '============== System Tests (Run) ==============' && \
	echo '>>>> Redirecting all output to file: spin.log'
	if [ "$#" -eq 2 ]
	then
		if [ "$2" == '-v' ]
		then
			./spin-singleuse -v -p 4 build/example_gen/test '.class' junit-4.12.jar hamcrest-all-1.3.jar
		else
			print_help
			exit 1
		fi
	else
		./spin-singleuse -v -p 4 build/example_gen/test '.class' junit-4.12.jar hamcrest-all-1.3.jar &> spin.log
	fi
	echo '============== Validating Results  =============='
	python3 spin/suite_autogen.py --validate gen_file ./config/db_config.txt 0
	if [ "$?" -eq 0 ]
	then
		echo '>>>> PASS'
	else
		echo '>>>> FAILED'
	fi
	echo '============== System Tests (Complete) =============='
	cd $curr_dir

elif [ "$1" == '--clean' ]
then
	clean
else
	print_help
	exit 1
fi
