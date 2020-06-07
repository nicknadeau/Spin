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
	echo '>>>> Auto-generating the project to test.' && \
	python spin/java_class_tool.py --generate 3 3 4 'empty' && \
	echo '>>>> Compiling the auto-generated project.' && \
	ant build && \
	echo '============== System Tests (Run) ==============' && \
	echo '>>>> Redirecting all output to file: spin.log'

	if [ $# -eq 2 ]
	then
		if [ "$2" == '-v' ]
		then
			./spin-singleuse -v -p 4 build/test
		else
			help
			exit 1
		fi
	else
		./spin-singleuse -v -p 4 build/test &> spin.log
	fi

	echo '============== System Tests (Complete) =============='
	cd $curr_dir

elif [ "$1" == '--clean' ]
then
	ant clean
	rm spin.log
	rm *.jar
	rm spin-singleuse
	rm -rf config
else
	help
	exit 1
fi
