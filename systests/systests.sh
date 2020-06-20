#!/bin/bash

help () {
	echo 'USAGE: systests.sh <action>'
	echo -e "\t"'action:'
	echo -e "\t\t"'--run [-v]: runs the Spin system tests.'
	echo -e "\t\t\t"'-v: outputs Spin to the console rather than redirecting to a log file.'
	echo -e "\t\t"'--clean: cleans up the auto-generated system tests.'
}

if [ "$1" == '--run' ]
then
	curr_dir=$(pwd)

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
	mkdir config && \
	cp ../config/db_config.txt config/ && \
	echo '>>>> Auto-generating the project to test.'
	echo 'TODO: auto-gen tool under development'
	echo '>>>> Compiling the auto-generated project.'
	echo 'TODO: auto-gen tool under development'

	echo '============== System Tests (Run) ==============' && \
	echo '>>>> Redirecting all output to file: spin.log'
	echo 'TODO: auto-gen tool under development'
	echo '============== System Tests (Complete) =============='
	cd $curr_dir

elif [ "$1" == '--clean' ]
then
	ant clean
	rm *.jar
	rm spin-singleuse
	rm -rf config
	rm gen_file
else
	help
	exit 1
fi
